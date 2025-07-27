package com.enokdev.springapigenerator.generator

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.ProjectTypeDetectionService
import com.enokdev.springapigenerator.service.TemplateCustomizationService
import com.enokdev.springapigenerator.service.CodeStyleDetector
import com.enokdev.springapigenerator.service.CodeStyleAdapter
import com.enokdev.springapigenerator.service.TemplateErrorHandler
import com.enokdev.springapigenerator.service.TemplateCacheService
import com.intellij.openapi.project.Project
import freemarker.template.Configuration
import freemarker.template.TemplateException
import java.io.File
import java.io.StringWriter
import java.nio.file.Paths

/**
 * Abstract base class for template-based code generators.
 */
abstract class AbstractTemplateCodeGenerator : CodeGenerator {
    
    /**
     * Get the appropriate template name for the project type.
     * Subclasses can override this method to provide custom template selection logic.
     */
    protected open fun getTemplateName(project: Project, forceLanguage: String? = null): String {
        val baseTemplateName = getBaseTemplateName()
        val shouldUseKotlin = when (forceLanguage?.lowercase()) {
            "kotlin", "kt" -> true
            "java" -> false
            else -> isKotlinProject(project)
        }

        // Si le nom de template contient déjà l'extension, on fait le remplacement
        return if (baseTemplateName.contains(".java.ft") || baseTemplateName.contains(".kt.ft")) {
            if (shouldUseKotlin) {
                baseTemplateName.replace(".java.ft", ".kt.ft")
            } else {
                baseTemplateName.replace(".kt.ft", ".java.ft")
            }
        } else {
            // Si c'est juste un nom de base, on ajoute l'extension appropriée
            if (shouldUseKotlin) {
                "$baseTemplateName.kt.ft"
            } else {
                "$baseTemplateName.java.ft"
            }
        }
    }

    /**
     * Get the base template name (Java version).
     * Subclasses must implement this method.
     */
    protected abstract fun getBaseTemplateName(): String

    /**
     * Check if the project is a Kotlin project.
     */
    protected fun isKotlinProject(project: Project): Boolean {
        return ProjectTypeDetectionService.shouldGenerateKotlinCode(project)
    }
    
    /**
     * Get file extension based on project type or forced language.
     */
    protected fun getFileExtensionForProject(project: Project, forceLanguage: String? = null): String {
        return when (forceLanguage?.lowercase()) {
            "kotlin", "kt" -> "kt"
            "java" -> "java"
            else -> if (isKotlinProject(project)) "kt" else "java"
        }
    }

    /**
     * Get source root directory based on project language.
     */
    protected fun getSourceRootDirForProject(project: Project, forceLanguage: String? = null): String {
        val language = when (forceLanguage?.lowercase()) {
            "kotlin", "kt" -> ProjectTypeDetectionService.ProjectLanguage.KOTLIN
            "java" -> ProjectTypeDetectionService.ProjectLanguage.JAVA
            else -> null
        }
        return ProjectTypeDetectionService.getSourceRootForLanguage(project, language)
    }

    /**
     * Get test root directory based on project language.
     */
    protected fun getTestRootDirForProject(project: Project, forceLanguage: String? = null): String {
        val language = when (forceLanguage?.lowercase()) {
            "kotlin", "kt" -> ProjectTypeDetectionService.ProjectLanguage.KOTLIN
            "java" -> ProjectTypeDetectionService.ProjectLanguage.JAVA
            else -> null
        }
        return ProjectTypeDetectionService.getTestRootForLanguage(project, language)
    }

    /**
     * Generate code using the template engine with automatic style adaptation and language support.
     * Enhanced to support both Java and Kotlin with user preference override.
     */
    override fun generate(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        return generateWithLanguage(project, entityMetadata, packageConfig, null)
    }

    /**
     * Generate code with specific language override.
     */
    fun generateWithLanguage(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        forceLanguage: String? = null
    ): String {
        // Use progress indicator for better user experience
        return com.intellij.openapi.progress.ProgressManager.getInstance().runProcessWithProgressSynchronously<String, Exception>(
            {
                val progressIndicator = com.intellij.openapi.progress.ProgressManager.getInstance().progressIndicator
                val languageInfo = ProjectTypeDetectionService.getProjectLanguageInfo(project)
                val targetLanguage = forceLanguage ?: if (languageInfo.primaryLanguage == ProjectTypeDetectionService.ProjectLanguage.KOTLIN) "kotlin" else "java"

                progressIndicator?.text = "Generating ${targetLanguage.uppercase()} code for ${entityMetadata.className}"

                // Get template cache service
                val templateCacheService = TemplateCacheService.getInstance(project)
                
                // Get cached configuration or create a new one
                progressIndicator?.text2 = "Preparing template configuration"
                val configKey = "default_${project.locationHash}"
                val cfg = templateCacheService.getConfiguration(configKey) { createFreemarkerConfig(project) }
                
                val templateName = getTemplateName(project, forceLanguage)

                // Get the template from cache with better error handling
                progressIndicator?.text2 = "Loading template: $templateName"
                progressIndicator?.fraction = 0.2
                val template = try {
                    templateCacheService.getTemplate(cfg, templateName)
                } catch (e: Exception) {
                    val errorMsg = "Failed to load template '$templateName': ${e.message}"
                    com.intellij.openapi.diagnostic.Logger.getInstance(javaClass).error(errorMsg, e)
                    throw RuntimeException(errorMsg, e)
                }
        
                // Detect project code style
                progressIndicator?.text2 = "Detecting code style"
                progressIndicator?.fraction = 0.4
                val codeStyleDetector = CodeStyleDetector()
                val styleConfig = codeStyleDetector.detectCodeStyle(project)
                val styleAdapter = CodeStyleAdapter(styleConfig)
        
                // Create enhanced data model with style information and language support
                progressIndicator?.text2 = "Creating data model"
                progressIndicator?.fraction = 0.6
                val dataModel = createDataModel(entityMetadata, packageConfig, project, styleAdapter)

                // Add language-specific data
                val isKotlinGeneration = forceLanguage?.lowercase() == "kotlin" ||
                    (forceLanguage == null && ProjectTypeDetectionService.shouldGenerateKotlinCode(project))

                dataModel["isKotlinProject"] = isKotlinGeneration
                dataModel["isJavaProject"] = !isKotlinGeneration
                dataModel["targetLanguage"] = if (isKotlinGeneration) "kotlin" else "java"
                dataModel["projectLanguageInfo"] = languageInfo
                dataModel["isMixedProject"] = languageInfo.isMixed

                dataModel["kotlinSupport"] = mapOf(
                    "nullSafety" to isKotlinGeneration,
                    "dataClasses" to isKotlinGeneration,
                    "extensionFunctions" to isKotlinGeneration,
                    "propertyAccess" to isKotlinGeneration,
                    "coroutines" to isKotlinGeneration
                )

                // Add language-specific imports
                dataModel["languageSpecificImports"] = generateLanguageSpecificImports(isKotlinGeneration, entityMetadata)

                // Process the template
                progressIndicator?.text2 = "Processing template"
                progressIndicator?.fraction = 0.8
                val writer = StringWriter()
                try {
                    template.process(dataModel, writer)
                } catch (e: TemplateException) {
                    // Create detailed error message using our custom error handler
                    val detailedErrorMsg = TemplateErrorHandler.createDetailedErrorMessage(e, templateName)

                    // Log the detailed error
                    com.intellij.openapi.diagnostic.Logger.getInstance(javaClass).error(detailedErrorMsg, e)

                    // Provide a more helpful error message to the user
                    throw RuntimeException(
                        """
                        |Template processing error in '$templateName':
                        |${e.message}
                        |
                        |Please check the IDE log for more details.
                        |If this is a custom template, verify the template syntax.
                        """.trimMargin(), e)
                }
        
                // Apply style adaptation to the generated code
                progressIndicator?.text2 = "Adapting code style"
                progressIndicator?.fraction = 0.95
                val generatedCode = writer.toString()
                val adaptedCode = styleAdapter.adaptCode(generatedCode)
                
                // Return the adapted code
                adaptedCode
            },
            "Generating Spring Boot Code",
            true,
            project
        )
    }

    /**
     * Generate imports specific to the target language.
     */
    private fun generateLanguageSpecificImports(isKotlin: Boolean, entityMetadata: EntityMetadata): List<String> {
        val imports = mutableListOf<String>()

        if (isKotlin) {
            // Kotlin-specific imports
            imports.addAll(listOf(
                "import kotlin.jvm.JvmStatic",
                "import kotlin.collections.*"
            ))
        } else {
            // Java-specific imports
            imports.addAll(listOf(
                "import java.util.*;",
                "import java.util.stream.*;"
            ))
        }

        return imports
    }

    /**
     * Create the data model for the template.
     */
    protected open fun createDataModel(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): MutableMap<String, Any> {
        val model = mutableMapOf<String, Any>()

        // Add entity metadata
        model["className"] = entityMetadata.className
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["idType"] = extractSimpleTypeName(entityMetadata.idType)
        model["fields"] = entityMetadata.fields
        model["tableName"] = entityMetadata.tableName

        // Add package information
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        model["packageName"] = basePackage
        model["domainPackage"] = packageConfig["domainPackage"] ?: entityMetadata.domainPackage
        model["dtoPackage"] = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage
        model["repositoryPackage"] = packageConfig["repositoryPackage"] ?: entityMetadata.repositoryPackage
        model["servicePackage"] = packageConfig["servicePackage"] ?: entityMetadata.servicePackage
        model["mapperPackage"] = packageConfig["mapperPackage"] ?: entityMetadata.mapperPackage
        model["controllerPackage"] = packageConfig["controllerPackage"] ?: entityMetadata.controllerPackage

        return model
    }

    /**
     * Create the data model for the template with project context for dependency checking.
     */
    protected open fun createDataModel(entityMetadata: EntityMetadata, packageConfig: Map<String, String>, project: Project): MutableMap<String, Any> {
        val model = createDataModel(entityMetadata, packageConfig)

        // Add dependency validation information
        model["hasValidationDependency"] = com.enokdev.springapigenerator.service.DependencyValidationService.hasRequiredDependencies(project, "validation")
        model["hasSwaggerDependency"] = com.enokdev.springapigenerator.service.DependencyValidationService.hasRequiredDependencies(project, "swagger")
        model["hasSecurityDependency"] = com.enokdev.springapigenerator.service.DependencyValidationService.hasRequiredDependencies(project, "security")
        model["hasGraphQLDependency"] = com.enokdev.springapigenerator.service.DependencyValidationService.hasRequiredDependencies(project, "graphql")
        model["hasMapStructDependency"] = com.enokdev.springapigenerator.service.DependencyValidationService.hasRequiredDependencies(project, "mapstruct")

        return model
    }

    /**
     * Create the data model for the template with style adapter support.
     */
    protected open fun createDataModel(entityMetadata: EntityMetadata, packageConfig: Map<String, String>, styleAdapter: CodeStyleAdapter): MutableMap<String, Any> {
        val model = createDataModel(entityMetadata, packageConfig)

        // Add style-aware helpers to the template context
        model["styleAdapter"] = styleAdapter
        model["indent"] = styleAdapter.getIndentation()
        model["indent2"] = styleAdapter.getIndentation(2)
        model["indent3"] = styleAdapter.getIndentation(3)

        // Add style-adapted field and method names
        val adaptedFields = entityMetadata.fields.map { field ->
            mapOf(
                "name" to field.name,
                "adaptedName" to styleAdapter.adaptFieldName(field.name),
                "type" to field.type,
                "getterName" to styleAdapter.formatGetterName(field.name, field.type == "Boolean"),
                "setterName" to styleAdapter.formatSetterName(field.name),
                "nullable" to field.nullable,
                "columnName" to field.columnName,
                "relationType" to field.relationType
            )
        }
        model["adaptedFields"] = adaptedFields

        return model
    }

    /**
     * Create the data model for the template with project context and style adapter support.
     */
    protected open fun createDataModel(entityMetadata: EntityMetadata, packageConfig: Map<String, String>, project: Project, styleAdapter: CodeStyleAdapter): MutableMap<String, Any> {
        val model = createDataModel(entityMetadata, packageConfig, project)

        // Add style-aware helpers to the template context
        model["styleAdapter"] = styleAdapter
        model["indent"] = styleAdapter.getIndentation()
        model["indent2"] = styleAdapter.getIndentation(2)
        model["indent3"] = styleAdapter.getIndentation(3)

        // Add style-adapted field and method names
        val adaptedFields = entityMetadata.fields.map { field ->
            mapOf(
                "name" to field.name,
                "adaptedName" to styleAdapter.adaptFieldName(field.name),
                "type" to field.type,
                "getterName" to styleAdapter.formatGetterName(field.name, field.type == "Boolean"),
                "setterName" to styleAdapter.formatSetterName(field.name),
                "nullable" to field.nullable,
                "columnName" to field.columnName,
                "relationType" to field.relationType
            )
        }
        model["adaptedFields"] = adaptedFields

        return model
    }

    /**
     * Create and configure the FreeMarker template engine with custom template support.
     */
    protected fun createFreemarkerConfig(project: Project): Configuration {
        val cfg = Configuration(Configuration.VERSION_2_3_30)

        // Get template customization service
        val templateService = project.getService(TemplateCustomizationService::class.java)

        // Set up template loaders with priority: custom templates first, then built-in templates
        val templateLoaders = mutableListOf<freemarker.cache.TemplateLoader>()

        // Add custom template directories if they exist
        val projectTemplatesDir = File(templateService.getProjectTemplatesDirectory())
        if (projectTemplatesDir.exists()) {
            templateLoaders.add(freemarker.cache.FileTemplateLoader(projectTemplatesDir))
        }

        val userTemplatesDir = File(templateService.getUserTemplatesDirectory())
        if (userTemplatesDir.exists()) {
            templateLoaders.add(freemarker.cache.FileTemplateLoader(userTemplatesDir))
        }

        // Add built-in templates as fallback
        val classLoader = javaClass.classLoader
        templateLoaders.add(freemarker.cache.ClassTemplateLoader(classLoader, "templates"))

        // Create multi-template loader
        if (templateLoaders.size > 1) {
            cfg.templateLoader = freemarker.cache.MultiTemplateLoader(templateLoaders.toTypedArray())
        } else {
            cfg.templateLoader = templateLoaders.first()
        }

        cfg.defaultEncoding = "UTF-8"
        
        // Use enhanced template error handler for better error reporting and graceful fallbacks
        cfg.templateExceptionHandler = TemplateErrorHandler()
        
        cfg.logTemplateExceptions = true
        cfg.wrapUncheckedExceptions = true
        cfg.fallbackOnNullLoopVariable = false
        return cfg
    }

    /**
     * Get the appropriate source root directory based on the project type.
     */
    protected fun getSourceRootDirForProject(project: Project): String {
        val basePath = project.basePath ?: throw RuntimeException("Project base path not found")
        val language = if (ProjectTypeDetectionService.shouldGenerateKotlinCode(project)) "kotlin" else "java"
        return Paths.get(basePath, "src", "main", language).toString()
    }

    /**
     * Get the appropriate test root directory based on the project type.
     */
    protected fun getTestRootDirForProject(project: Project): String {
        val basePath = project.basePath ?: throw RuntimeException("Project base path not found")
        val language = if (ProjectTypeDetectionService.shouldGenerateKotlinCode(project)) "kotlin" else "java"
        return Paths.get(basePath, "src", "test", language).toString()
    }

    /**
     * Extract the simple name of a type (without package).
     */
    protected fun extractSimpleTypeName(fullTypeName: String): String {
        return fullTypeName.substringAfterLast(".")
    }
}

package com.enokdev.springapigenerator.generator

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.ProjectTypeDetectionService
import com.enokdev.springapigenerator.service.TemplateCustomizationService
import com.enokdev.springapigenerator.service.CodeStyleDetector
import com.enokdev.springapigenerator.service.CodeStyleAdapter
import com.intellij.openapi.project.Project
import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateException
import freemarker.template.TemplateExceptionHandler
import java.io.File
import java.io.StringWriter
import java.nio.file.Paths

/**
 * Abstract base class for template-based code generators.
 */
abstract class AbstractTemplateCodeGenerator(
    private val javaTemplateName: String
) : CodeGenerator {

    /**
     * Get the Kotlin template name based on the Java template name.
     */
    private fun getKotlinTemplateName(): String {
        // Convert .java.ft to .kt.ft
        return javaTemplateName.replace(".java.ft", ".kt.ft")
    }

    /**
     * Get the appropriate template name based on the project type.
     */
    private fun getTemplateNameForProject(project: Project): String {
        return if (ProjectTypeDetectionService.shouldGenerateKotlinCode(project)) {
            getKotlinTemplateName()
        } else {
            javaTemplateName
        }
    }

    /**
     * Generate code using the template engine with automatic style adaptation and Kotlin support.
     */
    override fun generate(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val templateName = getTemplateNameForProject(project)
        val template = cfg.getTemplate(templateName)

        // Detect project code style
        val codeStyleDetector = CodeStyleDetector()
        val styleConfig = codeStyleDetector.detectCodeStyle(project)
        val styleAdapter = CodeStyleAdapter(styleConfig)

        // Create enhanced data model with style information and Kotlin support
        val dataModel = createDataModel(entityMetadata, packageConfig, styleAdapter)

        // Add Kotlin-specific data
        dataModel["isKotlinProject"] = ProjectTypeDetectionService.shouldGenerateKotlinCode(project)
        dataModel["kotlinSupport"] = mapOf(
            "nullSafety" to true,
            "dataClasses" to true,
            "extensionFunctions" to true,
            "propertyAccess" to true
        )

        val writer = StringWriter()
        try {
            template.process(dataModel, writer)
        } catch (e: TemplateException) {
            throw RuntimeException("Error processing template: ${e.message}", e)
        }

        // Apply style adaptation to the generated code
        val generatedCode = writer.toString()
        return styleAdapter.adaptCode(generatedCode)
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
        cfg.templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        cfg.logTemplateExceptions = false
        cfg.wrapUncheckedExceptions = true
        cfg.fallbackOnNullLoopVariable = false
        return cfg
    }

    /**
     * Get the appropriate file extension (.java or .kt) based on the project type.
     */
    protected fun getFileExtensionForProject(project: Project): String {
        return if (ProjectTypeDetectionService.shouldGenerateKotlinCode(project)) {
            "kt"
        } else {
            "java"
        }
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

package com.enokdev.springapigenerator.generator

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.ProjectTypeDetectionService
import com.intellij.openapi.project.Project
import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateException
import freemarker.template.TemplateExceptionHandler
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
     * Generate code using the template engine.
     */
    override fun generate(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig()
        val templateName = getTemplateNameForProject(project)
        val template = cfg.getTemplate(templateName)
        val dataModel = createDataModel(entityMetadata, packageConfig)

        val writer = StringWriter()
        try {
            template.process(dataModel, writer)
        } catch (e: TemplateException) {
            throw RuntimeException("Error processing template: ${e.message}", e)
        }

        return writer.toString()
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
     * Create and configure the FreeMarker template engine.
     */
    protected fun createFreemarkerConfig(): Configuration {
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")
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

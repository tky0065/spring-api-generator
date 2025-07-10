package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for Swagger configuration.
 */
class SwaggerConfigGenerator : AbstractTemplateCodeGenerator("SwaggerConfig.java.ft") {

    companion object {
        // Templates Java et Kotlin
        const val SWAGGER_CONFIG_TEMPLATE_JAVA = "SwaggerConfig.java.ft"
        const val SWAGGER_CONFIG_TEMPLATE_KOTLIN = "SwaggerConfig.kt.ft"
    }

    /**
     * Détermine le template à utiliser en fonction du type de projet.
     */
    private fun getSwaggerConfigTemplate(project: Project): String {
        return if (com.enokdev.springapigenerator.service.ProjectTypeDetectionService.shouldGenerateKotlinCode(project)) {
            SWAGGER_CONFIG_TEMPLATE_KOTLIN
        } else {
            SWAGGER_CONFIG_TEMPLATE_JAVA
        }
    }

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val configPackage = packageConfig["configPackage"] ?: "$basePackage.config"
        val configDir = configPackage.replace(".", "/")
        val extension = getFileExtensionForProject(project)
        val fileName = "SwaggerConfig.$extension"
        return Paths.get(sourceRoot, configDir, fileName).toString()
    }

    /**
     * Generate code using the template engine.
     */
    override fun generate(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig()
        val template = cfg.getTemplate(getSwaggerConfigTemplate(project))
        val dataModel = createDataModel(entityMetadata, packageConfig)

        val writer = java.io.StringWriter()
        try {
            template.process(dataModel, writer)
        } catch (e: freemarker.template.TemplateException) {
            throw RuntimeException("Error processing template: ${e.message}", e)
        }

        return writer.toString()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val configPackage = packageConfig["configPackage"] ?: "$basePackage.config"
        val apiTitle = "API for ${entityMetadata.className}"
        val apiDescription = "REST API for managing ${entityMetadata.className} resources"
        val apiVersion = "1.0"
        val apiLicense = "MIT"
        val apiContact = "contact@example.com"

        model["configPackage"] = configPackage
        model["apiTitle"] = apiTitle
        model["apiDescription"] = apiDescription
        model["apiVersion"] = apiVersion
        model["apiLicense"] = apiLicense
        model["apiContact"] = apiContact
        model["basePackage"] = basePackage

        return model
    }
}

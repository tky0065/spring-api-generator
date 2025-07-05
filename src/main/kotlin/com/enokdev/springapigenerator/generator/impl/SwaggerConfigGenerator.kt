package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for Swagger configuration.
 */
class SwaggerConfigGenerator : AbstractTemplateCodeGenerator("SwaggerConfig.java.ft") {

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val configPackage = packageConfig["configPackage"] ?: "$basePackage.config"
        val configDir = configPackage.replace(".", "/")
        val fileName = "SwaggerConfig.java"
        return Paths.get(sourceRoot, configDir, fileName).toString()
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

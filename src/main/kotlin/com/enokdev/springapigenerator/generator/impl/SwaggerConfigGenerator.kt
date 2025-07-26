package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for Swagger configuration.
 */
class SwaggerConfigGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "SwaggerConfig.java.ft"
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

    override fun createDataModel(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val configPackage = packageConfig["configPackage"] ?: "$basePackage.config"

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["packageName"] = configPackage
        model["basePackage"] = basePackage
        model["className"] = "SwaggerConfig"
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // ========== VARIABLES POUR SWAGGER/API DOCUMENTATION ==========
        model["apiTitle"] = "${entityMetadata.className} API"
        model["apiDescription"] = "API for managing ${entityMetadata.className} entities"
        model["apiVersion"] = "1.0.0"
        model["apiLicense"] = "Apache 2.0"
        model["apiLicenseUrl"] = "http://www.apache.org/licenses/LICENSE-2.0.html"
        model["apiContact"] = "developer@example.com"
        model["apiContactName"] = "API Support"
        model["apiContactUrl"] = "https://example.com/support"

        // ========== VARIABLES POUR LES IMPORTS ET MÉTHODES PERSONNALISÉES ==========
        model["imports"] = ""
        model["customMethods"] = ""

        return model
    }
}

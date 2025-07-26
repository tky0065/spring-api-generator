package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for OpenAPI 3.0 documentation configuration.
 * This generator creates the necessary configuration for Spring Boot applications
 * to expose OpenAPI 3.0 documentation via springdoc-openapi.
 */
class OpenApiConfigGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "OpenApiConfig.java.ft"
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
        val fileName = "OpenApiConfig.$extension"
        return Paths.get(sourceRoot, configDir, fileName).toString()
    }

    override fun createDataModel(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val configPackage = packageConfig["configPackage"] ?: "$basePackage.config"

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["packageName"] = configPackage
        model["basePackage"] = basePackage
        model["className"] = "OpenApiConfig"
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // ========== VARIABLES POUR OPENAPI DOCUMENTATION ==========
        model["apiTitle"] = "${entityMetadata.className} API Documentation"
        model["apiDescription"] = "OpenAPI 3.0 documentation for ${entityMetadata.className} REST API"
        model["apiVersion"] = "1.0.0"
        model["apiTermsOfService"] = "https://example.com/terms"
        model["contactName"] = "API Support"
        model["contactEmail"] = "support@example.com"
        model["contactUrl"] = "https://example.com/support"
        model["licenseName"] = "Apache 2.0"
        model["licenseUrl"] = "https://www.apache.org/licenses/LICENSE-2.0"

        // ========== VARIABLES POUR LES IMPORTS ET MÉTHODES PERSONNALISÉES ==========
        model["imports"] = ""
        model["customMethods"] = ""

        return model
    }
}

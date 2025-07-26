package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for global exception handler.
 */
class GlobalExceptionHandlerGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "GlobalExceptionHandler.java.ft"
    }

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val exceptionPackage = packageConfig["exceptionPackage"] ?: "$basePackage.exception"
        val exceptionDir = exceptionPackage.replace(".", "/")
        val extension = getFileExtensionForProject(project)
        return Paths.get(sourceRoot, exceptionDir, "GlobalExceptionHandler.$extension").toString()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val exceptionPackage = packageConfig["exceptionPackage"] ?: "$basePackage.exception"

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["packageName"] = exceptionPackage
        model["basePackage"] = basePackage
        model["className"] = "GlobalExceptionHandler"
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["exceptionPackage"] = exceptionPackage

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // ========== VARIABLES POUR LES IMPORTS ET MÉTHODES PERSONNALISÉES ==========
        model["imports"] = ""
        model["customMethods"] = ""

        return model
    }
}

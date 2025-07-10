package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for global exception handler.
 */
class GlobalExceptionHandlerGenerator : AbstractTemplateCodeGenerator("GlobalExceptionHandler.java.ft") {

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

        // Add exception handler specific data
        model["exceptionPackage"] = exceptionPackage
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["entityName"] = entityMetadata.className

        return model
    }
}

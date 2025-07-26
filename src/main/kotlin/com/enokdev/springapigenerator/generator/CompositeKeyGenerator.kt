package com.enokdev.springapigenerator.generator

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.*
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Paths

/**
 * Generator for composite key classes.
 * Supports both Java and Kotlin code generation.
 */
class CompositeKeyGenerator : IncrementalCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "CompositeKey.java.ft"
    }

    /**
     * Generate composite key code with language detection
     */
    fun generateCompositeKey(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        styleAdapter: CodeStyleAdapter,
        project: Project,
        outputDir: File
    ): File {
        val generatedCode = generate(project, entityMetadata, packageConfig)

        // Write to output file
        val extension = getFileExtensionForProject(project)
        val fileName = "${entityMetadata.className}Id.$extension"
        val outputFile = File(outputDir, fileName)
        outputFile.writeText(generatedCode)

        return outputFile
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        styleAdapter: CodeStyleAdapter
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig, styleAdapter)

        // Generate composite key fields based on entity metadata
        val keyFields = generateCompositeKeyFields(entityMetadata)

        // Add composite key specific data
        model["className"] = "${entityMetadata.className}Id"
        model["entityName"] = entityMetadata.className
        model["keyFields"] = keyFields
        model["packageName"] = packageConfig["idPackage"] ?: "${entityMetadata.entityBasePackage}.id"

        return model
    }

    private fun generateCompositeKeyFields(entityMetadata: EntityMetadata): List<Map<String, Any>> {
        // Extract key fields from entity metadata
        // In a real implementation, this would analyze @Id annotations or composite key definitions
        val keyFields = mutableListOf<Map<String, Any>>()

        // For demo purposes, create mock composite key fields
        if (entityMetadata.className.contains("User")) {
            keyFields.add(mapOf(
                "name" to "userId",
                "type" to "Long",
                "nullable" to false,
                "columnName" to "user_id"
            ))
            keyFields.add(mapOf(
                "name" to "tenantId",
                "type" to "String",
                "nullable" to false,
                "columnName" to "tenant_id"
            ))
        } else {
            // Default composite key structure
            keyFields.add(mapOf(
                "name" to "field1",
                "type" to "Long",
                "nullable" to false,
                "columnName" to "field1"
            ))
            keyFields.add(mapOf(
                "name" to "field2",
                "type" to "String",
                "nullable" to false,
                "columnName" to "field2"
            ))
        }

        return keyFields
    }

    /**
     * Get the target file path for the generated composite key.
     */
    override fun getTargetFilePath(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRootDir = getSourceRootDirForProject(project)
        val idPackage = packageConfig["idPackage"] ?: "${entityMetadata.packageName}.id"
        val packagePath = idPackage.replace(".", File.separator)
        val fileExtension = getFileExtensionForProject(project)
        return Paths.get(sourceRootDir, packagePath, "${entityMetadata.className}Id.$fileExtension").toString()
    }
}

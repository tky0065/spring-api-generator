package com.enokdev.springapigenerator.generator

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.*
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Paths

/**
 * Generator for embedded ID classes.
 * Supports both Java and Kotlin code generation.
 */
class EmbeddedIdGenerator : IncrementalCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "EmbeddedId.java.ft"
    }

    /**
     * Generate embedded ID code with language detection
     */
    fun generateEmbeddedId(
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

        // Generate embedded ID fields based on entity metadata
        val embeddedFields = generateEmbeddedIdFields(entityMetadata)

        // Add embedded ID specific data
        model["className"] = "${entityMetadata.className}Id"
        model["entityName"] = entityMetadata.className
        model["embeddedFields"] = embeddedFields
        model["packageName"] = packageConfig["idPackage"] ?: "${entityMetadata.packageName}.id"
        model["generateQueryMethods"] = true

        return model
    }

    private fun generateEmbeddedIdFields(entityMetadata: EntityMetadata): List<Map<String, Any>> {
        // Extract embedded fields from entity metadata
        val embeddedFields = mutableListOf<Map<String, Any>>()

        // For demo purposes, create mock embedded ID fields based on entity type
        when {
            entityMetadata.className.contains("Order") -> {
                embeddedFields.add(mapOf(
                    "name" to "orderNumber",
                    "type" to "String",
                    "nullable" to false,
                    "columnName" to "order_number"
                ))
                embeddedFields.add(mapOf(
                    "name" to "orderDate",
                    "type" to "LocalDate",
                    "nullable" to false,
                    "columnName" to "order_date"
                ))
            }
            entityMetadata.className.contains("Product") -> {
                embeddedFields.add(mapOf(
                    "name" to "productCode",
                    "type" to "String",
                    "nullable" to false,
                    "columnName" to "product_code"
                ))
                embeddedFields.add(mapOf(
                    "name" to "version",
                    "type" to "Integer",
                    "nullable" to false,
                    "columnName" to "version"
                ))
            }
            else -> {
                // Default embedded ID structure
                embeddedFields.add(mapOf(
                    "name" to "identifier",
                    "type" to "String",
                    "nullable" to false,
                    "columnName" to "identifier"
                ))
                embeddedFields.add(mapOf(
                    "name" to "sequence",
                    "type" to "Long",
                    "nullable" to false,
                    "columnName" to "sequence"
                ))
            }
        }

        return embeddedFields
    }

    /**
     * Get the target file path for the generated embedded ID.
     */
    override fun getTargetFilePath(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRootDir = getSourceRootDirForProject(project)
        val idPackage = packageConfig["idPackage"] ?: "${entityMetadata.packageName}.id"
        val packagePath = idPackage.replace(".", File.separator)
        val fileExtension = getFileExtensionForProject(project)
        return Paths.get(sourceRootDir, packagePath, "${entityMetadata.className}Id.$fileExtension").toString()
    }
}

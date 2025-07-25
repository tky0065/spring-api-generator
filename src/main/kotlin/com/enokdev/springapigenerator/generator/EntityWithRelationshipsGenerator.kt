package com.enokdev.springapigenerator.generator

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.*
import com.enokdev.springapigenerator.service.ComplexRelationshipAnalyzer.RelationshipInfo
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Paths

/**
 * Enhanced entity generator with bidirectional relationship management.
 * Supports both Java and Kotlin code generation.
 */
class EntityWithRelationshipsGenerator(
    javaTemplateName: String = "EntityWithRelationships.java.ft",
    private val kotlinTemplateName: String = "EntityWithRelationships.kt.ft"
) : IncrementalCodeGenerator(javaTemplateName) {

    private val relationshipAnalyzer = ComplexRelationshipAnalyzer()
    private val relationshipManager = BidirectionalRelationshipManager()

    /**
     * Generate entity with relationships code with language detection
     */
    fun generateEntityWithRelationships(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        styleAdapter: CodeStyleAdapter,
        project: Project,
        outputDir: File
    ): File {
        val isKotlinProject = detectKotlinProject(project)

        // Create a temporary generator with the appropriate template
        val generator = if (isKotlinProject) {
            EntityWithRelationshipsGenerator(kotlinTemplateName, kotlinTemplateName)
        } else {
            this
        }

        val generatedCode = generator.generate(project, entityMetadata, packageConfig)

        // Write to output file
        val fileName = "${entityMetadata.className}.${if (isKotlinProject) "kt" else "java"}"
        val outputFile = File(outputDir, fileName)
        outputFile.writeText(generatedCode)

        return outputFile
    }

    /**
     * Detect if the project uses Kotlin
     */
    private fun detectKotlinProject(project: Project): Boolean {
        val projectPath = project.basePath ?: return false
        val kotlinFiles = File(projectPath).walkTopDown()
            .filter { it.extension == "kt" }
            .take(1)
        return kotlinFiles.any()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        styleAdapter: CodeStyleAdapter
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig, styleAdapter)

        // Analyze relationships for this entity
        val relationships = analyzeEntityRelationships(entityMetadata)

        // Generate bidirectional sync methods
        val syncMethods = generateSyncMethodsForEntity(entityMetadata, relationships, styleAdapter)
        val helperMethods = relationshipManager.generateRelationshipHelperMethods(entityMetadata, relationships, styleAdapter)
        val cascadeMethods = generateCascadeMethodsForEntity(relationships, styleAdapter)

        // Add relationship-specific data to the model
        model["relationships"] = relationships
        model["bidirectionalSyncMethods"] = syncMethods
        model["relationshipHelperMethods"] = helperMethods
        model["cascadeHandlingMethods"] = cascadeMethods
        model["relationshipImports"] = collectRelationshipImports(relationships, syncMethods)

        return model
    }

    /**
     * Analyzes relationships for the current entity.
     */
    private fun analyzeEntityRelationships(entityMetadata: EntityMetadata): List<RelationshipInfo> {
        // This would normally use PSI analysis, but for now we'll create mock relationships
        // based on the entity fields to demonstrate the functionality
        return entityMetadata.fields.mapNotNull { field ->
            when {
                field.relationType.name.contains("ONE_TO_MANY") -> createMockOneToManyRelationship(entityMetadata, field)
                field.relationType.name.contains("MANY_TO_ONE") -> createMockManyToOneRelationship(entityMetadata, field)
                field.relationType.name.contains("ONE_TO_ONE") -> createMockOneToOneRelationship(entityMetadata, field)
                field.relationType.name.contains("MANY_TO_MANY") -> createMockManyToManyRelationship(entityMetadata, field)
                else -> null
            }
        }
    }

    /**
     * Generates synchronization methods for all bidirectional relationships.
     */
    private fun generateSyncMethodsForEntity(
        entityMetadata: EntityMetadata,
        relationships: List<RelationshipInfo>,
        styleAdapter: CodeStyleAdapter
    ): Map<String, Any> {
        val syncMethods = mutableMapOf<String, Any>()

        relationships.filter { it.isBidirectional }.forEach { relationship ->
            val methods = relationshipManager.generateBidirectionalSyncMethods(entityMetadata, relationship, styleAdapter)
            val fieldName = relationship.fieldName

            syncMethods[fieldName] = mapOf(
                "addMethod" to methods.addMethod,
                "removeMethod" to methods.removeMethod,
                "setMethod" to (methods.setMethod ?: ""),
                "clearMethod" to (methods.clearMethod ?: ""),
                "imports" to methods.imports
            )
        }

        return syncMethods
    }

    /**
     * Generates cascade handling methods for the entity.
     */
    private fun generateCascadeMethodsForEntity(
        relationships: List<RelationshipInfo>,
        styleAdapter: CodeStyleAdapter
    ): String {
        val cascadeMethods = StringBuilder()

        relationships.forEach { relationship ->
            val cascadeMethod = relationshipManager.generateCascadeHandling(relationship, styleAdapter)
            if (cascadeMethod.isNotEmpty()) {
                cascadeMethods.append(cascadeMethod).append("\n\n")
            }
        }

        return cascadeMethods.toString().trimEnd()
    }

    /**
     * Collects all imports needed for relationship management.
     */
    private fun collectRelationshipImports(
        relationships: List<RelationshipInfo>,
        syncMethods: Map<String, Any>
    ): Set<String> {
        val imports = mutableSetOf<String>()

        // Add JPA imports
        imports.add("javax.persistence.*")

        // Add collection imports based on relationship types
        relationships.forEach { relationship ->
            when (relationship.relationType) {
                com.enokdev.springapigenerator.model.RelationType.ONE_TO_MANY -> {
                    imports.add("java.util.List")
                    imports.add("java.util.ArrayList")
                }
                com.enokdev.springapigenerator.model.RelationType.MANY_TO_MANY -> {
                    imports.add("java.util.Set")
                    imports.add("java.util.HashSet")
                }
                else -> { /* No additional imports needed */ }
            }
        }

        // Add imports from sync methods
        syncMethods.values.forEach { methodData ->
            if (methodData is Map<*, *>) {
                val methodImports = methodData["imports"] as? Set<*>
                methodImports?.forEach { import ->
                    if (import is String) {
                        imports.add(import)
                    }
                }
            }
        }

        return imports
    }

    /**
     * Get the target file path for the generated entity with relationships.
     */
    override fun getTargetFilePath(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRootDir = getSourceRootDirForProject(project)
        val packagePath = entityMetadata.packageName.replace(".", File.separator)
        val fileExtension = getFileExtensionForProject(project)
        return Paths.get(sourceRootDir, packagePath, "${entityMetadata.className}.$fileExtension").toString()
    }

    // Mock relationship creation methods for demonstration

    private fun createMockOneToManyRelationship(entityMetadata: EntityMetadata, field: com.enokdev.springapigenerator.model.EntityField): RelationshipInfo {
        return RelationshipInfo(
            sourceEntity = entityMetadata.qualifiedClassName,
            targetEntity = field.type,
            relationType = com.enokdev.springapigenerator.model.RelationType.ONE_TO_MANY,
            fieldName = field.name,
            mappedBy = "parent",
            cascade = setOf(ComplexRelationshipAnalyzer.CascadeType.PERSIST, ComplexRelationshipAnalyzer.CascadeType.MERGE),
            fetchType = ComplexRelationshipAnalyzer.FetchType.LAZY,
            orphanRemoval = true,
            isBidirectional = true,
            inverseFieldName = "parent",
            isOwnerSide = false
        )
    }

    private fun createMockManyToOneRelationship(entityMetadata: EntityMetadata, field: com.enokdev.springapigenerator.model.EntityField): RelationshipInfo {
        return RelationshipInfo(
            sourceEntity = entityMetadata.qualifiedClassName,
            targetEntity = field.type,
            relationType = com.enokdev.springapigenerator.model.RelationType.MANY_TO_ONE,
            fieldName = field.name,
            cascade = setOf(ComplexRelationshipAnalyzer.CascadeType.PERSIST),
            fetchType = ComplexRelationshipAnalyzer.FetchType.LAZY,
            isBidirectional = true,
            inverseFieldName = "children",
            isOwnerSide = true
        )
    }

    private fun createMockOneToOneRelationship(entityMetadata: EntityMetadata, field: com.enokdev.springapigenerator.model.EntityField): RelationshipInfo {
        return RelationshipInfo(
            sourceEntity = entityMetadata.qualifiedClassName,
            targetEntity = field.type,
            relationType = com.enokdev.springapigenerator.model.RelationType.ONE_TO_ONE,
            fieldName = field.name,
            cascade = setOf(ComplexRelationshipAnalyzer.CascadeType.ALL),
            fetchType = ComplexRelationshipAnalyzer.FetchType.LAZY,
            orphanRemoval = true,
            isBidirectional = true,
            inverseFieldName = "inverse",
            isOwnerSide = true
        )
    }

    private fun createMockManyToManyRelationship(entityMetadata: EntityMetadata, field: com.enokdev.springapigenerator.model.EntityField): RelationshipInfo {
        return RelationshipInfo(
            sourceEntity = entityMetadata.qualifiedClassName,
            targetEntity = field.type,
            relationType = com.enokdev.springapigenerator.model.RelationType.MANY_TO_MANY,
            fieldName = field.name,
            joinTable = "${entityMetadata.className.lowercase()}_${field.name}",
            joinColumns = listOf("${entityMetadata.className.lowercase()}_id"),
            inverseJoinColumns = listOf("${field.name.removeSuffix("s")}_id"),
            cascade = setOf(ComplexRelationshipAnalyzer.CascadeType.PERSIST, ComplexRelationshipAnalyzer.CascadeType.MERGE),
            fetchType = ComplexRelationshipAnalyzer.FetchType.LAZY,
            isBidirectional = true,
            inverseFieldName = "inverse${entityMetadata.className}s",
            isOwnerSide = true
        )
    }
}

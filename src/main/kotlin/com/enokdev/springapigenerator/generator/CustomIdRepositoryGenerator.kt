package com.enokdev.springapigenerator.generator

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.*
import com.enokdev.springapigenerator.service.CustomIdTypeAnalyzer.IdConfiguration
import com.enokdev.springapigenerator.service.CustomIdTypeAnalyzer.IdType
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Paths

/**
 * Repository generator that adapts to custom ID types.
 * Supports both Java and Kotlin code generation.
 */
class CustomIdRepositoryGenerator : IncrementalCodeGenerator() {

    private val idAnalyzer = CustomIdTypeAnalyzer()

    override fun getBaseTemplateName(): String {
        return "CustomIdRepository.java.ft"
    }

    /**
     * Generate repository code with language detection
     */
    fun generateRepository(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        styleAdapter: CodeStyleAdapter,
        project: Project,
        outputDir: File
    ): File {
        val generatedCode = generate(project, entityMetadata, packageConfig)

        // Write to output file
        val extension = getFileExtensionForProject(project)
        val fileName = "${entityMetadata.repositoryName}.$extension"
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

        // Analyze ID configuration
        val idConfig = analyzeIdConfiguration(entityMetadata)

        // Generate custom repository methods
        val customMethods = generateCustomRepositoryMethods(entityMetadata, idConfig, styleAdapter)
        val queryMethods = generateQueryMethods(entityMetadata, idConfig, styleAdapter)

        // Add ID-specific data to model
        model["idConfig"] = createIdConfigModel(idConfig)
        model["customRepositoryMethods"] = customMethods
        model["queryMethods"] = queryMethods
        model["repositoryImports"] = getRepositoryImports(idConfig)
        model["extendsInterface"] = getRepositoryInterface(idConfig)

        return model
    }

    private fun analyzeIdConfiguration(entityMetadata: EntityMetadata): IdConfiguration {
        // For demo purposes, we'll determine ID type based on the idType in metadata
        return when {
            entityMetadata.idType.contains("UUID") -> IdConfiguration(
                idType = IdType.UUID,
                javaType = "UUID",
                generationStrategy = CustomIdTypeAnalyzer.GenerationType.UUID,
                requiresCustomRepository = true
            )
            entityMetadata.idType == "String" -> IdConfiguration(
                idType = IdType.SIMPLE_STRING,
                javaType = "String",
                requiresCustomRepository = true
            )
            entityMetadata.className.contains("Composite") -> IdConfiguration(
                idType = IdType.COMPOSITE_KEY,
                javaType = "${entityMetadata.className}Id",
                compositeKeyClass = "${entityMetadata.entityBasePackage}.id.${entityMetadata.className}Id",
                requiresCustomRepository = true
            )
            else -> IdConfiguration(
                idType = IdType.SIMPLE_LONG,
                javaType = "Long"
            )
        }
    }

    private fun createIdConfigModel(idConfig: IdConfiguration): Map<String, Any> {
        return mapOf(
            "type" to idConfig.idType.name,
            "javaType" to idConfig.javaType,
            "isUuid" to (idConfig.idType == IdType.UUID),
            "isComposite" to (idConfig.idType == IdType.COMPOSITE_KEY),
            "isEmbedded" to (idConfig.idType == IdType.EMBEDDED_ID),
            "isString" to (idConfig.idType == IdType.SIMPLE_STRING),
            "requiresCustomRepository" to idConfig.requiresCustomRepository,
            "compositeKeyClass" to (idConfig.compositeKeyClass ?: ""),
            "embeddedIdClass" to (idConfig.embeddedIdClass ?: "")
        )
    }

    private fun generateCustomRepositoryMethods(
        entityMetadata: EntityMetadata,
        idConfig: IdConfiguration,
        styleAdapter: CodeStyleAdapter
    ): List<String> {
        val methods = mutableListOf<String>()
        val indent = styleAdapter.getIndentation()

        when (idConfig.idType) {
            IdType.UUID -> {
                methods.addAll(generateUuidRepositoryMethods(entityMetadata, styleAdapter))
            }
            IdType.SIMPLE_STRING -> {
                methods.addAll(generateStringRepositoryMethods(entityMetadata, styleAdapter))
            }
            IdType.COMPOSITE_KEY -> {
                methods.addAll(generateCompositeKeyRepositoryMethods(entityMetadata, idConfig, styleAdapter))
            }
            IdType.EMBEDDED_ID -> {
                methods.addAll(generateEmbeddedIdRepositoryMethods(entityMetadata, idConfig, styleAdapter))
            }
            else -> {
                // No custom methods for simple Long/Integer IDs
            }
        }

        return methods
    }

    private fun generateUuidRepositoryMethods(entityMetadata: EntityMetadata, styleAdapter: CodeStyleAdapter): List<String> {
        val indent = styleAdapter.getIndentation()
        val entityName = entityMetadata.className

        return listOf(
            """
            ${indent}/**
            ${indent} * Find entity by UUID string representation.
            ${indent} */
            ${indent}@Query("SELECT e FROM $entityName e WHERE e.id = :uuid")
            ${indent}Optional<$entityName> findByUuidString(@Param("uuid") String uuid);
            """.trimIndent(),

            """
            ${indent}/**
            ${indent} * Check if entity exists by UUID string.
            ${indent} */
            ${indent}@Query("SELECT COUNT(e) > 0 FROM $entityName e WHERE e.id = :uuid")
            ${indent}boolean existsByUuidString(@Param("uuid") String uuid);
            """.trimIndent(),

            """
            ${indent}/**
            ${indent} * Delete entity by UUID string.
            ${indent} */
            ${indent}@Modifying
            ${indent}@Query("DELETE FROM $entityName e WHERE e.id = :uuid")
            ${indent}void deleteByUuidString(@Param("uuid") String uuid);
            """.trimIndent()
        )
    }

    private fun generateStringRepositoryMethods(entityMetadata: EntityMetadata, styleAdapter: CodeStyleAdapter): List<String> {
        val indent = styleAdapter.getIndentation()
        val entityName = entityMetadata.className

        return listOf(
            """
            ${indent}/**
            ${indent} * Find entities by ID containing the specified text (case-insensitive).
            ${indent} */
            ${indent}List<$entityName> findByIdContainingIgnoreCase(String idPart);
            """.trimIndent(),

            """
            ${indent}/**
            ${indent} * Find entities by ID starting with the specified prefix.
            ${indent} */
            ${indent}List<$entityName> findByIdStartingWithIgnoreCase(String prefix);
            """.trimIndent(),

            """
            ${indent}/**
            ${indent} * Find entity by exact ID match (case-insensitive).
            ${indent} */
            ${indent}Optional<$entityName> findByIdIgnoreCase(String id);
            """.trimIndent()
        )
    }

    private fun generateCompositeKeyRepositoryMethods(
        entityMetadata: EntityMetadata,
        idConfig: IdConfiguration,
        styleAdapter: CodeStyleAdapter
    ): List<String> {
        val indent = styleAdapter.getIndentation()
        val entityName = entityMetadata.className
        val keyClass = idConfig.compositeKeyClass?.substringAfterLast(".") ?: "${entityName}Id"

        return listOf(
            """
            ${indent}/**
            ${indent} * Find entities by partial key match.
            ${indent} */
            ${indent}@Query("SELECT e FROM $entityName e WHERE e.id.field1 = :field1")
            ${indent}List<$entityName> findByIdField1(@Param("field1") String field1);
            """.trimIndent(),

            """
            ${indent}/**
            ${indent} * Check if entity exists with composite key.
            ${indent} */
            ${indent}boolean existsById($keyClass id);
            """.trimIndent(),

            """
            ${indent}/**
            ${indent} * Delete entity by composite key.
            ${indent} */
            ${indent}void deleteById($keyClass id);
            """.trimIndent()
        )
    }

    private fun generateEmbeddedIdRepositoryMethods(
        entityMetadata: EntityMetadata,
        idConfig: IdConfiguration,
        styleAdapter: CodeStyleAdapter
    ): List<String> {
        val indent = styleAdapter.getIndentation()
        val entityName = entityMetadata.className
        val embeddedClass = idConfig.embeddedIdClass?.substringAfterLast(".") ?: "${entityName}Id"

        return listOf(
            """
            ${indent}/**
            ${indent} * Find entities by embedded ID field.
            ${indent} */
            ${indent}@Query("SELECT e FROM $entityName e WHERE e.id.field1 = :field1")
            ${indent}List<$entityName> findByEmbeddedIdField1(@Param("field1") String field1);
            """.trimIndent(),

            """
            ${indent}/**
            ${indent} * Check if entity exists with embedded ID.
            ${indent} */
            ${indent}boolean existsById($embeddedClass id);
            """.trimIndent()
        )
    }

    private fun generateQueryMethods(
        entityMetadata: EntityMetadata,
        idConfig: IdConfiguration,
        styleAdapter: CodeStyleAdapter
    ): List<String> {
        val methods = mutableListOf<String>()
        val indent = styleAdapter.getIndentation()
        val entityName = entityMetadata.className

        // Generate common query methods regardless of ID type
        methods.add("""
            ${indent}/**
            ${indent} * Find all entities with pagination and sorting.
            ${indent} */
            ${indent}Page<$entityName> findAll(Pageable pageable);
        """.trimIndent())

        methods.add("""
            ${indent}/**
            ${indent} * Count total number of entities.
            ${indent} */
            ${indent}long count();
        """.trimIndent())

        // Add ID-type specific query methods
        when (idConfig.idType) {
            IdType.UUID -> {
                methods.add("""
                    ${indent}/**
                    ${indent} * Find entities created in the last N days based on UUID timestamp.
                    ${indent} */
                    ${indent}@Query("SELECT e FROM $entityName e WHERE e.id IS NOT NULL ORDER BY e.id DESC")
                    ${indent}List<$entityName> findRecentEntities(Pageable pageable);
                """.trimIndent())
            }
            IdType.SIMPLE_STRING -> {
                methods.add("""
                    ${indent}/**
                    ${indent} * Find entities by ID pattern matching.
                    ${indent} */
                    ${indent}@Query("SELECT e FROM $entityName e WHERE e.id LIKE :pattern")
                    ${indent}List<$entityName> findByIdPattern(@Param("pattern") String pattern);
                """.trimIndent())
            }
            else -> { /* No additional query methods */ }
        }

        return methods
    }

    private fun getRepositoryImports(idConfig: IdConfiguration): Set<String> {
        val imports = mutableSetOf<String>()

        // Standard Spring Data imports
        imports.add("org.springframework.data.jpa.repository.JpaRepository")
        imports.add("org.springframework.data.domain.Page")
        imports.add("org.springframework.data.domain.Pageable")
        imports.add("org.springframework.data.jpa.repository.Query")
        imports.add("org.springframework.data.repository.query.Param")
        imports.add("java.util.List")
        imports.add("java.util.Optional")

        // ID-type specific imports
        when (idConfig.idType) {
            IdType.UUID -> {
                imports.add("java.util.UUID")
            }
            IdType.COMPOSITE_KEY -> {
                idConfig.compositeKeyClass?.let { imports.add(it) }
            }
            IdType.EMBEDDED_ID -> {
                idConfig.embeddedIdClass?.let { imports.add(it) }
            }
            else -> { /* No additional imports */ }
        }

        // Add modifying annotation for delete operations
        if (idConfig.requiresCustomRepository) {
            imports.add("org.springframework.data.jpa.repository.Modifying")
            imports.add("org.springframework.transaction.annotation.Transactional")
        }

        return imports
    }

    private fun getRepositoryInterface(idConfig: IdConfiguration): String {
        return when (idConfig.idType) {
            IdType.COMPOSITE_KEY, IdType.EMBEDDED_ID ->
                "JpaRepository<${idConfig.javaType.substringBeforeLast("Id")}, ${idConfig.javaType}>"
            else ->
                "JpaRepository<T, ${idConfig.javaType}>"
        }
    }

    /**
     * Get the target file path for the generated repository.
     */
    override fun getTargetFilePath(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRootDir = getSourceRootDirForProject(project)
        val repositoryPackage = packageConfig["repositoryPackage"] ?: entityMetadata.repositoryPackage
        val packagePath = repositoryPackage.replace(".", File.separator)
        val fileExtension = getFileExtensionForProject(project)
        return Paths.get(sourceRootDir, packagePath, "${entityMetadata.repositoryName}.$fileExtension").toString()
    }
}

package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.EntityField
import com.intellij.openapi.project.Project
import com.intellij.psi.*

/**
 * Service for detecting and handling custom ID types in JPA entities.
 */
class CustomIdTypeAnalyzer {

    data class IdConfiguration(
        val idType: IdType,
        val javaType: String,
        val generationStrategy: GenerationType = GenerationType.AUTO,
        val sequenceName: String? = null,
        val tableName: String? = null,
        val columnName: String? = null,
        val compositeKeyClass: String? = null,
        val embeddedIdClass: String? = null,
        val keyFields: List<EntityField> = emptyList(),
        val requiresCustomRepository: Boolean = false,
        val customRepositoryMethods: List<String> = emptyList()
    )

    enum class IdType {
        SIMPLE_LONG,           // Standard Long ID
        SIMPLE_INTEGER,        // Standard Integer ID
        SIMPLE_STRING,         // String-based ID
        UUID,                  // UUID-based ID
        COMPOSITE_KEY,         // @IdClass composite key
        EMBEDDED_ID,           // @EmbeddedId
        CUSTOM_GENERATOR       // Custom ID generator
    }

    enum class GenerationType {
        AUTO, IDENTITY, SEQUENCE, TABLE, UUID, CUSTOM
    }

    /**
     * Analyzes the ID configuration of an entity.
     */
    fun analyzeIdConfiguration(entityClass: PsiClass): IdConfiguration {
        val idFields = findIdFields(entityClass)

        return when {
            idFields.isEmpty() -> createDefaultIdConfiguration()
            idFields.size == 1 -> analyzeSingleIdField(idFields.first(), entityClass)
            else -> analyzeCompositeId(idFields, entityClass)
        }
    }

    /**
     * Detects if an entity uses UUID as ID type.
     */
    fun isUuidIdType(entityClass: PsiClass): Boolean {
        val idFields = findIdFields(entityClass)
        return idFields.any { field ->
            field.type.canonicalText.contains("UUID") ||
            hasAnnotation(field, "GeneratedValue") &&
            getGenerationStrategy(field) == GenerationType.UUID
        }
    }

    /**
     * Detects if an entity uses composite keys.
     */
    fun hasCompositeKey(entityClass: PsiClass): Boolean {
        val idClassAnnotation = entityClass.getAnnotation("javax.persistence.IdClass")
                               ?: entityClass.getAnnotation("jakarta.persistence.IdClass")
        val embeddedIdFields = entityClass.fields.filter { hasAnnotation(it, "EmbeddedId") }

        return idClassAnnotation != null || embeddedIdFields.isNotEmpty()
    }

    /**
     * Generates repository methods for custom ID types.
     */
    fun generateCustomRepositoryMethods(idConfig: IdConfiguration): List<String> {
        return when (idConfig.idType) {
            IdType.UUID -> generateUuidRepositoryMethods(idConfig)
            IdType.COMPOSITE_KEY -> generateCompositeKeyRepositoryMethods(idConfig)
            IdType.EMBEDDED_ID -> generateEmbeddedIdRepositoryMethods(idConfig)
            IdType.SIMPLE_STRING -> generateStringIdRepositoryMethods(idConfig)
            else -> emptyList()
        }
    }

    /**
     * Generates the appropriate ID field declaration.
     */
    fun generateIdFieldDeclaration(idConfig: IdConfiguration, styleAdapter: CodeStyleAdapter): String {
        val indent = styleAdapter.getIndentation()

        return when (idConfig.idType) {
            IdType.UUID -> generateUuidIdField(idConfig, styleAdapter)
            IdType.COMPOSITE_KEY -> generateCompositeKeyField(idConfig, styleAdapter)
            IdType.EMBEDDED_ID -> generateEmbeddedIdField(idConfig, styleAdapter)
            IdType.CUSTOM_GENERATOR -> generateCustomGeneratorIdField(idConfig, styleAdapter)
            else -> generateSimpleIdField(idConfig, styleAdapter)
        }
    }

    /**
     * Generates imports required for the ID type.
     */
    fun getRequiredImports(idConfig: IdConfiguration): Set<String> {
        val imports = mutableSetOf<String>()

        when (idConfig.idType) {
            IdType.UUID -> {
                imports.add("java.util.UUID")
                imports.add("org.hibernate.annotations.GenericGenerator")
            }
            IdType.COMPOSITE_KEY -> {
                imports.add("javax.persistence.IdClass")
                idConfig.compositeKeyClass?.let { imports.add(it) }
            }
            IdType.EMBEDDED_ID -> {
                imports.add("javax.persistence.EmbeddedId")
                idConfig.embeddedIdClass?.let { imports.add(it) }
            }
            IdType.CUSTOM_GENERATOR -> {
                imports.add("org.hibernate.annotations.GenericGenerator")
                imports.add("org.hibernate.annotations.Parameter")
            }
            else -> {
                // Standard imports for simple IDs
                imports.add("javax.persistence.Id")
                imports.add("javax.persistence.GeneratedValue")
                imports.add("javax.persistence.GenerationType")
            }
        }

        return imports
    }

    private fun findIdFields(entityClass: PsiClass): List<PsiField> {
        return entityClass.fields.filter { field ->
            hasAnnotation(field, "Id") || hasAnnotation(field, "EmbeddedId")
        }
    }

    private fun createDefaultIdConfiguration(): IdConfiguration {
        return IdConfiguration(
            idType = IdType.SIMPLE_LONG,
            javaType = "Long",
            generationStrategy = GenerationType.IDENTITY
        )
    }

    private fun analyzeSingleIdField(idField: PsiField, entityClass: PsiClass): IdConfiguration {
        val fieldType = idField.type.canonicalText
        val generationStrategy = getGenerationStrategy(idField)

        return when {
            fieldType.contains("UUID") -> IdConfiguration(
                idType = IdType.UUID,
                javaType = "UUID",
                generationStrategy = GenerationType.UUID
            )
            fieldType.contains("String") -> IdConfiguration(
                idType = IdType.SIMPLE_STRING,
                javaType = "String",
                generationStrategy = generationStrategy,
                requiresCustomRepository = true
            )
            fieldType.contains("Integer") -> IdConfiguration(
                idType = IdType.SIMPLE_INTEGER,
                javaType = "Integer",
                generationStrategy = generationStrategy
            )
            hasAnnotation(idField, "EmbeddedId") -> analyzeEmbeddedId(idField, entityClass)
            else -> IdConfiguration(
                idType = IdType.SIMPLE_LONG,
                javaType = "Long",
                generationStrategy = generationStrategy
            )
        }
    }

    private fun analyzeCompositeId(idFields: List<PsiField>, entityClass: PsiClass): IdConfiguration {
        val idClassAnnotation = entityClass.getAnnotation("javax.persistence.IdClass")
                               ?: entityClass.getAnnotation("jakarta.persistence.IdClass")

        val compositeKeyClass = idClassAnnotation?.findAttributeValue("value")?.text?.trim('"')

        return IdConfiguration(
            idType = IdType.COMPOSITE_KEY,
            javaType = compositeKeyClass ?: "CompositeKey",
            compositeKeyClass = compositeKeyClass,
            keyFields = idFields.map { convertToEntityField(it) },
            requiresCustomRepository = true
        )
    }

    private fun analyzeEmbeddedId(idField: PsiField, entityClass: PsiClass): IdConfiguration {
        val embeddedIdClass = idField.type.canonicalText

        return IdConfiguration(
            idType = IdType.EMBEDDED_ID,
            javaType = embeddedIdClass,
            embeddedIdClass = embeddedIdClass,
            requiresCustomRepository = true
        )
    }

    private fun getGenerationStrategy(idField: PsiField): GenerationType {
        val generatedValueAnnotation = idField.getAnnotation("javax.persistence.GeneratedValue")
                                      ?: idField.getAnnotation("jakarta.persistence.GeneratedValue")

        val strategyValue = generatedValueAnnotation?.findAttributeValue("strategy")?.text

        return when {
            strategyValue?.contains("IDENTITY") == true -> GenerationType.IDENTITY
            strategyValue?.contains("SEQUENCE") == true -> GenerationType.SEQUENCE
            strategyValue?.contains("TABLE") == true -> GenerationType.TABLE
            idField.type.canonicalText.contains("UUID") -> GenerationType.UUID
            else -> GenerationType.AUTO
        }
    }

    private fun generateUuidRepositoryMethods(idConfig: IdConfiguration): List<String> {
        return listOf(
            "Optional<T> findByUuid(UUID uuid);",
            "void deleteByUuid(UUID uuid);",
            "boolean existsByUuid(UUID uuid);"
        )
    }

    private fun generateCompositeKeyRepositoryMethods(idConfig: IdConfiguration): List<String> {
        val keyClass = idConfig.compositeKeyClass ?: "CompositeKey"
        return listOf(
            "Optional<T> findById($keyClass id);",
            "void deleteById($keyClass id);",
            "boolean existsById($keyClass id);"
        )
    }

    private fun generateEmbeddedIdRepositoryMethods(idConfig: IdConfiguration): List<String> {
        val embeddedClass = idConfig.embeddedIdClass ?: "EmbeddedId"
        return listOf(
            "Optional<T> findById($embeddedClass id);",
            "void deleteById($embeddedClass id);",
            "boolean existsById($embeddedClass id);"
        )
    }

    private fun generateStringIdRepositoryMethods(idConfig: IdConfiguration): List<String> {
        return listOf(
            "Optional<T> findByIdIgnoreCase(String id);",
            "List<T> findByIdContaining(String idPart);",
            "List<T> findByIdStartingWith(String prefix);"
        )
    }

    private fun generateUuidIdField(idConfig: IdConfiguration, styleAdapter: CodeStyleAdapter): String {
        val indent = styleAdapter.getIndentation()
        return """
            ${indent}@Id
            ${indent}@GeneratedValue(generator = "UUID")
            ${indent}@GenericGenerator(
            ${styleAdapter.getIndentation(2)}name = "UUID",
            ${styleAdapter.getIndentation(2)}strategy = "org.hibernate.id.UUIDGenerator"
            ${indent})
            ${indent}@Column(name = "id", updatable = false, nullable = false)
            ${indent}private UUID id;
        """.trimIndent()
    }

    private fun generateCompositeKeyField(idConfig: IdConfiguration, styleAdapter: CodeStyleAdapter): String {
        val indent = styleAdapter.getIndentation()
        val keyFields = StringBuilder()

        idConfig.keyFields.forEach { field ->
            keyFields.append("""
                ${indent}@Id
                ${indent}@Column(name = "${field.columnName ?: field.name}")
                ${indent}private ${field.type} ${field.name};
                
            """.trimIndent())
        }

        return keyFields.toString().trimEnd()
    }

    private fun generateEmbeddedIdField(idConfig: IdConfiguration, styleAdapter: CodeStyleAdapter): String {
        val indent = styleAdapter.getIndentation()
        val embeddedType = idConfig.embeddedIdClass?.substringAfterLast(".") ?: "EmbeddedId"

        return """
            ${indent}@EmbeddedId
            ${indent}private $embeddedType id;
        """.trimIndent()
    }

    private fun generateCustomGeneratorIdField(idConfig: IdConfiguration, styleAdapter: CodeStyleAdapter): String {
        val indent = styleAdapter.getIndentation()
        return """
            ${indent}@Id
            ${indent}@GeneratedValue(generator = "custom-generator")
            ${indent}@GenericGenerator(
            ${styleAdapter.getIndentation(2)}name = "custom-generator",
            ${styleAdapter.getIndentation(2)}strategy = "custom.generator.class",
            ${styleAdapter.getIndentation(2)}parameters = {
            ${styleAdapter.getIndentation(3)}@Parameter(name = "sequence_name", value = "${idConfig.sequenceName ?: "custom_seq"}")
            ${styleAdapter.getIndentation(2)}}
            ${indent})
            ${indent}private ${idConfig.javaType} id;
        """.trimIndent()
    }

    private fun generateSimpleIdField(idConfig: IdConfiguration, styleAdapter: CodeStyleAdapter): String {
        val indent = styleAdapter.getIndentation()
        val strategy = when (idConfig.generationStrategy) {
            GenerationType.IDENTITY -> "GenerationType.IDENTITY"
            GenerationType.SEQUENCE -> "GenerationType.SEQUENCE"
            GenerationType.TABLE -> "GenerationType.TABLE"
            else -> "GenerationType.AUTO"
        }

        return """
            ${indent}@Id
            ${indent}@GeneratedValue(strategy = $strategy)
            ${indent}private ${idConfig.javaType} id;
        """.trimIndent()
    }

    private fun hasAnnotation(element: PsiModifierListOwner, annotationName: String): Boolean {
        return element.getAnnotation("javax.persistence.$annotationName") != null ||
               element.getAnnotation("jakarta.persistence.$annotationName") != null ||
               element.getAnnotation(annotationName) != null
    }

    private fun convertToEntityField(psiField: PsiField): EntityField {
        return EntityField(
            name = psiField.name ?: "",
            type = psiField.type.canonicalText,
            nullable = true, // Composite key fields are typically not nullable
            columnName = extractColumnName(psiField)
        )
    }

    private fun extractColumnName(psiField: PsiField): String? {
        val columnAnnotation = psiField.getAnnotation("javax.persistence.Column")
                              ?: psiField.getAnnotation("jakarta.persistence.Column")
        return columnAnnotation?.findAttributeValue("name")?.text?.trim('"')
    }
}

package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.ComplexRelationshipAnalyzer.InheritanceStrategy
import com.intellij.openapi.project.Project
import com.intellij.psi.*

/**
 * Service for detecting and generating advanced JPA features.
 */
class AdvancedJpaFeatureAnalyzer {

    data class JpaFeatureConfiguration(
        val inheritanceStrategy: InheritanceInfo? = null,
        val embeddableFields: List<EmbeddableFieldInfo> = emptyList(),
        val lifecycleCallbacks: List<LifecycleCallback> = emptyList(),
        val auditingEnabled: Boolean = false,
        val versioningEnabled: Boolean = false,
        val softDeleteEnabled: Boolean = false,
        val customConverters: List<AttributeConverter> = emptyList()
    )

    data class InheritanceInfo(
        val strategy: InheritanceStrategy,
        val discriminatorColumn: String = "dtype",
        val discriminatorType: String = "STRING",
        val discriminatorValue: String? = null,
        val superclass: String? = null,
        val subclasses: List<String> = emptyList(),
        val isMappedSuperclass: Boolean = false
    )

    data class EmbeddableFieldInfo(
        val fieldName: String,
        val embeddableClass: String,
        val attributeOverrides: Map<String, String> = emptyMap(),
        val isNested: Boolean = false
    )

    data class LifecycleCallback(
        val type: CallbackType,
        val methodName: String,
        val isEntityListener: Boolean = false,
        val listenerClass: String? = null
    )

    enum class CallbackType {
        PRE_PERSIST, POST_PERSIST, PRE_UPDATE, POST_UPDATE,
        PRE_REMOVE, POST_REMOVE, POST_LOAD
    }

    data class AttributeConverter(
        val fieldName: String,
        val converterClass: String,
        val databaseType: String,
        val entityType: String
    )

    /**
     * Analyzes advanced JPA features for an entity.
     */
    fun analyzeJpaFeatures(entityClass: PsiClass): JpaFeatureConfiguration {
        return JpaFeatureConfiguration(
            inheritanceStrategy = analyzeInheritance(entityClass),
            embeddableFields = analyzeEmbeddableFields(entityClass),
            lifecycleCallbacks = analyzeLifecycleCallbacks(entityClass),
            auditingEnabled = hasAuditingAnnotations(entityClass),
            versioningEnabled = hasVersionField(entityClass),
            softDeleteEnabled = hasSoftDeleteSupport(entityClass),
            customConverters = analyzeAttributeConverters(entityClass)
        )
    }

    /**
     * Generates inheritance-related code.
     */
    fun generateInheritanceCode(
        inheritanceInfo: InheritanceInfo,
        entityMetadata: EntityMetadata,
        styleAdapter: CodeStyleAdapter
    ): String {
        val code = StringBuilder()
        val indent = styleAdapter.getIndentation()

        when (inheritanceInfo.strategy) {
            InheritanceStrategy.SINGLE_TABLE -> {
                code.append(generateSingleTableInheritance(inheritanceInfo, styleAdapter))
            }
            InheritanceStrategy.TABLE_PER_CLASS -> {
                code.append(generateTablePerClassInheritance(inheritanceInfo, styleAdapter))
            }
            InheritanceStrategy.JOINED -> {
                code.append(generateJoinedInheritance(inheritanceInfo, styleAdapter))
            }
        }

        if (inheritanceInfo.isMappedSuperclass) {
            code.append(generateMappedSuperclassCode(inheritanceInfo, styleAdapter))
        }

        return code.toString()
    }

    /**
     * Generates embeddable field code.
     */
    fun generateEmbeddableFieldsCode(
        embeddableFields: List<EmbeddableFieldInfo>,
        styleAdapter: CodeStyleAdapter
    ): String {
        val code = StringBuilder()
        val indent = styleAdapter.getIndentation()

        embeddableFields.forEach { embeddableField ->
            code.append("""
                ${indent}@Embedded
                """.trimIndent())

            if (embeddableField.attributeOverrides.isNotEmpty()) {
                code.append(generateAttributeOverrides(embeddableField, styleAdapter))
            }

            val fieldType = embeddableField.embeddableClass.substringAfterLast(".")
            code.append("""
                ${indent}private $fieldType ${embeddableField.fieldName};
                
                """.trimIndent())
        }

        return code.toString()
    }

    /**
     * Generates lifecycle callback methods.
     */
    fun generateLifecycleCallbacks(
        callbacks: List<LifecycleCallback>,
        styleAdapter: CodeStyleAdapter
    ): String {
        val code = StringBuilder()
        val indent = styleAdapter.getIndentation()

        callbacks.forEach { callback ->
            if (!callback.isEntityListener) {
                code.append(generateLifecycleMethod(callback, styleAdapter))
                code.append("\n\n")
            }
        }

        return code.toString().trimEnd()
    }

    /**
     * Generates entity listener class.
     */
    fun generateEntityListener(
        callbacks: List<LifecycleCallback>,
        entityMetadata: EntityMetadata,
        styleAdapter: CodeStyleAdapter
    ): String {
        val entityListenerCallbacks = callbacks.filter { it.isEntityListener }
        if (entityListenerCallbacks.isEmpty()) return ""

        val indent = styleAdapter.getIndentation()
        val entityName = entityMetadata.className
        val listenerName = "${entityName}EntityListener"

        val code = StringBuilder()
        code.append("""
            package ${entityMetadata.entityBasePackage}.listener;
            
            import javax.persistence.*;
            import java.time.LocalDateTime;
            import java.util.logging.Logger;
            
            /**
             * Entity listener for ${entityName}.
             */
            public class $listenerName {
            
            ${indent}private static final Logger logger = Logger.getLogger(${listenerName}.class.getName());
            
            """.trimIndent())

        entityListenerCallbacks.forEach { callback ->
            code.append(generateEntityListenerMethod(callback, entityName, styleAdapter))
            code.append("\n\n")
        }

        code.append("}")

        return code.toString()
    }

    /**
     * Generates auditing support code.
     */
    fun generateAuditingSupport(styleAdapter: CodeStyleAdapter): String {
        val indent = styleAdapter.getIndentation()

        return """
            ${indent}@CreatedDate
            ${indent}@Column(name = "created_at", nullable = false, updatable = false)
            ${indent}private LocalDateTime createdAt;
            
            ${indent}@LastModifiedDate
            ${indent}@Column(name = "updated_at")
            ${indent}private LocalDateTime updatedAt;
            
            ${indent}@CreatedBy
            ${indent}@Column(name = "created_by", updatable = false)
            ${indent}private String createdBy;
            
            ${indent}@LastModifiedBy
            ${indent}@Column(name = "last_modified_by")
            ${indent}private String lastModifiedBy;
        """.trimIndent()
    }

    /**
     * Generates versioning support code.
     */
    fun generateVersioningSupport(styleAdapter: CodeStyleAdapter): String {
        val indent = styleAdapter.getIndentation()

        return """
            ${indent}@Version
            ${indent}@Column(name = "version")
            ${indent}private Long version;
        """.trimIndent()
    }

    /**
     * Generates soft delete support code.
     */
    fun generateSoftDeleteSupport(styleAdapter: CodeStyleAdapter): String {
        val indent = styleAdapter.getIndentation()

        return """
            ${indent}@Column(name = "deleted", nullable = false)
            ${indent}private Boolean deleted = false;
            
            ${indent}@Column(name = "deleted_at")
            ${indent}private LocalDateTime deletedAt;
            
            ${indent}/**
            ${indent} * Marks this entity as deleted (soft delete).
            ${indent} */
            ${indent}public void markAsDeleted() {
            ${styleAdapter.getIndentation(2)}this.deleted = true;
            ${styleAdapter.getIndentation(2)}this.deletedAt = LocalDateTime.now();
            ${indent}}
            
            ${indent}/**
            ${indent} * Restores this entity from deleted state.
            ${indent} */
            ${indent}public void restore() {
            ${styleAdapter.getIndentation(2)}this.deleted = false;
            ${styleAdapter.getIndentation(2)}this.deletedAt = null;
            ${indent}}
            
            ${indent}/**
            ${indent} * Checks if this entity is deleted.
            ${indent} */
            ${indent}public boolean isDeleted() {
            ${styleAdapter.getIndentation(2)}return Boolean.TRUE.equals(deleted);
            ${indent}}
        """.trimIndent()
    }

    private fun analyzeInheritance(entityClass: PsiClass): InheritanceInfo? {
        val inheritanceAnnotation = entityClass.getAnnotation("javax.persistence.Inheritance")
                                   ?: entityClass.getAnnotation("jakarta.persistence.Inheritance")

        val mappedSuperclassAnnotation = entityClass.getAnnotation("javax.persistence.MappedSuperclass")
                                        ?: entityClass.getAnnotation("jakarta.persistence.MappedSuperclass")

        val discriminatorColumnAnnotation = entityClass.getAnnotation("javax.persistence.DiscriminatorColumn")
                                          ?: entityClass.getAnnotation("jakarta.persistence.DiscriminatorColumn")

        val discriminatorValueAnnotation = entityClass.getAnnotation("javax.persistence.DiscriminatorValue")
                                         ?: entityClass.getAnnotation("jakarta.persistence.DiscriminatorValue")

        if (inheritanceAnnotation != null || mappedSuperclassAnnotation != null) {
            val strategy = extractInheritanceStrategy(inheritanceAnnotation)
            val discriminatorColumn = extractAnnotationAttribute(discriminatorColumnAnnotation, "name") ?: "dtype"
            val discriminatorValue = extractAnnotationAttribute(discriminatorValueAnnotation, "value")

            return InheritanceInfo(
                strategy = strategy,
                discriminatorColumn = discriminatorColumn,
                discriminatorValue = discriminatorValue,
                superclass = entityClass.superClass?.qualifiedName,
                isMappedSuperclass = mappedSuperclassAnnotation != null
            )
        }

        return null
    }

    private fun analyzeEmbeddableFields(entityClass: PsiClass): List<EmbeddableFieldInfo> {
        return entityClass.fields.filter { hasAnnotation(it, "Embedded") }
            .map { field ->
                val attributeOverrides = extractAttributeOverrides(field)
                EmbeddableFieldInfo(
                    fieldName = field.name ?: "",
                    embeddableClass = field.type.canonicalText,
                    attributeOverrides = attributeOverrides
                )
            }
    }

    private fun analyzeLifecycleCallbacks(entityClass: PsiClass): List<LifecycleCallback> {
        val callbacks = mutableListOf<LifecycleCallback>()

        // Check for entity listener
        val entityListenersAnnotation = entityClass.getAnnotation("javax.persistence.EntityListeners")
                                       ?: entityClass.getAnnotation("jakarta.persistence.EntityListeners")

        // Check for lifecycle methods in the entity itself
        entityClass.methods.forEach { method ->
            val callbackType = getLifecycleCallbackType(method)
            if (callbackType != null) {
                callbacks.add(LifecycleCallback(
                    type = callbackType,
                    methodName = method.name ?: "",
                    isEntityListener = false
                ))
            }
        }

        return callbacks
    }

    private fun hasAuditingAnnotations(entityClass: PsiClass): Boolean {
        return entityClass.fields.any { field ->
            hasAnnotation(field, "CreatedDate") ||
            hasAnnotation(field, "LastModifiedDate") ||
            hasAnnotation(field, "CreatedBy") ||
            hasAnnotation(field, "LastModifiedBy")
        }
    }

    private fun hasVersionField(entityClass: PsiClass): Boolean {
        return entityClass.fields.any { hasAnnotation(it, "Version") }
    }

    private fun hasSoftDeleteSupport(entityClass: PsiClass): Boolean {
        return entityClass.fields.any { field ->
            field.name == "deleted" || field.name == "deletedAt"
        }
    }

    private fun analyzeAttributeConverters(entityClass: PsiClass): List<AttributeConverter> {
        return entityClass.fields.filter { hasAnnotation(it, "Convert") }
            .map { field ->
                val convertAnnotation = field.getAnnotation("javax.persistence.Convert")
                                       ?: field.getAnnotation("jakarta.persistence.Convert")
                val converterClass = extractAnnotationAttribute(convertAnnotation, "converter") ?: ""

                AttributeConverter(
                    fieldName = field.name ?: "",
                    converterClass = converterClass,
                    databaseType = "VARCHAR", // Default, would need more analysis
                    entityType = field.type.canonicalText
                )
            }
    }

    // Helper methods for code generation

    private fun generateSingleTableInheritance(inheritanceInfo: InheritanceInfo, styleAdapter: CodeStyleAdapter): String {
        return """
            @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
            @DiscriminatorColumn(name = "${inheritanceInfo.discriminatorColumn}", discriminatorType = DiscriminatorType.STRING)
            ${if (inheritanceInfo.discriminatorValue != null) "@DiscriminatorValue(\"${inheritanceInfo.discriminatorValue}\")" else ""}
        """.trimIndent()
    }

    private fun generateTablePerClassInheritance(inheritanceInfo: InheritanceInfo, styleAdapter: CodeStyleAdapter): String {
        return """
            @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
        """.trimIndent()
    }

    private fun generateJoinedInheritance(inheritanceInfo: InheritanceInfo, styleAdapter: CodeStyleAdapter): String {
        return """
            @Inheritance(strategy = InheritanceType.JOINED)
            ${if (inheritanceInfo.discriminatorValue != null) "@DiscriminatorValue(\"${inheritanceInfo.discriminatorValue}\")" else ""}
        """.trimIndent()
    }

    private fun generateMappedSuperclassCode(inheritanceInfo: InheritanceInfo, styleAdapter: CodeStyleAdapter): String {
        return """
            @MappedSuperclass
        """.trimIndent()
    }

    private fun generateAttributeOverrides(embeddableField: EmbeddableFieldInfo, styleAdapter: CodeStyleAdapter): String {
        if (embeddableField.attributeOverrides.isEmpty()) return ""

        val overrides = embeddableField.attributeOverrides.entries.joinToString(",\n    ") { (field, column) ->
            "@AttributeOverride(name = \"$field\", column = @Column(name = \"$column\"))"
        }

        return """
            @AttributeOverrides({
                $overrides
            })
        """.trimIndent()
    }

    private fun generateLifecycleMethod(callback: LifecycleCallback, styleAdapter: CodeStyleAdapter): String {
        val indent = styleAdapter.getIndentation()
        val annotation = "@${callback.type.name.split('_').joinToString("") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }}"

        return """
            ${indent}$annotation
            ${indent}private void ${callback.methodName}() {
            ${styleAdapter.getIndentation(2)}// Lifecycle callback implementation
            ${styleAdapter.getIndentation(2)}// Add your logic here
            ${indent}}
        """.trimIndent()
    }

    private fun generateEntityListenerMethod(callback: LifecycleCallback, entityName: String, styleAdapter: CodeStyleAdapter): String {
        val indent = styleAdapter.getIndentation()
        val annotation = "@${callback.type.name.split('_').joinToString("") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }}"

        return """
            ${indent}$annotation
            ${indent}public void ${callback.methodName}($entityName entity) {
            ${styleAdapter.getIndentation(2)}logger.info("${callback.type.name} callback triggered for entity: " + entity.getId());
            ${styleAdapter.getIndentation(2)}// Add your listener logic here
            ${indent}}
        """.trimIndent()
    }

    // Utility methods

    private fun extractInheritanceStrategy(annotation: PsiAnnotation?): InheritanceStrategy {
        val strategyValue = annotation?.findAttributeValue("strategy")?.text
        return when {
            strategyValue?.contains("SINGLE_TABLE") == true -> InheritanceStrategy.SINGLE_TABLE
            strategyValue?.contains("TABLE_PER_CLASS") == true -> InheritanceStrategy.TABLE_PER_CLASS
            strategyValue?.contains("JOINED") == true -> InheritanceStrategy.JOINED
            else -> InheritanceStrategy.SINGLE_TABLE
        }
    }

    private fun extractAttributeOverrides(field: PsiField): Map<String, String> {
        // Simplified implementation - would need more complex parsing
        return emptyMap()
    }

    private fun getLifecycleCallbackType(method: PsiMethod): CallbackType? {
        return when {
            hasAnnotation(method, "PrePersist") -> CallbackType.PRE_PERSIST
            hasAnnotation(method, "PostPersist") -> CallbackType.POST_PERSIST
            hasAnnotation(method, "PreUpdate") -> CallbackType.PRE_UPDATE
            hasAnnotation(method, "PostUpdate") -> CallbackType.POST_UPDATE
            hasAnnotation(method, "PreRemove") -> CallbackType.PRE_REMOVE
            hasAnnotation(method, "PostRemove") -> CallbackType.POST_REMOVE
            hasAnnotation(method, "PostLoad") -> CallbackType.POST_LOAD
            else -> null
        }
    }

    private fun hasAnnotation(element: PsiModifierListOwner, annotationName: String): Boolean {
        return element.getAnnotation("javax.persistence.$annotationName") != null ||
               element.getAnnotation("jakarta.persistence.$annotationName") != null ||
               element.getAnnotation(annotationName) != null
    }

    private fun extractAnnotationAttribute(annotation: PsiAnnotation?, attributeName: String): String? {
        return annotation?.findAttributeValue(attributeName)?.text?.trim('"')
    }
}

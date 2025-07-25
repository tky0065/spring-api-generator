package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.model.EntityField
import com.enokdev.springapigenerator.model.RelationType
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope

/**
 * Advanced service for detecting and analyzing complex entity relationships.
 */
class ComplexRelationshipAnalyzer {

    data class RelationshipInfo(
        val sourceEntity: String,
        val targetEntity: String,
        val relationType: RelationType,
        val fieldName: String,
        val mappedBy: String? = null,
        val joinTable: String? = null,
        val joinColumns: List<String> = emptyList(),
        val inverseJoinColumns: List<String> = emptyList(),
        val cascade: Set<CascadeType> = emptySet(),
        val fetchType: FetchType = FetchType.LAZY,
        val orphanRemoval: Boolean = false,
        val isBidirectional: Boolean = false,
        val inverseFieldName: String? = null,
        val isOwnerSide: Boolean = true,
        val additionalFields: List<EntityField> = emptyList(), // For complex many-to-many
        val inheritanceStrategy: InheritanceStrategy? = null,
        val discriminatorColumn: String? = null,
        val discriminatorValue: String? = null
    )

    enum class CascadeType {
        ALL, PERSIST, MERGE, REMOVE, REFRESH, DETACH
    }

    enum class FetchType {
        LAZY, EAGER
    }

    enum class InheritanceStrategy {
        SINGLE_TABLE, TABLE_PER_CLASS, JOINED
    }

    data class ManyToManyWithFields(
        val relationshipEntity: String,
        val relationshipTable: String,
        val additionalFields: List<EntityField>,
        val sourceJoinColumn: String,
        val targetJoinColumn: String
    )

    /**
     * Analyzes all relationships in the project to detect complex patterns.
     */
    fun analyzeProjectRelationships(project: Project): List<RelationshipInfo> {
        val relationships = mutableListOf<RelationshipInfo>()
        val entityClasses = findEntityClasses(project)

        entityClasses.forEach { entityClass ->
            relationships.addAll(analyzeEntityRelationships(entityClass, entityClasses))
        }

        // Post-process to detect bidirectional relationships
        return detectBidirectionalRelationships(relationships)
    }

    /**
     * Analyzes relationships for a specific entity class.
     */
    fun analyzeEntityRelationships(entityClass: PsiClass, allEntityClasses: List<PsiClass>): List<RelationshipInfo> {
        val relationships = mutableListOf<RelationshipInfo>()

        entityClass.fields.forEach { field ->
            val relationshipInfo = analyzeFieldRelationship(field, entityClass, allEntityClasses)
            if (relationshipInfo != null) {
                relationships.add(relationshipInfo)
            }
        }

        // Analyze inheritance relationships
        val inheritanceInfo = analyzeInheritance(entityClass)
        if (inheritanceInfo != null) {
            relationships.add(inheritanceInfo)
        }

        return relationships
    }

    /**
     * Detects many-to-many relationships with additional fields.
     */
    fun detectManyToManyWithFields(project: Project): List<ManyToManyWithFields> {
        val result = mutableListOf<ManyToManyWithFields>()
        val entityClasses = findEntityClasses(project)

        entityClasses.forEach { entityClass ->
            val manyToManyFields = entityClass.fields.filter { field ->
                hasAnnotation(field, "ManyToMany") ||
                (hasAnnotation(field, "OneToMany") && isJoinTableRelationship(field))
            }

            manyToManyFields.forEach { field ->
                val relationshipEntity = detectRelationshipEntity(field, entityClasses)
                if (relationshipEntity != null) {
                    val additionalFields = analyzeRelationshipEntityFields(relationshipEntity)
                    if (additionalFields.isNotEmpty()) {
                        result.add(
                            ManyToManyWithFields(
                                relationshipEntity = relationshipEntity.qualifiedName ?: "",
                                relationshipTable = extractTableName(relationshipEntity),
                                additionalFields = additionalFields,
                                sourceJoinColumn = extractJoinColumn(field, entityClass),
                                targetJoinColumn = extractInverseJoinColumn(field)
                            )
                        )
                    }
                }
            }
        }

        return result
    }

    /**
     * Analyzes nested compositions and complex inheritance.
     */
    fun analyzeNestedCompositions(entityClass: PsiClass): List<RelationshipInfo> {
        val compositions = mutableListOf<RelationshipInfo>()

        analyzeEmbeddedFields(entityClass).forEach { embedded ->
            compositions.add(embedded)

            // Recursively analyze embedded types for nested compositions
            val embeddedClass = findClassByName(entityClass.project, embedded.targetEntity)
            if (embeddedClass != null) {
                compositions.addAll(analyzeNestedCompositions(embeddedClass))
            }
        }

        return compositions
    }

    private fun analyzeFieldRelationship(
        field: PsiField,
        entityClass: PsiClass,
        allEntityClasses: List<PsiClass>
    ): RelationshipInfo? {
        val annotations = field.annotations

        return when {
            hasAnnotation(field, "OneToOne") -> analyzeOneToOne(field, entityClass)
            hasAnnotation(field, "OneToMany") -> analyzeOneToMany(field, entityClass)
            hasAnnotation(field, "ManyToOne") -> analyzeManyToOne(field, entityClass)
            hasAnnotation(field, "ManyToMany") -> analyzeManyToMany(field, entityClass)
            hasAnnotation(field, "Embedded") -> analyzeEmbedded(field, entityClass)
            isCollectionWithEntity(field, allEntityClasses) -> analyzeImplicitRelationship(field, entityClass)
            else -> null
        }
    }

    private fun analyzeOneToOne(field: PsiField, entityClass: PsiClass): RelationshipInfo {
        val annotation = field.getAnnotation("javax.persistence.OneToOne")
                        ?: field.getAnnotation("jakarta.persistence.OneToOne")

        return RelationshipInfo(
            sourceEntity = entityClass.qualifiedName ?: "",
            targetEntity = extractTargetEntity(field),
            relationType = RelationType.ONE_TO_ONE,
            fieldName = field.name ?: "",
            mappedBy = extractAnnotationAttribute(annotation, "mappedBy"),
            cascade = extractCascadeTypes(annotation),
            fetchType = extractFetchType(annotation),
            orphanRemoval = extractBooleanAttribute(annotation, "orphanRemoval"),
            isOwnerSide = extractAnnotationAttribute(annotation, "mappedBy") == null
        )
    }

    private fun analyzeOneToMany(field: PsiField, entityClass: PsiClass): RelationshipInfo {
        val annotation = field.getAnnotation("javax.persistence.OneToMany")
                        ?: field.getAnnotation("jakarta.persistence.OneToMany")

        val joinTableAnnotation = field.getAnnotation("javax.persistence.JoinTable")
                                 ?: field.getAnnotation("jakarta.persistence.JoinTable")

        return RelationshipInfo(
            sourceEntity = entityClass.qualifiedName ?: "",
            targetEntity = extractCollectionTargetEntity(field),
            relationType = RelationType.ONE_TO_MANY,
            fieldName = field.name ?: "",
            mappedBy = extractAnnotationAttribute(annotation, "mappedBy"),
            joinTable = extractAnnotationAttribute(joinTableAnnotation, "name"),
            joinColumns = extractJoinColumns(joinTableAnnotation, "joinColumns"),
            inverseJoinColumns = extractJoinColumns(joinTableAnnotation, "inverseJoinColumns"),
            cascade = extractCascadeTypes(annotation),
            fetchType = extractFetchType(annotation),
            orphanRemoval = extractBooleanAttribute(annotation, "orphanRemoval"),
            isOwnerSide = extractAnnotationAttribute(annotation, "mappedBy") == null
        )
    }

    private fun analyzeManyToOne(field: PsiField, entityClass: PsiClass): RelationshipInfo {
        val annotation = field.getAnnotation("javax.persistence.ManyToOne")
                        ?: field.getAnnotation("jakarta.persistence.ManyToOne")

        return RelationshipInfo(
            sourceEntity = entityClass.qualifiedName ?: "",
            targetEntity = extractTargetEntity(field),
            relationType = RelationType.MANY_TO_ONE,
            fieldName = field.name ?: "",
            cascade = extractCascadeTypes(annotation),
            fetchType = extractFetchType(annotation),
            isOwnerSide = true
        )
    }

    private fun analyzeManyToMany(field: PsiField, entityClass: PsiClass): RelationshipInfo {
        val annotation = field.getAnnotation("javax.persistence.ManyToMany")
                        ?: field.getAnnotation("jakarta.persistence.ManyToMany")

        val joinTableAnnotation = field.getAnnotation("javax.persistence.JoinTable")
                                 ?: field.getAnnotation("jakarta.persistence.JoinTable")

        return RelationshipInfo(
            sourceEntity = entityClass.qualifiedName ?: "",
            targetEntity = extractCollectionTargetEntity(field),
            relationType = RelationType.MANY_TO_MANY,
            fieldName = field.name ?: "",
            mappedBy = extractAnnotationAttribute(annotation, "mappedBy"),
            joinTable = extractAnnotationAttribute(joinTableAnnotation, "name"),
            joinColumns = extractJoinColumns(joinTableAnnotation, "joinColumns"),
            inverseJoinColumns = extractJoinColumns(joinTableAnnotation, "inverseJoinColumns"),
            cascade = extractCascadeTypes(annotation),
            fetchType = extractFetchType(annotation),
            isOwnerSide = extractAnnotationAttribute(annotation, "mappedBy") == null
        )
    }

    private fun analyzeEmbedded(field: PsiField, entityClass: PsiClass): RelationshipInfo {
        return RelationshipInfo(
            sourceEntity = entityClass.qualifiedName ?: "",
            targetEntity = extractTargetEntity(field),
            relationType = RelationType.EMBEDDED,
            fieldName = field.name ?: "",
            isOwnerSide = true
        )
    }

    private fun analyzeImplicitRelationship(field: PsiField, entityClass: PsiClass): RelationshipInfo {
        // Analyze fields that might be relationships but lack explicit annotations
        val targetEntity = extractCollectionTargetEntity(field)

        return RelationshipInfo(
            sourceEntity = entityClass.qualifiedName ?: "",
            targetEntity = targetEntity,
            relationType = if (isCollectionType(field.type)) RelationType.ONE_TO_MANY else RelationType.MANY_TO_ONE,
            fieldName = field.name ?: "",
            isOwnerSide = true
        )
    }

    private fun analyzeInheritance(entityClass: PsiClass): RelationshipInfo? {
        val inheritanceAnnotation = entityClass.getAnnotation("javax.persistence.Inheritance")
                                   ?: entityClass.getAnnotation("jakarta.persistence.Inheritance")

        val discriminatorColumnAnnotation = entityClass.getAnnotation("javax.persistence.DiscriminatorColumn")
                                          ?: entityClass.getAnnotation("jakarta.persistence.DiscriminatorColumn")

        val discriminatorValueAnnotation = entityClass.getAnnotation("javax.persistence.DiscriminatorValue")
                                         ?: entityClass.getAnnotation("jakarta.persistence.DiscriminatorValue")

        if (inheritanceAnnotation != null || entityClass.superClass?.hasAnnotation("Entity") == true) {
            return RelationshipInfo(
                sourceEntity = entityClass.qualifiedName ?: "",
                targetEntity = entityClass.superClass?.qualifiedName ?: "",
                relationType = RelationType.INHERITANCE,
                fieldName = "superclass",
                inheritanceStrategy = extractInheritanceStrategy(inheritanceAnnotation),
                discriminatorColumn = extractAnnotationAttribute(discriminatorColumnAnnotation, "name"),
                discriminatorValue = extractAnnotationAttribute(discriminatorValueAnnotation, "value")
            )
        }

        return null
    }

    private fun analyzeEmbeddedFields(entityClass: PsiClass): List<RelationshipInfo> {
        return entityClass.fields.filter { hasAnnotation(it, "Embedded") }
            .map { field ->
                RelationshipInfo(
                    sourceEntity = entityClass.qualifiedName ?: "",
                    targetEntity = extractTargetEntity(field),
                    relationType = RelationType.EMBEDDED,
                    fieldName = field.name ?: "",
                    isOwnerSide = true
                )
            }
    }

    private fun detectBidirectionalRelationships(relationships: List<RelationshipInfo>): List<RelationshipInfo> {
        val enhanced = relationships.toMutableList()

        relationships.forEach { relationship ->
            val inverse = findInverseRelationship(relationship, relationships)
            if (inverse != null) {
                val index = enhanced.indexOf(relationship)
                enhanced[index] = relationship.copy(
                    isBidirectional = true,
                    inverseFieldName = inverse.fieldName
                )
            }
        }

        return enhanced
    }

    private fun findInverseRelationship(relationship: RelationshipInfo, allRelationships: List<RelationshipInfo>): RelationshipInfo? {
        return allRelationships.find { other ->
            other.sourceEntity == relationship.targetEntity &&
            other.targetEntity == relationship.sourceEntity &&
            (other.mappedBy == relationship.fieldName || relationship.mappedBy == other.fieldName)
        }
    }

    // Helper methods for annotation processing

    private fun findEntityClasses(project: Project): List<PsiClass> {
        val result = mutableListOf<PsiClass>()
        val scope = GlobalSearchScope.projectScope(project)

        // This is a simplified implementation - in reality you'd use PSI search
        // to find all classes with @Entity annotation
        return result
    }

    private fun hasAnnotation(element: PsiModifierListOwner, annotationName: String): Boolean {
        return element.getAnnotation("javax.persistence.$annotationName") != null ||
               element.getAnnotation("jakarta.persistence.$annotationName") != null ||
               element.getAnnotation(annotationName) != null
    }

    private fun extractTargetEntity(field: PsiField): String {
        return field.type.canonicalText
    }

    private fun extractCollectionTargetEntity(field: PsiField): String {
        val type = field.type
        if (type is PsiClassType) {
            val parameters = type.parameters
            if (parameters.isNotEmpty()) {
                return parameters[0].canonicalText
            }
        }
        return ""
    }

    private fun extractAnnotationAttribute(annotation: PsiAnnotation?, attributeName: String): String? {
        return annotation?.findAttributeValue(attributeName)?.text?.trim('"')
    }

    private fun extractBooleanAttribute(annotation: PsiAnnotation?, attributeName: String): Boolean {
        return annotation?.findAttributeValue(attributeName)?.text?.toBoolean() ?: false
    }

    private fun extractCascadeTypes(annotation: PsiAnnotation?): Set<CascadeType> {
        val cascadeValue = annotation?.findAttributeValue("cascade")?.text
        return if (cascadeValue != null) {
            parseCascadeTypes(cascadeValue)
        } else {
            emptySet()
        }
    }

    private fun extractFetchType(annotation: PsiAnnotation?): FetchType {
        val fetchValue = annotation?.findAttributeValue("fetch")?.text
        return when {
            fetchValue?.contains("EAGER") == true -> FetchType.EAGER
            else -> FetchType.LAZY
        }
    }

    private fun extractInheritanceStrategy(annotation: PsiAnnotation?): InheritanceStrategy? {
        val strategyValue = annotation?.findAttributeValue("strategy")?.text
        return when {
            strategyValue?.contains("SINGLE_TABLE") == true -> InheritanceStrategy.SINGLE_TABLE
            strategyValue?.contains("TABLE_PER_CLASS") == true -> InheritanceStrategy.TABLE_PER_CLASS
            strategyValue?.contains("JOINED") == true -> InheritanceStrategy.JOINED
            else -> null
        }
    }

    private fun extractJoinColumns(annotation: PsiAnnotation?, attributeName: String): List<String> {
        // Simplified implementation
        return emptyList()
    }

    private fun parseCascadeTypes(cascadeText: String): Set<CascadeType> {
        return setOf() // Simplified implementation
    }

    private fun isCollectionType(type: PsiType): Boolean {
        return type.canonicalText.contains("List") ||
               type.canonicalText.contains("Set") ||
               type.canonicalText.contains("Collection")
    }

    private fun isCollectionWithEntity(field: PsiField, entityClasses: List<PsiClass>): Boolean {
        return isCollectionType(field.type) &&
               entityClasses.any { it.qualifiedName == extractCollectionTargetEntity(field) }
    }

    private fun isJoinTableRelationship(field: PsiField): Boolean {
        return hasAnnotation(field, "JoinTable")
    }

    private fun detectRelationshipEntity(field: PsiField, entityClasses: List<PsiClass>): PsiClass? {
        // Logic to detect if this field represents a relationship entity
        return null
    }

    private fun analyzeRelationshipEntityFields(relationshipEntity: PsiClass): List<EntityField> {
        // Analyze additional fields in the relationship entity
        return emptyList()
    }

    private fun extractTableName(entityClass: PsiClass): String {
        val tableAnnotation = entityClass.getAnnotation("javax.persistence.Table")
                             ?: entityClass.getAnnotation("jakarta.persistence.Table")
        return extractAnnotationAttribute(tableAnnotation, "name") ?: entityClass.name ?: ""
    }

    private fun extractJoinColumn(field: PsiField, entityClass: PsiClass): String {
        // Extract join column information
        return "${entityClass.name?.lowercase()}_id"
    }

    private fun extractInverseJoinColumn(field: PsiField): String {
        // Extract inverse join column information
        return "${extractCollectionTargetEntity(field).lowercase()}_id"
    }

    private fun findClassByName(project: Project, className: String): PsiClass? {
        // Find PSI class by qualified name
        return null
    }
}

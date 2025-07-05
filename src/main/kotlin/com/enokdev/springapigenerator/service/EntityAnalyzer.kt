package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.model.EntityField
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.RelationType
import com.intellij.psi.*

/**
 * Service for analyzing JPA entity structure.
 */
class EntityAnalyzer {

    /**
     * Analyzes a JPA entity class and extracts metadata.
     * @param psiClass the JPA entity class
     * @return entity metadata
     */
    fun analyzeEntity(psiClass: PsiClass): EntityMetadata {
        val fields = extractFields(psiClass)
        val idType = extractIdType(psiClass)
        val tableName = extractTableName(psiClass)

        return EntityMetadata(
            className = psiClass.name ?: "",
            qualifiedClassName = psiClass.qualifiedName ?: "",
            packageName = psiClass.qualifiedName?.substringBeforeLast(".") ?: "",
            fields = fields,
            idType = idType,
            tableName = tableName
        )
    }

    /**
     * Extracts fields from an entity class.
     * @param psiClass the JPA entity class
     * @return list of entity fields with metadata
     */
    private fun extractFields(psiClass: PsiClass): List<EntityField> {
        return psiClass.allFields.mapNotNull { field ->
            // Check if the field is constant or static
            if (isConstantField(field) || field.hasModifierProperty(PsiModifier.STATIC)) {
                // Skip constants and static fields
                return@mapNotNull null
            }

            val type = field.type.canonicalText
            val name = field.name
            val nullable = !field.annotations.any { it.qualifiedName == "javax.validation.constraints.NotNull" }
            val columnName = extractColumnName(field)
            val relationType = extractRelationType(field)
            val relationTarget = if (relationType != RelationType.NONE) extractRelationTarget(field) else null

            EntityField(
                name = name,
                type = type,
                nullable = nullable,
                columnName = columnName,
                relationType = relationType,
                relationTargetEntity = relationTarget
            )
        }
    }

    /**
     * Checks if a field is a constant (final and initialized with a literal)
     */
    private fun isConstantField(field: PsiField): Boolean {
        return field.hasModifierProperty(PsiModifier.FINAL) &&
                field.initializer != null &&
                (field.initializer is PsiLiteralExpression)
    }

    /**
     * Extracts the ID type of an entity.
     * @param psiClass the JPA entity class
     * @return ID field type
     */
    private fun extractIdType(psiClass: PsiClass): String {
        // First check fields with @Id annotation
        for (field in psiClass.allFields) {
            if (field.annotations.any { it.qualifiedName == "javax.persistence.Id" || it.qualifiedName == "jakarta.persistence.Id" }) {
                return field.type.canonicalText
            }
        }

        // Then check getters with @Id annotation
        for (method in psiClass.allMethods) {
            if (isGetterMethod(method) &&
                method.annotations.any { it.qualifiedName == "javax.persistence.Id" || it.qualifiedName == "jakarta.persistence.Id" }) {
                return method.returnType?.canonicalText ?: "java.lang.Long"
            }
        }

        // Default to Long if not found
        return "java.lang.Long"
    }

    /**
     * Checks if a method is a getter (accessor method)
     */
    private fun isGetterMethod(method: PsiMethod): Boolean {
        val name = method.name
        return (name.startsWith("get") && name.length > 3) &&
               !method.hasModifierProperty(PsiModifier.STATIC) &&
               method.parameterList.parametersCount == 0 &&
               method.returnType != null &&
               method.returnType != PsiTypes.voidType()  // Utilisation de PsiTypes.voidType() au lieu de PsiType.VOID
    }

    /**
     * Extracts the table name from @Table annotation.
     * @param psiClass the JPA entity class
     * @return table name or class name if @Table annotation is not present
     */
    private fun extractTableName(psiClass: PsiClass): String {
        // Look for @Table annotation
        val tableAnnotation = psiClass.annotations.find {
            it.qualifiedName == "javax.persistence.Table" || it.qualifiedName == "jakarta.persistence.Table"
        }

        if (tableAnnotation != null) {
            // Extract name attribute from @Table
            val nameAttribute = tableAnnotation.findAttributeValue("name")
            if (nameAttribute != null && nameAttribute is PsiLiteralExpression) {
                val nameValue = nameAttribute.value
                if (nameValue is String) {
                    return nameValue
                }
            }
        }

        // Fall back to class name with underscore naming convention
        return psiClass.name?.replace("([a-z])([A-Z])".toRegex(), "$1_$2")?.lowercase() ?: ""
    }

    /**
     * Extracts column name from @Column annotation.
     * @param field the entity field
     * @return column name or field name if @Column annotation is not present
     */
    private fun extractColumnName(field: PsiField): String {
        // Look for @Column annotation
        val columnAnnotation = field.annotations.find {
            it.qualifiedName == "javax.persistence.Column" || it.qualifiedName == "jakarta.persistence.Column"
        }

        if (columnAnnotation != null) {
            // Extract name attribute from @Column
            val nameAttribute = columnAnnotation.findAttributeValue("name")
            if (nameAttribute != null && nameAttribute is PsiLiteralExpression) {
                val nameValue = nameAttribute.value
                if (nameValue is String) {
                    return nameValue
                }
            }
        }

        // Fall back to field name with underscore naming convention
        return field.name.replace("([a-z])([A-Z])".toRegex(), "$1_$2").lowercase()
    }

    /**
     * Extracts relation type from JPA annotations.
     * @param field the entity field
     * @return relation type
     */
    private fun extractRelationType(field: PsiField): RelationType {
        val annotations = field.annotations.map { it.qualifiedName }

        return when {
            annotations.any { it == "javax.persistence.OneToMany" || it == "jakarta.persistence.OneToMany" } ->
                RelationType.ONE_TO_MANY
            annotations.any { it == "javax.persistence.ManyToOne" || it == "jakarta.persistence.ManyToOne" } ->
                RelationType.MANY_TO_ONE
            annotations.any { it == "javax.persistence.OneToOne" || it == "jakarta.persistence.OneToOne" } ->
                RelationType.ONE_TO_ONE
            annotations.any { it == "javax.persistence.ManyToMany" || it == "jakarta.persistence.ManyToMany" } ->
                RelationType.MANY_TO_MANY
            else -> RelationType.NONE
        }
    }

    /**
     * Extracts relation target entity class name.
     * @param field the entity field
     * @return target entity class name
     */
    private fun extractRelationTarget(field: PsiField): String {
        val type = field.type

        // For collection types, extract the generic type parameter
        if (type is PsiClassType) {
            val parameters = type.parameters
            if (parameters.isNotEmpty()) {
                // For collections, get the generic type
                val elementType = parameters[0]
                return elementType.canonicalText
            }

            // For non-collection types, return the class name
            return type.canonicalText
        }

        return ""
    }
}

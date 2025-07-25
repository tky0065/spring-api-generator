package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.EntityField
import com.enokdev.springapigenerator.model.RelationType
import com.enokdev.springapigenerator.service.ComplexRelationshipAnalyzer.RelationshipInfo
import com.enokdev.springapigenerator.service.ComplexRelationshipAnalyzer.CascadeType
import com.enokdev.springapigenerator.service.ComplexRelationshipAnalyzer.FetchType

/**
 * Service for generating bidirectional relationship management code.
 */
class BidirectionalRelationshipManager {

    data class RelationshipSyncMethods(
        val addMethod: String,
        val removeMethod: String,
        val setMethod: String? = null,
        val clearMethod: String? = null,
        val imports: Set<String> = emptySet()
    )

    data class CascadeConfiguration(
        val shouldGenerateOrphanRemoval: Boolean = false,
        val shouldGenerateCascadePersist: Boolean = false,
        val shouldGenerateCascadeMerge: Boolean = false,
        val shouldGenerateCascadeRemove: Boolean = false,
        val shouldGeneratePreventCircularReference: Boolean = true
    )

    /**
     * Generates synchronization methods for bidirectional relationships.
     */
    fun generateBidirectionalSyncMethods(
        entityMetadata: EntityMetadata,
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): RelationshipSyncMethods {

        return when (relationshipInfo.relationType) {
            RelationType.ONE_TO_ONE -> generateOneToOneSyncMethods(entityMetadata, relationshipInfo, styleAdapter)
            RelationType.ONE_TO_MANY -> generateOneToManySyncMethods(entityMetadata, relationshipInfo, styleAdapter)
            RelationType.MANY_TO_ONE -> generateManyToOneSyncMethods(entityMetadata, relationshipInfo, styleAdapter)
            RelationType.MANY_TO_MANY -> generateManyToManySyncMethods(entityMetadata, relationshipInfo, styleAdapter)
            else -> RelationshipSyncMethods("", "") // No sync methods for other types
        }
    }

    /**
     * Generates helper methods for relationship management.
     */
    fun generateRelationshipHelperMethods(
        entityMetadata: EntityMetadata,
        relationships: List<RelationshipInfo>,
        styleAdapter: CodeStyleAdapter
    ): String {
        val methods = StringBuilder()
        val indent = styleAdapter.getIndentation()

        relationships.filter { it.isBidirectional }.forEach { relationship ->
            val helperMethods = generateHelperMethodsForRelationship(entityMetadata, relationship, styleAdapter)
            methods.append(helperMethods).append("\n\n")
        }

        // Generate utility methods for relationship validation
        methods.append(generateRelationshipValidationMethods(entityMetadata, relationships, styleAdapter))

        return methods.toString().trimEnd()
    }

    /**
     * Generates cascade handling code.
     */
    fun generateCascadeHandling(
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): String {
        val cascadeConfig = determineCascadeConfiguration(relationshipInfo)
        val methods = StringBuilder()
        val indent = styleAdapter.getIndentation()

        if (cascadeConfig.shouldGenerateOrphanRemoval) {
            methods.append(generateOrphanRemovalMethod(relationshipInfo, styleAdapter))
            methods.append("\n\n")
        }

        if (cascadeConfig.shouldGenerateCascadePersist) {
            methods.append(generateCascadePersistMethod(relationshipInfo, styleAdapter))
            methods.append("\n\n")
        }

        if (cascadeConfig.shouldGeneratePreventCircularReference) {
            methods.append(generateCircularReferencePreventionMethod(relationshipInfo, styleAdapter))
        }

        return methods.toString().trimEnd()
    }

    private fun generateOneToOneSyncMethods(
        entityMetadata: EntityMetadata,
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): RelationshipSyncMethods {
        val indent = styleAdapter.getIndentation()
        val fieldName = styleAdapter.adaptFieldName(relationshipInfo.fieldName)
        val entityName = entityMetadata.className
        val targetEntityName = relationshipInfo.targetEntity.substringAfterLast(".")
        val setterName = styleAdapter.formatSetterName(relationshipInfo.fieldName)
        val inverseFieldName = relationshipInfo.inverseFieldName ?: entityMetadata.className.lowercase()

        val setMethod = """
            ${indent}public void $setterName($targetEntityName $fieldName) {
            ${styleAdapter.getIndentation(2)}// Break old relationship
            ${styleAdapter.getIndentation(2)}if (this.$fieldName != null) {
            ${styleAdapter.getIndentation(3)}this.$fieldName.set${inverseFieldName.replaceFirstChar { it.uppercase() }}(null);
            ${styleAdapter.getIndentation(2)}}
            ${styleAdapter.getIndentation(2)}
            ${styleAdapter.getIndentation(2)}// Set new relationship
            ${styleAdapter.getIndentation(2)}this.$fieldName = $fieldName;
            ${styleAdapter.getIndentation(2)}
            ${styleAdapter.getIndentation(2)}// Establish bidirectional link
            ${styleAdapter.getIndentation(2)}if ($fieldName != null && $fieldName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != this) {
            ${styleAdapter.getIndentation(3)}$fieldName.set${inverseFieldName.replaceFirstChar { it.uppercase() }}(this);
            ${styleAdapter.getIndentation(2)}}
            ${indent}}
        """.trimIndent()

        return RelationshipSyncMethods(
            addMethod = "",
            removeMethod = "",
            setMethod = setMethod,
            imports = setOf("java.util.Objects")
        )
    }

    private fun generateOneToManySyncMethods(
        entityMetadata: EntityMetadata,
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): RelationshipSyncMethods {
        val indent = styleAdapter.getIndentation()
        val fieldName = styleAdapter.adaptFieldName(relationshipInfo.fieldName)
        val entityName = entityMetadata.className
        val targetEntityName = relationshipInfo.targetEntity.substringAfterLast(".")
        val singularName = singularize(relationshipInfo.fieldName)
        val inverseFieldName = relationshipInfo.inverseFieldName ?: entityMetadata.className.lowercase()

        val addMethod = """
            ${indent}public void add${singularName.replaceFirstChar { it.uppercase() }}($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}if ($singularName == null) return;
            ${styleAdapter.getIndentation(2)}
            ${styleAdapter.getIndentation(2)}// Initialize collection if null
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == null) {
            ${styleAdapter.getIndentation(3)}this.$fieldName = new ArrayList<>();
            ${styleAdapter.getIndentation(2)}}
            ${styleAdapter.getIndentation(2)}
            ${styleAdapter.getIndentation(2)}// Add to collection if not already present
            ${styleAdapter.getIndentation(2)}if (!this.$fieldName.contains($singularName)) {
            ${styleAdapter.getIndentation(3)}this.$fieldName.add($singularName);
            ${styleAdapter.getIndentation(2)}}
            ${styleAdapter.getIndentation(2)}
            ${styleAdapter.getIndentation(2)}// Set inverse relationship
            ${styleAdapter.getIndentation(2)}if ($singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != this) {
            ${styleAdapter.getIndentation(3)}$singularName.set${inverseFieldName.replaceFirstChar { it.uppercase() }}(this);
            ${styleAdapter.getIndentation(2)}}
            ${indent}}
        """.trimIndent()

        val removeMethod = """
            ${indent}public void remove${singularName.replaceFirstChar { it.uppercase() }}($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}if ($singularName == null || this.$fieldName == null) return;
            ${styleAdapter.getIndentation(2)}
            ${styleAdapter.getIndentation(2)}// Remove from collection
            ${styleAdapter.getIndentation(2)}this.$fieldName.remove($singularName);
            ${styleAdapter.getIndentation(2)}
            ${styleAdapter.getIndentation(2)}// Clear inverse relationship
            ${styleAdapter.getIndentation(2)}if ($singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() == this) {
            ${styleAdapter.getIndentation(3)}$singularName.set${inverseFieldName.replaceFirstChar { it.uppercase() }}(null);
            ${styleAdapter.getIndentation(2)}}
            ${indent}}
        """.trimIndent()

        val clearMethod = """
            ${indent}public void clear${fieldName.replaceFirstChar { it.uppercase() }}() {
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == null) return;
            ${styleAdapter.getIndentation(2)}
            ${styleAdapter.getIndentation(2)}// Clear inverse relationships
            ${styleAdapter.getIndentation(2)}for ($targetEntityName $singularName : new ArrayList<>(this.$fieldName)) {
            ${styleAdapter.getIndentation(3)}remove${singularName.replaceFirstChar { it.uppercase() }}($singularName);
            ${styleAdapter.getIndentation(2)}}
            ${indent}}
        """.trimIndent()

        return RelationshipSyncMethods(
            addMethod = addMethod,
            removeMethod = removeMethod,
            clearMethod = clearMethod,
            imports = setOf("java.util.ArrayList", "java.util.List")
        )
    }

    private fun generateManyToOneSyncMethods(
        entityMetadata: EntityMetadata,
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): RelationshipSyncMethods {
        // Similar to OneToOne but handles the many side
        return generateOneToOneSyncMethods(entityMetadata, relationshipInfo, styleAdapter)
    }

    private fun generateManyToManySyncMethods(
        entityMetadata: EntityMetadata,
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): RelationshipSyncMethods {
        val indent = styleAdapter.getIndentation()
        val fieldName = styleAdapter.adaptFieldName(relationshipInfo.fieldName)
        val targetEntityName = relationshipInfo.targetEntity.substringAfterLast(".")
        val singularName = singularize(relationshipInfo.fieldName)
        val inverseFieldName = relationshipInfo.inverseFieldName ?: pluralize(entityMetadata.className.lowercase())

        val addMethod = """
            ${indent}public void add${singularName.replaceFirstChar { it.uppercase() }}($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}if ($singularName == null) return;
            ${styleAdapter.getIndentation(2)}
            ${styleAdapter.getIndentation(2)}// Initialize collection if null
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == null) {
            ${styleAdapter.getIndentation(3)}this.$fieldName = new HashSet<>();
            ${styleAdapter.getIndentation(2)}}
            ${styleAdapter.getIndentation(2)}
            ${styleAdapter.getIndentation(2)}// Add to collection if not already present
            ${styleAdapter.getIndentation(2)}if (this.$fieldName.add($singularName)) {
            ${styleAdapter.getIndentation(3)}// Set inverse relationship
            ${styleAdapter.getIndentation(3)}if ($singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() == null) {
            ${styleAdapter.getIndentation(4)}$singularName.set${inverseFieldName.replaceFirstChar { it.uppercase() }}(new HashSet<>());
            ${styleAdapter.getIndentation(3)}}
            ${styleAdapter.getIndentation(3)}$singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}().add(this);
            ${styleAdapter.getIndentation(2)}}
            ${indent}}
        """.trimIndent()

        val removeMethod = """
            ${indent}public void remove${singularName.replaceFirstChar { it.uppercase() }}($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}if ($singularName == null || this.$fieldName == null) return;
            ${styleAdapter.getIndentation(2)}
            ${styleAdapter.getIndentation(2)}// Remove from collection
            ${styleAdapter.getIndentation(2)}if (this.$fieldName.remove($singularName)) {
            ${styleAdapter.getIndentation(3)}// Clear inverse relationship
            ${styleAdapter.getIndentation(3)}if ($singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != null) {
            ${styleAdapter.getIndentation(4)}$singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}().remove(this);
            ${styleAdapter.getIndentation(3)}}
            ${styleAdapter.getIndentation(2)}}
            ${indent}}
        """.trimIndent()

        return RelationshipSyncMethods(
            addMethod = addMethod,
            removeMethod = removeMethod,
            imports = setOf("java.util.HashSet", "java.util.Set")
        )
    }

    private fun generateHelperMethodsForRelationship(
        entityMetadata: EntityMetadata,
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): String {
        val methods = StringBuilder()
        val indent = styleAdapter.getIndentation()
        val fieldName = styleAdapter.adaptFieldName(relationshipInfo.fieldName)
        val targetEntityName = relationshipInfo.targetEntity.substringAfterLast(".")

        when (relationshipInfo.relationType) {
            RelationType.ONE_TO_MANY, RelationType.MANY_TO_MANY -> {
                // Generate count method
                methods.append("""
                    ${indent}public int count${fieldName.replaceFirstChar { it.uppercase() }}() {
                    ${styleAdapter.getIndentation(2)}return this.$fieldName != null ? this.$fieldName.size() : 0;
                    ${indent}}
                """.trimIndent())

                methods.append("\n\n")

                // Generate has method
                val singularName = singularize(relationshipInfo.fieldName)
                methods.append("""
                    ${indent}public boolean has${singularName.replaceFirstChar { it.uppercase() }}($targetEntityName $singularName) {
                    ${styleAdapter.getIndentation(2)}return this.$fieldName != null && this.$fieldName.contains($singularName);
                    ${indent}}
                """.trimIndent())
            }
            RelationType.ONE_TO_ONE, RelationType.MANY_TO_ONE -> {
                // Generate has method
                methods.append("""
                    ${indent}public boolean has${fieldName.replaceFirstChar { it.uppercase() }}() {
                    ${styleAdapter.getIndentation(2)}return this.$fieldName != null;
                    ${indent}}
                """.trimIndent())
            }
            else -> { /* No helper methods for other types */ }
        }

        return methods.toString()
    }

    private fun generateRelationshipValidationMethods(
        entityMetadata: EntityMetadata,
        relationships: List<RelationshipInfo>,
        styleAdapter: CodeStyleAdapter
    ): String {
        val methods = StringBuilder()
        val indent = styleAdapter.getIndentation()

        // Generate method to validate all relationships
        methods.append("""
            ${indent}/**
            ${indent} * Validates the consistency of all bidirectional relationships.
            ${indent} * @return true if all relationships are consistent, false otherwise
            ${indent} */
            ${indent}public boolean validateRelationships() {
            ${styleAdapter.getIndentation(2)}boolean isValid = true;
        """.trimIndent())

        relationships.filter { it.isBidirectional }.forEach { relationship ->
            val fieldName = styleAdapter.adaptFieldName(relationship.fieldName)
            val validation = generateRelationshipValidation(relationship, styleAdapter)
            methods.append("\n${styleAdapter.getIndentation(2)}$validation")
        }

        methods.append("""
            
            ${styleAdapter.getIndentation(2)}return isValid;
            ${indent}}
        """.trimIndent())

        return methods.toString()
    }

    private fun generateRelationshipValidation(
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): String {
        val fieldName = styleAdapter.adaptFieldName(relationshipInfo.fieldName)
        val inverseFieldName = relationshipInfo.inverseFieldName ?: "parent"

        return when (relationshipInfo.relationType) {
            RelationType.ONE_TO_ONE -> {
                """
                // Validate ${relationshipInfo.fieldName} relationship
                if (this.$fieldName != null && this.$fieldName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != this) {
                ${styleAdapter.getIndentation()}isValid = false;
                }
                """.trimIndent()
            }
            RelationType.ONE_TO_MANY -> {
                val singularName = singularize(relationshipInfo.fieldName)
                """
                // Validate ${relationshipInfo.fieldName} relationship
                if (this.$fieldName != null) {
                ${styleAdapter.getIndentation()}for (var $singularName : this.$fieldName) {
                ${styleAdapter.getIndentation(2)}if ($singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != this) {
                ${styleAdapter.getIndentation(3)}isValid = false;
                ${styleAdapter.getIndentation(2)}break;
                ${styleAdapter.getIndentation()}}
                ${styleAdapter.getIndentation()}}
                }
                """.trimIndent()
            }
            else -> ""
        }
    }

    private fun generateOrphanRemovalMethod(
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): String {
        if (!relationshipInfo.orphanRemoval) return ""

        val indent = styleAdapter.getIndentation()
        val fieldName = styleAdapter.adaptFieldName(relationshipInfo.fieldName)

        return """
            ${indent}/**
            ${indent} * Handles orphan removal for ${relationshipInfo.fieldName}.
            ${indent} */
            ${indent}private void handleOrphanRemoval() {
            ${styleAdapter.getIndentation(2)}// Implementation would depend on JPA EntityManager
            ${styleAdapter.getIndentation(2)}// This is a placeholder for orphan removal logic
            ${indent}}
        """.trimIndent()
    }

    private fun generateCascadePersistMethod(
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): String {
        if (!relationshipInfo.cascade.contains(CascadeType.PERSIST)) return ""

        val indent = styleAdapter.getIndentation()

        return """
            ${indent}/**
            ${indent} * Handles cascade persist for ${relationshipInfo.fieldName}.
            ${indent} */
            ${indent}private void handleCascadePersist() {
            ${styleAdapter.getIndentation(2)}// Implementation would depend on JPA EntityManager
            ${styleAdapter.getIndentation(2)}// This is a placeholder for cascade persist logic
            ${indent}}
        """.trimIndent()
    }

    private fun generateCircularReferencePreventionMethod(
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): String {
        val indent = styleAdapter.getIndentation()

        return """
            ${indent}/**
            ${indent} * Prevents circular references in relationships.
            ${indent} */
            ${indent}private void preventCircularReference() {
            ${styleAdapter.getIndentation(2)}// Implementation for preventing circular references
            ${styleAdapter.getIndentation(2)}// This helps avoid infinite loops in bidirectional relationships
            ${indent}}
        """.trimIndent()
    }

    private fun determineCascadeConfiguration(relationshipInfo: RelationshipInfo): CascadeConfiguration {
        return CascadeConfiguration(
            shouldGenerateOrphanRemoval = relationshipInfo.orphanRemoval,
            shouldGenerateCascadePersist = relationshipInfo.cascade.contains(CascadeType.PERSIST),
            shouldGenerateCascadeMerge = relationshipInfo.cascade.contains(CascadeType.MERGE),
            shouldGenerateCascadeRemove = relationshipInfo.cascade.contains(CascadeType.REMOVE),
            shouldGeneratePreventCircularReference = relationshipInfo.isBidirectional
        )
    }

    private fun singularize(word: String): String {
        return when {
            word.endsWith("ies") -> word.dropLast(3) + "y"
            word.endsWith("es") -> word.dropLast(2)
            word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
            else -> word
        }
    }

    private fun pluralize(word: String): String {
        return when {
            word.endsWith("y") -> word.dropLast(1) + "ies"
            word.endsWith("s") || word.endsWith("sh") || word.endsWith("ch") -> word + "es"
            else -> word + "s"
        }
    }
}

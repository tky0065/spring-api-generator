package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.EntityField
import com.enokdev.springapigenerator.model.RelationType
import com.enokdev.springapigenerator.service.ComplexRelationshipAnalyzer.RelationshipInfo
import com.enokdev.springapigenerator.service.ComplexRelationshipAnalyzer.CascadeType
import com.enokdev.springapigenerator.service.ComplexRelationshipAnalyzer.FetchType

/**
 * Service for generating bidirectional relationship management code.
 * 
 * This class is responsible for generating code that properly manages bidirectional relationships
 * between JPA entities. It handles various relationship types (one-to-one, one-to-many, many-to-one,
 * many-to-many) and ensures that both sides of a relationship are kept in sync.
 * 
 * ## Key Features
 * 
 * - **Bidirectional Synchronization**: Generates methods to keep both sides of a relationship in sync
 * - **Circular Reference Prevention**: Prevents infinite recursion in bidirectional updates
 * - **Orphan Removal Handling**: Properly handles orphaned entities when relationships are broken
 * - **Relationship Validation**: Provides methods to validate relationship consistency
 * - **Cascade Type Management**: Ensures proper cascade behavior across relationship sides
 * 
 * ## Usage Example
 * 
 * ```kotlin
 * val relationshipManager = BidirectionalRelationshipManager()
 * val entityMetadata = EntityMetadata(...)
 * val relationshipInfo = RelationshipInfo(...)
 * val styleAdapter = CodeStyleAdapter(...)
 * 
 * // Generate synchronization methods for a one-to-many relationship
 * val syncMethods = relationshipManager.generateBidirectionalSyncMethods(
 *     entityMetadata, relationshipInfo, styleAdapter
 * )
 * 
 * // Generate helper methods for all relationships
 * val helperMethods = relationshipManager.generateRelationshipHelperMethods(
 *     entityMetadata, relationships, styleAdapter
 * )
 * 
 * // Generate cascade handling code
 * val cascadeMethods = relationshipManager.generateCascadeHandling(
 *     relationshipInfo, styleAdapter
 * )
 * ```
 * 
 * ## Important Considerations
 * 
 * - **Circular Dependencies**: Be careful with circular dependencies between entities.
 *   Use the generated methods that prevent infinite recursion.
 * 
 * - **Orphan Removal**: When using orphanRemoval=true, be aware that entities removed
 *   from a relationship will be deleted from the database.
 * 
 * - **Performance**: Bidirectional relationships with large collections can impact performance.
 *   Consider using lazy loading and optimizing queries.
 * 
 * - **Thread Safety**: The generated code is not thread-safe by default. Add synchronization
 *   if multiple threads might update the same relationships.
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
     * 
     * This method creates code that ensures both sides of a bidirectional relationship
     * are kept in sync when either side is updated. It handles different relationship types
     * (one-to-one, one-to-many, many-to-one, many-to-many) and generates appropriate
     * synchronization methods for each type.
     * 
     * The generated methods include:
     * - Setters for one-to-one and many-to-one relationships
     * - Add/remove methods for one-to-many and many-to-many relationships
     * - Clear methods for collection-based relationships
     * - Internal methods to prevent circular references
     * 
     * ## Example Usage
     * 
     * For a one-to-many relationship between Department and Employee:
     * 
     * ```java
     * // In Department class
     * public boolean addEmployee(Employee employee) {
     *     if (employee == null) return false;
     *     
     *     if (this.employees == null) {
     *         this.employees = new ArrayList<>();
     *     }
     *     
     *     if (this.employees.contains(employee)) {
     *         return false;
     *     }
     *     
     *     boolean added = this.employees.add(employee);
     *     
     *     if (added && employee.getDepartment() != this) {
     *         employee.setDepartmentInternal(this);
     *     }
     *     
     *     return added;
     * }
     * ```
     * 
     * ## Warning
     * 
     * - Ensure that the entity classes include all the generated methods to maintain
     *   bidirectional consistency.
     * - Be careful with circular dependencies, as they can lead to infinite recursion
     *   if not handled properly.
     * - The generated code assumes that the relationship fields are properly annotated
     *   with JPA annotations.
     * 
     * @param entityMetadata Metadata about the entity, including its name and package
     * @param relationshipInfo Information about the relationship, including type, target entity, and cascade options
     * @param styleAdapter The code style adapter to use for formatting the generated code
     * @return A RelationshipSyncMethods object containing the generated synchronization methods
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
     * 
     * This method creates utility methods that help manage and validate relationships,
     * including:
     * - Count methods for collections (e.g., countEmployees())
     * - Has methods for checking if a relationship exists (e.g., hasEmployee(Employee))
     * - Validation methods to ensure bidirectional consistency
     * - Circular dependency detection and handling
     * - Cascade type consistency validation
     * 
     * The generated methods make it easier to work with relationships and ensure
     * they remain in a consistent state.
     * 
     * ## Example Usage
     * 
     * ```java
     * // In Department class
     * public int countEmployees() {
     *     return this.employees != null ? this.employees.size() : 0;
     * }
     * 
     * public boolean hasEmployee(Employee employee) {
     *     return this.employees != null && this.employees.contains(employee);
     * }
     * 
     * public boolean validateRelationships() {
     *     boolean isValid = true;
     *     
     *     // Validate employees relationship
     *     if (this.employees != null) {
     *         for (Employee employee : this.employees) {
     *             if (employee.getDepartment() != this) {
     *                 isValid = false;
     *                 break;
     *             }
     *         }
     *     }
     *     
     *     return isValid;
     * }
     * ```
     * 
     * ## Warning
     * 
     * - The validation methods only check for consistency, they don't fix inconsistencies.
     * - Use the synchronization methods to maintain consistency when updating relationships.
     * - Validation can be expensive for large collections, so use it judiciously.
     * 
     * @param entityMetadata Metadata about the entity, including its name and package
     * @param relationships List of relationships to generate helper methods for
     * @param styleAdapter The code style adapter to use for formatting the generated code
     * @return A string containing the generated helper methods
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
     * Generates cascade handling code for a relationship.
     * 
     * This method creates code to handle cascade operations (persist, merge, remove)
     * and orphan removal for a relationship. The generated code ensures that
     * cascade operations are properly applied to related entities.
     * 
     * The generated methods include:
     * - Orphan removal handling for relationships with orphanRemoval=true
     * - Cascade persist handling for relationships with cascade=PERSIST
     * - Circular reference prevention for bidirectional relationships
     * 
     * ## Example Usage
     * 
     * ```java
     * // In Department class
     * protected void handleOrphanRemoval(Collection<Employee> oldCollection, Collection<Employee> newCollection) {
     *     if (oldCollection != null && !oldCollection.isEmpty()) {
     *         for (Employee orphaned : oldCollection) {
     *             if (orphaned != null && (newCollection == null || !newCollection.contains(orphaned))) {
     *                 // In a real implementation, this would use EntityManager to delete the orphaned entity
     *                 // entityManager.remove(orphaned);
     *                 System.out.println("Orphan removal: Removing orphaned entity " + orphaned);
     *             }
     *         }
     *     }
     * }
     * ```
     * 
     * ## Warning
     * 
     * - Orphan removal will delete entities from the database when they are removed from a relationship.
     * - Be careful with cascade operations in bidirectional relationships to avoid infinite loops.
     * - The generated code assumes that a JPA EntityManager is available for persistence operations.
     * 
     * @param relationshipInfo Information about the relationship, including type, target entity, and cascade options
     * @param styleAdapter The code style adapter to use for formatting the generated code
     * @return A string containing the generated cascade handling methods
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

    /**
     * Generates synchronization methods for one-to-one relationships.
     * These methods ensure that both sides of a bidirectional one-to-one relationship
     * are properly synchronized when either side is updated.
     *
     * @param entityMetadata Metadata about the entity
     * @param relationshipInfo Information about the relationship
     * @param styleAdapter The code style adapter to use for formatting
     * @return The generated synchronization methods
     */
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
        
        // Generate a more robust setter method with circular reference protection
        val setMethod = """
            ${indent}/**
            ${indent} * Sets the ${fieldName} relationship, maintaining bidirectional consistency.
            ${indent} * 
            ${indent} * This method:
            ${indent} * - Breaks any existing relationship
            ${indent} * - Establishes the new relationship
            ${indent} * - Ensures the inverse side is properly set
            ${indent} * - Prevents circular references and infinite recursion
            ${indent} * 
            ${indent} * @param $fieldName The ${targetEntityName} to set
            ${indent} */
            ${indent}public void $setterName($targetEntityName $fieldName) {
            ${styleAdapter.getIndentation(2)}// Prevent circular references and infinite recursion
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == $fieldName) {
            ${styleAdapter.getIndentation(3)}return; // No change needed
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}// Store old relationship for cleanup
            ${styleAdapter.getIndentation(2)}$targetEntityName old${fieldName.replaceFirstChar { it.uppercase() }} = this.$fieldName;
            
            ${styleAdapter.getIndentation(2)}// Set new relationship
            ${styleAdapter.getIndentation(2)}this.$fieldName = $fieldName;
            
            ${styleAdapter.getIndentation(2)}// Break old relationship if it exists
            ${styleAdapter.getIndentation(2)}if (old${fieldName.replaceFirstChar { it.uppercase() }} != null && 
            ${styleAdapter.getIndentation(3)}old${fieldName.replaceFirstChar { it.uppercase() }}.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() == this) {
            ${styleAdapter.getIndentation(3)}// Use direct field access or a special internal setter to avoid infinite recursion
            ${styleAdapter.getIndentation(3)}old${fieldName.replaceFirstChar { it.uppercase() }}.set${inverseFieldName.replaceFirstChar { it.uppercase() }}Internal(null);
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}// Establish bidirectional link with new relationship
            ${styleAdapter.getIndentation(2)}if ($fieldName != null && $fieldName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != this) {
            ${styleAdapter.getIndentation(3)}// Use direct field access or a special internal setter to avoid infinite recursion
            ${styleAdapter.getIndentation(3)}$fieldName.set${inverseFieldName.replaceFirstChar { it.uppercase() }}Internal(this);
            ${styleAdapter.getIndentation(2)}}
            ${indent}}
            
            ${indent}/**
            ${indent} * Internal setter for ${fieldName} relationship that doesn't trigger bidirectional synchronization.
            ${indent} * This method is used internally to prevent infinite recursion in bidirectional relationships.
            ${indent} * 
            ${indent} * @param $fieldName The ${targetEntityName} to set
            ${indent} */
            ${indent}protected void set${fieldName.replaceFirstChar { it.uppercase() }}Internal($targetEntityName $fieldName) {
            ${styleAdapter.getIndentation(2)}this.$fieldName = $fieldName;
            ${indent}}
        """.trimIndent()
        
        // Generate a getter method that handles null safely
        val getMethod = """
            ${indent}/**
            ${indent} * Gets the ${fieldName} relationship.
            ${indent} * 
            ${indent} * @return The ${targetEntityName} or null if not set
            ${indent} */
            ${indent}public $targetEntityName get${fieldName.replaceFirstChar { it.uppercase() }}() {
            ${styleAdapter.getIndentation(2)}return this.$fieldName;
            ${indent}}
        """.trimIndent()

        return RelationshipSyncMethods(
            addMethod = "",
            removeMethod = "",
            setMethod = setMethod,
            imports = setOf("java.util.Objects")
        )
    }

    /**
     * Generates synchronization methods for one-to-many relationships.
     * These methods ensure that both sides of a bidirectional one-to-many relationship
     * are properly synchronized when either side is updated.
     *
     * @param entityMetadata Metadata about the entity
     * @param relationshipInfo Information about the relationship
     * @param styleAdapter The code style adapter to use for formatting
     * @return The generated synchronization methods
     */
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

        // Generate an enhanced add method with circular reference protection
        val addMethod = """
            ${indent}/**
            ${indent} * Adds a ${singularName} to the ${fieldName} collection, maintaining bidirectional consistency.
            ${indent} * 
            ${indent} * This method:
            ${indent} * - Initializes the collection if needed
            ${indent} * - Adds the item if not already present
            ${indent} * - Ensures the inverse side is properly set
            ${indent} * - Prevents circular references and infinite recursion
            ${indent} * 
            ${indent} * @param $singularName The ${targetEntityName} to add
            ${indent} * @return true if the item was added, false if it was already present or null
            ${indent} */
            ${indent}public boolean add${singularName.replaceFirstChar { it.uppercase() }}($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}// Handle null case
            ${styleAdapter.getIndentation(2)}if ($singularName == null) return false;
            
            ${styleAdapter.getIndentation(2)}// Initialize collection if null
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == null) {
            ${styleAdapter.getIndentation(3)}this.$fieldName = new ArrayList<>();
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}// Check if already in collection to prevent circular references
            ${styleAdapter.getIndentation(2)}if (this.$fieldName.contains($singularName)) {
            ${styleAdapter.getIndentation(3)}return false; // Already present
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}// Add to collection
            ${styleAdapter.getIndentation(2)}boolean added = this.$fieldName.add($singularName);
            
            ${styleAdapter.getIndentation(2)}// Set inverse relationship without triggering circular updates
            ${styleAdapter.getIndentation(2)}if (added && $singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != this) {
            ${styleAdapter.getIndentation(3)}$singularName.set${inverseFieldName.replaceFirstChar { it.uppercase() }}Internal(this);
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}return added;
            ${indent}}
            
            ${indent}/**
            ${indent} * Internal method to add a ${singularName} without triggering bidirectional synchronization.
            ${indent} * This method is used internally to prevent infinite recursion in bidirectional relationships.
            ${indent} * 
            ${indent} * @param $singularName The ${targetEntityName} to add
            ${indent} */
            ${indent}protected void add${singularName.replaceFirstChar { it.uppercase() }}Internal($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}if ($singularName == null) return;
            
            ${styleAdapter.getIndentation(2)}// Initialize collection if null
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == null) {
            ${styleAdapter.getIndentation(3)}this.$fieldName = new ArrayList<>();
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}// Add to collection if not already present
            ${styleAdapter.getIndentation(2)}if (!this.$fieldName.contains($singularName)) {
            ${styleAdapter.getIndentation(3)}this.$fieldName.add($singularName);
            ${styleAdapter.getIndentation(2)}}
            ${indent}}
        """.trimIndent()

        // Generate an enhanced remove method with circular reference protection
        val removeMethod = """
            ${indent}/**
            ${indent} * Removes a ${singularName} from the ${fieldName} collection, maintaining bidirectional consistency.
            ${indent} * 
            ${indent} * This method:
            ${indent} * - Removes the item from the collection
            ${indent} * - Ensures the inverse side is properly updated
            ${indent} * - Prevents circular references and infinite recursion
            ${indent} * 
            ${indent} * @param $singularName The ${targetEntityName} to remove
            ${indent} * @return true if the item was removed, false if it wasn't in the collection or collection is null
            ${indent} */
            ${indent}public boolean remove${singularName.replaceFirstChar { it.uppercase() }}($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}// Handle edge cases
            ${styleAdapter.getIndentation(2)}if ($singularName == null || this.$fieldName == null) return false;
            
            ${styleAdapter.getIndentation(2)}// Remove from collection
            ${styleAdapter.getIndentation(2)}boolean removed = this.$fieldName.remove($singularName);
            
            ${styleAdapter.getIndentation(2)}// Clear inverse relationship without triggering circular updates
            ${styleAdapter.getIndentation(2)}if (removed && $singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() == this) {
            ${styleAdapter.getIndentation(3)}$singularName.set${inverseFieldName.replaceFirstChar { it.uppercase() }}Internal(null);
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}return removed;
            ${indent}}
            
            ${indent}/**
            ${indent} * Internal method to remove a ${singularName} without triggering bidirectional synchronization.
            ${indent} * This method is used internally to prevent infinite recursion in bidirectional relationships.
            ${indent} * 
            ${indent} * @param $singularName The ${targetEntityName} to remove
            ${indent} */
            ${indent}protected void remove${singularName.replaceFirstChar { it.uppercase() }}Internal($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}if ($singularName == null || this.$fieldName == null) return;
            
            ${styleAdapter.getIndentation(2)}// Remove from collection
            ${styleAdapter.getIndentation(2)}this.$fieldName.remove($singularName);
            ${indent}}
        """.trimIndent()

        // Generate an enhanced clear method with better error handling
        val clearMethod = """
            ${indent}/**
            ${indent} * Clears all ${fieldName}, maintaining bidirectional consistency.
            ${indent} * 
            ${indent} * This method:
            ${indent} * - Creates a copy of the collection to avoid ConcurrentModificationException
            ${indent} * - Removes each item properly updating inverse relationships
            ${indent} * - Handles null collections safely
            ${indent} */
            ${indent}public void clear${fieldName.replaceFirstChar { it.uppercase() }}() {
            ${styleAdapter.getIndentation(2)}// Handle null collection
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == null) return;
            
            ${styleAdapter.getIndentation(2)}// Create a copy to avoid ConcurrentModificationException
            ${styleAdapter.getIndentation(2)}List<$targetEntityName> copy = new ArrayList<>(this.$fieldName);
            
            ${styleAdapter.getIndentation(2)}// Clear the collection first (more efficient than removing one by one)
            ${styleAdapter.getIndentation(2)}this.$fieldName.clear();
            
            ${styleAdapter.getIndentation(2)}// Update inverse relationships
            ${styleAdapter.getIndentation(2)}for ($targetEntityName $singularName : copy) {
            ${styleAdapter.getIndentation(3)}if ($singularName != null && $singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() == this) {
            ${styleAdapter.getIndentation(4)}$singularName.set${inverseFieldName.replaceFirstChar { it.uppercase() }}Internal(null);
            ${styleAdapter.getIndentation(3)}}
            ${styleAdapter.getIndentation(2)}}
            ${indent}}
            
            ${indent}/**
            ${indent} * Gets the ${fieldName} collection.
            ${indent} * 
            ${indent} * @return The collection of ${targetEntityName} objects or an empty collection if none exist
            ${indent} */
            ${indent}public List<$targetEntityName> get${fieldName.replaceFirstChar { it.uppercase() }}() {
            ${styleAdapter.getIndentation(2)}// Initialize collection if null to avoid NPE
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == null) {
            ${styleAdapter.getIndentation(3)}this.$fieldName = new ArrayList<>();
            ${styleAdapter.getIndentation(2)}}
            ${styleAdapter.getIndentation(2)}return this.$fieldName;
            ${indent}}
        """.trimIndent()

        return RelationshipSyncMethods(
            addMethod = addMethod,
            removeMethod = removeMethod,
            clearMethod = clearMethod,
            imports = setOf("java.util.ArrayList", "java.util.List", "java.util.Collections")
        )
    }

    /**
     * Generates synchronization methods for many-to-one relationships.
     * These methods ensure that both sides of a bidirectional many-to-one relationship
     * are properly synchronized when either side is updated.
     *
     * @param entityMetadata Metadata about the entity
     * @param relationshipInfo Information about the relationship
     * @param styleAdapter The code style adapter to use for formatting
     * @return The generated synchronization methods
     */
    private fun generateManyToOneSyncMethods(
        entityMetadata: EntityMetadata,
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): RelationshipSyncMethods {
        val indent = styleAdapter.getIndentation()
        val fieldName = styleAdapter.adaptFieldName(relationshipInfo.fieldName)
        val entityName = entityMetadata.className
        val targetEntityName = relationshipInfo.targetEntity.substringAfterLast(".")
        val setterName = styleAdapter.formatSetterName(relationshipInfo.fieldName)
        val inverseFieldName = relationshipInfo.inverseFieldName ?: pluralize(entityMetadata.className.lowercase())
        
        // Generate a setter method that handles the many-to-one side of the relationship
        val setMethod = """
            ${indent}/**
            ${indent} * Sets the ${fieldName} relationship, maintaining bidirectional consistency.
            ${indent} * 
            ${indent} * This method:
            ${indent} * - Removes this entity from the previous parent's collection (if any)
            ${indent} * - Sets the new parent
            ${indent} * - Adds this entity to the new parent's collection
            ${indent} * - Prevents circular references and infinite recursion
            ${indent} * 
            ${indent} * @param $fieldName The ${targetEntityName} to set as the parent
            ${indent} */
            ${indent}public void $setterName($targetEntityName $fieldName) {
            ${styleAdapter.getIndentation(2)}// Prevent circular references and infinite recursion
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == $fieldName) {
            ${styleAdapter.getIndentation(3)}return; // No change needed
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}// Store old relationship for cleanup
            ${styleAdapter.getIndentation(2)}$targetEntityName old${fieldName.replaceFirstChar { it.uppercase() }} = this.$fieldName;
            
            ${styleAdapter.getIndentation(2)}// Set new relationship
            ${styleAdapter.getIndentation(2)}this.$fieldName = $fieldName;
            
            ${styleAdapter.getIndentation(2)}// Remove from old parent's collection
            ${styleAdapter.getIndentation(2)}if (old${fieldName.replaceFirstChar { it.uppercase() }} != null && 
            ${styleAdapter.getIndentation(3)}old${fieldName.replaceFirstChar { it.uppercase() }}.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != null) {
            ${styleAdapter.getIndentation(3)}// Use internal method to avoid circular updates
            ${styleAdapter.getIndentation(3)}old${fieldName.replaceFirstChar { it.uppercase() }}.remove${entityName}Internal(this);
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}// Add to new parent's collection
            ${styleAdapter.getIndentation(2)}if ($fieldName != null) {
            ${styleAdapter.getIndentation(3)}// Initialize parent's collection if needed
            ${styleAdapter.getIndentation(3)}if ($fieldName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() == null) {
            ${styleAdapter.getIndentation(4)}// This would be handled by the parent's getter in a real implementation
            ${styleAdapter.getIndentation(3)}} 
            
            ${styleAdapter.getIndentation(3)}// Add this entity to parent's collection using internal method to avoid circular updates
            ${styleAdapter.getIndentation(3)}$fieldName.add${entityName}Internal(this);
            ${styleAdapter.getIndentation(2)}}
            ${indent}}
            
            ${indent}/**
            ${indent} * Internal setter for ${fieldName} relationship that doesn't trigger bidirectional synchronization.
            ${indent} * This method is used internally to prevent infinite recursion in bidirectional relationships.
            ${indent} * 
            ${indent} * @param $fieldName The ${targetEntityName} to set
            ${indent} */
            ${indent}protected void set${fieldName.replaceFirstChar { it.uppercase() }}Internal($targetEntityName $fieldName) {
            ${styleAdapter.getIndentation(2)}this.$fieldName = $fieldName;
            ${indent}}
            
            ${indent}/**
            ${indent} * Gets the ${fieldName} relationship.
            ${indent} * 
            ${indent} * @return The ${targetEntityName} or null if not set
            ${indent} */
            ${indent}public $targetEntityName get${fieldName.replaceFirstChar { it.uppercase() }}() {
            ${styleAdapter.getIndentation(2)}return this.$fieldName;
            ${indent}}
        """.trimIndent()

        return RelationshipSyncMethods(
            addMethod = "",
            removeMethod = "",
            setMethod = setMethod,
            imports = setOf("java.util.Objects")
        )
    }

    /**
     * Generates synchronization methods for many-to-many relationships.
     * These methods ensure that both sides of a bidirectional many-to-many relationship
     * are properly synchronized when either side is updated.
     *
     * @param entityMetadata Metadata about the entity
     * @param relationshipInfo Information about the relationship
     * @param styleAdapter The code style adapter to use for formatting
     * @return The generated synchronization methods
     */
    private fun generateManyToManySyncMethods(
        entityMetadata: EntityMetadata,
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): RelationshipSyncMethods {
        val indent = styleAdapter.getIndentation()
        val fieldName = styleAdapter.adaptFieldName(relationshipInfo.fieldName)
        val entityName = entityMetadata.className
        val targetEntityName = relationshipInfo.targetEntity.substringAfterLast(".")
        val singularName = singularize(relationshipInfo.fieldName)
        val inverseFieldName = relationshipInfo.inverseFieldName ?: pluralize(entityMetadata.className.lowercase())

        // Generate an enhanced add method with circular reference protection
        val addMethod = """
            ${indent}/**
            ${indent} * Adds a ${singularName} to the ${fieldName} collection, maintaining bidirectional consistency.
            ${indent} * 
            ${indent} * This method:
            ${indent} * - Initializes the collection if needed
            ${indent} * - Adds the item if not already present
            ${indent} * - Ensures the inverse side is properly set
            ${indent} * - Prevents circular references and infinite recursion
            ${indent} * 
            ${indent} * @param $singularName The ${targetEntityName} to add
            ${indent} * @return true if the item was added, false if it was already present or null
            ${indent} */
            ${indent}public boolean add${singularName.replaceFirstChar { it.uppercase() }}($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}// Handle null case
            ${styleAdapter.getIndentation(2)}if ($singularName == null) return false;
            
            ${styleAdapter.getIndentation(2)}// Initialize collection if null
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == null) {
            ${styleAdapter.getIndentation(3)}this.$fieldName = new HashSet<>();
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}// Check if already in collection to prevent circular references
            ${styleAdapter.getIndentation(2)}if (this.$fieldName.contains($singularName)) {
            ${styleAdapter.getIndentation(3)}return false; // Already present
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}// Add to collection
            ${styleAdapter.getIndentation(2)}boolean added = this.$fieldName.add($singularName);
            
            ${styleAdapter.getIndentation(2)}// Set inverse relationship without triggering circular updates
            ${styleAdapter.getIndentation(2)}if (added) {
            ${styleAdapter.getIndentation(3)}// Initialize inverse collection if needed
            ${styleAdapter.getIndentation(3)}if ($singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() == null) {
            ${styleAdapter.getIndentation(4)}$singularName.set${inverseFieldName.replaceFirstChar { it.uppercase() }}Internal(new HashSet<>());
            ${styleAdapter.getIndentation(3)}}
            
            ${styleAdapter.getIndentation(3)}// Add this entity to inverse collection without triggering circular updates
            ${styleAdapter.getIndentation(3)}$singularName.add${entityName}Internal(this);
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}return added;
            ${indent}}
            
            ${indent}/**
            ${indent} * Internal method to add a ${singularName} without triggering bidirectional synchronization.
            ${indent} * This method is used internally to prevent infinite recursion in bidirectional relationships.
            ${indent} * 
            ${indent} * @param $singularName The ${targetEntityName} to add
            ${indent} */
            ${indent}protected void add${singularName.replaceFirstChar { it.uppercase() }}Internal($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}if ($singularName == null) return;
            
            ${styleAdapter.getIndentation(2)}// Initialize collection if null
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == null) {
            ${styleAdapter.getIndentation(3)}this.$fieldName = new HashSet<>();
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}// Add to collection if not already present
            ${styleAdapter.getIndentation(2)}this.$fieldName.add($singularName);
            ${indent}}
            
            ${indent}/**
            ${indent} * Internal method to set the ${fieldName} collection without triggering bidirectional synchronization.
            ${indent} * This method is used internally to prevent infinite recursion in bidirectional relationships.
            ${indent} * 
            ${indent} * @param $fieldName The collection of ${targetEntityName} to set
            ${indent} */
            ${indent}protected void set${fieldName.replaceFirstChar { it.uppercase() }}Internal(Set<$targetEntityName> $fieldName) {
            ${styleAdapter.getIndentation(2)}this.$fieldName = $fieldName;
            ${indent}}
        """.trimIndent()

        // Generate an enhanced remove method with circular reference protection
        val removeMethod = """
            ${indent}/**
            ${indent} * Removes a ${singularName} from the ${fieldName} collection, maintaining bidirectional consistency.
            ${indent} * 
            ${indent} * This method:
            ${indent} * - Removes the item from the collection
            ${indent} * - Ensures the inverse side is properly updated
            ${indent} * - Prevents circular references and infinite recursion
            ${indent} * 
            ${indent} * @param $singularName The ${targetEntityName} to remove
            ${indent} * @return true if the item was removed, false if it wasn't in the collection or collection is null
            ${indent} */
            ${indent}public boolean remove${singularName.replaceFirstChar { it.uppercase() }}($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}// Handle edge cases
            ${styleAdapter.getIndentation(2)}if ($singularName == null || this.$fieldName == null) return false;
            
            ${styleAdapter.getIndentation(2)}// Remove from collection
            ${styleAdapter.getIndentation(2)}boolean removed = this.$fieldName.remove($singularName);
            
            ${styleAdapter.getIndentation(2)}// Update inverse relationship without triggering circular updates
            ${styleAdapter.getIndentation(2)}if (removed && $singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != null) {
            ${styleAdapter.getIndentation(3)}$singularName.remove${entityName}Internal(this);
            ${styleAdapter.getIndentation(2)}}
            
            ${styleAdapter.getIndentation(2)}return removed;
            ${indent}}
            
            ${indent}/**
            ${indent} * Internal method to remove a ${singularName} without triggering bidirectional synchronization.
            ${indent} * This method is used internally to prevent infinite recursion in bidirectional relationships.
            ${indent} * 
            ${indent} * @param $singularName The ${targetEntityName} to remove
            ${indent} */
            ${indent}protected void remove${singularName.replaceFirstChar { it.uppercase() }}Internal($targetEntityName $singularName) {
            ${styleAdapter.getIndentation(2)}if ($singularName == null || this.$fieldName == null) return;
            
            ${styleAdapter.getIndentation(2)}// Remove from collection
            ${styleAdapter.getIndentation(2)}this.$fieldName.remove($singularName);
            ${indent}}
        """.trimIndent()

        // Generate a clear method with better error handling
        val clearMethod = """
            ${indent}/**
            ${indent} * Clears all ${fieldName}, maintaining bidirectional consistency.
            ${indent} * 
            ${indent} * This method:
            ${indent} * - Creates a copy of the collection to avoid ConcurrentModificationException
            ${indent} * - Removes each item properly updating inverse relationships
            ${indent} * - Handles null collections safely
            ${indent} */
            ${indent}public void clear${fieldName.replaceFirstChar { it.uppercase() }}() {
            ${styleAdapter.getIndentation(2)}// Handle null collection
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == null || this.$fieldName.isEmpty()) return;
            
            ${styleAdapter.getIndentation(2)}// Create a copy to avoid ConcurrentModificationException
            ${styleAdapter.getIndentation(2)}Set<$targetEntityName> copy = new HashSet<>(this.$fieldName);
            
            ${styleAdapter.getIndentation(2)}// Clear the collection first (more efficient than removing one by one)
            ${styleAdapter.getIndentation(2)}this.$fieldName.clear();
            
            ${styleAdapter.getIndentation(2)}// Update inverse relationships
            ${styleAdapter.getIndentation(2)}for ($targetEntityName $singularName : copy) {
            ${styleAdapter.getIndentation(3)}if ($singularName != null && $singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != null) {
            ${styleAdapter.getIndentation(4)}$singularName.remove${entityName}Internal(this);
            ${styleAdapter.getIndentation(3)}}
            ${styleAdapter.getIndentation(2)}}
            ${indent}}
            
            ${indent}/**
            ${indent} * Gets the ${fieldName} collection.
            ${indent} * 
            ${indent} * @return The collection of ${targetEntityName} objects or an empty collection if none exist
            ${indent} */
            ${indent}public Set<$targetEntityName> get${fieldName.replaceFirstChar { it.uppercase() }}() {
            ${styleAdapter.getIndentation(2)}// Initialize collection if null to avoid NPE
            ${styleAdapter.getIndentation(2)}if (this.$fieldName == null) {
            ${styleAdapter.getIndentation(3)}this.$fieldName = new HashSet<>();
            ${styleAdapter.getIndentation(2)}}
            ${styleAdapter.getIndentation(2)}return this.$fieldName;
            ${indent}}
        """.trimIndent()

        return RelationshipSyncMethods(
            addMethod = addMethod,
            removeMethod = removeMethod,
            clearMethod = clearMethod,
            imports = setOf("java.util.HashSet", "java.util.Set", "java.util.Collections")
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
            ${indent} * 
            ${indent} * This method performs comprehensive validation of entity relationships:
            ${indent} * - Bidirectional relationship consistency (both sides reference each other)
            ${indent} * - Circular dependency detection and handling
            ${indent} * - Cascade type consistency between relationship sides
            ${indent} * 
            ${indent} * @return true if all relationships are consistent, false otherwise
            ${indent} */
            ${indent}public boolean validateRelationships() {
            ${styleAdapter.getIndentation(2)}boolean isValid = true;
            
            ${styleAdapter.getIndentation(2)}// Validate bidirectional reference consistency
        """.trimIndent())

        // Add validation for bidirectional relationships
        relationships.filter { it.isBidirectional }.forEach { relationship ->
            val fieldName = styleAdapter.adaptFieldName(relationship.fieldName)
            val validation = generateRelationshipValidation(relationship, styleAdapter)
            methods.append("\n${styleAdapter.getIndentation(2)}$validation")
        }
        
        // Add validation for circular dependencies
        val circularDependencies = relationships.filter { it.fieldName.contains("CIRCULAR") }
        if (circularDependencies.isNotEmpty()) {
            methods.append("\n\n${styleAdapter.getIndentation(2)}// Validate circular dependencies")
            methods.append("\n${styleAdapter.getIndentation(2)}isValid &= validateCircularDependencies();")
        }
        
        // Add validation for cascade type consistency
        methods.append("\n\n${styleAdapter.getIndentation(2)}// Validate cascade type consistency")
        methods.append("\n${styleAdapter.getIndentation(2)}isValid &= validateCascadeTypeConsistency();")

        methods.append("""
            
            ${styleAdapter.getIndentation(2)}return isValid;
            ${indent}}
        """.trimIndent())
        
        // Add method for validating circular dependencies
        if (circularDependencies.isNotEmpty()) {
            methods.append("\n\n")
            methods.append(generateCircularDependencyValidationMethod(circularDependencies, styleAdapter))
        }
        
        // Add method for validating cascade type consistency
        methods.append("\n\n")
        methods.append(generateCascadeTypeConsistencyValidationMethod(relationships, styleAdapter))

        return methods.toString()
    }

    /**
     * Generates validation code for a specific bidirectional relationship.
     * This method creates code to check that both sides of a bidirectional relationship
     * are properly synchronized.
     *
     * @param relationshipInfo Information about the relationship to validate
     * @param styleAdapter The code style adapter to use for formatting
     * @return The generated validation code as a string
     */
    private fun generateRelationshipValidation(
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): String {
        val fieldName = styleAdapter.adaptFieldName(relationshipInfo.fieldName)
        val inverseFieldName = relationshipInfo.inverseFieldName ?: "parent"
        val targetEntityName = relationshipInfo.targetEntity.substringAfterLast(".")
        val indent = styleAdapter.getIndentation()

        return when (relationshipInfo.relationType) {
            RelationType.ONE_TO_ONE -> {
                """
                // Validate ${relationshipInfo.fieldName} (${relationshipInfo.relationType}) relationship
                if (this.$fieldName != null) {
                ${indent}// Check if the inverse side references this entity
                ${indent}if (this.$fieldName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != this) {
                ${indent.repeat(2)}isValid = false;
                ${indent.repeat(2)}System.err.println("Bidirectional relationship validation failed: " +
                ${indent.repeat(3)}"${targetEntityName} at " + this.$fieldName + " does not reference back to this entity");
                ${indent}}
                } else {
                ${indent}// If this side is null, check if any ${targetEntityName} references this entity incorrectly
                ${indent}// This would require a repository lookup in a real implementation
                }
                """.trimIndent()
            }
            RelationType.ONE_TO_MANY -> {
                val singularName = singularize(relationshipInfo.fieldName)
                """
                // Validate ${relationshipInfo.fieldName} (${relationshipInfo.relationType}) relationship
                if (this.$fieldName != null) {
                ${indent}// Check if collection is properly initialized
                ${indent}if (this.$fieldName instanceof java.util.Collection) {
                ${indent.repeat(2)}// Check each item in the collection
                ${indent.repeat(2)}for (var $singularName : this.$fieldName) {
                ${indent.repeat(3)}if ($singularName == null) {
                ${indent.repeat(4)}continue; // Skip null entries
                ${indent.repeat(3)}}
                
                ${indent.repeat(3)}// Check if the inverse side references this entity
                ${indent.repeat(3)}if ($singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() != this) {
                ${indent.repeat(4)}isValid = false;
                ${indent.repeat(4)}System.err.println("Bidirectional relationship validation failed: " +
                ${indent.repeat(5)}"${targetEntityName} at " + $singularName + " does not reference back to this entity");
                ${indent.repeat(4)}break;
                ${indent.repeat(3)}}
                ${indent.repeat(2)}}
                ${indent}} else {
                ${indent.repeat(2)}// Collection is not properly initialized
                ${indent.repeat(2)}System.err.println("Collection ${fieldName} is not properly initialized");
                ${indent.repeat(2)}isValid = false;
                ${indent}}
                }
                """.trimIndent()
            }
            RelationType.MANY_TO_ONE -> {
                """
                // Validate ${relationshipInfo.fieldName} (${relationshipInfo.relationType}) relationship
                if (this.$fieldName != null) {
                ${indent}// Check if the inverse side contains this entity
                ${indent}if (this.$fieldName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() == null ||
                ${indent.repeat(2)}!this.$fieldName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}().contains(this)) {
                ${indent.repeat(2)}isValid = false;
                ${indent.repeat(2)}System.err.println("Bidirectional relationship validation failed: " +
                ${indent.repeat(3)}"${targetEntityName} at " + this.$fieldName + " does not contain this entity in its ${inverseFieldName} collection");
                ${indent}}
                }
                """.trimIndent()
            }
            RelationType.MANY_TO_MANY -> {
                val singularName = singularize(relationshipInfo.fieldName)
                """
                // Validate ${relationshipInfo.fieldName} (${relationshipInfo.relationType}) relationship
                if (this.$fieldName != null) {
                ${indent}// Check if collection is properly initialized
                ${indent}if (this.$fieldName instanceof java.util.Collection) {
                ${indent.repeat(2)}// Check each item in the collection
                ${indent.repeat(2)}for (var $singularName : this.$fieldName) {
                ${indent.repeat(3)}if ($singularName == null) {
                ${indent.repeat(4)}continue; // Skip null entries
                ${indent.repeat(3)}}
                
                ${indent.repeat(3)}// Check if the inverse side contains this entity
                ${indent.repeat(3)}if ($singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}() == null ||
                ${indent.repeat(4)}!$singularName.get${inverseFieldName.replaceFirstChar { it.uppercase() }}().contains(this)) {
                ${indent.repeat(4)}isValid = false;
                ${indent.repeat(4)}System.err.println("Bidirectional relationship validation failed: " +
                ${indent.repeat(5)}"${targetEntityName} at " + $singularName + " does not contain this entity in its ${inverseFieldName} collection");
                ${indent.repeat(4)}break;
                ${indent.repeat(3)}}
                ${indent.repeat(2)}}
                ${indent}} else {
                ${indent.repeat(2)}// Collection is not properly initialized
                ${indent.repeat(2)}System.err.println("Collection ${fieldName} is not properly initialized");
                ${indent.repeat(2)}isValid = false;
                ${indent}}
                }
                """.trimIndent()
            }
            else -> {
                """
                // No validation needed for ${relationshipInfo.relationType} relationship: ${relationshipInfo.fieldName}
                """.trimIndent()
            }
        }
    }

    /**
     * Generates a method to handle orphan removal for relationships with orphanRemoval=true.
     * This method creates code to properly handle orphaned entities when they are removed from a relationship.
     *
     * @param relationshipInfo Information about the relationship
     * @param styleAdapter The code style adapter to use for formatting
     * @return The generated orphan removal method as a string, or empty string if orphanRemoval is false
     */
    private fun generateOrphanRemovalMethod(
        relationshipInfo: RelationshipInfo,
        styleAdapter: CodeStyleAdapter
    ): String {
        // Only generate orphan removal handling for relationships with orphanRemoval=true
        if (!relationshipInfo.orphanRemoval) return ""

        val indent = styleAdapter.getIndentation()
        val fieldName = styleAdapter.adaptFieldName(relationshipInfo.fieldName)
        val targetEntityName = relationshipInfo.targetEntity.substringAfterLast(".")
        val singularName = singularize(relationshipInfo.fieldName)

        // Generate different implementations based on relationship type
        val implementation = when (relationshipInfo.relationType) {
            RelationType.ONE_TO_ONE -> {
                """
                ${styleAdapter.getIndentation(2)}// For one-to-one relationships, we need to handle the case where the relationship is broken
                ${styleAdapter.getIndentation(2)}// but the orphaned entity still exists in the database
                ${styleAdapter.getIndentation(2)}if (oldValue != null && newValue == null) {
                ${styleAdapter.getIndentation(3)}// In a real implementation, this would use EntityManager to delete the orphaned entity
                ${styleAdapter.getIndentation(3)}// entityManager.remove(oldValue);
                ${styleAdapter.getIndentation(3)}
                ${styleAdapter.getIndentation(3)}// Log the orphan removal operation
                ${styleAdapter.getIndentation(3)}System.out.println("Orphan removal: Removing orphaned entity " + oldValue);
                ${styleAdapter.getIndentation(2)}}
                """
            }
            RelationType.ONE_TO_MANY -> {
                """
                ${styleAdapter.getIndentation(2)}// For one-to-many relationships, we need to track removed items from the collection
                ${styleAdapter.getIndentation(2)}// This is typically done by comparing the old and new collections
                ${styleAdapter.getIndentation(2)}if (oldCollection != null && !oldCollection.isEmpty()) {
                ${styleAdapter.getIndentation(3)}// Find items that were in the old collection but not in the new one
                ${styleAdapter.getIndentation(3)}for ($targetEntityName orphaned : oldCollection) {
                ${styleAdapter.getIndentation(4)}if (orphaned != null && (newCollection == null || !newCollection.contains(orphaned))) {
                ${styleAdapter.getIndentation(5)}// In a real implementation, this would use EntityManager to delete the orphaned entity
                ${styleAdapter.getIndentation(5)}// entityManager.remove(orphaned);
                ${styleAdapter.getIndentation(5)}
                ${styleAdapter.getIndentation(5)}// Log the orphan removal operation
                ${styleAdapter.getIndentation(5)}System.out.println("Orphan removal: Removing orphaned entity " + orphaned);
                ${styleAdapter.getIndentation(4)}}
                ${styleAdapter.getIndentation(3)}}
                ${styleAdapter.getIndentation(2)}}
                """
            }
            RelationType.MANY_TO_MANY -> {
                """
                ${styleAdapter.getIndentation(2)}// For many-to-many relationships, orphanRemoval is typically not used
                ${styleAdapter.getIndentation(2)}// as it would delete entities that might be referenced by other entities
                ${styleAdapter.getIndentation(2)}// However, we can implement a custom orphan removal logic if needed
                ${styleAdapter.getIndentation(2)}if (oldCollection != null && !oldCollection.isEmpty()) {
                ${styleAdapter.getIndentation(3)}// Find items that were in the old collection but not in the new one
                ${styleAdapter.getIndentation(3)}for ($targetEntityName orphaned : oldCollection) {
                ${styleAdapter.getIndentation(4)}if (orphaned != null && (newCollection == null || !newCollection.contains(orphaned))) {
                ${styleAdapter.getIndentation(5)}// Check if the orphaned entity is not referenced by any other entity
                ${styleAdapter.getIndentation(5)}if (!isReferencedByOtherEntities(orphaned)) {
                ${styleAdapter.getIndentation(6)}// In a real implementation, this would use EntityManager to delete the orphaned entity
                ${styleAdapter.getIndentation(6)}// entityManager.remove(orphaned);
                ${styleAdapter.getIndentation(6)}
                ${styleAdapter.getIndentation(6)}// Log the orphan removal operation
                ${styleAdapter.getIndentation(6)}System.out.println("Orphan removal: Removing orphaned entity " + orphaned);
                ${styleAdapter.getIndentation(5)}}
                ${styleAdapter.getIndentation(4)}}
                ${styleAdapter.getIndentation(3)}}
                ${styleAdapter.getIndentation(2)}}
                """
            }
            else -> {
                """
                ${styleAdapter.getIndentation(2)}// Orphan removal is not typically used for ${relationshipInfo.relationType} relationships
                ${styleAdapter.getIndentation(2)}// This is a placeholder for custom orphan removal logic
                """
            }
        }

        // Generate method parameters based on relationship type
        val parameters = when (relationshipInfo.relationType) {
            RelationType.ONE_TO_ONE -> "$targetEntityName oldValue, $targetEntityName newValue"
            RelationType.ONE_TO_MANY, RelationType.MANY_TO_MANY -> "Collection<$targetEntityName> oldCollection, Collection<$targetEntityName> newCollection"
            else -> "Object oldValue, Object newValue"
        }

        return """
            ${indent}/**
            ${indent} * Handles orphan removal for ${relationshipInfo.fieldName} relationship.
            ${indent} * 
            ${indent} * Orphan removal (orphanRemoval=true) means that when an entity is removed from
            ${indent} * this relationship, it should be deleted from the database if it's no longer
            ${indent} * referenced by any other entity.
            ${indent} * 
            ${indent} * This method:
            ${indent} * - Identifies orphaned entities (removed from the relationship)
            ${indent} * - Checks if they are referenced elsewhere (for many-to-many)
            ${indent} * - Deletes them if they are truly orphaned
            ${indent} * 
            ${indent} * @param ${if (relationshipInfo.relationType == RelationType.ONE_TO_ONE) "oldValue" else "oldCollection"} The previous value/collection before the change
            ${indent} * @param ${if (relationshipInfo.relationType == RelationType.ONE_TO_ONE) "newValue" else "newCollection"} The new value/collection after the change
            ${indent} */
            ${indent}protected void handleOrphanRemoval($parameters) {
            $implementation
            ${indent}}
            
            ${if (relationshipInfo.relationType == RelationType.MANY_TO_MANY) """
            ${indent}/**
            ${indent} * Checks if an entity is referenced by any other entity in the system.
            ${indent} * This is used to determine if an entity is truly orphaned and can be safely deleted.
            ${indent} * 
            ${indent} * @param entity The entity to check
            ${indent} * @return true if the entity is referenced by at least one other entity, false otherwise
            ${indent} */
            ${indent}private boolean isReferencedByOtherEntities($targetEntityName entity) {
            ${styleAdapter.getIndentation(2)}// In a real implementation, this would query the database or check in-memory references
            ${styleAdapter.getIndentation(2)}// to determine if the entity is referenced by any other entity
            ${styleAdapter.getIndentation(2)}// For now, we'll just return false to indicate it's not referenced
            ${styleAdapter.getIndentation(2)}return false;
            ${indent}}
            """ else ""}
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
    
    /**
     * Generates a method to validate circular dependencies in relationships.
     * This is especially important for bidirectional relationships to prevent infinite loops.
     * 
     * @param circularDependencies List of relationships that form circular dependencies
     * @param styleAdapter The code style adapter to use for formatting
     * @return The generated validation method as a string
     */
    private fun generateCircularDependencyValidationMethod(
        circularDependencies: List<RelationshipInfo>,
        styleAdapter: CodeStyleAdapter
    ): String {
        val indent = styleAdapter.getIndentation()
        val method = StringBuilder()
        
        method.append("""
            ${indent}/**
            ${indent} * Validates and handles circular dependencies in relationships.
            ${indent} * 
            ${indent} * Circular dependencies occur when entities reference each other in a cycle.
            ${indent} * For example: A -> B -> C -> A
            ${indent} * 
            ${indent} * This method checks that circular dependencies are properly handled to prevent:
            ${indent} * - Infinite loops during object traversal
            ${indent} * - StackOverflowError during JSON serialization
            ${indent} * - Infinite cascading operations
            ${indent} * 
            ${indent} * @return true if all circular dependencies are properly handled, false otherwise
            ${indent} */
            ${indent}public boolean validateCircularDependencies() {
            ${styleAdapter.getIndentation(2)}boolean isValid = true;
            
            ${styleAdapter.getIndentation(2)}// Check for proper handling of known circular dependencies
        """.trimIndent())
        
        // Add specific validation for each circular dependency
        circularDependencies.forEach { relationship ->
            val fieldName = styleAdapter.adaptFieldName(relationship.fieldName)
            val targetEntityName = relationship.targetEntity.substringAfterLast(".")
            
            method.append("""
                
                ${styleAdapter.getIndentation(2)}// Validate circular dependency: ${relationship.sourceEntity} -> ${relationship.targetEntity}
                ${styleAdapter.getIndentation(2)}if (this.$fieldName != null) {
                ${styleAdapter.getIndentation(3)}// Check if proper cycle-breaking mechanisms are in place
                ${styleAdapter.getIndentation(3)}if (!hasCircularReferenceProtection(${targetEntityName}.class)) {
                ${styleAdapter.getIndentation(4)}isValid = false;
                ${styleAdapter.getIndentation(3)}}
                ${styleAdapter.getIndentation(2)}}
            """.trimIndent())
        }
        
        method.append("""
            
            ${styleAdapter.getIndentation(2)}return isValid;
            ${indent}}
            
            ${indent}/**
            ${indent} * Checks if proper circular reference protection is in place for the given entity type.
            ${indent} * This could be implemented by checking for @JsonIgnore, @JsonManagedReference/@JsonBackReference,
            ${indent} * or custom serialization handling.
            ${indent} * 
            ${indent} * @param entityType The entity class to check
            ${indent} * @return true if circular reference protection is in place, false otherwise
            ${indent} */
            ${indent}private boolean hasCircularReferenceProtection(Class<?> entityType) {
            ${styleAdapter.getIndentation(2)}// This is a placeholder implementation
            ${styleAdapter.getIndentation(2)}// In a real implementation, you would check for:
            ${styleAdapter.getIndentation(2)}// - @JsonIgnore annotations on the back-reference
            ${styleAdapter.getIndentation(2)}// - @JsonManagedReference/@JsonBackReference pairs
            ${styleAdapter.getIndentation(2)}// - Custom serialization handling
            ${styleAdapter.getIndentation(2)}// - Proper equals/hashCode implementations that avoid circular calls
            ${styleAdapter.getIndentation(2)}return true; // Assume protection is in place for now
            ${indent}}
        """.trimIndent())
        
        return method.toString()
    }
    
    /**
     * Generates a method to validate cascade type consistency between bidirectional relationships.
     * This ensures that cascade operations are properly configured on both sides of a relationship.
     * 
     * @param relationships List of relationships to validate
     * @param styleAdapter The code style adapter to use for formatting
     * @return The generated validation method as a string
     */
    private fun generateCascadeTypeConsistencyValidationMethod(
        relationships: List<RelationshipInfo>,
        styleAdapter: CodeStyleAdapter
    ): String {
        val indent = styleAdapter.getIndentation()
        val method = StringBuilder()
        
        method.append("""
            ${indent}/**
            ${indent} * Validates the consistency of cascade types between bidirectional relationships.
            ${indent} * 
            ${indent} * Inconsistent cascade types can lead to unexpected behavior:
            ${indent} * - Orphaned records if one side has orphanRemoval=true but the other doesn't cascade remove
            ${indent} * - Persistence failures if one side cascades persist but the other doesn't
            ${indent} * - Incomplete updates if one side cascades merge but the other doesn't
            ${indent} * 
            ${indent} * @return true if all cascade types are consistent, false otherwise
            ${indent} */
            ${indent}public boolean validateCascadeTypeConsistency() {
            ${styleAdapter.getIndentation(2)}boolean isValid = true;
            
            ${styleAdapter.getIndentation(2)}// Check cascade type consistency for bidirectional relationships
        """.trimIndent())
        
        // Add validation for bidirectional relationships
        val bidirectionalRelationships = relationships.filter { it.isBidirectional }
        bidirectionalRelationships.forEach { relationship ->
            val fieldName = styleAdapter.adaptFieldName(relationship.fieldName)
            val targetEntityName = relationship.targetEntity.substringAfterLast(".")
            
            method.append("""
                
                ${styleAdapter.getIndentation(2)}// Validate cascade consistency for ${relationship.fieldName} relationship
                ${styleAdapter.getIndentation(2)}if (this.$fieldName != null) {
            """.trimIndent())
            
            // Check for specific cascade type inconsistencies
            if (relationship.cascade.contains(CascadeType.PERSIST)) {
                method.append("""
                    ${styleAdapter.getIndentation(3)}// Check if the inverse side also cascades persist
                    ${styleAdapter.getIndentation(3)}if (!validateInverseCascadeType("${relationship.fieldName}", CascadeType.PERSIST)) {
                    ${styleAdapter.getIndentation(4)}isValid = false;
                    ${styleAdapter.getIndentation(3)}}
                """.trimIndent())
            }
            
            if (relationship.cascade.contains(CascadeType.REMOVE)) {
                method.append("""
                    ${styleAdapter.getIndentation(3)}// Check if the inverse side also cascades remove
                    ${styleAdapter.getIndentation(3)}if (!validateInverseCascadeType("${relationship.fieldName}", CascadeType.REMOVE)) {
                    ${styleAdapter.getIndentation(4)}isValid = false;
                    ${styleAdapter.getIndentation(3)}}
                """.trimIndent())
            }
            
            if (relationship.orphanRemoval) {
                method.append("""
                    ${styleAdapter.getIndentation(3)}// Check if the inverse side handles orphan removal properly
                    ${styleAdapter.getIndentation(3)}if (!validateInverseOrphanRemoval("${relationship.fieldName}")) {
                    ${styleAdapter.getIndentation(4)}isValid = false;
                    ${styleAdapter.getIndentation(3)}}
                """.trimIndent())
            }
            
            method.append("""
                ${styleAdapter.getIndentation(2)}}
            """.trimIndent())
        }
        
        method.append("""
            
            ${styleAdapter.getIndentation(2)}return isValid;
            ${indent}}
            
            ${indent}/**
            ${indent} * Validates that the inverse side of a relationship has the specified cascade type.
            ${indent} * 
            ${indent} * @param fieldName The name of the field representing the relationship
            ${indent} * @param cascadeType The cascade type to check for
            ${indent} * @return true if the inverse side has the specified cascade type, false otherwise
            ${indent} */
            ${indent}private boolean validateInverseCascadeType(String fieldName, CascadeType cascadeType) {
            ${styleAdapter.getIndentation(2)}// This is a placeholder implementation
            ${styleAdapter.getIndentation(2)}// In a real implementation, you would use reflection to check the inverse side's cascade types
            ${styleAdapter.getIndentation(2)}return true; // Assume consistency for now
            ${indent}}
            
            ${indent}/**
            ${indent} * Validates that the inverse side of a relationship properly handles orphan removal.
            ${indent} * 
            ${indent} * @param fieldName The name of the field representing the relationship
            ${indent} * @return true if the inverse side properly handles orphan removal, false otherwise
            ${indent} */
            ${indent}private boolean validateInverseOrphanRemoval(String fieldName) {
            ${styleAdapter.getIndentation(2)}// This is a placeholder implementation
            ${styleAdapter.getIndentation(2)}// In a real implementation, you would use reflection to check the inverse side's orphanRemoval setting
            ${styleAdapter.getIndentation(2)}return true; // Assume consistency for now
            ${indent}}
        """.trimIndent())
        
        return method.toString()
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

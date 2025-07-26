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
     * Enhanced to detect circular dependencies and other complex relationship patterns.
     */
    fun analyzeProjectRelationships(project: Project): List<RelationshipInfo> {
        val relationships = mutableListOf<RelationshipInfo>()
        val entityClasses = findEntityClasses(project)

        entityClasses.forEach { entityClass ->
            relationships.addAll(analyzeEntityRelationships(entityClass, entityClasses))
        }

        // Post-process to detect bidirectional relationships
        val enhancedRelationships = detectBidirectionalRelationships(relationships)
        
        // Detect circular dependencies
        val circularDependencies = detectCircularDependencies(enhancedRelationships)
        
        // Mark relationships involved in circular dependencies
        return markCircularDependencies(enhancedRelationships, circularDependencies)
    }
    
    /**
     * Detects circular dependencies in entity relationships.
     * Returns a list of circular dependency chains.
     */
    fun detectCircularDependencies(relationships: List<RelationshipInfo>): List<List<RelationshipInfo>> {
        val result = mutableListOf<List<RelationshipInfo>>()
        val entityGraph = buildEntityGraph(relationships)
        
        // For each entity, perform a depth-first search to find cycles
        entityGraph.keys.forEach { entity ->
            val visited = mutableSetOf<String>()
            val path = mutableListOf<String>()
            val pathRelationships = mutableListOf<RelationshipInfo>()
            
            findCycles(entity, entity, visited, path, pathRelationships, entityGraph, relationships, result)
        }
        
        return result
    }
    
    /**
     * Builds a graph representation of entity relationships.
     * Returns a map of entity to list of entities it depends on.
     */
    private fun buildEntityGraph(relationships: List<RelationshipInfo>): Map<String, List<String>> {
        val graph = mutableMapOf<String, MutableList<String>>()
        
        relationships.forEach { relationship ->
            val source = relationship.sourceEntity
            val target = relationship.targetEntity
            
            // Skip self-references and inheritance relationships
            if (source != target && relationship.relationType != RelationType.INHERITANCE) {
                if (!graph.containsKey(source)) {
                    graph[source] = mutableListOf()
                }
                
                // Only add if not already present
                if (!graph[source]!!.contains(target)) {
                    graph[source]!!.add(target)
                }
            }
        }
        
        return graph
    }
    
    /**
     * Recursive depth-first search to find cycles in the entity graph.
     */
    private fun findCycles(
        start: String,
        current: String,
        visited: MutableSet<String>,
        path: MutableList<String>,
        pathRelationships: MutableList<RelationshipInfo>,
        graph: Map<String, List<String>>,
        allRelationships: List<RelationshipInfo>,
        result: MutableList<List<RelationshipInfo>>
    ) {
        // If we've already visited this node in this path, we found a cycle
        if (path.contains(current)) {
            // Extract the cycle
            val cycleStart = path.indexOf(current)
            val cycle = path.subList(cycleStart, path.size).toMutableList()
            cycle.add(current) // Complete the cycle
            
            // Extract the relationships in the cycle
            val cycleRelationships = mutableListOf<RelationshipInfo>()
            for (i in cycleStart until path.size) {
                val source = path[i]
                val target = if (i == path.size - 1) current else path[i + 1]
                
                // Find the relationship from source to target
                val relationship = allRelationships.find { 
                    it.sourceEntity == source && it.targetEntity == target 
                }
                
                if (relationship != null) {
                    cycleRelationships.add(relationship)
                }
            }
            
            // Add the cycle to the result if it's not already there
            if (cycleRelationships.isNotEmpty() && !result.any { it.containsAll(cycleRelationships) && it.size == cycleRelationships.size }) {
                result.add(cycleRelationships)
            }
            
            return
        }
        
        // Mark current node as visited
        visited.add(current)
        path.add(current)
        
        // Visit all neighbors
        graph[current]?.forEach { neighbor ->
            // Find the relationship from current to neighbor
            val relationship = allRelationships.find { 
                it.sourceEntity == current && it.targetEntity == neighbor 
            }
            
            if (relationship != null) {
                pathRelationships.add(relationship)
                findCycles(start, neighbor, visited, path, pathRelationships, graph, allRelationships, result)
                pathRelationships.removeAt(pathRelationships.size - 1)
            }
        }
        
        // Backtrack
        path.removeAt(path.size - 1)
        visited.remove(current)
    }
    
    /**
     * Marks relationships involved in circular dependencies.
     */
    private fun markCircularDependencies(
        relationships: List<RelationshipInfo>,
        circularDependencies: List<List<RelationshipInfo>>
    ): List<RelationshipInfo> {
        // Flatten all circular dependencies into a single set
        val circularRelationships = circularDependencies.flatten().toSet()
        
        // Mark relationships involved in circular dependencies
        return relationships.map { relationship ->
            if (circularRelationships.any { it.sourceEntity == relationship.sourceEntity && it.targetEntity == relationship.targetEntity }) {
                // This relationship is part of a circular dependency
                relationship.copy(
                    // We could add a new field to RelationshipInfo to mark circular dependencies,
                    // but for now we'll just add it to the field name as a comment
                    fieldName = "${relationship.fieldName} /* CIRCULAR */"
                )
            } else {
                relationship
            }
        }
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
     * Enhanced to support more complex composite relationships.
     */
    fun detectManyToManyWithFields(project: Project): List<ManyToManyWithFields> {
        val result = mutableListOf<ManyToManyWithFields>()
        val entityClasses = findEntityClasses(project)

        entityClasses.forEach { entityClass ->
            // Look for fields that might be part of a many-to-many relationship
            val manyToManyFields = entityClass.fields.filter { field ->
                hasAnnotation(field, "ManyToMany") ||
                (hasAnnotation(field, "OneToMany") && isJoinTableRelationship(field)) ||
                // Also consider fields with @JoinTable annotation even if they don't have @ManyToMany
                hasAnnotation(field, "JoinTable")
            }

            manyToManyFields.forEach { field ->
                // Check for explicit relationship entity
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
                } else {
                    // Check for implicit relationship entity through @JoinTable
                    val joinTableAnnotation = field.getAnnotation("javax.persistence.JoinTable")
                                            ?: field.getAnnotation("jakarta.persistence.JoinTable")
                    
                    if (joinTableAnnotation != null) {
                        val tableName = extractAnnotationAttribute(joinTableAnnotation, "name") ?: ""
                        if (tableName.isNotEmpty()) {
                            // Look for an entity class that might map to this table
                            val potentialRelationshipEntity = entityClasses.find { 
                                val tableAnnotation = it.getAnnotation("javax.persistence.Table")
                                                    ?: it.getAnnotation("jakarta.persistence.Table")
                                extractAnnotationAttribute(tableAnnotation, "name") == tableName
                            }
                            
                            if (potentialRelationshipEntity != null) {
                                val additionalFields = analyzeRelationshipEntityFields(potentialRelationshipEntity)
                                if (additionalFields.isNotEmpty()) {
                                    result.add(
                                        ManyToManyWithFields(
                                            relationshipEntity = potentialRelationshipEntity.qualifiedName ?: "",
                                            relationshipTable = tableName,
                                            additionalFields = additionalFields,
                                            sourceJoinColumn = extractJoinColumn(field, entityClass),
                                            targetJoinColumn = extractInverseJoinColumn(field)
                                        )
                                    )
                                }
                            } else {
                                // Create a synthetic relationship entity based on join columns
                                val joinColumns = extractJoinColumns(joinTableAnnotation, "joinColumns")
                                val inverseJoinColumns = extractJoinColumns(joinTableAnnotation, "inverseJoinColumns")
                                
                                if (joinColumns.isNotEmpty() && inverseJoinColumns.isNotEmpty()) {
                                    // Extract target entity from field type
                                    val targetEntity = extractCollectionTargetEntity(field)
                                    
                                    if (targetEntity.isNotEmpty()) {
                                        // Create synthetic additional fields based on join columns
                                        val syntheticFields = mutableListOf<EntityField>()
                                        
                                        // Add ID field
                                        syntheticFields.add(EntityField(
                                            name = "id",
                                            type = "Long",
                                            nullable = false,
                                            columnName = "id"
                                        ))
                                        
                                        // Add source and target reference fields
                                        val sourceEntityName = entityClass.name?.lowercase() ?: "source"
                                        syntheticFields.add(EntityField(
                                            name = "${sourceEntityName}Id",
                                            type = "Long",
                                            nullable = false,
                                            columnName = joinColumns.first(),
                                            relationType = RelationType.MANY_TO_ONE,
                                            relationTargetEntity = entityClass.qualifiedName
                                        ))
                                        
                                        val targetEntityName = targetEntity.substringAfterLast('.')
                                        syntheticFields.add(EntityField(
                                            name = "${targetEntityName.lowercase()}Id",
                                            type = "Long",
                                            nullable = false,
                                            columnName = inverseJoinColumns.first(),
                                            relationType = RelationType.MANY_TO_ONE,
                                            relationTargetEntity = targetEntity
                                        ))
                                        
                                        result.add(
                                            ManyToManyWithFields(
                                                relationshipEntity = "${entityClass.name ?: "Source"}${targetEntityName}Relation",
                                                relationshipTable = tableName,
                                                additionalFields = syntheticFields,
                                                sourceJoinColumn = joinColumns.first(),
                                                targetJoinColumn = inverseJoinColumns.first()
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return result
    }

    /**
     * Analyzes nested compositions and complex inheritance.
     * Enhanced to handle deeper nesting levels and detect composition patterns.
     */
    fun analyzeNestedCompositions(entityClass: PsiClass): List<RelationshipInfo> {
        val compositions = mutableListOf<RelationshipInfo>()
        val processedClasses = mutableSetOf<String>()
        
        // Add the current class to prevent circular references
        processedClasses.add(entityClass.qualifiedName ?: "")
        
        // Analyze embedded fields
        analyzeEmbeddedFields(entityClass).forEach { embedded ->
            compositions.add(embedded)

            // Recursively analyze embedded types for nested compositions
            val embeddedClass = findClassByName(entityClass.project, embedded.targetEntity)
            if (embeddedClass != null && !processedClasses.contains(embeddedClass.qualifiedName ?: "")) {
                compositions.addAll(analyzeNestedCompositions(embeddedClass))
            }
        }
        
        // Also analyze composition relationships (OneToOne with cascade ALL)
        entityClass.fields.forEach { field ->
            if (hasAnnotation(field, "OneToOne")) {
                val annotation = field.getAnnotation("javax.persistence.OneToOne")
                            ?: field.getAnnotation("jakarta.persistence.OneToOne")
                
                val cascadeTypes = extractCascadeTypes(annotation)
                
                // Check if this is a composition relationship (cascade ALL or contains both PERSIST and REMOVE)
                if (cascadeTypes.contains(CascadeType.ALL) || 
                    (cascadeTypes.contains(CascadeType.PERSIST) && cascadeTypes.contains(CascadeType.REMOVE))) {
                    
                    val targetEntity = extractTargetEntity(field)
                    val compositionInfo = RelationshipInfo(
                        sourceEntity = entityClass.qualifiedName ?: "",
                        targetEntity = targetEntity,
                        relationType = RelationType.COMPOSITION,
                        fieldName = field.name ?: "",
                        cascade = cascadeTypes,
                        fetchType = extractFetchType(annotation),
                        isOwnerSide = true
                    )
                    
                    compositions.add(compositionInfo)
                    
                    // Recursively analyze the composed entity
                    val composedClass = findClassByName(entityClass.project, targetEntity)
                    if (composedClass != null && !processedClasses.contains(composedClass.qualifiedName ?: "")) {
                        compositions.addAll(analyzeNestedCompositions(composedClass))
                    }
                }
            }
        }
        
        // Analyze element collections which can also form compositions
        entityClass.fields.forEach { field ->
            if (hasAnnotation(field, "ElementCollection")) {
                val targetEntity = extractCollectionTargetEntity(field)
                
                // Only process if it's an embeddable type
                val targetClass = findClassByName(entityClass.project, targetEntity)
                if (targetClass != null && hasAnnotation(targetClass, "Embeddable") && 
                    !processedClasses.contains(targetClass.qualifiedName ?: "")) {
                    
                    val compositionInfo = RelationshipInfo(
                        sourceEntity = entityClass.qualifiedName ?: "",
                        targetEntity = targetEntity,
                        relationType = RelationType.COMPOSITION, // Use COMPOSITION for element collections
                        fieldName = field.name ?: "",
                        isOwnerSide = true
                    )
                    
                    compositions.add(compositionInfo)
                    
                    // Recursively analyze the element type
                    compositions.addAll(analyzeNestedCompositions(targetClass))
                }
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
                        
        val mappedBy = extractAnnotationAttribute(annotation, "mappedBy")
        val isOwnerSide = mappedBy == null

        return RelationshipInfo(
            sourceEntity = entityClass.qualifiedName ?: "",
            targetEntity = extractTargetEntity(field),
            relationType = RelationType.ONE_TO_ONE,
            fieldName = field.name ?: "",
            mappedBy = mappedBy,
            cascade = extractCascadeTypes(
                annotation, 
                RelationType.ONE_TO_ONE, 
                isOwnerSide, 
                false, // isBidirectional will be set later in detectBidirectionalRelationships
                false  // isCircular will be set later in markCircularDependencies
            ),
            fetchType = extractFetchType(annotation),
            orphanRemoval = extractBooleanAttribute(annotation, "orphanRemoval"),
            isOwnerSide = isOwnerSide
        )
    }

    private fun analyzeOneToMany(field: PsiField, entityClass: PsiClass): RelationshipInfo {
        val annotation = field.getAnnotation("javax.persistence.OneToMany")
                        ?: field.getAnnotation("jakarta.persistence.OneToMany")

        val joinTableAnnotation = field.getAnnotation("javax.persistence.JoinTable")
                                 ?: field.getAnnotation("jakarta.persistence.JoinTable")
                                 
        val mappedBy = extractAnnotationAttribute(annotation, "mappedBy")
        val isOwnerSide = mappedBy == null

        return RelationshipInfo(
            sourceEntity = entityClass.qualifiedName ?: "",
            targetEntity = extractCollectionTargetEntity(field),
            relationType = RelationType.ONE_TO_MANY,
            fieldName = field.name ?: "",
            mappedBy = mappedBy,
            joinTable = extractAnnotationAttribute(joinTableAnnotation, "name"),
            joinColumns = extractJoinColumns(joinTableAnnotation, "joinColumns"),
            inverseJoinColumns = extractJoinColumns(joinTableAnnotation, "inverseJoinColumns"),
            cascade = extractCascadeTypes(
                annotation, 
                RelationType.ONE_TO_MANY, 
                isOwnerSide, 
                false, // isBidirectional will be set later in detectBidirectionalRelationships
                false  // isCircular will be set later in markCircularDependencies
            ),
            fetchType = extractFetchType(annotation),
            orphanRemoval = extractBooleanAttribute(annotation, "orphanRemoval"),
            isOwnerSide = isOwnerSide
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
            cascade = extractCascadeTypes(
                annotation, 
                RelationType.MANY_TO_ONE, 
                true, // ManyToOne is always owner side
                false, // isBidirectional will be set later in detectBidirectionalRelationships
                false  // isCircular will be set later in markCircularDependencies
            ),
            fetchType = extractFetchType(annotation),
            isOwnerSide = true
        )
    }

    private fun analyzeManyToMany(field: PsiField, entityClass: PsiClass): RelationshipInfo {
        val annotation = field.getAnnotation("javax.persistence.ManyToMany")
                        ?: field.getAnnotation("jakarta.persistence.ManyToMany")

        val joinTableAnnotation = field.getAnnotation("javax.persistence.JoinTable")
                                 ?: field.getAnnotation("jakarta.persistence.JoinTable")
                                 
        val mappedBy = extractAnnotationAttribute(annotation, "mappedBy")
        val isOwnerSide = mappedBy == null

        return RelationshipInfo(
            sourceEntity = entityClass.qualifiedName ?: "",
            targetEntity = extractCollectionTargetEntity(field),
            relationType = RelationType.MANY_TO_MANY,
            fieldName = field.name ?: "",
            mappedBy = mappedBy,
            joinTable = extractAnnotationAttribute(joinTableAnnotation, "name"),
            joinColumns = extractJoinColumns(joinTableAnnotation, "joinColumns"),
            inverseJoinColumns = extractJoinColumns(joinTableAnnotation, "inverseJoinColumns"),
            cascade = extractCascadeTypes(
                annotation, 
                RelationType.MANY_TO_MANY, 
                isOwnerSide, 
                false, // isBidirectional will be set later in detectBidirectionalRelationships
                false  // isCircular will be set later in markCircularDependencies
            ),
            fetchType = extractFetchType(annotation),
            isOwnerSide = isOwnerSide
        )
    }

    private fun analyzeEmbedded(field: PsiField, entityClass: PsiClass): RelationshipInfo {
        return RelationshipInfo(
            sourceEntity = entityClass.qualifiedName ?: "",
            targetEntity = extractTargetEntity(field),
            relationType = RelationType.EMBEDDED,
            fieldName = field.name ?: "",
            cascade = extractCascadeTypes(
                null, 
                RelationType.EMBEDDED, 
                true, // Embedded is always owner side
                false, 
                false
            ),
            isOwnerSide = true
        )
    }

    private fun analyzeImplicitRelationship(field: PsiField, entityClass: PsiClass): RelationshipInfo {
        // Analyze fields that might be relationships but lack explicit annotations
        val targetEntity = extractCollectionTargetEntity(field)
        val relationType = if (isCollectionType(field.type)) RelationType.ONE_TO_MANY else RelationType.MANY_TO_ONE

        return RelationshipInfo(
            sourceEntity = entityClass.qualifiedName ?: "",
            targetEntity = targetEntity,
            relationType = relationType,
            fieldName = field.name ?: "",
            cascade = extractCascadeTypes(
                null, 
                relationType, 
                true, // Implicit relationships are always owner side
                false, 
                false
            ),
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

    /**
     * Extracts cascade types from an annotation, or suggests appropriate cascade types
     * if none are specified based on the relationship type and context.
     * 
     * @param annotation The JPA relationship annotation
     * @param relationType The type of relationship
     * @param isOwnerSide Whether this is the owner side of the relationship
     * @param isBidirectional Whether this is a bidirectional relationship
     * @param isCircular Whether this relationship is part of a circular dependency
     * @return The set of cascade types to use
     */
    fun extractCascadeTypes(
        annotation: PsiAnnotation?,
        relationType: RelationType = RelationType.NONE,
        isOwnerSide: Boolean = true,
        isBidirectional: Boolean = false,
        isCircular: Boolean = false
    ): Set<CascadeType> {
        // First check if cascade types are explicitly specified
        val cascadeValue = annotation?.findAttributeValue("cascade")?.text
        if (cascadeValue != null) {
            return parseCascadeTypes(cascadeValue)
        }
        
        // If no cascade types are specified, suggest appropriate ones based on the context
        return suggestCascadeTypes(relationType, isOwnerSide, isBidirectional, isCircular)
    }
    
    /**
     * Suggests appropriate cascade types based on the relationship type and context.
     * 
     * @param relationType The type of relationship
     * @param isOwnerSide Whether this is the owner side of the relationship
     * @param isBidirectional Whether this is a bidirectional relationship
     * @param isCircular Whether this relationship is part of a circular dependency
     * @return The suggested set of cascade types
     */
    fun suggestCascadeTypes(
        relationType: RelationType,
        isOwnerSide: Boolean,
        isBidirectional: Boolean,
        isCircular: Boolean
    ): Set<CascadeType> {
        // For circular dependencies, be careful with cascade types to avoid infinite loops
        if (isCircular) {
            return setOf(CascadeType.PERSIST, CascadeType.MERGE)
        }
        
        return when (relationType) {
            RelationType.ONE_TO_ONE -> {
                if (isOwnerSide) {
                    // Owner side of one-to-one typically cascades all operations
                    setOf(CascadeType.ALL)
                } else {
                    // Non-owner side should be more careful with cascades
                    setOf(CascadeType.PERSIST, CascadeType.MERGE)
                }
            }
            
            RelationType.ONE_TO_MANY -> {
                // One-to-many typically cascades most operations to the "many" side
                if (isBidirectional) {
                    // In bidirectional relationships, be careful with REMOVE to avoid orphaned records
                    setOf(CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH)
                } else {
                    // In unidirectional relationships, can often cascade all
                    setOf(CascadeType.ALL)
                }
            }
            
            RelationType.MANY_TO_ONE -> {
                // Many-to-one typically doesn't cascade REMOVE to avoid deleting shared parents
                setOf(CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH)
            }
            
            RelationType.MANY_TO_MANY -> {
                // Many-to-many typically doesn't cascade REMOVE to avoid unintended deletions
                setOf(CascadeType.PERSIST, CascadeType.MERGE)
            }
            
            RelationType.EMBEDDED -> {
                // Embedded types are always cascaded (implicit in JPA)
                setOf(CascadeType.ALL)
            }
            
            RelationType.COMPOSITION -> {
                // Compositions by definition cascade all operations
                setOf(CascadeType.ALL)
            }
            
            else -> emptySet()
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

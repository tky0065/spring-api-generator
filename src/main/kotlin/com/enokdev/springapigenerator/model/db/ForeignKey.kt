package com.enokdev.springapigenerator.model.db

/**
 * Represents a foreign key constraint.
 */
data class ForeignKey(
    val name: String?,
    val columnName: String,
    val referenceTable: String,
    val referenceColumn: String,
    val updateRule: Int,
    val deleteRule: Int
) {
    /**
     * Determines the type of JPA relationship based on foreign key configuration.
     * This is a simplistic approach and might need refinement in real-world scenarios.
     */
    fun determineRelationType(): String {
        // Simple logic: if column name ends with "_id", it's likely a Many-to-One
        // More sophisticated logic would involve analyzing both tables' structures
        return if (columnName.endsWith("_id", ignoreCase = true)) {
            "ManyToOne"
        } else {
            "OneToOne"  // Default to OneToOne for simplicity
        }
    }

    /**
     * Gets the Java field name for the relationship.
     */
    fun getRelationshipFieldName(): String {
        // Remove "_id" suffix if present and convert to camelCase
        val baseName = if (columnName.endsWith("_id", ignoreCase = true)) {
            columnName.substring(0, columnName.length - 3)
        } else {
            columnName
        }

        val parts = baseName.split("_")
        return parts[0].lowercase() + parts.drop(1).joinToString("") {
            it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
        }
    }
}

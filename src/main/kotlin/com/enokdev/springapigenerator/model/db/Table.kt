package com.enokdev.springapigenerator.model.db

/**
 * Represents a database table.
 */
data class Table(
    val name: String,
    val comments: String = "",
    val columns: List<Column>,
    val primaryKeyColumns: List<String>,
    var foreignKeys: List<ForeignKey> = emptyList()
) {
    /**
     * Gets the Java entity name derived from the table name.
     * Converts snake_case to PascalCase and singularizes plural names.
     */
    val entityName: String get() {
        val baseName = name
            .split("_")
            .joinToString("") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }

        // Simple singularization
        return if (baseName.endsWith("s") && !baseName.endsWith("ss")) {
            baseName.substring(0, baseName.length - 1)
        } else {
            baseName
        }
    }

    /**
     * Gets the primary key column objects.
     */
    fun findPrimaryKeyColumns(): List<Column> {
        return columns.filter { primaryKeyColumns.contains(it.name) }
    }

    /**
     * Gets the first primary key column (usually the ID).
     */
    fun getPrimaryKeyColumn(): Column? {
        return findPrimaryKeyColumns().firstOrNull()
    }

    /**
     * Gets columns that are not primary keys.
     */
    fun getNonPrimaryKeyColumns(): List<Column> {
        return columns.filter { !primaryKeyColumns.contains(it.name) }
    }

    /**
     * Gets columns that are foreign keys.
     */
    fun getForeignKeyColumns(): List<Column> {
        val foreignKeyColumnNames = foreignKeys.map { it.columnName }
        return columns.filter { foreignKeyColumnNames.contains(it.name) }
    }
}

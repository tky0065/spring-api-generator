package com.enokdev.springapigenerator.model.db

/**
 * Represents a database column.
 */
data class Column(
    val name: String,
    val sqlType: Int,
    val sqlTypeName: String,
    val size: Int,
    val decimalDigits: Int,
    val nullable: Boolean,
    val comments: String = "",
    val defaultValue: String? = null,
    val autoIncrement: Boolean = false,
    val javaType: String
) {
    /**
     * Gets the Java field name derived from the column name.
     * Converts snake_case to camelCase.
     */
    val fieldName: String get() {
        val parts = name.split("_")
        return parts[0].lowercase() + parts.drop(1).joinToString("") { it.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }}
    }

    /**
     * Gets the Java getter method name.
     */
    val getterName: String get() = "get${fieldName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"

    /**
     * Gets the Java setter method name.
     */
    val setterName: String get() = "set${fieldName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
}

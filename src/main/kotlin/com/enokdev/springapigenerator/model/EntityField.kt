package com.enokdev.springapigenerator.model

/**
 * Metadata for a field in a JPA entity.
 */
data class EntityField(
    val name: String,
    val type: String,
    val nullable: Boolean = true,
    val columnName: String? = null,
    val relationType: RelationType = RelationType.NONE,
    val relationTargetEntity: String? = null
) {
    /**
     * Gets the simple type name without package.
     */
    val simpleTypeName: String get() = type.substringAfterLast(".")

    /**
     * Checks if the field is a collection type.
     */
    val isCollection: Boolean get() = relationType == RelationType.ONE_TO_MANY ||
                                     relationType == RelationType.MANY_TO_MANY

    /**
     * Gets the target entity simple name for relations.
     */
    val relationTargetSimpleName: String? get() = relationTargetEntity?.substringAfterLast(".")

    /**
     * Checks if the field has a primitive Java type.
     */
    val isPrimitiveType: Boolean get() = PRIMITIVE_TYPES.contains(type)

    /**
     * Checks if the field has a simple Java type (non-entity).
     */
    val isSimpleType: Boolean get() = SIMPLE_TYPES.contains(type) ||
                                     type.startsWith("java.lang") ||
                                     type.startsWith("java.time") ||
                                     type.startsWith("java.util") ||
                                     type.startsWith("java.math")

    companion object {
        private val PRIMITIVE_TYPES = setOf(
            "boolean", "byte", "short", "int", "long", "float", "double", "char"
        )

        private val SIMPLE_TYPES = setOf(
            "java.lang.String", "java.lang.Boolean", "java.lang.Byte",
            "java.lang.Short", "java.lang.Integer", "java.lang.Long",
            "java.lang.Float", "java.lang.Double", "java.lang.Character",
            "java.math.BigDecimal", "java.math.BigInteger",
            "java.time.LocalDate", "java.time.LocalTime", "java.time.LocalDateTime",
            "java.time.ZonedDateTime", "java.time.Instant", "java.util.Date",
            "java.util.UUID"
        )
    }
}

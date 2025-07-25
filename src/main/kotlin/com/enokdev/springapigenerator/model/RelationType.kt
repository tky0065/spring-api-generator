package com.enokdev.springapigenerator.model

/**
 * Enum representing JPA relation types including complex relationships.
 */
enum class RelationType {
    NONE,
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY,
    EMBEDDED,        // For @Embedded and @Embeddable relationships
    INHERITANCE,     // For inheritance relationships (@Inheritance)
    COMPOSITION      // For complex composition relationships
}

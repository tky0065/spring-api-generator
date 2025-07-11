package com.enokdev.springapigenerator.model

/**
 * Enum representing JPA relation types.
 */
enum class RelationType {
    NONE,
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY
}

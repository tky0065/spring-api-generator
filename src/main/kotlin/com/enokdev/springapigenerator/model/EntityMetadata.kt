package com.enokdev.springapigenerator.model

/**
 * Metadata for a JPA entity class.
 */
data class EntityMetadata(
    val className: String,
    val qualifiedClassName: String,
    val packageName: String,
    val fields: List<EntityField>,
    val idType: String,
    val tableName: String,

    // Derived properties for code generation
    val entityNameLower: String = className.replaceFirstChar { it.lowercase() },
    val dtoName: String = "${className}DTO",
    val repositoryName: String = "${className}Repository",
    val serviceName: String = "${className}Service",
    val serviceImplName: String = "${className}ServiceImpl",
    val controllerName: String = "${className}Controller",
    val mapperName: String = "${className}Mapper",
    // Extract base package correctly from packageName
    val entityBasePackage: String = when {
        // Si le package se termine par .entity, on retire .entity
        packageName.endsWith(".entity") -> packageName.substringBeforeLast(".entity")
        // Si le package contient .entity., on prend tout avant .entity
        packageName.contains(".entity.") -> packageName.substringBefore(".entity")
        // Sinon, on prend le package tel quel (pour les entités pas dans .entity)
        packageName.isNotEmpty() -> packageName
        // Fallback si packageName est vide
        else -> qualifiedClassName.substringBeforeLast(".").ifEmpty { "com.example" }
    }
) {
    // Generate packages using standard Spring Boot structure
    val domainPackage: String get() = "$entityBasePackage.entity"
    val dtoPackage: String get() = "$entityBasePackage.dto"
    val repositoryPackage: String get() = "$entityBasePackage.repository"
    val servicePackage: String get() = "$entityBasePackage.service"
    val serviceImplPackage: String get() = "$entityBasePackage.service.impl"
    val controllerPackage: String get() = "$entityBasePackage.controller"
    val mapperPackage: String get() = "$entityBasePackage.mapper"
}

/**
 * Extension function to make first character lowercase.
 */
fun String.decapitalize(): String {
    return this.replaceFirstChar { it.lowercase() }
}

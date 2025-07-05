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
    val entityBasePackage: String = packageName.substringBeforeLast(".")
) {
    val domainPackage: String get() = "$entityBasePackage.domain"
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

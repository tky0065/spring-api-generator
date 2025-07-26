package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for Spring Data JPA repositories.
 */
class RepositoryGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "Repository.java.ft"
    }

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val repositoryPackage = packageConfig["repositoryPackage"] ?: entityMetadata.repositoryPackage
        val repositoryDir = repositoryPackage.replace(".", "/")
        val extension = getFileExtensionForProject(project)
        val fileName = "${entityMetadata.repositoryName}.$extension"
        return Paths.get(sourceRoot, repositoryDir, fileName).toString()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["repositoryName"] = entityMetadata.repositoryName
        model["className"] = entityMetadata.className
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["packageName"] = packageConfig["repositoryPackage"] ?: entityMetadata.repositoryPackage
        model["tableName"] = entityMetadata.tableName

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower
        model["repositoryVarName"] = "${entityMetadata.entityNameLower}Repository"

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // Find primary key field - assuming first field or field named "id"
        val primaryKeyField = entityMetadata.fields.find { it.name == "id" }
            ?: entityMetadata.fields.firstOrNull()
        model["primaryKey"] = primaryKeyField?.name ?: "id"
        model["fields"] = entityMetadata.fields

        // Add repository-specific model data
        val additionalImports = generateAdditionalImports(entityMetadata)
        val customQueryMethods = generateCustomQueryMethods(entityMetadata)
        val customMethods = generateCustomMethods(entityMetadata)

        model["additionalImports"] = additionalImports
        model["imports"] = additionalImports
        model["customQueryMethods"] = customQueryMethods
        model["customMethods"] = customMethods

        return model
    }

    /**
     * Generate additional imports needed for the repository.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata): String {
        val imports = mutableSetOf<String>()

        // Add imports for the entity
        imports.add("${entityMetadata.domainPackage}.${entityMetadata.className}")

        // Add common Spring Data JPA imports
        imports.add("org.springframework.data.jpa.repository.JpaRepository")
        imports.add("org.springframework.stereotype.Repository")
        imports.add("java.util.List")
        
        // Add imports for advanced JPA features
        imports.add("org.springframework.data.jpa.repository.Query")
        imports.add("org.springframework.data.repository.query.Param")
        imports.add("org.springframework.data.domain.Page")
        imports.add("org.springframework.data.domain.Pageable")
        imports.add("org.springframework.data.domain.Sort")
        
        // Add additional imports based on field types
        for (field in entityMetadata.fields) {
            // Generate finder methods for certain field types/names
            if (field.name != "id" && !field.isCollection) {
                if (field.name.equals("name", ignoreCase = true) ||
                    field.name.equals("email", ignoreCase = true) ||
                    field.name.equals("username", ignoreCase = true)) {
                    imports.add("java.util.Optional")
                    break
                }
            }
        }

        return imports.joinToString("\n") { "import $it;" }
    }

    /**
     * Generate custom query methods for searchable fields.
     * This includes derived query methods and custom @Query methods.
     */
    private fun generateCustomQueryMethods(entityMetadata: EntityMetadata): String {
        val methods = StringBuilder()
        val entityName = entityMetadata.className
        val tableName = entityName.lowercase()

        // Generate derived query methods for each field
        entityMetadata.fields.forEach { field ->
            if (!field.isCollection && field.name != "id") {
                val fieldNameCapitalized = field.name.replaceFirstChar { it.uppercase() }
                
                when (field.simpleTypeName) {
                    "String" -> {
                        // For String fields, generate findBy and findByContains methods
                        methods.append("    // Find by exact ${field.name} match\n")
                        methods.append("    List<$entityName> findBy$fieldNameCapitalized(${field.simpleTypeName} ${field.name});\n\n")

                        // Contains search method
                        methods.append("    // Find by ${field.name} containing the given string\n")
                        methods.append("    List<$entityName> findBy${fieldNameCapitalized}Containing(${field.simpleTypeName} ${field.name});\n\n")
                        
                        // Ignore case search method
                        methods.append("    // Find by ${field.name} ignoring case\n")
                        methods.append("    List<$entityName> findBy${fieldNameCapitalized}IgnoreCase(${field.simpleTypeName} ${field.name});\n\n")
                        
                        // Custom query example with JPQL
                        methods.append("    // Custom query to find by ${field.name} with pagination\n")
                        methods.append("    @Query(\"SELECT e FROM $entityName e WHERE LOWER(e.${field.name}) LIKE LOWER(CONCAT('%', :${field.name}, '%'))\")\n")
                        methods.append("    Page<$entityName> search${fieldNameCapitalized}(@Param(\"${field.name}\") String ${field.name}, Pageable pageable);\n\n")
                    }
                    "Integer", "Long", "Double", "Float", "BigDecimal" -> {
                        // For numeric fields, generate comparison methods
                        methods.append("    // Find by exact ${field.name} match\n")
                        methods.append("    List<$entityName> findBy$fieldNameCapitalized(${field.simpleTypeName} ${field.name});\n\n")
                        
                        // Greater than method
                        methods.append("    // Find by ${field.name} greater than the given value\n")
                        methods.append("    List<$entityName> findBy${fieldNameCapitalized}GreaterThan(${field.simpleTypeName} ${field.name});\n\n")
                        
                        // Less than method
                        methods.append("    // Find by ${field.name} less than the given value\n")
                        methods.append("    List<$entityName> findBy${fieldNameCapitalized}LessThan(${field.simpleTypeName} ${field.name});\n\n")
                        
                        // Between method
                        methods.append("    // Find by ${field.name} between two values\n")
                        methods.append("    List<$entityName> findBy${fieldNameCapitalized}Between(${field.simpleTypeName} min${fieldNameCapitalized}, ${field.simpleTypeName} max${fieldNameCapitalized});\n\n")
                    }
                    "Date", "LocalDate", "LocalDateTime", "ZonedDateTime" -> {
                        // For date fields, generate date-specific methods
                        methods.append("    // Find by exact ${field.name} match\n")
                        methods.append("    List<$entityName> findBy$fieldNameCapitalized(${field.simpleTypeName} ${field.name});\n\n")
                        
                        // After method
                        methods.append("    // Find by ${field.name} after the given date\n")
                        methods.append("    List<$entityName> findBy${fieldNameCapitalized}After(${field.simpleTypeName} ${field.name});\n\n")
                        
                        // Before method
                        methods.append("    // Find by ${field.name} before the given date\n")
                        methods.append("    List<$entityName> findBy${fieldNameCapitalized}Before(${field.simpleTypeName} ${field.name});\n\n")
                        
                        // Between method
                        methods.append("    // Find by ${field.name} between two dates\n")
                        methods.append("    List<$entityName> findBy${fieldNameCapitalized}Between(${field.simpleTypeName} start${fieldNameCapitalized}, ${field.simpleTypeName} end${fieldNameCapitalized});\n\n")
                    }
                    "Boolean" -> {
                        // For boolean fields, generate simple findBy method
                        methods.append("    // Find by ${field.name} value\n")
                        methods.append("    List<$entityName> findBy$fieldNameCapitalized(${field.simpleTypeName} ${field.name});\n\n")
                    }
                    else -> {
                        // For other types, generate a simple findBy method
                        methods.append("    // Find by ${field.name} value\n")
                        methods.append("    List<$entityName> findBy$fieldNameCapitalized(${field.simpleTypeName} ${field.name});\n\n")
                    }
                }
            }
        }
        
        // Add some common custom query methods with @Query annotation
        methods.append("    // Custom query example with native SQL\n")
        methods.append("    @Query(value = \"SELECT * FROM $tableName WHERE active = true\", nativeQuery = true)\n")
        methods.append("    List<$entityName> findAllActive();\n\n")
        
        // Add example of a derived query method with multiple conditions
        if (entityMetadata.fields.any { it.name == "active" || it.name == "enabled" }) {
            val statusField = entityMetadata.fields.find { it.name == "active" || it.name == "enabled" }
            if (statusField != null) {
                val statusFieldName = statusField.name.replaceFirstChar { it.uppercase() }
                methods.append("    // Example of a derived query method with multiple conditions\n")
                methods.append("    List<$entityName> findBy${statusFieldName}TrueOrderByIdDesc();\n\n")
            }
        }
        
        // Add example of a method with pagination
        methods.append("    // Example of a method with pagination\n")
        methods.append("    Page<$entityName> findAll(Pageable pageable);\n\n")
        
        // Add example of a method with sorting
        methods.append("    // Example of a method with sorting\n")
        methods.append("    List<$entityName> findAll(Sort sort);\n\n")

        return methods.toString()
    }

    /**
     * Generate custom methods for the repository.
     */
    private fun generateCustomMethods(entityMetadata: EntityMetadata): String {
        // Return empty string for now, can be expanded later
        return ""
    }

    /**
     * Check if a type name is a primitive type.
     */
    private fun isPrimitiveType(typeName: String): Boolean {
        val primitiveTypes = setOf(
            "boolean", "byte", "short", "int", "long", "float", "double", "char"
        )
        return primitiveTypes.contains(typeName)
    }
}

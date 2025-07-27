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
        return "Repository"
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
        model["idType"] = entityMetadata.idType

        // ========== VARIABLES POUR LES PACKAGES (NÉCESSAIRES POUR LES IMPORTS) ==========
        model["domainPackage"] = packageConfig["domainPackage"] ?: entityMetadata.domainPackage
        model["dtoPackage"] = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage

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
        // Vérifie si les méthodes personnalisées sont activées dans la configuration
        val generateCustomQueryMethods = packageConfig["generateCustomQueryMethods"]?.toBoolean() ?: false
        val customQueryMethods = if (generateCustomQueryMethods) {
            generateCustomQueryMethods(entityMetadata)
        } else {
            ""
        }
        val additionalMethods = if (generateCustomQueryMethods) {
            generateAdditionalMethods(entityMetadata)
        } else {
            ""
        }
        val customMethods = if (generateCustomQueryMethods) {
            generateCustomMethods(entityMetadata)
        } else {
            ""
        }

        model["additionalImports"] = additionalImports
        model["imports"] = additionalImports
        model["customQueryMethods"] = customQueryMethods
        model["additionalMethods"] = additionalMethods
        model["customMethods"] = customMethods

        return model
    }

    /**
     * Generate additional imports needed for the repository.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata): String {
        val imports = mutableSetOf<String>()

        // Note: Entity import is already handled in the template directly via ${domainPackage}.${entityName}
        // So we don't add it here to avoid duplicates

        // Only add imports that are not already in the template and are actually needed
        // The basic imports (JpaRepository, Page, Pageable, etc.) are handled in the template

        return "" // Return empty string as basic imports are handled in templates
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
                        methods.append("    fun findBy${fieldNameCapitalized}(${field.name}: String): List<${entityName}>\n\n")

                        // Contains search method
                        methods.append("    // Find by ${field.name} containing the given string\n")
                        methods.append("    fun findBy${fieldNameCapitalized}Containing(${field.name}: String): List<${entityName}>\n\n")

                        // Ignore case search method
                        methods.append("    // Find by ${field.name} ignoring case\n")
                        methods.append("    fun findBy${fieldNameCapitalized}IgnoreCase(${field.name}: String): List<${entityName}>\n\n")

                        // Custom query example with JPQL
                        methods.append("    // Custom query to find by ${field.name} with pagination\n")
                        methods.append("    @Query(\"SELECT e FROM ${entityName} e WHERE LOWER(e.${field.name}) LIKE LOWER(CONCAT('%', :${field.name}, '%'))\")\n")
                        methods.append("    fun search${fieldNameCapitalized}(@Param(\"${field.name}\") ${field.name}: String, pageable: Pageable): Page<${entityName}>\n\n")
                    }
                    "Integer", "Long", "Double", "Float", "BigDecimal" -> {
                        val kotlinType = when (field.simpleTypeName) {
                            "Integer" -> "Int"
                            else -> field.simpleTypeName
                        }

                        // For numeric fields, generate comparison methods
                        methods.append("    // Find by exact ${field.name} match\n")
                        methods.append("    fun findBy${fieldNameCapitalized}(${field.name}: ${kotlinType}): List<${entityName}>\n\n")

                        // Greater than method
                        methods.append("    // Find by ${field.name} greater than the given value\n")
                        methods.append("    fun findBy${fieldNameCapitalized}GreaterThan(${field.name}: ${kotlinType}): List<${entityName}>\n\n")

                        // Less than method
                        methods.append("    // Find by ${field.name} less than the given value\n")
                        methods.append("    fun findBy${fieldNameCapitalized}LessThan(${field.name}: ${kotlinType}): List<${entityName}>\n\n")

                        // Between method
                        methods.append("    // Find by ${field.name} between two values\n")
                        methods.append("    fun findBy${fieldNameCapitalized}Between(min${fieldNameCapitalized}: ${kotlinType}, max${fieldNameCapitalized}: ${kotlinType}): List<${entityName}>\n\n")
                    }
                    "Boolean" -> {
                        // For Boolean fields
                        methods.append("    // Find by ${field.name} value\n")
                        methods.append("    fun findBy${fieldNameCapitalized}(${field.name}: Boolean): List<${entityName}>\n\n")
                    }
                    else -> {
                        // For other types, generate a simple findBy method
                        val kotlinType = when (field.simpleTypeName) {
                            "boolean" -> "Boolean"
                            "int" -> "Int"
                            else -> field.simpleTypeName
                        }
                        methods.append("    // Find by ${field.name} value\n")
                        methods.append("    fun findBy${fieldNameCapitalized}(${field.name}: ${kotlinType}): List<${entityName}>\n\n")
                    }
                }
            }
        }
        
        // Add some common custom query methods with @Query annotation
        methods.append("    // Custom query example with native SQL\n")
        methods.append("    @Query(value = \"SELECT * FROM ${tableName} WHERE active = true\", nativeQuery = true)\n")
        methods.append("    fun findAllActive(): List<${entityName}>\n\n")

        return methods.toString()
    }

    /**
     * Generate additional methods for the repository.
     */
    private fun generateAdditionalMethods(entityMetadata: EntityMetadata): String {
        val methods = StringBuilder()
        val entityName = entityMetadata.className

        // Example: Method to count entities by a specific field
        entityMetadata.fields.forEach { field ->
            if (field.name != "id" && !field.isCollection) {
                val fieldNameCapitalized = field.name.replaceFirstChar { it.uppercase() }

                // Count method
                methods.append("    // Count by ${field.name} value\n")
                methods.append("    Long countBy$fieldNameCapitalized(${field.simpleTypeName} ${field.name});\n\n")
            }
        }

        // Add more additional methods as needed

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

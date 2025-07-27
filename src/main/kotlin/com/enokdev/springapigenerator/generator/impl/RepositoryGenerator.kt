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
        // DÉSACTIVÉ TEMPORAIREMENT : Les méthodes personnalisées causent des problèmes
        // val generateCustomQueryMethods = packageConfig["generateCustomQueryMethods"]?.toBoolean() ?: false
        val generateCustomQueryMethods = false // Force désactivation
        val customQueryMethods = if (generateCustomQueryMethods) {
            generateCustomQueryMethods(entityMetadata, packageConfig)
        } else {
            ""
        }
        val additionalMethods = if (generateCustomQueryMethods) {
            generateAdditionalMethods(entityMetadata, packageConfig)
        } else {
            ""
        }
        val customMethods = if (generateCustomQueryMethods) {
            generateCustomMethods(entityMetadata, packageConfig)
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
        // Les imports de base sont déjà dans le template
        // Pas besoin d'ajouter java.util.* car les imports spécifiques sont déjà dans le template
        return ""
    }

    /**
     * Generate custom query methods for searchable fields.
     * This includes derived query methods and custom @Query methods.
     */
    private fun generateCustomQueryMethods(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val methods = StringBuilder()
        val entityName = entityMetadata.className

        // Déterminer si c'est un projet Java ou Kotlin basé sur l'extension de fichier cible
        val isKotlinProject = currentProject?.let { getFileExtensionForProject(it) == "kt" } ?: false

        // Generate derived query methods for each field
        entityMetadata.fields.forEach { field ->
            if (!field.isCollection && field.name != "id") {
                val fieldNameCapitalized = field.name.replaceFirstChar { it.uppercase() }
                
                when (field.simpleTypeName) {
                    "String" -> {
                        if (isKotlinProject) {
                            // Kotlin syntax
                            methods.append("    // Find by exact ${field.name} match\n")
                            methods.append("    fun findBy${fieldNameCapitalized}(${field.name}: String): List<${entityName}>\n\n")

                            methods.append("    // Find by ${field.name} containing given string\n")
                            methods.append("    fun findBy${fieldNameCapitalized}Containing(${field.name}: String): List<${entityName}>\n\n")

                            methods.append("    // Find by ${field.name} ignoring case\n")
                            methods.append("    fun findBy${fieldNameCapitalized}IgnoreCase(${field.name}: String): List<${entityName}>\n\n")

                            methods.append("    // Custom query to find by ${field.name} with pagination\n")
                            methods.append("    @Query(\"SELECT e FROM ${entityName} e WHERE LOWER(e.${field.name}) LIKE LOWER(CONCAT('%', :${field.name}, '%'))\")\n")
                            methods.append("    fun search${fieldNameCapitalized}(@Param(\"${field.name}\") ${field.name}: String, pageable: Pageable): Page<${entityName}>\n\n")
                        } else {
                            // Java syntax
                            methods.append("    // Find by exact ${field.name} match\n")
                            methods.append("    List<${entityName}> findBy${fieldNameCapitalized}(String ${field.name});\n\n")

                            methods.append("    // Find by ${field.name} containing given string\n")
                            methods.append("    List<${entityName}> findBy${fieldNameCapitalized}Containing(String ${field.name});\n\n")

                            methods.append("    // Find by ${field.name} ignoring case\n")
                            methods.append("    List<${entityName}> findBy${fieldNameCapitalized}IgnoreCase(String ${field.name});\n\n")

                            methods.append("    // Custom query to find by ${field.name} with pagination\n")
                            methods.append("    @Query(\"SELECT e FROM ${entityName} e WHERE LOWER(e.${field.name}) LIKE LOWER(CONCAT('%', :${field.name}, '%'))\")\n")
                            methods.append("    Page<${entityName}> search${fieldNameCapitalized}(@Param(\"${field.name}\") String ${field.name}, Pageable pageable);\n\n")
                        }
                    }
                    "Boolean", "boolean" -> {
                        if (isKotlinProject) {
                            // Kotlin syntax
                            methods.append("    // Find by ${field.name} value\n")
                            methods.append("    fun findBy${fieldNameCapitalized}(${field.name}: Boolean): List<${entityName}>\n\n")
                        } else {
                            // Java syntax - utiliser boolean primitive au lieu de Boolean wrapper
                            methods.append("    // Find by ${field.name} value\n")
                            methods.append("    List<${entityName}> findBy${fieldNameCapitalized}(boolean ${field.name});\n\n")
                        }
                    }
                    else -> {
                        // Pour tous les autres types (incluant les types de collection incorrects)
                        val fieldType = if (isKotlinProject) {
                            when (field.simpleTypeName) {
                                "Integer" -> "Int"
                                "boolean" -> "Boolean"
                                "int" -> "Int"
                                else -> field.simpleTypeName
                            }
                        } else {
                            // Corriger les types Java - utiliser les types appropriés
                            when (field.simpleTypeName) {
                                "Integer" -> "Integer"
                                "Long" -> "Long"
                                "boolean" -> "boolean"
                                "Boolean" -> "boolean"
                                else -> field.simpleTypeName
                            }
                        }

                        // Éviter de générer des méthodes pour des types de collection incorrects comme "String>"
                        if (!fieldType.contains(">") && !fieldType.contains("<")) {
                            if (isKotlinProject) {
                                // Kotlin syntax
                                methods.append("    // Find by ${field.name} value\n")
                                methods.append("    fun findBy${fieldNameCapitalized}(${field.name}: ${fieldType}): List<${entityName}>\n\n")
                            } else {
                                // Java syntax
                                methods.append("    // Find by ${field.name} value\n")
                                methods.append("    List<${entityName}> findBy${fieldNameCapitalized}(${fieldType} ${field.name});\n\n")
                            }
                        }
                    }
                }
            }
        }

        return methods.toString()
    }

    /**
     * Generate additional methods for the repository.
     */
    private fun generateAdditionalMethods(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val methods = StringBuilder()
        val entityName = entityMetadata.className

        // Déterminer si c'est un projet Java ou Kotlin
        val isKotlinProject = currentProject?.let { getFileExtensionForProject(it) == "kt" } ?: false

        // Example: Method to count entities by a specific field
        entityMetadata.fields.forEach { field ->
            if (field.name != "id" && !field.isCollection) {
                val fieldNameCapitalized = field.name.replaceFirstChar { it.uppercase() }

                val fieldType = if (isKotlinProject) {
                    when (field.simpleTypeName) {
                        "Integer" -> "Int"
                        "boolean" -> "Boolean"
                        "int" -> "Int"
                        else -> field.simpleTypeName
                    }
                } else {
                    field.simpleTypeName
                }

                // Count method
                if (isKotlinProject) {
                    methods.append("    // Count by ${field.name} value\n")
                    methods.append("    fun countBy$fieldNameCapitalized(${field.name}: ${fieldType}): Long\n\n")
                } else {
                    methods.append("    // Count by ${field.name} value\n")
                    methods.append("    Long countBy$fieldNameCapitalized(${fieldType} ${field.name});\n\n")
                }
            }
        }

        // Add more additional methods as needed

        return methods.toString()
    }

    /**
     * Generate custom methods for the repository.
     */
    private fun generateCustomMethods(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
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

    override fun generate(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        // Store project for use in other methods
        this.currentProject = project
        return super.generate(project, entityMetadata, packageConfig)
    }

    // Add a property to store the current project
    private var currentProject: Project? = null
}

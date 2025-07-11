package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for Spring Data JPA repositories.
 */
class RepositoryGenerator : AbstractTemplateCodeGenerator("Repository.java.ft") {

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

        // Add repository-specific model data
        val additionalImports = generateAdditionalImports(entityMetadata)
        val customQueryMethods = generateCustomQueryMethods(entityMetadata)

        model["additionalImports"] = additionalImports
        model["customQueryMethods"] = customQueryMethods

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
     */
    private fun generateCustomQueryMethods(entityMetadata: EntityMetadata): String {
        val methods = StringBuilder()

        entityMetadata.fields.forEach { field ->
            if (!field.isCollection && field.name != "id") {
                when (field.simpleTypeName) {
                    "String" -> {
                        // Pour les champs String, générer des méthodes findBy et findByContains
                        val methodName = "findBy${field.name.replaceFirstChar { it.uppercase() }}"
                        methods.append("    List<${entityMetadata.className}> $methodName(${field.simpleTypeName} ${field.name});\n\n")

                        // Méthode de recherche par contenu
                        methods.append("    List<${entityMetadata.className}> findBy${field.name.replaceFirstChar { it.uppercase() }}Contains(${field.simpleTypeName} ${field.name});\n\n")
                    }
                    // Autres types de champs pourraient avoir des requêtes spécifiques
                    else -> {
                        // Pour les autres types, générer une méthode findBy simple
                        methods.append("    List<${entityMetadata.className}> findBy${field.name.replaceFirstChar { it.uppercase() }}(${field.simpleTypeName} ${field.name});\n\n")
                    }
                }
            }
        }

        return methods.toString()
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

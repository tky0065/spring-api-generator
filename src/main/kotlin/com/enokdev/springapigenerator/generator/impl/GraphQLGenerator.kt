package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import freemarker.template.TemplateException
import java.io.StringWriter
import java.nio.file.Paths
import java.util.*

/**
 * Generator for GraphQL schema and resolvers.
 * This generator creates:
 * 1. GraphQL schema files (.graphqls)
 * 2. GraphQL unified controller (combines Query and Mutation resolvers)
 * 3. GraphQL configuration
 */
class GraphQLGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "GraphQLSchema.graphqls"
    }

    /**
     * Override template name logic for GraphQL since it's language-independent
     */
    override fun getTemplateName(project: Project, forceLanguage: String?): String {
        // GraphQL schemas are language-independent, so we always use the .ft extension
        return "${getBaseTemplateName()}.ft"
    }

    /**
     * Get the target file path for the GraphQL schema.
     */
    override fun getTargetFilePath(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        // Les schémas GraphQL sont placés dans les ressources, indépendamment du langage du projet
        val resourceRoot = getResourceRootDir(project)
        return Paths.get(resourceRoot, "graphql", "${entityMetadata.entityNameLower}.graphqls").toString()
    }

    override fun createDataModel(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["packageName"] = basePackage
        model["basePackage"] = basePackage
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["className"] = entityMetadata.className

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // Map Java/Kotlin types to GraphQL types and create a map of field to GraphQL type
        val fieldToGraphQLType = mutableMapOf<String, String>()

        entityMetadata.fields.forEach { field ->
            val graphqlType = mapJavaTypeToGraphQLType(field.type)
            fieldToGraphQLType[field.name] = if (field.nullable) graphqlType else "$graphqlType!"
        }

        model["fields"] = entityMetadata.fields
        model["fieldToGraphQLType"] = fieldToGraphQLType
        model["idType"] = extractSimpleTypeName(entityMetadata.idType)

        // ========== GÉNÉRATION DES CHAMPS GRAPHQL ==========
        model["graphqlFields"] = generateGraphQLFields(entityMetadata)
        model["createInputFields"] = generateCreateInputFields(entityMetadata)
        model["updateInputFields"] = generateUpdateInputFields(entityMetadata)
        model["additionalTypes"] = generateAdditionalTypes(entityMetadata)

        // ========== VARIABLES POUR LES IMPORTS ET MÉTHODES PERSONNALISÉES ==========
        model["imports"] = ""
        model["customMethods"] = ""

        return model
    }

    /**
     * Map Java/Kotlin types to GraphQL types.
     */
    private fun mapJavaTypeToGraphQLType(javaType: String): String {
        return when (javaType.lowercase()) {
            "string", "java.lang.string" -> "String"
            "int", "integer", "java.lang.integer" -> "Int"
            "long", "java.lang.long" -> "Int"
            "float", "java.lang.float" -> "Float"
            "double", "java.lang.double" -> "Float"
            "boolean", "java.lang.boolean" -> "Boolean"
            "bigdecimal", "java.math.bigdecimal" -> "Float"
            "date", "java.util.date", "localdate", "java.time.localdate" -> "String"
            "localdatetime", "java.time.localdatetime", "timestamp" -> "String"
            "uuid", "java.util.uuid" -> "ID"
            else -> if (javaType.contains("List") || javaType.contains("Set")) {
                val elementType = extractGenericType(javaType)
                "[${mapJavaTypeToGraphQLType(elementType)}]"
            } else {
                javaType.substringAfterLast(".")
            }
        }
    }

    /**
     * Extract generic type from collection types.
     */
    private fun extractGenericType(type: String): String {
        val start = type.indexOf('<')
        val end = type.lastIndexOf('>')
        return if (start > 0 && end > start) {
            type.substring(start + 1, end).trim()
        } else {
            "String"
        }
    }

    /**
     * Generate GraphQL controller with unified Query and Mutation resolvers.
     */
    fun generateGraphQLController(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val templateName = if (isKotlinProject(project)) "GraphQLController.kt.ft" else "GraphQLController.java.ft"
        val template = cfg.getTemplate(templateName)

        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val controllerPackage = "$basePackage.graphql"

        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["packageName"] = controllerPackage
        dataModel["className"] = "${entityMetadata.className}GraphQLController"

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)
        return writer.toString()
    }

    /**
     * Generate GraphQL configuration.
     */
    fun generateGraphQLConfig(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val templateName = if (isKotlinProject(project)) "GraphQLConfig.kt.ft" else "GraphQLConfig.java.ft"
        val template = cfg.getTemplate(templateName)

        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val configPackage = "$basePackage.config"

        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["packageName"] = configPackage
        dataModel["className"] = "GraphQLConfig"

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)
        return writer.toString()
    }

    /**
     * Generate GraphQL schema content.
     */
    fun generateSchema(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val template = cfg.getTemplate(getBaseTemplateName()) // Use base template name
        val dataModel = createDataModel(entityMetadata, packageConfig)

        val writer = StringWriter()
        try {
            template.process(dataModel, writer)
        } catch (e: TemplateException) {
            throw RuntimeException("Error processing schema template: ${e.message}", e)
        }

        return writer.toString()
    }

    /**
     * Get the file path for the GraphQL schema.
     *
     * @param project The IntelliJ project
     * @param packageConfig Package configuration map
     * @return The file path for the GraphQL schema
     */
    fun getSchemaFilePath(project: Project, packageConfig: Map<String, String>): String {
        val resourceRoot = getResourceRootDir(project)
        // Utiliser un dossier graphql avec le nom complet pour éviter un dossier vide
        return Paths.get(resourceRoot, "graphql", "schema.graphqls").toString()
    }

    /**
     * Generate controller content.
     */
    fun generateController(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val templateName = if (isKotlinProject(project)) "GraphQLController.kt.ft" else "GraphQLController.java.ft"
        val template = cfg.getTemplate(templateName)
        val dataModel = createDataModel(entityMetadata, packageConfig)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val controllerPackage = packageConfig["controllerPackage"] ?: "$basePackage.controller"
        dataModel["controllerPackage"] = controllerPackage

        val writer = java.io.StringWriter()
        try {
            template.process(dataModel, writer)
        } catch (e: TemplateException) {
            throw RuntimeException("Error processing controller template: ${e.message}", e)
        }

        return writer.toString()
    }

    /**
     * Get the file path for the GraphQL controller.
     *
     * @param project The IntelliJ project
     * @param entityMetadata Metadata about the entity
     * @param packageConfig Package configuration map
     * @return The file path for the GraphQL controller
     */
    fun getControllerFilePath(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val controllerPackage = packageConfig["controllerPackage"] ?: "$basePackage.controller"
        val packagePath = controllerPackage.replace('.', '/')
        val extension = getFileExtensionForProject(project)

        return Paths.get(sourceRoot, packagePath, "${entityMetadata.className}GraphQLController.$extension").toString()
    }

    /**
     * Generate GraphQL configuration content.
     */
    fun generateConfig(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val templateName = if (isKotlinProject(project)) "GraphQLConfig.kt.ft" else "GraphQLConfig.java.ft"
        val template = cfg.getTemplate(templateName)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val configPackage = "$basePackage.config"

        val dataModel = mutableMapOf<String, Any>()
        dataModel["packageName"] = configPackage
        dataModel["currentDate"] = Date()

        val writer = StringWriter()
        try {
            template.process(dataModel, writer)
        } catch (e: TemplateException) {
            throw RuntimeException("Error processing config template: ${e.message}", e)
        }

        return writer.toString()
    }

    /**
     * Get the file path for the GraphQL configuration.
     *
     * @param project The IntelliJ project
     * @param packageConfig Package configuration map
     * @return The file path for the GraphQL configuration
     */
    fun getConfigFilePath(project: Project, packageConfig: Map<String, String>): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val basePackage = packageConfig["basePackage"] ?: throw RuntimeException("Base package is not defined")
        val configPackage = "$basePackage.config"
        val packagePath = configPackage.replace('.', '/')
        val extension = getFileExtensionForProject(project)

        return Paths.get(sourceRoot, packagePath, "GraphQLConfig.$extension").toString()
    }

    /**
     * Helper method to get the resource root directory.
     */
    private fun getResourceRootDir(project: Project): String {
        val basePath = project.basePath ?: throw RuntimeException("Project base path not found")
        return Paths.get(basePath, "src", "main", "resources").toString()
    }

    /**
     * Generate GraphQL fields for the main type.
     */
    private fun generateGraphQLFields(entityMetadata: EntityMetadata): String {
        val fields = StringBuilder()

        entityMetadata.fields.forEach { field ->
            val graphqlType = mapJavaTypeToGraphQLType(field.type)
            val fieldType = if (field.nullable) graphqlType else "$graphqlType!"

            // Add field with optional description
            fields.append("    ${field.name}: $fieldType")
            if (field != entityMetadata.fields.last()) {
                fields.append("\n")
            }
        }

        return fields.toString()
    }

    /**
     * Generate input fields for create operations.
     */
    private fun generateCreateInputFields(entityMetadata: EntityMetadata): String {
        val fields = StringBuilder()

        entityMetadata.fields.forEach { field ->
            // Skip ID field for create operations
            if (field.name.lowercase() != "id") {
                val graphqlType = mapJavaTypeToGraphQLType(field.type)
                val fieldType = if (field.nullable) graphqlType else "$graphqlType!"

                fields.append("    ${field.name}: $fieldType")
                if (field != entityMetadata.fields.last()) {
                    fields.append("\n")
                }
            }
        }

        return fields.toString()
    }

    /**
     * Generate input fields for update operations.
     */
    private fun generateUpdateInputFields(entityMetadata: EntityMetadata): String {
        val fields = StringBuilder()

        entityMetadata.fields.forEach { field ->
            // All fields are optional for updates (except ID which is passed separately)
            if (field.name.lowercase() != "id") {
                val graphqlType = mapJavaTypeToGraphQLType(field.type)

                fields.append("    ${field.name}: $graphqlType")
                if (field != entityMetadata.fields.last()) {
                    fields.append("\n")
                }
            }
        }

        return fields.toString()
    }

    /**
     * Generate additional GraphQL types for relationships.
     */
    private fun generateAdditionalTypes(entityMetadata: EntityMetadata): String {
        val types = StringBuilder()

        // Generate types for related entities if any
        entityMetadata.fields.forEach { field ->
            if (field.relationType != com.enokdev.springapigenerator.model.RelationType.NONE) {
                val relatedTypeName = field.relationTargetSimpleName
                if (relatedTypeName != null && !relatedTypeName.equals(entityMetadata.className)) {
                    types.append("\n# Placeholder type for ${relatedTypeName}")
                    types.append("\ntype ${relatedTypeName} {")
                    types.append("\n    id: ID!")
                    types.append("\n    # Add other fields as needed")
                    types.append("\n}\n")
                }
            }
        }

        return types.toString()
    }
}

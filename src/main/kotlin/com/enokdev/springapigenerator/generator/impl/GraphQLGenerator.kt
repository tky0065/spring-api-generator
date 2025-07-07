package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.RelationType
import com.intellij.openapi.project.Project
import freemarker.template.TemplateException
import java.io.File
import java.io.StringWriter
import java.nio.file.Paths
import java.util.Date

/**
 * Generator for GraphQL schema and resolvers.
 * This generator creates:
 * 1. GraphQL schema files (.graphqls)
 * 2. GraphQL unified controller (combines Query and Mutation resolvers)
 * 3. GraphQL configuration
 */
class GraphQLGenerator : AbstractTemplateCodeGenerator("GraphQLSchema.graphqls.ft") {

    companion object {
        const val GRAPHQL_SCHEMA_TEMPLATE = "GraphQLSchema.graphqls.ft"
        const val GRAPHQL_CONTROLLER_TEMPLATE = "GraphQLController.java.ft"
        const val GRAPHQL_CONFIG_TEMPLATE = "GraphQLConfig.java.ft"
    }

    /**
     * Get the target file path for the GraphQL schema.
     *
     * @param project The IntelliJ project
     * @param entityMetadata Metadata about the entity
     * @param packageConfig Package configuration map
     * @return File path where the GraphQL schema should be saved
     */
    override fun getTargetFilePath(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val resourceRoot = getResourceRootDir(project)
        return Paths.get(resourceRoot, "graphql", "${entityMetadata.entityNameLower}.graphqls").toString()
    }

    /**
     * Create the data model for the template.
     */
    override fun createDataModel(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // Map Java/Kotlin types to GraphQL types and create a map of field to GraphQL type
        val fieldToGraphQLType = mutableMapOf<String, String>()
        val mappedFields = entityMetadata.fields.map { field ->
            val graphqlType = when (field.type) {
                "String" -> "String"
                "Integer", "int" -> "Int"
                "Long", "long" -> "Int"
                "Double", "double", "Float", "float" -> "Float"
                "Boolean", "boolean" -> "Boolean"
                "LocalDate", "LocalDateTime", "Date" -> "String"
                "UUID" -> "ID"
                else -> {
                    if (field.relationType != RelationType.NONE) "ID" else "String"
                }
            }
            fieldToGraphQLType[field.name] = graphqlType
            field
        }

        // Add GraphQL-specific parameters
        model["currentDate"] = Date()
        model["graphqlType"] = entityMetadata.className
        model["graphqlInputType"] = "${entityMetadata.className}Input"
        model["entityFields"] = mappedFields
        model["fieldToGraphQLType"] = fieldToGraphQLType
        model["idType"] = entityMetadata.idType
        model["basePackage"] = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        model["domainPackage"] = packageConfig["domainPackage"] ?: "${entityMetadata.entityBasePackage}.domain"
        model["servicePackage"] = packageConfig["servicePackage"] ?: "${entityMetadata.entityBasePackage}.service"

        return model
    }

    /**
     * Generate all GraphQL related files for the entity.
     */
    fun generateAll(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): List<String> {
        val generatedFiles = mutableListOf<String>()

        // Generate the schema file
        val schemaPath = getTargetFilePath(project, entityMetadata, packageConfig)
        generateSchemaFile(project, entityMetadata, packageConfig, schemaPath)
        generatedFiles.add(schemaPath)

        // Generate the unified GraphQL controller (combines query and mutation resolvers)
        val controllerPath = generateGraphQLController(project, entityMetadata, packageConfig)
        generatedFiles.add(controllerPath)

        // Generate GraphQL configuration if not exists
        val configPath = generateGraphQLConfig(project, entityMetadata, packageConfig)
        if (configPath != null) {
            generatedFiles.add(configPath)
        }

        return generatedFiles
    }

    /**
     * Generate the GraphQL schema file.
     */
    private fun generateSchemaFile(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>, targetPath: String) {
        val content = generate(project, entityMetadata, packageConfig)

        val file = File(targetPath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    /**
     * Generate a unified GraphQL controller with both query and mutation resolvers.
     */
    fun generateGraphQLController(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        // Utiliser le même package que les contrôleurs REST
        val controllerPackage = packageConfig["controllerPackage"] ?: "$basePackage.controller"
        val packagePath = controllerPackage.replace('.', '/')

        val targetPath = Paths.get(sourceRoot, packagePath, "${entityMetadata.className}GraphQLController.java").toString()

        // Generate the content using FreeMarker template
        val cfg = createFreemarkerConfig()
        val template = cfg.getTemplate(GRAPHQL_CONTROLLER_TEMPLATE)
        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["controllerPackage"] = controllerPackage

        val writer = StringWriter()
        try {
            template.process(dataModel, writer)
        } catch (e: TemplateException) {
            throw RuntimeException("Error processing template: ${e.message}", e)
        }

        // Create the file
        val file = File(targetPath)
        file.parentFile.mkdirs()
        file.writeText(writer.toString())

        return targetPath
    }

    /**
     * Generate GraphQL configuration if it does not exist already.
     */
    fun generateGraphQLConfig(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String? {
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val configPackage = "$basePackage.config"
        val packagePath = configPackage.replace('.', '/')

        val targetPath = Paths.get(sourceRoot, packagePath, "GraphQLConfig.java").toString()

        // Check if the config already exists
        val configFile = File(targetPath)
        if (configFile.exists()) {
            return null // Config already exists, no need to generate
        }

        // Generate the content using FreeMarker template
        val cfg = createFreemarkerConfig()
        val template = cfg.getTemplate(GRAPHQL_CONFIG_TEMPLATE)
        val dataModel = mutableMapOf<String, Any>()
        dataModel["packageName"] = configPackage
        dataModel["currentDate"] = Date()

        val writer = StringWriter()
        try {
            template.process(dataModel, writer)
        } catch (e: TemplateException) {
            throw RuntimeException("Error processing template: ${e.message}", e)
        }

        // Create the file
        configFile.parentFile.mkdirs()
        configFile.writeText(writer.toString())

        return targetPath
    }

    /**
     * Generate GraphQL schema content.
     *
     * @param project The IntelliJ project
     * @param entityMetadata Metadata about the entity
     * @param packageConfig Package configuration map
     * @return The generated schema content
     */
    fun generateSchema(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig()
        val template = cfg.getTemplate(GRAPHQL_SCHEMA_TEMPLATE)
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
     *
     * @param project The IntelliJ project
     * @param entityMetadata Metadata about the entity
     * @param packageConfig Package configuration map
     * @return The generated controller content
     */
    fun generateController(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig()
        val template = cfg.getTemplate(GRAPHQL_CONTROLLER_TEMPLATE)
        val dataModel = createDataModel(entityMetadata, packageConfig)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val controllerPackage = packageConfig["controllerPackage"] ?: "$basePackage.controller"
        dataModel["controllerPackage"] = controllerPackage

        val writer = StringWriter()
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
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val controllerPackage = packageConfig["controllerPackage"] ?: "$basePackage.controller"
        val packagePath = controllerPackage.replace('.', '/')

        return Paths.get(sourceRoot, packagePath, "${entityMetadata.className}GraphQLController.java").toString()
    }

    /**
     * Generate GraphQL configuration content.
     *
     * @param project The IntelliJ project
     * @param entityMetadata Metadata about the entity
     * @param packageConfig Package configuration map
     * @return The generated configuration content
     */
    fun generateConfig(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig()
        val template = cfg.getTemplate(GRAPHQL_CONFIG_TEMPLATE)
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
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: throw RuntimeException("Base package is not defined")
        val configPackage = "$basePackage.config"
        val packagePath = configPackage.replace('.', '/')

        return Paths.get(sourceRoot, packagePath, "GraphQLConfig.java").toString()
    }

    /**
     * Helper method to get the resource root directory.
     */
    private fun getResourceRootDir(project: Project): String {
        val basePath = project.basePath ?: throw RuntimeException("Project base path not found")
        return Paths.get(basePath, "src", "main", "resources").toString()
    }
}

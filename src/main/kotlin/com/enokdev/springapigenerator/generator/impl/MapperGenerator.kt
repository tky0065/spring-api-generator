package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.RelationType
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for MapStruct mapper interfaces.
 */
class MapperGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "Mapper"
    }

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val mapperPackage = packageConfig["mapperPackage"] ?: entityMetadata.mapperPackage
        val mapperDir = mapperPackage.replace(".", "/")
        val extension = getFileExtensionForProject(project)
        val fileName = "${entityMetadata.className}Mapper.$extension"
        return Paths.get(sourceRoot, mapperDir, fileName).toString()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["mapperName"] = "${entityMetadata.className}Mapper"
        model["className"] = entityMetadata.className
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["dtoName"] = "${entityMetadata.className}DTO"
        model["packageName"] = packageConfig["mapperPackage"] ?: entityMetadata.mapperPackage

        // ========== PACKAGES POUR LES IMPORTS ==========
        model["domainPackage"] = packageConfig["entityPackage"] ?: entityMetadata.domainPackage
        model["dtoPackage"] = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower
        model["mapperVarName"] = "${entityMetadata.entityNameLower}Mapper"
        model["dtoVarName"] = "${entityMetadata.entityNameLower}DTO"

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // Déterminer si c'est un projet Java ou Kotlin
        val isKotlinProject = currentProject?.let { getFileExtensionForProject(it) == "kt" } ?: false

        // Add mapper-specific data
        val mappings = generateMappings(entityMetadata)
        val usesMappers = collectUsedMappers(entityMetadata)
        val additionalImports = generateAdditionalImports(entityMetadata, packageConfig)
        val mapperMethods = generateMapperMethods(entityMetadata, packageConfig)

        model["mappings"] = mappings
        model["usesMappers"] = usesMappers
        model["additionalImports"] = additionalImports
        model["mapperMethods"] = mapperMethods
        model["customMethods"] = mapperMethods // Pour compatibilité avec les templates existants
        model["isKotlinProject"] = isKotlinProject

        return model
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

    /**
     * Generate field mappings for related entities.
     */
    private fun generateMappings(entityMetadata: EntityMetadata): List<String> {
        val mappings = mutableListOf<String>()

        // Déterminer si c'est un projet Java ou Kotlin
        val isKotlinProject = currentProject?.let { getFileExtensionForProject(it) == "kt" } ?: false

        entityMetadata.fields.forEach { field ->
            when (field.relationType) {
                RelationType.ONE_TO_ONE, RelationType.MANY_TO_ONE -> {
                    val targetName = field.relationTargetSimpleName
                    if (targetName != null) {
                        // Générer les annotations @Mapping pour les relations
                        if (isKotlinProject) {
                            mappings.add("@Mapping(target = \"${field.name}\", source = \"${field.name}\")")
                        } else {
                            mappings.add("@Mapping(target = \"${field.name}\", source = \"${field.name}\")")
                        }
                    }
                }
                RelationType.ONE_TO_MANY, RelationType.MANY_TO_MANY -> {
                    val targetName = field.relationTargetSimpleName
                    if (targetName != null) {
                        // Pour les collections, on peut ignorer le mapping ou utiliser un mapping spécifique
                        if (isKotlinProject) {
                            mappings.add("@Mapping(target = \"${field.name}\", ignore = true)")
                        } else {
                            mappings.add("@Mapping(target = \"${field.name}\", ignore = true)")
                        }
                    }
                }
                else -> {}
            }
        }

        return mappings
    }

    /**
     * Collect names of other mappers that need to be used.
     */
    private fun collectUsedMappers(entityMetadata: EntityMetadata): List<String> {
        val mappers = mutableSetOf<String>()

        // Déterminer si c'est un projet Java ou Kotlin
        val isKotlinProject = currentProject?.let { getFileExtensionForProject(it) == "kt" } ?: false

        entityMetadata.fields.forEach { field ->
            if (field.relationType != RelationType.NONE) {
                val targetName = field.relationTargetSimpleName
                if (targetName != null) {
                    if (isKotlinProject) {
                        mappers.add("${targetName}Mapper::class")
                    } else {
                        mappers.add("${targetName}Mapper.class")
                    }
                }
            }
        }

        return mappers.toList()
    }

    /**
     * Generate additional imports needed for the mapper.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val imports = mutableSetOf<String>()

        // Les imports de base sont déjà dans le template, pas besoin de les ajouter ici
        // pour éviter les doublons

        // Add imports for related entities only
        entityMetadata.fields.forEach { field ->
            if (field.relationType != RelationType.NONE) {
                val targetName = field.relationTargetSimpleName
                if (targetName != null) {
                    val entityPackage = packageConfig["entityPackage"] ?: entityMetadata.domainPackage
                    val dtoPackage = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage
                    imports.add("${entityPackage}.${targetName}")
                    imports.add("${dtoPackage}.${targetName}DTO")
                }
            }
        }

        return if (imports.isNotEmpty()) {
            imports.sorted().joinToString("\n") { "import $it;" }
        } else {
            ""
        }
    }

    /**
     * Generate mapper methods with proper MapStruct annotations.
     * Note: Les méthodes de base sont déjà dans les templates, on génère seulement les méthodes spécifiques ici
     */
    private fun generateMapperMethods(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        // Retourner une chaîne vide car toutes les méthodes de base sont déjà dans les templates
        // Cela évite les doublons de méthodes
        return ""
    }
}

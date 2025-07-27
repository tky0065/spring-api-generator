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

        // Add mapper-specific data
        val mappings = generateMappings(entityMetadata)
        val usesMappers = collectUsedMappers(entityMetadata)
        val additionalImports = generateAdditionalImports(entityMetadata, packageConfig)
        val customMethods = generateCustomMethods(entityMetadata)

        model["mappings"] = mappings
        model["usesMappers"] = usesMappers
        model["additionalImports"] = additionalImports
        model["customMethods"] = customMethods

        return model
    }

    /**
     * Generate field mappings for related entities.
     */
    private fun generateMappings(entityMetadata: EntityMetadata): List<String> {
        val mappings = mutableListOf<String>()

        entityMetadata.fields.forEach { field ->
            when (field.relationType) {
                RelationType.ONE_TO_ONE, RelationType.MANY_TO_ONE -> {
                    val targetName = field.relationTargetSimpleName
                    if (targetName != null) {
                        mappings.add("@Mapping(target = \"${field.name}\", source = \"${field.name}\")")
                    }
                }
                RelationType.ONE_TO_MANY, RelationType.MANY_TO_MANY -> {
                    val targetName = field.relationTargetSimpleName
                    if (targetName != null) {
                        mappings.add("@Mapping(target = \"${field.name}\", source = \"${field.name}\")")
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

        entityMetadata.fields.forEach { field ->
            if (field.relationType != RelationType.NONE) {
                val targetName = field.relationTargetSimpleName
                if (targetName != null) {
                    mappers.add("${targetName}Mapper.class")
                }
            }
        }

        return mappers.toList()
    }

    /**
     * Generate additional imports needed for the mapper.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val imports = mutableListOf<String>()

        // Add imports for related entities
        entityMetadata.fields.forEach { field ->
            if (field.relationType != RelationType.NONE) {
                val targetName = field.relationTargetSimpleName
                if (targetName != null) {
                    val entityPackage = packageConfig["entityPackage"] ?: entityMetadata.domainPackage
                    val dtoPackage = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage
                    imports.add("import ${entityPackage}.${targetName};")
                    imports.add("import ${dtoPackage}.${targetName}DTO;")
                }
            }
        }

        return imports.joinToString("\n")
    }

    /**
     * Generate custom methods for the mapper.
     */
    private fun generateCustomMethods(entityMetadata: EntityMetadata): String {
        // Return empty string for now, can be expanded later
        return ""
    }
}

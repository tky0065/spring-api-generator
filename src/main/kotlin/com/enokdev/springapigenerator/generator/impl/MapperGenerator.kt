package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.RelationType
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for MapStruct mapper interfaces.
 */
class MapperGenerator : AbstractTemplateCodeGenerator("Mapper.java.ft") {

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val mapperPackage = packageConfig["mapperPackage"] ?: entityMetadata.mapperPackage
        val mapperDir = mapperPackage.replace(".", "/")
        val extension = getFileExtensionForProject(project)
        val fileName = "${entityMetadata.mapperName}.$extension"
        return Paths.get(sourceRoot, mapperDir, fileName).toString()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // Add mapper-specific data
        val mappings = generateMappings(entityMetadata)
        val usesMappers = collectUsedMappers(entityMetadata)
        val additionalImports = generateAdditionalImports(entityMetadata)

        model["mappings"] = mappings
        model["usesMappers"] = usesMappers
        model["additionalImports"] = additionalImports

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
     * Generate imports for the mapper.
     */
    private fun generateImports(entityMetadata: EntityMetadata): List<String> {
        val imports = mutableListOf<String>()

        val entityPackage = entityMetadata.domainPackage // On garde le nom de la variable mais il contient maintenant le package entity
        val dtoPackage = entityMetadata.dtoPackage

        imports.add("$entityPackage.${entityMetadata.className}")
        imports.add("$dtoPackage.${entityMetadata.dtoName}")
        imports.add("org.mapstruct.*")

        return imports
    }

    /**
     * Generate additional imports needed for the mapper.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata): String {
        val imports = mutableListOf<String>()

        // Add imports for related entities
        entityMetadata.fields.forEach { field ->
            if (field.relationType != RelationType.NONE) {
                val targetName = field.relationTargetSimpleName
                if (targetName != null) {
                    imports.add("import ${entityMetadata.entityBasePackage}.dto.${targetName}DTO;")
                }
            }
        }

        return imports.joinToString("\n")
    }
}

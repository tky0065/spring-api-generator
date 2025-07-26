package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for REST controllers.
 */
class ControllerGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "Controller.java.ft"
    }

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val controllerPackage = packageConfig["controllerPackage"] ?: entityMetadata.controllerPackage
        val controllerDir = controllerPackage.replace(".", "/")
        val extension = getFileExtensionForProject(project)
        val fileName = "${entityMetadata.controllerName}.$extension"
        return Paths.get(sourceRoot, controllerDir, fileName).toString()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["controllerName"] = entityMetadata.controllerName
        model["className"] = entityMetadata.className
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["serviceName"] = entityMetadata.serviceName
        model["dtoName"] = entityMetadata.dtoName
        model["repositoryName"] = entityMetadata.repositoryName
        model["mapperName"] = entityMetadata.mapperName
        model["packageName"] = packageConfig["controllerPackage"] ?: entityMetadata.controllerPackage

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower
        model["serviceVarName"] = "${entityMetadata.entityNameLower}Service"
        model["repositoryVarName"] = "${entityMetadata.entityNameLower}Repository"
        model["mapperVarName"] = "${entityMetadata.entityNameLower}Mapper"

        // ========== VARIABLES POUR LES API PATHS ==========
        val baseApiPath = formatApiPath(entityMetadata.entityNameLower)
        model["baseApiPath"] = baseApiPath
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // ========== VARIABLES POUR SWAGGER/API DOCUMENTATION ==========
        model["apiTitle"] = "${entityMetadata.className} API"
        model["apiDescription"] = "API for managing ${entityMetadata.className} entities"
        model["apiVersion"] = "1.0.0"

        // ========== IMPORTS ET ENDPOINTS ADDITIONNELS ==========
        val additionalImports = generateAdditionalImports(entityMetadata)
        val additionalEndpoints = generateAdditionalEndpoints(entityMetadata)
        val customMethods = generateCustomMethods(entityMetadata)

        model["additionalImports"] = additionalImports
        model["imports"] = additionalImports
        model["additionalEndpoints"] = additionalEndpoints
        model["customMethods"] = customMethods

        return model
    }

    /**
     * Format the base API path in REST style (e.g., "users" from "User").
     */
    private fun formatApiPath(entityName: String): String {
        return if (entityName.endsWith("y")) {
            // Handle special case: entity -> entities
            "${entityName.substring(0, entityName.length - 1)}ies"
        } else {
            "${entityName}s"
        }
    }

    /**
     * Generate additional imports needed for the controller.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata): String {
        val imports = mutableSetOf<String>()

        // Add imports for DTO and service
        imports.add("${entityMetadata.dtoPackage}.${entityMetadata.dtoName}")
        imports.add("${entityMetadata.servicePackage}.${entityMetadata.serviceName}")

        return imports.joinToString("\n") { "import $it" }
    }

    /**
     * Generate additional endpoints for the controller.
     */
    private fun generateAdditionalEndpoints(entityMetadata: EntityMetadata): String {
        // Generate additional endpoints based on entity fields
        val endpoints = mutableListOf<String>()

        // Add search endpoints if there are string fields
        val stringFields = entityMetadata.fields.filter { it.type == "String" }
        if (stringFields.isNotEmpty()) {
            val firstStringField = stringFields.first()
            endpoints.add("""
    /**
     * Search ${entityMetadata.className} by ${firstStringField.name}.
     */
    @GetMapping("/search")
    fun search${entityMetadata.className}sByName(@RequestParam ${firstStringField.name}: String, pageable: Pageable): ResponseEntity<Page<${entityMetadata.dtoName}>> {
        log.debug("REST request to search ${entityMetadata.className} by ${firstStringField.name}: {}", ${firstStringField.name})
        val page = ${entityMetadata.entityNameLower}Service.findBy${firstStringField.name.replaceFirstChar { it.uppercase() }}(${firstStringField.name}, pageable)
        return ResponseEntity.ok().body(page)
    }
            """.trimIndent())
        }

        return endpoints.joinToString("\n\n")
    }

    /**
     * Generate custom methods for the controller.
     */
    private fun generateCustomMethods(entityMetadata: EntityMetadata): String {
        // Return empty string for now, can be expanded later
        return ""
    }
}

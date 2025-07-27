package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.DependencyValidationService
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for REST controllers.
 */
class ControllerGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "Controller"
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
        val fileName = "${entityMetadata.className}Controller.$extension"
        return Paths.get(sourceRoot, controllerDir, fileName).toString()
    }

    override fun generate(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        // Vérifier et ajouter les dépendances requises si nécessaire
        val features = mapOf(
            "swagger" to true,
            "validation" to true
        )
        DependencyValidationService.validateAndEnsureDependencies(project, features)

        // Générer le code de base via le template
        val baseCode = super.generate(project, entityMetadata, packageConfig)

        // INJECTER L'ANNOTATION @RestController et @RequestMapping directement
        return injectControllerAnnotations(baseCode, entityMetadata)
    }

    /**
     * Injecte les annotations @RestController et @RequestMapping directement dans le code généré
     */
    private fun injectControllerAnnotations(code: String, entityMetadata: EntityMetadata): String {
        val className = "${entityMetadata.className}Controller"

        // Calculer le chemin API de manière sécurisée
        val apiPath = entityMetadata.entityNameLower.lowercase()
            .replace("@rest", entityMetadata.className.lowercase())
            .replace("@", "")
            .trim()
            .takeIf { it.isNotBlank() && !it.contains("@") && !it.contains("{") && !it.contains("}") }
            ?: entityMetadata.className.lowercase()

        // Chercher la déclaration de classe
        val classPattern = Regex("(public\\s+)?class\\s+$className", RegexOption.MULTILINE)

        return classPattern.replace(code) { matchResult ->
            val classDeclaration = matchResult.value

            // Vérifier si les annotations sont déjà présentes
            val beforeClass = code.substring(0, matchResult.range.first)
            val hasRestController = beforeClass.takeLast(300).contains("@RestController")
            val hasRequestMapping = beforeClass.takeLast(300).contains("@RequestMapping")

            if (hasRestController && hasRequestMapping) {
                // Annotations déjà présentes
                classDeclaration
            } else {
                // Injecter les annotations manquantes avec le chemin API corrigé
                val annotations = buildString {
                    if (!hasRestController) append("@RestController\n")
                    if (!hasRequestMapping) append("@RequestMapping(\"/api/$apiPath\")\n")
                }
                "$annotations$classDeclaration"
            }
        }
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["controllerName"] = "${entityMetadata.className}Controller"
        model["className"] = entityMetadata.className
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["serviceName"] = "${entityMetadata.className}Service"
        model["dtoName"] = "${entityMetadata.className}DTO"
        model["repositoryName"] = "${entityMetadata.className}Repository"
        model["mapperName"] = "${entityMetadata.className}Mapper"
        model["packageName"] = packageConfig["controllerPackage"] ?: entityMetadata.controllerPackage

        // ========== PACKAGES POUR LES IMPORTS ==========
        model["dtoPackage"] = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage
        model["servicePackage"] = packageConfig["servicePackage"] ?: entityMetadata.servicePackage
        model["repositoryPackage"] = packageConfig["repositoryPackage"] ?: entityMetadata.repositoryPackage
        model["mapperPackage"] = packageConfig["mapperPackage"] ?: entityMetadata.mapperPackage
        model["domainPackage"] = packageConfig["domainPackage"] ?: entityMetadata.domainPackage

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower
        model["serviceVarName"] = "${entityMetadata.entityNameLower}Service"
        model["repositoryVarName"] = "${entityMetadata.entityNameLower}Repository"
        model["mapperVarName"] = "${entityMetadata.entityNameLower}Mapper"

        // ========== VARIABLES POUR LES API PATHS (CRITIQUES POUR LES ANNOTATIONS) ==========
        val baseApiPath = formatApiPath(entityMetadata.entityNameLower)
        model["baseApiPath"] = baseApiPath

        // S'assurer que entityApiPath ne contient jamais de placeholders non remplacés
        val entityApiPath = entityMetadata.entityNameLower.lowercase()
            .replace("@rest", entityMetadata.className.lowercase())
            .replace("@", "")
            .trim()

        // Validation et fallback pour entityApiPath
        val validEntityApiPath = if (entityApiPath.isBlank() || entityApiPath.contains("@") || entityApiPath.contains("{") || entityApiPath.contains("}")) {
            entityMetadata.className.lowercase()
        } else {
            entityApiPath
        }

        model["entityApiPath"] = validEntityApiPath

        // Log pour debug
        println("DEBUG: Controller generation for ${entityMetadata.className}")
        println("  - entityNameLower: '${entityMetadata.entityNameLower}'")
        println("  - entityApiPath: '$validEntityApiPath'")
        println("  - Generated API path: '/api/$validEntityApiPath'")

        // ========== VARIABLES POUR SWAGGER/API DOCUMENTATION ==========
        model["apiTitle"] = "${entityMetadata.className} API"
        model["apiDescription"] = "API for managing ${entityMetadata.className} entities"
        model["apiVersion"] = "1.0.0"

        // ========== VARIABLES POUR LES ANNOTATIONS (TOUJOURS ACTIVÉES) ==========
        model["hasRestControllerAnnotation"] = true
        model["hasRequestMappingAnnotation"] = true
        model["hasSwaggerDependency"] = true
        model["hasValidationDependency"] = true
        model["hasAutowiredAnnotation"] = true

        // ========== VARIABLES CRITIQUES POUR ÉVITER LES ERREURS FREEMARKER ==========
        // Ces variables sont utilisées dans les templates et DOIVENT être définies
        model["idType"] = extractSimpleTypeName(entityMetadata.idType)
        model["fields"] = entityMetadata.fields
        model["tableName"] = entityMetadata.tableName ?: entityMetadata.entityNameLower

        // ========== IMPORTS ET ENDPOINTS ADDITIONNELS ==========
        val additionalImports = generateAdditionalImports(entityMetadata, packageConfig)
        val additionalEndpoints = generateAdditionalEndpoints(entityMetadata)
        val customMethods = generateCustomMethods(entityMetadata)

        model["additionalImports"] = additionalImports
        model["additionalEndpoints"] = additionalEndpoints
        model["customMethods"] = customMethods

        // ========== VALIDATION DES VARIABLES CRITIQUES ==========
        validateRequiredVariables(model, entityMetadata)

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
    private fun generateAdditionalImports(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val imports = mutableSetOf<String>()

        // Add imports for DTO and Service using the correct packages
        val dtoPackage = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage
        val servicePackage = packageConfig["servicePackage"] ?: entityMetadata.servicePackage

        imports.add("${dtoPackage}.${entityMetadata.className}DTO")
        imports.add("${servicePackage}.${entityMetadata.className}Service")

        return imports.joinToString("\n") { "import $it;" }
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

    /**
     * Validate that all required variables for the Controller template are defined.
     * This prevents FreeMarker from failing to process template sections.
     */
    private fun validateRequiredVariables(model: MutableMap<String, Any>, entityMetadata: EntityMetadata) {
        val requiredVars = listOf(
            "controllerName", "entityName", "entityNameLower", "entityApiPath",
            "serviceName", "dtoName", "packageName", "dtoPackage", "servicePackage",
            "serviceVarName", "idType"
        )

        requiredVars.forEach { varName ->
            if (!model.containsKey(varName) || model[varName] == null) {
                throw RuntimeException("Required template variable '$varName' is missing for entity ${entityMetadata.className}")
            }
        }
    }
}

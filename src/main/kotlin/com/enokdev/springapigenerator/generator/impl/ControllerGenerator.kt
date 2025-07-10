package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for REST controllers.
 */
class ControllerGenerator : AbstractTemplateCodeGenerator("Controller.java.ft") {

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

        // Add controller-specific model data
        val baseApiPath = formatApiPath(entityMetadata.entityNameLower)
        val additionalImports = generateAdditionalImports(entityMetadata)
        val additionalEndpoints = generateAdditionalEndpoints(entityMetadata)

        model["baseApiPath"] = baseApiPath
        model["additionalImports"] = additionalImports
        model["additionalEndpoints"] = additionalEndpoints

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

        // Add common Spring imports
        imports.add("org.springframework.beans.factory.annotation.Autowired")
        imports.add("org.springframework.http.HttpStatus")
        imports.add("org.springframework.http.ResponseEntity")
        imports.add("org.springframework.web.bind.annotation.*")
        imports.add("org.springframework.data.domain.Page")
        imports.add("org.springframework.data.domain.Pageable")

        // Add validation imports
        imports.add("jakarta.validation.Valid")

        // Add java util imports
        imports.add("java.util.List")
        imports.add("java.util.Optional")

        // Add swagger annotations if requested
        imports.add("io.swagger.v3.oas.annotations.Operation")
        imports.add("io.swagger.v3.oas.annotations.Parameter")
        imports.add("io.swagger.v3.oas.annotations.responses.ApiResponse")
        imports.add("io.swagger.v3.oas.annotations.responses.ApiResponses")
        imports.add("io.swagger.v3.oas.annotations.tags.Tag")

        return imports.joinToString("\n") { "import $it;" }
    }

    /**
     * Generate additional endpoint methods for the controller.
     */
    private fun generateAdditionalEndpoints(entityMetadata: EntityMetadata): String {
        val endpoints = StringBuilder()

        // Generate search endpoints for common fields (e.g., name, email)
        entityMetadata.fields.forEach { field ->
            if (field.name != "id" && !field.isCollection) {
                if (field.simpleTypeName == "String" &&
                    (field.name.equals("name", ignoreCase = true) ||
                    field.name.equals("email", ignoreCase = true) ||
                    field.name.equals("username", ignoreCase = true))
                ) {
                    val methodName = "findBy${field.name.replaceFirstChar { it.uppercase() }}"
                    val paramName = field.name
                    val entityNameLower = entityMetadata.entityNameLower

                    endpoints.append("""
                        /**
                         * GET /api/${entityNameLower}s/${paramName}/{${paramName}} : Get ${entityNameLower}s by ${paramName}.
                         *
                         * @param ${paramName} the ${paramName} to search for
                         * @return the ResponseEntity with status 200 (OK) and the ${entityNameLower} in the body
                         */
                        @GetMapping("/${paramName}/{${paramName}}")
                        @Operation(summary = "Find ${entityNameLower} by ${paramName}")
                        @ApiResponses(value = {
                            @ApiResponse(responseCode = "200", description = "Found the ${entityNameLower}"),
                            @ApiResponse(responseCode = "404", description = "${entityNameLower} not found")
                        })
                        public ResponseEntity<${entityMetadata.dtoName}> get${entityMetadata.className}By${field.name.replaceFirstChar { it.uppercase() }}(
                                @PathVariable ${field.simpleTypeName} ${paramName}) {
                            return ${entityNameLower}Service.${methodName}(${paramName})
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
                        }
                        
                    """.trimIndent())
                    endpoints.append("\n")
                }
            }
        }

        // Add a paginated endpoint
        val entityNameLower = entityMetadata.entityNameLower
        endpoints.append("""
            /**
             * GET /api/${entityNameLower}s/paginated : Get paginated list of ${entityNameLower}s.
             *
             * @param pageable the pagination information
             * @return the ResponseEntity with status 200 (OK) and the list of ${entityNameLower}s in body
             */
            @GetMapping("/paginated")
            @Operation(summary = "Get a paginated list of ${entityNameLower}s")
            public ResponseEntity<Page<${entityMetadata.dtoName}>> getAllPaginated(
                    @Parameter(description = "Pageable parameters") Pageable pageable) {
                Page<${entityMetadata.dtoName}> page = ${entityNameLower}Service.findAllPaginated(pageable);
                return ResponseEntity.ok(page);
            }
        """.trimIndent())

        return endpoints.toString()
    }
}

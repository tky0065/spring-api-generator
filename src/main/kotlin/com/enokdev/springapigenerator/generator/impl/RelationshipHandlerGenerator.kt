package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.RelationType
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for relationship handling code.
 * This generator creates additional endpoints and DTOs for managing JPA relationships.
 */
class RelationshipHandlerGenerator : AbstractTemplateCodeGenerator("RelationshipController.java.ft") {

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDir(project)
        val controllerPackage = packageConfig["controllerPackage"] ?: entityMetadata.controllerPackage
        val controllerDir = controllerPackage.replace(".", "/")
        val fileName = "${entityMetadata.className}RelationshipController.java"
        return Paths.get(sourceRoot, controllerDir, fileName).toString()
    }

    override fun generate(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        // Only generate relationship code if the entity has relationships
        if (!hasRelationships(entityMetadata)) {
            return ""  // Return empty string, no file will be created
        }

        return super.generate(project, entityMetadata, packageConfig)
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // Add relationship-specific model data
        val relationshipMethods = generateRelationshipMethods(entityMetadata)
        val additionalImports = generateAdditionalImports(entityMetadata)
        val idType = extractSimpleTypeName(entityMetadata.idType)

        model["relationshipMethods"] = relationshipMethods
        model["additionalImports"] = additionalImports
        model["idType"] = idType
        model["hasToManyRelationships"] = hasToManyRelationships(entityMetadata)
        model["hasToOneRelationships"] = hasToOneRelationships(entityMetadata)

        return model
    }

    /**
     * Check if the entity has any relationships.
     */
    private fun hasRelationships(entityMetadata: EntityMetadata): Boolean {
        return entityMetadata.fields.any { it.relationType != RelationType.NONE }
    }

    /**
     * Check if the entity has any to-many relationships.
     */
    private fun hasToManyRelationships(entityMetadata: EntityMetadata): Boolean {
        return entityMetadata.fields.any {
            it.relationType == RelationType.ONE_TO_MANY || it.relationType == RelationType.MANY_TO_MANY
        }
    }

    /**
     * Check if the entity has any to-one relationships.
     */
    private fun hasToOneRelationships(entityMetadata: EntityMetadata): Boolean {
        return entityMetadata.fields.any {
            it.relationType == RelationType.ONE_TO_ONE || it.relationType == RelationType.MANY_TO_ONE
        }
    }

    /**
     * Generate additional imports needed for the relationship handler.
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

        // Add validation imports
        imports.add("jakarta.validation.Valid")

        // Add java util imports
        imports.add("java.util.List")
        imports.add("java.util.Set")
        imports.add("java.util.stream.Collectors")

        // Add related entity imports
        entityMetadata.fields.forEach { field ->
            if (field.relationType != RelationType.NONE) {
                val targetName = field.relationTargetSimpleName
                if (targetName != null) {
                    imports.add("${entityMetadata.dtoPackage}.${targetName}DTO")
                }
            }
        }

        return imports.joinToString("\n") { "import $it;" }
    }

    /**
     * Generate relationship management methods for controllers.
     */
    private fun generateRelationshipMethods(entityMetadata: EntityMetadata): String {
        val methods = StringBuilder()
        val entityName = entityMetadata.className
        val entityNameLower = entityMetadata.entityNameLower

        // Generate methods for each relationship
        entityMetadata.fields.forEach { field ->
            when (field.relationType) {
                RelationType.ONE_TO_MANY, RelationType.MANY_TO_MANY -> {
                    val relationName = field.name
                    val relationTargetName = field.relationTargetSimpleName ?: "Object"

                    // Get all related items
                    methods.append("""
                        /**
                         * GET /api/${entityNameLower}s/{id}/${relationName} : Get all ${relationName} for a ${entityNameLower}.
                         *
                         * @param id the id of the ${entityNameLower}
                         * @return the ResponseEntity with status 200 (OK) and the list of ${relationName}
                         */
                        @GetMapping("/{id}/${relationName}")
                        public ResponseEntity<Set<${relationTargetName}DTO>> get${entityName}${relationName.replaceFirstChar { it.uppercase() }}(
                                @PathVariable ${entityMetadata.idType} id) {
                            ${entityName}DTO ${entityNameLower} = ${entityNameLower}Service.findOne(id);
                            return ResponseEntity.ok(${entityNameLower}.get${relationName.replaceFirstChar { it.uppercase() }}());
                        }
                        
                    """.trimIndent())

                    // Add relationship
                    methods.append("""
                        /**
                         * POST /api/${entityNameLower}s/{id}/${relationName}/{relationId} : Add a ${relationName} to a ${entityNameLower}.
                         *
                         * @param id the id of the ${entityNameLower}
                         * @param relationId the id of the ${relationTargetName} to add
                         * @return the ResponseEntity with status 200 (OK)
                         */
                        @PostMapping("/{id}/${relationName}/{relationId}")
                        public ResponseEntity<Void> add${relationName.replaceFirstChar { it.uppercase() }}To${entityName}(
                                @PathVariable ${entityMetadata.idType} id,
                                @PathVariable ${entityMetadata.idType} relationId) {
                            ${entityNameLower}Service.add${relationName.replaceFirstChar { it.uppercase() }}(id, relationId);
                            return ResponseEntity.ok().build();
                        }
                        
                    """.trimIndent())

                    // Remove relationship
                    methods.append("""
                        /**
                         * DELETE /api/${entityNameLower}s/{id}/${relationName}/{relationId} : Remove a ${relationName} from a ${entityNameLower}.
                         *
                         * @param id the id of the ${entityNameLower}
                         * @param relationId the id of the ${relationTargetName} to remove
                         * @return the ResponseEntity with status 200 (OK)
                         */
                        @DeleteMapping("/{id}/${relationName}/{relationId}")
                        public ResponseEntity<Void> remove${relationName.replaceFirstChar { it.uppercase() }}From${entityName}(
                                @PathVariable ${entityMetadata.idType} id,
                                @PathVariable ${entityMetadata.idType} relationId) {
                            ${entityNameLower}Service.remove${relationName.replaceFirstChar { it.uppercase() }}(id, relationId);
                            return ResponseEntity.ok().build();
                        }
                        
                    """.trimIndent())
                }

                RelationType.MANY_TO_ONE, RelationType.ONE_TO_ONE -> {
                    val relationName = field.name
                    val relationTargetName = field.relationTargetSimpleName ?: "Object"

                    // Get related item
                    methods.append("""
                        /**
                         * GET /api/${entityNameLower}s/{id}/${relationName} : Get the ${relationName} for a ${entityNameLower}.
                         *
                         * @param id the id of the ${entityNameLower}
                         * @return the ResponseEntity with status 200 (OK) and the ${relationName}
                         */
                        @GetMapping("/{id}/${relationName}")
                        public ResponseEntity<${relationTargetName}DTO> get${entityName}${relationName.replaceFirstChar { it.uppercase() }}(
                                @PathVariable ${entityMetadata.idType} id) {
                            ${entityName}DTO ${entityNameLower} = ${entityNameLower}Service.findOne(id);
                            return ResponseEntity.ok(${entityNameLower}.get${relationName.replaceFirstChar { it.uppercase() }}());
                        }
                        
                    """.trimIndent())

                    // Set relationship - cette méthode doit appeler set${relationName.replaceFirstChar { it.uppercase() }}
                    methods.append("""
                        /**
                         * PUT /api/${entityNameLower}s/{id}/${relationName}/{relationId} : Set the ${relationName} for a ${entityNameLower}.
                         *
                         * @param id the id of the ${entityNameLower}
                         * @param relationId the id of the ${relationTargetName} to set
                         * @return the ResponseEntity with status 200 (OK)
                         */
                        @PutMapping("/{id}/${relationName}/{relationId}")
                        public ResponseEntity<Void> set${entityName}${relationName.replaceFirstChar { it.uppercase() }}(
                                @PathVariable ${entityMetadata.idType} id,
                                @PathVariable ${entityMetadata.idType} relationId) {
                            ${entityNameLower}Service.set${relationName.replaceFirstChar { it.uppercase() }}(id, relationId);
                            return ResponseEntity.ok().build();
                        }
                        
                    """.trimIndent())

                    // Remove relationship - cette méthode doit appeler remove${relationName.replaceFirstChar { it.uppercase() }}
                    methods.append("""
                        /**
                         * DELETE /api/${entityNameLower}s/{id}/${relationName} : Remove the ${relationName} from a ${entityNameLower}.
                         *
                         * @param id the id of the ${entityNameLower}
                         * @return the ResponseEntity with status 200 (OK)
                         */
                        @DeleteMapping("/{id}/${relationName}")
                        public ResponseEntity<Void> remove${entityName}${relationName.replaceFirstChar { it.uppercase() }}(
                                @PathVariable ${entityMetadata.idType} id) {
                            ${entityNameLower}Service.remove${relationName.replaceFirstChar { it.uppercase() }}(id);
                            return ResponseEntity.ok().build();
                        }
                        
                    """.trimIndent())
                }

                else -> {}
            }
        }

        return methods.toString()
    }
}

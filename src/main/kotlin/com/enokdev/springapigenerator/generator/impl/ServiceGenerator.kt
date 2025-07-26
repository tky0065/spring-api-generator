package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.RelationType
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for service interfaces and implementations.
 */
class ServiceGenerator : AbstractTemplateCodeGenerator() {

    private val serviceImplGenerator = ServiceImplGenerator()

    override fun getBaseTemplateName(): String {
        return "Service.java.ft"
    }

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val servicePackage = packageConfig["servicePackage"] ?: entityMetadata.servicePackage
        val serviceDir = servicePackage.replace(".", "/")
        val extension = getFileExtensionForProject(project)
        val fileName = "${entityMetadata.serviceName}.$extension"
        return Paths.get(sourceRoot, serviceDir, fileName).toString()
    }

    override fun generate(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        // Generate the service interface
        val interfaceCode = super.generate(project, entityMetadata, packageConfig)

        // Also generate the service implementation
        serviceImplGenerator.generate(project, entityMetadata, packageConfig)

        return interfaceCode
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["serviceName"] = entityMetadata.serviceName
        model["serviceImplName"] = entityMetadata.serviceImplName
        model["className"] = entityMetadata.className
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["dtoName"] = entityMetadata.dtoName
        model["repositoryName"] = entityMetadata.repositoryName
        model["mapperName"] = entityMetadata.mapperName
        model["packageName"] = packageConfig["servicePackage"] ?: entityMetadata.servicePackage

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower
        model["serviceVarName"] = "${entityMetadata.entityNameLower}Service"
        model["repositoryVarName"] = "${entityMetadata.entityNameLower}Repository"
        model["mapperVarName"] = "${entityMetadata.entityNameLower}Mapper"

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // Add service-specific model data
        val additionalImports = generateAdditionalImports(entityMetadata)
        val additionalMethods = generateAdditionalMethods(entityMetadata)
        val customMethods = generateCustomMethods(entityMetadata)

        model["additionalImports"] = additionalImports
        model["imports"] = additionalImports
        model["additionalMethods"] = additionalMethods
        model["customMethods"] = customMethods

        return model
    }

    /**
     * Generate additional imports needed for the service interface.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata): String {
        val imports = mutableSetOf<String>()

        // Add imports for DTO
        imports.add("${entityMetadata.dtoPackage}.${entityMetadata.dtoName}")

        // Add common imports
        imports.add("java.util.List")
        imports.add("java.util.Optional")

        return imports.joinToString("\n") { "import $it;" }
    }

    /**
     * Generate additional service methods based on entity fields.
     */
    private fun generateAdditionalMethods(entityMetadata: EntityMetadata): String {
        val methods = StringBuilder()

        // Generate find methods for common fields (e.g., name, email)
        entityMetadata.fields.forEach { field ->
            if (field.name != "id" && !field.isCollection) {
                if (field.simpleTypeName == "String" &&
                    (field.name.equals("name", ignoreCase = true) ||
                    field.name.equals("email", ignoreCase = true) ||
                    field.name.equals("username", ignoreCase = true))
                ) {
                    val methodName = "findBy${field.name.replaceFirstChar { it.uppercase() }}"
                    methods.append("""
                        /**
                         * Find a ${entityMetadata.entityNameLower} by ${field.name}.
                         *
                         * @param ${field.name} the ${field.name} to search for
                         * @return the entity DTO
                         */
                        Optional<${entityMetadata.dtoName}> $methodName(${field.simpleTypeName} ${field.name});
                        
                    """.trimIndent())
                    methods.append("\n")
                }
            }
        }

        // Generate relationship management methods
        entityMetadata.fields.forEach { field ->
            when (field.relationType) {
                RelationType.ONE_TO_MANY, RelationType.MANY_TO_MANY -> {
                    val relationName = field.name
                    val relationTargetName = field.relationTargetSimpleName ?: "Object"
                    val idType = entityMetadata.idType.substringAfterLast(".")

                    // Add related entity
                    methods.append("""
                        /**
                         * Add a ${relationName} to a ${entityMetadata.entityNameLower}.
                         *
                         * @param id the id of the ${entityMetadata.entityNameLower}
                         * @param relationId the id of the ${relationTargetName} to add
                         * @return the updated entity
                         */
                        ${entityMetadata.dtoName} add${relationName.replaceFirstChar { it.uppercase() }}($idType id, $idType relationId);
                        
                    """.trimIndent())
                    methods.append("\n")

                    // Remove related entity
                    methods.append("""
                        /**
                         * Remove a ${relationName} from a ${entityMetadata.entityNameLower}.
                         *
                         * @param id the id of the ${entityMetadata.entityNameLower}
                         * @param relationId the id of the ${relationTargetName} to remove
                         * @return the updated entity
                         */
                        ${entityMetadata.dtoName} remove${relationName.replaceFirstChar { it.uppercase() }}($idType id, $idType relationId);
                        
                    """.trimIndent())
                    methods.append("\n")
                }

                RelationType.MANY_TO_ONE, RelationType.ONE_TO_ONE -> {
                    val relationName = field.name
                    val relationTargetName = field.relationTargetSimpleName ?: "Object"
                    val idType = entityMetadata.idType.substringAfterLast(".")

                    // Set related entity
                    methods.append("""
                        /**
                         * Set the ${relationName} for a ${entityMetadata.entityNameLower}.
                         *
                         * @param id the id of the ${entityMetadata.entityNameLower}
                         * @param relationId the id of the ${relationTargetName} to set
                         * @return the updated entity
                         */
                        ${entityMetadata.dtoName} set${relationName.replaceFirstChar { it.uppercase() }}($idType id, $idType relationId);
                        
                    """.trimIndent())
                    methods.append("\n")

                    // Remove related entity
                    methods.append("""
                        /**
                         * Remove the ${relationName} from a ${entityMetadata.entityNameLower}.
                         *
                         * @param id the id of the ${entityMetadata.entityNameLower}
                         * @return the updated entity
                         */
                        ${entityMetadata.dtoName} remove${relationName.replaceFirstChar { it.uppercase() }}($idType id);
                        
                    """.trimIndent())
                    methods.append("\n")
                }
                else -> {}
            }
        }

        return methods.toString()
    }

    /**
     * Generate custom service methods for specific business logic.
     */
    private fun generateCustomMethods(entityMetadata: EntityMetadata): String {
        val methods = StringBuilder()

        // Generate pagination methods
        methods.append("""
            /**
             * Find all ${entityMetadata.entityNameLower}s with pagination.
             *
             * @param page the page number (0-based)
             * @param size the page size
             * @return a page of entity DTOs
             */
            Page<${entityMetadata.dtoName}> findAllPaginated(int page, int size);
            
        """.trimIndent())
        methods.append("\n")

        // Generate search methods if entity has string fields
        val stringFields = entityMetadata.fields.filter {
            it.simpleTypeName == "String" && it.name != "id" && !it.isCollection
        }

        if (stringFields.isNotEmpty()) {
            methods.append("""
                /**
                 * Search ${entityMetadata.entityNameLower}s by keyword.
                 *
                 * @param keyword the search keyword
                 * @return list of matching entity DTOs
                 */
                List<${entityMetadata.dtoName}> searchByKeyword(String keyword);
                
            """.trimIndent())
            methods.append("\n")
        }

        // Generate count methods
        methods.append("""
            /**
             * Count total number of ${entityMetadata.entityNameLower}s.
             *
             * @return the total count
             */
            long count();
            
        """.trimIndent())
        methods.append("\n")

        // Generate existence check methods
        methods.append("""
            /**
             * Check if a ${entityMetadata.entityNameLower} exists by id.
             *
             * @param id the id to check
             * @return true if exists, false otherwise
             */
            boolean existsById(${entityMetadata.idType.substringAfterLast(".")} id);
            
        """.trimIndent())
        methods.append("\n")

        // Generate bulk operations if applicable
        methods.append("""
            /**
             * Delete multiple ${entityMetadata.entityNameLower}s by ids.
             *
             * @param ids the list of ids to delete
             */
            void deleteByIds(List<${entityMetadata.idType.substringAfterLast(".")}> ids);
            
        """.trimIndent())
        methods.append("\n")

        return methods.toString()
    }

    /**
     * Helper class to generate service implementation.
     */
    private inner class ServiceImplGenerator : AbstractTemplateCodeGenerator() {

        override fun getBaseTemplateName(): String {
            return "ServiceImpl.java.ft"
        }

        override fun getTargetFilePath(
            project: Project,
            entityMetadata: EntityMetadata,
            packageConfig: Map<String, String>
        ): String {
            val sourceRoot = getSourceRootDirForProject(project)
            val serviceImplPackage = packageConfig["serviceImplPackage"]
                ?: "${entityMetadata.servicePackage}.impl"
            val serviceImplDir = serviceImplPackage.replace(".", "/")
            val extension = getFileExtensionForProject(project)
            val fileName = "${entityMetadata.serviceImplName}.$extension"
            return Paths.get(sourceRoot, serviceImplDir, fileName).toString()
        }

        override fun createDataModel(
            entityMetadata: EntityMetadata,
            packageConfig: Map<String, String>
        ): MutableMap<String, Any> {
            val model = super.createDataModel(entityMetadata, packageConfig)

            // Add service impl specific data
            val serviceImplPackage = packageConfig["serviceImplPackage"]
                ?: "${entityMetadata.servicePackage}.impl"
            val additionalImports = generateAdditionalImportsForImpl(entityMetadata)
            val additionalMethods = generateAdditionalMethodsForImpl(entityMetadata)

            model["serviceImplPackage"] = serviceImplPackage
            model["additionalImports"] = additionalImports
            model["additionalMethods"] = additionalMethods

            return model
        }

        /**
         * Generate additional imports for service implementation.
         */
        private fun generateAdditionalImportsForImpl(entityMetadata: EntityMetadata): String {
            val imports = mutableSetOf<String>()

            // Add imports for entity, DTO, repository, mapper, etc.
            imports.add("${entityMetadata.domainPackage}.${entityMetadata.className}")
            imports.add("${entityMetadata.dtoPackage}.${entityMetadata.dtoName}")
            imports.add("${entityMetadata.repositoryPackage}.${entityMetadata.repositoryName}")
            imports.add("${entityMetadata.mapperPackage}.${entityMetadata.mapperName}")
            imports.add("${entityMetadata.servicePackage}.${entityMetadata.serviceName}")

            // Add common imports
            imports.add("org.springframework.beans.factory.annotation.Autowired")
            imports.add("org.springframework.stereotype.Service")
            imports.add("org.springframework.transaction.annotation.Transactional")
            imports.add("java.util.List")
            imports.add("java.util.Optional")
            imports.add("java.util.stream.Collectors")

            return imports.joinToString("\n") { "import $it;" }
        }

        /**
         * Generate additional methods implementations.
         */
        private fun generateAdditionalMethodsForImpl(entityMetadata: EntityMetadata): String {
            val methods = StringBuilder()

            // Generate implementations for find methods
            entityMetadata.fields.forEach { field ->
                if (field.name != "id" && !field.isCollection) {
                    if (field.simpleTypeName == "String" &&
                        (field.name.equals("name", ignoreCase = true) ||
                        field.name.equals("email", ignoreCase = true) ||
                        field.name.equals("username", ignoreCase = true))
                    ) {
                        val methodName = "findBy${field.name.replaceFirstChar { it.uppercase() }}"
                        methods.append("""
                            @Override
                            @Transactional(readOnly = true)
                            public Optional<${entityMetadata.dtoName}> $methodName(${field.simpleTypeName} ${field.name}) {
                                return ${entityMetadata.entityNameLower}Repository.$methodName(${field.name})
                                    .map(${entityMetadata.entityNameLower}Mapper::toDto);
                            }
                            
                        """.trimIndent())
                        methods.append("\n")
                    }
                }
            }

            // Generate relationship management method implementations
            entityMetadata.fields.forEach { field ->
                when (field.relationType) {
                    RelationType.ONE_TO_MANY, RelationType.MANY_TO_MANY -> {
                        val relationName = field.name
                        val relationTargetName = field.relationTargetSimpleName ?: "Object"
                        val relationTargetField = relationTargetName.replaceFirstChar { it.lowercase() }
                        val idType = entityMetadata.idType.substringAfterLast(".")
                        val entityNameLower = entityMetadata.entityNameLower
                        val entityName = entityMetadata.className

                        // Add related entity
                        methods.append("""
                            @Override
                            public ${entityMetadata.dtoName} add${relationName.replaceFirstChar { it.uppercase() }}($idType id, $idType relationId) {
                                log.debug("Request to add ${relationTargetName} {} to ${entityName} {}", relationId, id);
                                
                                ${entityName} ${entityNameLower} = ${entityNameLower}Repository.findById(id)
                                    .orElseThrow(() -> new RuntimeException("${entityName} not found with id " + id));
                                
                                // Assuming a repository for the related entity exists
                                // This should be injected properly in a real implementation
                                ${relationTargetName} ${relationTargetField} = ${relationTargetField}Repository.findById(relationId)
                                    .orElseThrow(() -> new RuntimeException("${relationTargetName} not found with id " + relationId));
                                
                                ${entityNameLower}.get${relationName.replaceFirstChar { it.uppercase() }}().add(${relationTargetField});
                                ${entityNameLower} = ${entityNameLower}Repository.save(${entityNameLower});
                                
                                return ${entityNameLower}Mapper.toDto(${entityNameLower});
                            }
                            
                        """.trimIndent())
                        methods.append("\n")

                        // Remove related entity
                        methods.append("""
                            @Override
                            public ${entityMetadata.dtoName} remove${relationName.replaceFirstChar { it.uppercase() }}($idType id, $idType relationId) {
                                log.debug("Request to remove ${relationTargetName} {} from ${entityName} {}", relationId, id);
                                
                                ${entityName} ${entityNameLower} = ${entityNameLower}Repository.findById(id)
                                    .orElseThrow(() -> new RuntimeException("${entityName} not found with id " + id));
                                
                                // Assuming a repository for the related entity exists
                                // This should be injected properly in a real implementation
                                ${relationTargetName} ${relationTargetField} = ${relationTargetField}Repository.findById(relationId)
                                    .orElseThrow(() -> new RuntimeException("${relationTargetName} not found with id " + relationId));
                                
                                ${entityNameLower}.get${relationName.replaceFirstChar { it.uppercase() }}().remove(${relationTargetField});
                                ${entityNameLower} = ${entityNameLower}Repository.save(${entityNameLower});
                                
                                return ${entityNameLower}Mapper.toDto(${entityNameLower});
                            }
                            
                        """.trimIndent())
                        methods.append("\n")
                    }

                    RelationType.MANY_TO_ONE, RelationType.ONE_TO_ONE -> {
                        val relationName = field.name
                        val relationTargetName = field.relationTargetSimpleName ?: "Object"
                        val relationTargetField = relationTargetName.replaceFirstChar { it.lowercase() }
                        val idType = entityMetadata.idType.substringAfterLast(".")
                        val entityNameLower = entityMetadata.entityNameLower
                        val entityName = entityMetadata.className

                        // Set related entity
                        methods.append("""
                            @Override
                            public ${entityMetadata.dtoName} set${relationName.replaceFirstChar { it.uppercase() }}($idType id, $idType relationId) {
                                log.debug("Request to set ${relationTargetName} {} for ${entityName} {}", relationId, id);
                                
                                ${entityName} ${entityNameLower} = ${entityNameLower}Repository.findById(id)
                                    .orElseThrow(() -> new RuntimeException("${entityName} not found with id " + id));
                                
                                // Assuming a repository for the related entity exists
                                // This should be injected properly in a real implementation
                                ${relationTargetName} ${relationTargetField} = ${relationTargetField}Repository.findById(relationId)
                                    .orElseThrow(() -> new RuntimeException("${relationTargetName} not found with id " + relationId));
                                
                                ${entityNameLower}.set${relationName.replaceFirstChar { it.uppercase() }}(${relationTargetField});
                                ${entityNameLower} = ${entityNameLower}Repository.save(${entityNameLower});
                                
                                return ${entityNameLower}Mapper.toDto(${entityNameLower});
                            }
                            
                        """.trimIndent())
                        methods.append("\n")

                        // Remove related entity
                        methods.append("""
                            @Override
                            public ${entityMetadata.dtoName} remove${relationName.replaceFirstChar { it.uppercase() }}($idType id) {
                                log.debug("Request to remove ${relationTargetName} from ${entityName} {}", id);
                                
                                ${entityName} ${entityNameLower} = ${entityNameLower}Repository.findById(id)
                                    .orElseThrow(() -> new RuntimeException("${entityName} not found with id " + id));
                                
                                ${entityNameLower}.set${relationName.replaceFirstChar { it.uppercase() }}(null);
                                ${entityNameLower} = ${entityNameLower}Repository.save(${entityNameLower});
                                
                                return ${entityNameLower}Mapper.toDto(${entityNameLower});
                            }
                            
                        """.trimIndent())
                        methods.append("\n")
                    }
                    else -> {}
                }
            }

            return methods.toString()
        }
    }
}

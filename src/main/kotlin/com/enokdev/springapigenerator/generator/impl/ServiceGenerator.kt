package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.RelationType
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Paths

/**
 * Generator for service interfaces and implementations.
 */
class ServiceGenerator : AbstractTemplateCodeGenerator("Service.java.ft") {

    private val serviceImplGenerator = ServiceImplGenerator()

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

    override fun generate(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        // Generate both interface and implementation
        val serviceInterface = super.generate(project, entityMetadata, packageConfig)

        // Also generate the implementation class (ServiceImpl)
        val serviceImpl = serviceImplGenerator.generate(project, entityMetadata, packageConfig)

        // Write the ServiceImpl to file
        val serviceImplPath = serviceImplGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
        File(serviceImplPath).also {
            it.parentFile.mkdirs()
            it.writeText(serviceImpl)
        }

        // Return the interface content (implementation will be saved separately)
        return serviceInterface
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // Add service-specific model data
        val additionalImports = generateAdditionalImports(entityMetadata)
        val additionalMethods = generateAdditionalMethods(entityMetadata)

        model["additionalImports"] = additionalImports
        model["additionalMethods"] = additionalMethods

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
     * Helper class to generate service implementation.
     */
    private inner class ServiceImplGenerator : AbstractTemplateCodeGenerator("ServiceImpl.java.ft") {

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

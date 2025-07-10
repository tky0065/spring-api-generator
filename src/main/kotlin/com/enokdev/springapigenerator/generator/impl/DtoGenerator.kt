package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityField
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.RelationType
import com.intellij.openapi.project.Project
import java.nio.file.Paths
import java.util.*

/**
 * Generator for Data Transfer Objects (DTOs).
 */
class DtoGenerator : AbstractTemplateCodeGenerator("DTO.java.ft") {

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val dtoPackage = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage
        val dtoDir = dtoPackage.replace(".", "/")
        val extension = getFileExtensionForProject(project)
        val fileName = "${entityMetadata.className}DTO.$extension"
        return Paths.get(sourceRoot, dtoDir, fileName).toString()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // Add DTO-specific model data
        val dtoFields = generateDtoFields(entityMetadata)
        val importStatements = generateImportStatements(entityMetadata)
        val constructors = generateConstructors(entityMetadata)
        val gettersAndSetters = generateGettersAndSetters(entityMetadata)
        val toStringAttributes = generateToStringAttributes(entityMetadata)

        model["fields"] = dtoFields
        model["importStatements"] = importStatements
        model["constructors"] = constructors
        model["gettersAndSetters"] = gettersAndSetters
        model["toStringAttributes"] = toStringAttributes

        return model
    }

    /**
     * Generate field declarations for the DTO.
     */
    private fun generateDtoFields(entityMetadata: EntityMetadata): String {
        val fields = StringBuilder()

        entityMetadata.fields.forEach { field ->
            // Skip static and transient fields
            val fieldType = getFieldTypeForDto(field)

            // Add validation annotations if needed
            if (!field.nullable) {
                fields.append("    @jakarta.validation.constraints.NotNull\n")
            }

            fields.append("    private $fieldType ${field.name};\n\n")
        }

        return fields.toString()
    }

    /**
     * Determine the appropriate field type for the DTO.
     */
    private fun getFieldTypeForDto(field: EntityField): String {
        return when (field.relationType) {
            // For to-many relationships, use DTOs in collections
            RelationType.ONE_TO_MANY, RelationType.MANY_TO_MANY -> {
                val elementType = field.relationTargetSimpleName ?: "Object"
                "java.util.Set<${elementType}DTO>"
            }
            // For to-one relationships, reference by ID or use DTO
            RelationType.ONE_TO_ONE, RelationType.MANY_TO_ONE -> {
                val elementType = field.relationTargetSimpleName ?: "Object"
                "${elementType}DTO"
            }
            // For regular fields, use the original type
            else -> field.simpleTypeName
        }
    }

    /**
     * Generate necessary import statements.
     */
    private fun generateImportStatements(entityMetadata: EntityMetadata): String {
        val imports = HashSet<String>()

        // Add standard imports
        imports.add("jakarta.validation.constraints.*")

        // Add imports for field types
        entityMetadata.fields.forEach { field ->
            // Skip primitive types and java.lang.* types
            if (!field.isPrimitiveType && !field.type.startsWith("java.lang")) {
                if (field.relationType != RelationType.NONE) {
                    // For collections, we need the collection type
                    if (field.isCollection) {
                        imports.add("java.util.Set")
                    }

                    // Add import for DTO of the related entity
                    val dtoPackage = entityMetadata.dtoPackage
                    val relationTargetSimpleName = field.relationTargetSimpleName
                    if (relationTargetSimpleName != null) {
                        imports.add("$dtoPackage.${relationTargetSimpleName}DTO")
                    }
                } else {
                    // For regular fields, import the type if needed
                    if (!field.isSimpleType) {
                        imports.add(field.type)
                    } else if (field.type.startsWith("java.time") ||
                               field.type.startsWith("java.math") ||
                               field.type.startsWith("java.util") && field.type != "java.util.Set") {
                        imports.add(field.type)
                    }
                }
            }
        }

        return imports.joinToString("\n") { "import $it;" }
    }

    /**
     * Generate constructors for the DTO.
     */
    private fun generateConstructors(entityMetadata: EntityMetadata): String {
        // Using Lombok @NoArgsConstructor, @AllArgsConstructor, and @Builder
        // so we don't need to generate constructors manually
        return ""
    }

    /**
     * Generate getters and setters for the DTO.
     */
    private fun generateGettersAndSetters(entityMetadata: EntityMetadata): String {
        // Using Lombok @Data so we don't need to generate getters and setters manually
        return ""
    }

    /**
     * Generate toString attributes.
     */
    private fun generateToStringAttributes(entityMetadata: EntityMetadata): String {
        val attributes = StringBuilder()

        entityMetadata.fields.forEachIndexed { index, field ->
            attributes.append("            \"${field.name}=\" + ${field.name}")
            if (index < entityMetadata.fields.size - 1) {
                attributes.append(" + \", \" +\n")
            } else {
                attributes.append(" +\n")
            }
        }

        return attributes.toString()
    }
}

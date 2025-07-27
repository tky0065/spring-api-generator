package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityField
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.RelationType
import com.enokdev.springapigenerator.service.DependencyValidationService
import com.intellij.openapi.project.Project
import java.nio.file.Paths
import java.util.*

/**
 * Generator for Data Transfer Objects (DTOs).
 * Supports both Java and Kotlin with appropriate syntax and features.
 */
class DtoGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "DTO"
    }

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

    override fun generate(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        // Vérifier et ajouter les dépendances de validation si nécessaire
        val features = mapOf("validation" to true)
        DependencyValidationService.validateAndEnsureDependencies(project, features)

        // Appeler la méthode parent
        return super.generate(project, entityMetadata, packageConfig)
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["dtoName"] = "${entityMetadata.className}DTO"
        model["className"] = entityMetadata.className
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["packageName"] = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage
        model["idType"] = extractSimpleTypeName(entityMetadata.idType)

        // ========== PACKAGES POUR LES IMPORTS ==========
        model["domainPackage"] = packageConfig["domainPackage"] ?: entityMetadata.domainPackage
        model["entityPackage"] = packageConfig["domainPackage"] ?: entityMetadata.domainPackage
        model["dtoPackage"] = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower
        model["dtoVarName"] = "${entityMetadata.entityNameLower}DTO"

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // Add DTO-specific model data
        val additionalImports = generateAdditionalImports(entityMetadata, packageConfig)
        val kotlinFields = generateKotlinFields(entityMetadata)
        val javaFields = generateJavaFields(entityMetadata)
        val customMethods = generateCustomMethods(entityMetadata)

        model["additionalImports"] = additionalImports
        model["imports"] = additionalImports
        model["dtoFields"] = kotlinFields  // For Kotlin templates
        model["kotlinFields"] = kotlinFields
        model["javaFields"] = javaFields   // For Java templates
        model["customMethods"] = customMethods

        // Add validation flags
        model["hasValidation"] = entityMetadata.fields.any { !it.nullable }

        return model
    }

    /**
     * Generate additional imports needed for the DTO based on field types and relationships.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val imports = mutableSetOf<String>()

        // Note: Basic imports (validation, serializable) are handled in templates
        // Only add specific imports based on field types

        entityMetadata.fields.forEach { field ->
            when {
                field.relationType != RelationType.NONE -> {
                    // For relationships, add DTO imports
                    if (field.isCollection) {
                        // Collections are handled in templates
                    } else {
                        val relationTargetSimpleName = field.relationTargetSimpleName
                        if (relationTargetSimpleName != null) {
                            val dtoPackage = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage
                            imports.add("${dtoPackage}.${relationTargetSimpleName}DTO")
                        }
                    }
                }
                field.type.startsWith("java.time") -> {
                    imports.add(field.type)
                }
                field.type.startsWith("java.math") -> {
                    imports.add(field.type)
                }
                field.type.startsWith("java.util") && field.type != "java.util.Set" -> {
                    imports.add(field.type)
                }
            }
        }

        return if (imports.isNotEmpty()) {
            imports.joinToString("\n") {
                if (it.endsWith("DTO")) "import $it" else "import $it"
            }
        } else {
            ""
        }
    }

    /**
     * Generate DTO fields with Kotlin syntax.
     */
    private fun generateKotlinFields(entityMetadata: EntityMetadata): String {
        val fields = StringBuilder()

        entityMetadata.fields.forEachIndexed { index, field ->
            val fieldType = getFieldTypeForDto(field)
            val isLast = index == entityMetadata.fields.size - 1

            // Add validation annotations for non-nullable fields
            if (!field.nullable) {
                when {
                    fieldType.contains("String") -> {
                        fields.append("    @field:NotNull\n")
                        fields.append("    @field:NotBlank\n")
                    }
                    fieldType.contains("DTO") || !field.isSimpleType -> {
                        fields.append("    @field:NotNull\n")
                    }
                    else -> {
                        fields.append("    @field:NotNull\n")
                    }
                }
            }

            // Add field declaration (Kotlin syntax)
            val nullableSuffix = if (field.nullable) "? = null" else ""
            fields.append("    val ${field.name}: $fieldType$nullableSuffix")

            // Add comma if not the last field (for Kotlin data class)
            if (!isLast) {
                fields.append(",")
            }
            fields.append("\n")
        }

        return fields.toString()
    }

    /**
     * Generate DTO fields with Java syntax.
     */
    private fun generateJavaFields(entityMetadata: EntityMetadata): String {
        val fields = StringBuilder()

        entityMetadata.fields.forEach { field ->
            val fieldType = getJavaFieldType(field)

            // Add validation annotations for non-nullable fields
            if (!field.nullable) {
                when {
                    fieldType.contains("String") -> {
                        fields.append("    @NotNull\n")
                        fields.append("    @NotBlank\n")
                    }
                    fieldType.contains("DTO") || !field.isSimpleType -> {
                        fields.append("    @NotNull\n")
                    }
                    else -> {
                        fields.append("    @NotNull\n")
                    }
                }
            }

            // Add field declaration (Java syntax)
            fields.append("    private $fieldType ${field.name};\n\n")
        }

        return fields.toString()
    }

    /**
     * Get the appropriate Java type for a field in the DTO.
     */
    private fun getJavaFieldType(field: EntityField): String {
        return when (field.relationType) {
            RelationType.NONE -> {
                getJavaSimpleFieldType(field)
            }
            RelationType.ONE_TO_ONE, RelationType.MANY_TO_ONE -> {
                "${field.relationTargetSimpleName}DTO"
            }
            RelationType.ONE_TO_MANY, RelationType.MANY_TO_MANY -> {
                "Set<${field.relationTargetSimpleName}DTO>"
            }
            RelationType.EMBEDDED -> {
                field.relationTargetSimpleName ?: getJavaSimpleFieldType(field)
            }
            RelationType.INHERITANCE -> {
                "${field.relationTargetSimpleName}DTO"
            }
            RelationType.COMPOSITION -> {
                "${field.relationTargetSimpleName}DTO"
            }
        }
    }

    /**
     * Get the simple Java field type.
     */
    private fun getJavaSimpleFieldType(field: EntityField): String {
        return when {
            field.type.contains("String") -> "String"
            field.type.contains("Long") -> "Long"
            field.type.contains("Integer") || field.type.contains("int") -> "Integer"
            field.type.contains("Boolean") || field.type.contains("boolean") -> "Boolean"
            field.type.contains("Double") || field.type.contains("double") -> "Double"
            field.type.contains("Float") || field.type.contains("float") -> "Float"
            field.type.contains("BigDecimal") -> "BigDecimal"
            field.type.contains("LocalDate") -> "LocalDate"
            field.type.contains("LocalDateTime") -> "LocalDateTime"
            field.type.contains("LocalTime") -> "LocalTime"
            field.type.contains("ZonedDateTime") -> "ZonedDateTime"
            field.type.contains("Instant") -> "Instant"
            field.type.contains("UUID") -> "UUID"
            field.type.contains("byte[]") || field.type.contains("Byte[]") -> "byte[]"
            else -> {
                // Extract the simple class name from fully qualified class name
                field.type.substringAfterLast(".")
            }
        }
    }

    /**
     * Generate constructors for the DTO (mainly for Java).
     */
    private fun generateConstructors(entityMetadata: EntityMetadata): String {
        // For Kotlin data classes, constructors are auto-generated
        // For Java, we rely on Lombok annotations
        return ""
    }

    /**
     * Generate custom methods for the DTO.
     */
    private fun generateCustomMethods(entityMetadata: EntityMetadata): String {
        val methods = StringBuilder()

        // Add utility methods for common operations
        methods.append("""
    /**
     * Create a copy of this DTO with updated values.
     * Useful for partial updates.
     */
    fun copy(
""")

        entityMetadata.fields.forEachIndexed { index, field ->
            val fieldType = getFieldTypeForDto(field)
            val nullableSuffix = if (field.nullable) "?" else ""
            val defaultValue = if (field.nullable) " = null" else " = this.${field.name}"

            methods.append("        ${field.name}: $fieldType$nullableSuffix$defaultValue")
            if (index < entityMetadata.fields.size - 1) {
                methods.append(",\n")
            } else {
                methods.append("\n")
            }
        }

        methods.append("    ): ${entityMetadata.className}DTO {\n")
        methods.append("        return ${entityMetadata.className}DTO(\n")

        entityMetadata.fields.forEachIndexed { index, field ->
            methods.append("            ${field.name} = ${field.name}")
            if (index < entityMetadata.fields.size - 1) {
                methods.append(",\n")
            } else {
                methods.append("\n")
            }
        }

        methods.append("        )\n")
        methods.append("    }")

        return methods.toString()
    }
}

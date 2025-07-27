package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityField
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.RelationType
import com.enokdev.springapigenerator.service.DependencyValidationService
import com.intellij.openapi.project.Project
import java.nio.file.Paths

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
        // Store project for use in other methods
        this.currentProject = project

        // Vérifier et ajouter les dépendances de validation si nécessaire
        val features = mapOf("validation" to true)
        DependencyValidationService.validateAndEnsureDependencies(project, features)

        // Appeler la méthode parent
        val baseCode = super.generate(project, entityMetadata, packageConfig)

        // INJECTER LES ANNOTATIONS DTO directement
        return injectDtoAnnotations(baseCode, entityMetadata)
    }

    /**
     * Injecte les annotations DTO (Lombok ou data class) directement dans le code généré
     */
    private fun injectDtoAnnotations(code: String, entityMetadata: EntityMetadata): String {
        val className = "${entityMetadata.className}DTO"

        // Détecter si c'est Java ou Kotlin
        val isKotlinFile = code.contains("data class") || code.contains("class") && !code.contains("public class")

        if (isKotlinFile) {
            // Pour Kotlin - s'assurer que c'est une data class
            val classPattern = Regex("class\\s+$className", RegexOption.MULTILINE)
            return classPattern.replace(code) { matchResult ->
                val classDeclaration = matchResult.value

                // Vérifier si c'est déjà une data class
                val beforeClass = code.substring(0, matchResult.range.first)
                val hasDataClass = beforeClass.takeLast(50).contains("data")

                if (hasDataClass) {
                    classDeclaration
                } else {
                    classDeclaration.replace("class", "data class")
                }
            }
        } else {
            // Pour Java - ajouter les annotations Lombok
            val classPattern = Regex("(public\\s+)?class\\s+$className", RegexOption.MULTILINE)

            return classPattern.replace(code) { matchResult ->
                val classDeclaration = matchResult.value

                // Vérifier si les annotations Lombok sont déjà présentes
                val beforeClass = code.substring(0, matchResult.range.first)
                val hasData = beforeClass.takeLast(300).contains("@Data")
                val hasNoArgsConstructor = beforeClass.takeLast(300).contains("@NoArgsConstructor")
                val hasAllArgsConstructor = beforeClass.takeLast(300).contains("@AllArgsConstructor")

                if (hasData && hasNoArgsConstructor && hasAllArgsConstructor) {
                    // Annotations déjà présentes
                    classDeclaration
                } else {
                    // Injecter les annotations Lombok manquantes
                    val annotations = buildString {
                        if (!hasData) append("@Data\n")
                        if (!hasNoArgsConstructor) append("@NoArgsConstructor\n")
                        if (!hasAllArgsConstructor) append("@AllArgsConstructor\n")
                    }
                    "$annotations$classDeclaration"
                }
            }
        }
    }

    // Add a property to store the current project
    private var currentProject: Project? = null

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

        // ========== VARIABLES POUR LES ANNOTATIONS (TOUJOURS ACTIVÉES) ==========
        model["hasLombokAnnotation"] = true
        model["hasDataAnnotation"] = true
        model["hasBuilderAnnotation"] = true
        model["hasNoArgsConstructorAnnotation"] = true
        model["hasAllArgsConstructorAnnotation"] = true
        model["hasValidationDependency"] = true
        model["hasSerializableInterface"] = true
        model["hasValidationAnnotations"] = true
        model["hasJsonAnnotations"] = true

        // Déterminer si c'est un projet Java ou Kotlin
        val isKotlinProject = currentProject?.let { getFileExtensionForProject(it) == "kt" } ?: false
        model["isKotlinProject"] = isKotlinProject
        model["isJavaProject"] = !isKotlinProject

        // ========== GÉNÉRATION DES CHAMPS POUR LES TEMPLATES ==========
        // Les templates attendent des noms de variables spécifiques
        if (isKotlinProject) {
            val generatedKotlinFields = generateKotlinFields(entityMetadata)
            model["dtoFields"] = generatedKotlinFields  // Template Kotlin attend 'dtoFields'
            model["kotlinFields"] = generatedKotlinFields // Pour compatibilité
            model["fields"] = entityMetadata.fields

            // S'assurer qu'on a toujours les champs même si la génération échoue
            if (generatedKotlinFields.trim().isEmpty() && entityMetadata.fields.isNotEmpty()) {
                println("WARNING: Kotlin fields generation returned empty but entity has ${entityMetadata.fields.size} fields")
                // Forcer la mise à disposition des champs bruts pour le template de fallback
                model["hasFieldsForFallback"] = true
            }
        } else {
            val generatedJavaFields = generateJavaFields(entityMetadata)
            model["javaFields"] = generatedJavaFields    // Template Java attend 'javaFields'
            model["fields"] = entityMetadata.fields

            // S'assurer qu'on a toujours les champs même si la génération échoue
            if (generatedJavaFields.trim().isEmpty() && entityMetadata.fields.isNotEmpty()) {
                println("WARNING: Java fields generation returned empty but entity has ${entityMetadata.fields.size} fields")
                model["hasFieldsForFallback"] = true
            }
        }

        // Process fields with validation annotations (pour usage futur)
        val fieldsWithValidation = entityMetadata.fields.map { field ->
            processFieldForValidation(field, isKotlinProject)
        }
        model["fieldsWithValidation"] = fieldsWithValidation

        // Generate additional imports and methods
        val additionalImports = generateAdditionalImports(entityMetadata, packageConfig, isKotlinProject)
        val additionalMethods = generateAdditionalMethods(entityMetadata, isKotlinProject)

        model["additionalImports"] = additionalImports
        model["additionalMethods"] = additionalMethods

        return model
    }

    /**
     * Process field for validation based on its type and constraints.
     */
    private fun processFieldForValidation(field: EntityField, isKotlinProject: Boolean): Map<String, Any> {
        val fieldData = mutableMapOf<String, Any>()

        fieldData["name"] = field.name
        fieldData["type"] = if (isKotlinProject) getFieldTypeForDto(field) else getJavaFieldType(field)
        fieldData["nullable"] = field.nullable
        fieldData["isCollection"] = field.isCollection
        fieldData["relationType"] = field.relationType

        // Add validation annotations based on field type
        val validationAnnotations = mutableListOf<String>()

        if (!field.nullable) {
            validationAnnotations.add("@NotNull")

            if (field.type.contains("String")) {
                validationAnnotations.add("@NotBlank")
            }
        }

        // Add email validation for email fields
        if (field.name.lowercase().contains("email")) {
            validationAnnotations.add("@Email")
        }

        fieldData["validationAnnotations"] = validationAnnotations

        return fieldData
    }

    /**
     * Generate additional imports needed for the DTO based on field types and relationships.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata, packageConfig: Map<String, String>, isKotlinProject: Boolean): String {
        val imports = mutableSetOf<String>()
        var needsJavaUtil = false

        // Note: Basic imports (validation, serializable) are handled in templates
        // Only add specific imports based on field types

        entityMetadata.fields.forEach { field ->
            when {
                field.relationType != RelationType.NONE -> {
                    // For relationships, add DTO imports
                    if (field.isCollection) {
                        // Add collection import for Java only (Kotlin has built-in collections)
                        // Collections are handled in templates, but we need proper base collection types
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
                field.type.startsWith("java.util") && !field.type.contains("<") -> {
                    // Mark that we need java.util.* import instead of individual ones
                    needsJavaUtil = true
                }
                field.type.contains("UUID") -> {
                    needsJavaUtil = true
                }
            }
        }

        // Add collection imports only for the base types needed
        val needsSetImport = entityMetadata.fields.any { field ->
            field.relationType in listOf(RelationType.ONE_TO_MANY, RelationType.MANY_TO_MANY)
        }

        if (needsSetImport) {
            needsJavaUtil = true
        }

        // Add java.util.* if any java.util types are needed
        if (needsJavaUtil) {
            imports.add("java.util.*")
        }

        return if (imports.isNotEmpty()) {
            imports.sorted().joinToString("\n") { "import $it;" }
        } else {
            ""
        }
    }

    /**
     * Generate DTO fields with Kotlin syntax - Version corrigée pour inclure TOUS les champs
     */
    private fun generateKotlinFields(entityMetadata: EntityMetadata): String {
        // Vérifier d'abord si nous avons des champs
        if (entityMetadata.fields.isEmpty()) {
            return "    val id: ${extractSimpleTypeName(entityMetadata.idType)}? = null"
        }

        val fields = StringBuilder()

        // S'assurer que TOUS les champs de l'entité sont inclus
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

            // Add email validation for email fields
            if (field.name.lowercase().contains("email")) {
                fields.append("    @field:Email\n")
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

        val result = fields.toString().trim()

        // Log pour debug si nécessaire
        println("DEBUG: Generated Kotlin fields for ${entityMetadata.className}DTO:")
        println("Number of fields: ${entityMetadata.fields.size}")
        println("Generated content:\n$result")

        return result
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
     * Get the appropriate Kotlin type for a field in the DTO.
     */
    private fun getFieldTypeForDto(field: EntityField): String {
        return when (field.relationType) {
            RelationType.NONE -> {
                getKotlinSimpleFieldType(field)
            }
            RelationType.ONE_TO_ONE, RelationType.MANY_TO_ONE -> {
                "${field.relationTargetSimpleName}DTO"
            }
            RelationType.ONE_TO_MANY, RelationType.MANY_TO_MANY -> {
                "MutableList<${field.relationTargetSimpleName}DTO>"
            }
            RelationType.EMBEDDED -> {
                field.relationTargetSimpleName ?: getKotlinSimpleFieldType(field)
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
     * Get the simple Kotlin field type.
     */
    private fun getKotlinSimpleFieldType(field: EntityField): String {
        return when {
            field.type.contains("String") -> "String"
            field.type.contains("Long") -> "Long"
            field.type.contains("Integer") || field.type.contains("int") -> "Int"
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
            field.type.contains("byte[]") || field.type.contains("Byte[]") -> "ByteArray"
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

    /**
     * Generate additional methods for the DTO.
     */
    private fun generateAdditionalMethods(entityMetadata: EntityMetadata, isKotlinProject: Boolean): String {
        return if (isKotlinProject) {
            generateCustomMethods(entityMetadata)
        } else {
            // For Java, we rely on Lombok annotations for standard methods
            ""
        }
    }
}

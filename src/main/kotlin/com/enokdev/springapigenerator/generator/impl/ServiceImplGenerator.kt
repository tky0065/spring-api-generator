package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.DependencyValidationService
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for service implementations.
 */
class ServiceImplGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "ServiceImpl"
    }

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val servicePackage = packageConfig["servicePackage"] ?: entityMetadata.servicePackage
        val serviceImplPackage = "$servicePackage.impl"
        val serviceImplDir = serviceImplPackage.replace(".", "/")
        val extension = getFileExtensionForProject(project)
        val fileName = "${entityMetadata.className}ServiceImpl.$extension"
        return Paths.get(sourceRoot, serviceImplDir, fileName).toString()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["serviceName"] = "${entityMetadata.className}Service"
        model["serviceImplName"] = "${entityMetadata.className}ServiceImpl"
        model["className"] = entityMetadata.className
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["dtoName"] = "${entityMetadata.className}DTO"
        model["repositoryName"] = "${entityMetadata.className}Repository"
        model["mapperName"] = "${entityMetadata.className}Mapper"

        // Package pour l'implémentation (dans le sous-package impl)
        val servicePackage = packageConfig["servicePackage"] ?: entityMetadata.servicePackage
        model["packageName"] = "$servicePackage.impl"
        model["idType"] = extractSimpleTypeName(entityMetadata.idType)

        // ========== PACKAGES POUR LES IMPORTS ==========
        model["domainPackage"] = packageConfig["entityPackage"] ?: entityMetadata.domainPackage
        model["dtoPackage"] = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage
        model["repositoryPackage"] = packageConfig["repositoryPackage"] ?: entityMetadata.repositoryPackage
        model["servicePackage"] = servicePackage
        model["mapperPackage"] = packageConfig["mapperPackage"] ?: entityMetadata.mapperPackage
        model["entityPackage"] = packageConfig["entityPackage"] ?: entityMetadata.domainPackage

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower
        model["serviceVarName"] = "${entityMetadata.entityNameLower}Service"
        model["repositoryVarName"] = "${entityMetadata.entityNameLower}Repository"
        model["mapperVarName"] = "${entityMetadata.entityNameLower}Mapper"

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // ========== VARIABLES POUR LES ANNOTATIONS (TOUJOURS ACTIVÉES) ==========
        model["hasServiceAnnotation"] = true
        model["hasTransactionalAnnotation"] = true
        model["hasAutowiredAnnotation"] = true
        model["hasValidationDependency"] = true
        model["hasRepositoryDependency"] = true
        model["hasMapperDependency"] = true
        model["hasSlf4jAnnotation"] = true

        // ========== VARIABLES CRITIQUES POUR ÉVITER LES ERREURS FREEMARKER ==========
        model["fields"] = entityMetadata.fields
        model["tableName"] = entityMetadata.tableName ?: entityMetadata.entityNameLower

        // Déterminer si c'est un projet Java ou Kotlin
        val isKotlinProject = currentProject?.let { getFileExtensionForProject(it) == "kt" } ?: false
        model["isKotlinProject"] = isKotlinProject
        model["optionalType"] = if (isKotlinProject) "${entityMetadata.className}DTO?" else "Optional<${entityMetadata.className}DTO>"

        // Add service implementation-specific model data
        val additionalImports = generateAdditionalImports(entityMetadata, packageConfig)
        val relationshipMethods = generateRelationshipMethods(entityMetadata)
        val customMethods = generateCustomMethods(entityMetadata, packageConfig)

        model["additionalImports"] = additionalImports
        model["relationshipMethods"] = relationshipMethods
        model["customMethods"] = customMethods

        // ========== VALIDATION DES VARIABLES CRITIQUES ==========
        validateRequiredVariables(model, entityMetadata)

        return model
    }

    /**
     * Validate that all required variables for the ServiceImpl template are defined.
     * This prevents FreeMarker from failing to process template sections.
     */
    private fun validateRequiredVariables(model: MutableMap<String, Any>, entityMetadata: EntityMetadata) {
        val requiredVars = listOf(
            "serviceImplName", "serviceName", "entityName", "entityNameLower",
            "dtoName", "repositoryName", "mapperName", "packageName",
            "domainPackage", "dtoPackage", "repositoryPackage", "servicePackage",
            "mapperPackage", "entityPackage", "idType"
        )

        requiredVars.forEach { varName ->
            if (!model.containsKey(varName) || model[varName] == null) {
                throw RuntimeException("Required template variable '$varName' is missing for entity ${entityMetadata.className}")
            }
        }
    }

    override fun generate(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        // Store project for use in other methods
        this.currentProject = project

        // Vérifier et ajouter les dépendances requises si nécessaire
        val features = mapOf("mapstruct" to true)
        DependencyValidationService.validateAndEnsureDependencies(project, features)

        // Générer le code de base via le template
        val baseCode = super.generate(project, entityMetadata, packageConfig)

        // INJECTER LES ANNOTATIONS DIRECTEMENT dans le code généré
        return injectServiceImplAnnotations(baseCode, entityMetadata)
    }

    /**
     * Injecte les annotations @Service et @Transactional directement dans le code généré
     */
    private fun injectServiceImplAnnotations(code: String, entityMetadata: EntityMetadata): String {
        val className = "${entityMetadata.className}ServiceImpl"

        // Chercher la déclaration de classe
        val classPattern = Regex("(public\\s+)?class\\s+$className", RegexOption.MULTILINE)

        return classPattern.replace(code) { matchResult ->
            val classDeclaration = matchResult.value

            // Vérifier si les annotations sont déjà présentes
            val beforeClass = code.substring(0, matchResult.range.first)
            val hasService = beforeClass.takeLast(200).contains("@Service")
            val hasTransactional = beforeClass.takeLast(200).contains("@Transactional")

            if (hasService && hasTransactional) {
                // Annotations déjà présentes
                classDeclaration
            } else {
                // Injecter les annotations manquantes
                val annotations = buildString {
                    if (!hasService) append("@Service\n")
                    if (!hasTransactional) append("@Transactional\n")
                }
                "$annotations$classDeclaration"
            }
        }
    }

    // Add a property to store the current project
    private var currentProject: Project? = null

    /**
     * Generate additional imports needed for the service implementation.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val imports = mutableSetOf<String>()

        // Add imports using the correct packages
        val domainPackage = packageConfig["entityPackage"] ?: entityMetadata.domainPackage
        val dtoPackage = packageConfig["dtoPackage"] ?: entityMetadata.dtoPackage
        val repositoryPackage = packageConfig["repositoryPackage"] ?: entityMetadata.repositoryPackage
        val mapperPackage = packageConfig["mapperPackage"] ?: entityMetadata.mapperPackage

        imports.add("${domainPackage}.${entityMetadata.className}")
        imports.add("${dtoPackage}.${entityMetadata.className}DTO")
        imports.add("${repositoryPackage}.${entityMetadata.className}Repository")
        imports.add("${mapperPackage}.${entityMetadata.className}Mapper")

        return imports.joinToString("\n") { "import $it;" }
    }

    /**
     * Generate custom implementation methods for the service.
     */
    private fun generateCustomMethods(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        // Return empty string for now, can be expanded later with business logic methods
        return ""
    }

    /**
     * Generate relationship methods for the service implementation.
     */
    private fun generateRelationshipMethods(entityMetadata: EntityMetadata): String {
        val methods = StringBuilder()

        // Example: Generate a method to get related entities
        methods.appendLine("fun getRelatedEntities(entityId: Long): List<${entityMetadata.className}> {")
        methods.appendLine("    // Implement logic to retrieve related entities")
        methods.appendLine("}")

        return methods.toString()
    }
}

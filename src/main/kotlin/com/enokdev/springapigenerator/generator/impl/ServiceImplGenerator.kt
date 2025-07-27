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

    override fun generate(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        // Vérifier et ajouter les dépendances requises si nécessaire
        val features = mapOf("mapstruct" to true)
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

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower
        model["serviceVarName"] = "${entityMetadata.entityNameLower}Service"
        model["repositoryVarName"] = "${entityMetadata.entityNameLower}Repository"
        model["mapperVarName"] = "${entityMetadata.entityNameLower}Mapper"

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // Add service implementation-specific model data
        val additionalImports = generateAdditionalImports(entityMetadata, packageConfig)
        val customMethods = generateCustomImplementationMethods(entityMetadata)

        model["additionalImports"] = additionalImports
        model["customMethods"] = customMethods

        return model
    }

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
    private fun generateCustomImplementationMethods(entityMetadata: EntityMetadata): String {
        // Return empty string for now, can be expanded later with business logic methods
        return ""
    }
}

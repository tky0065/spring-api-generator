package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths
import java.util.Date

/**
 * Generator for Spring Security configuration.
 * This generator creates the Spring Security configuration class.
 */
class SecurityConfigGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "SpringSecurityConfig.java.ft"
    }

    /**
     * Get the target file path for the security configuration.
     *
     * @param project The IntelliJ project
     * @param entityMetadata Metadata about the entity
     * @param packageConfig Package configuration map
     * @return File path where the security config should be saved
     */
    override fun getTargetFilePath(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRoot = getSourceRootDirForProject(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val securityPackage = "$basePackage.config.security"
        val packagePath = securityPackage.replace('.', '/')
        val extension = getFileExtensionForProject(project)

        return Paths.get(sourceRoot, packagePath, "SecurityConfig.$extension").toString()
    }

    override fun createDataModel(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val securityPackage = "$basePackage.config.security"

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["packageName"] = securityPackage
        model["basePackage"] = basePackage
        model["className"] = "SecurityConfig"
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["currentDate"] = Date()

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // ========== VARIABLES POUR LES IMPORTS ET MÉTHODES PERSONNALISÉES ==========
        model["imports"] = ""
        model["customMethods"] = ""

        return model
    }

    /**
     * Generate JWT utility class.
     */
    fun generateJwtUtil(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val templateName = if (isKotlinProject(project)) "JwtUtil.kt.ft" else "JwtUtil.java.ft"
        val template = cfg.getTemplate(templateName)

        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val securityPackage = "$basePackage.config.security"

        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["packageName"] = securityPackage
        dataModel["className"] = "JwtUtil"

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)
        return writer.toString()
    }

    /**
     * Generate custom user details service.
     */
    fun generateUserDetailsService(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val templateName = if (isKotlinProject(project)) "CustomUserDetailsService.kt.ft" else "CustomUserDetailsService.java.ft"
        val template = cfg.getTemplate(templateName)

        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val securityPackage = "$basePackage.config.security"

        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["packageName"] = securityPackage
        dataModel["className"] = "CustomUserDetailsService"

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)
        return writer.toString()
    }

    /**
     * Generate user model class.
     */
    fun generateUserModel(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val templateName = if (isKotlinProject(project)) "User.kt.ft" else "User.java.ft"
        val template = cfg.getTemplate(templateName)

        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val userPackage = "$basePackage.entity"

        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["packageName"] = userPackage
        dataModel["className"] = "User"

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)
        return writer.toString()
    }

    /**
     * Generate user repository interface.
     */
    fun generateUserRepository(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val templateName = if (isKotlinProject(project)) "UserRepository.kt.ft" else "UserRepository.java.ft"
        val template = cfg.getTemplate(templateName)

        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val userPackage = "$basePackage.repository"

        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["packageName"] = userPackage
        dataModel["className"] = "UserRepository"

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)
        return writer.toString()
    }

    /**
     * Generate user service class.
     */
    fun generateUserService(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val templateName = if (isKotlinProject(project)) "UserService.kt.ft" else "UserService.java.ft"
        val template = cfg.getTemplate(templateName)

        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val userPackage = "$basePackage.service"

        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["packageName"] = userPackage
        dataModel["className"] = "UserService"

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)
        return writer.toString()
    }

    /**
     * Generate authentication controller class.
     */
    fun generateAuthController(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val cfg = createFreemarkerConfig(project)
        val templateName = if (isKotlinProject(project)) "AuthController.kt.ft" else "AuthController.java.ft"
        val template = cfg.getTemplate(templateName)

        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val controllerPackage = "$basePackage.controller"

        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["packageName"] = controllerPackage
        dataModel["className"] = "AuthController"

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)
        return writer.toString()
    }

    /**
     * Enum defining the security level to apply.
     */
    enum class SecurityLevel {
        BASIC,          // Basic HTTP authentication
        ROLE_BASED,     // Role-based security with @PreAuthorize
        JWT             // JWT token-based security
    }
}

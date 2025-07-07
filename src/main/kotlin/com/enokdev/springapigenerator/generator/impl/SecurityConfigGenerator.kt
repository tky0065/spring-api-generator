package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import freemarker.template.Configuration
import java.io.File
import java.nio.file.Paths
import java.util.Date

/**
 * Generator for Spring Security configuration.
 * This generator creates the Spring Security configuration class.
 */
class SecurityConfigGenerator : AbstractTemplateCodeGenerator("SpringSecurityConfig.java.ft") {

    companion object {
        const val JWT_UTIL_TEMPLATE = "JwtUtil.java.ft"
        const val USER_DETAILS_SERVICE_TEMPLATE = "CustomUserDetailsService.java.ft"

        // New user management templates
        const val USER_MODEL_TEMPLATE = "User.java.ft"
        const val USER_REPOSITORY_TEMPLATE = "UserRepository.java.ft"
        const val USER_SERVICE_TEMPLATE = "UserService.java.ft"
        const val AUTH_CONTROLLER_TEMPLATE = "AuthController.java.ft"
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
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val securityPackage = "$basePackage.config.security"
        val packagePath = securityPackage.replace('.', '/')

        return Paths.get(sourceRoot, packagePath, "SecurityConfig.java").toString()
    }

    /**
     * Create the data model for the template.
     */
    override fun createDataModel(entityMetadata: EntityMetadata, packageConfig: Map<String, String>): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // Add security-specific parameters
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val securityPackage = "$basePackage.config.security"

        model["packageName"] = securityPackage
        model["basePackage"] = basePackage
        model["currentDate"] = Date()

        return model
    }

    /**
     * Generate JWT utility class.
     */
    fun generateJwtUtil(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val securityPackage = "$basePackage.config.security"
        val packagePath = securityPackage.replace('.', '/')

        val targetPath = Paths.get(sourceRoot, packagePath, "JwtUtil.java").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(JWT_UTIL_TEMPLATE)
        val dataModel = createDataModel(entityMetadata, packageConfig)

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)

        // Create the file
        val file = File(targetPath)
        file.parentFile.mkdirs()
        file.writeText(writer.toString())

        return targetPath
    }

    /**
     * Generate custom user details service class.
     */
    fun generateUserDetailsService(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val securityPackage = "$basePackage.config.security"
        val packagePath = securityPackage.replace('.', '/')

        val targetPath = Paths.get(sourceRoot, packagePath, "CustomUserDetailsService.java").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(USER_DETAILS_SERVICE_TEMPLATE)
        val dataModel = createDataModel(entityMetadata, packageConfig)

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)

        // Create the file
        val file = File(targetPath)
        file.parentFile.mkdirs()
        file.writeText(writer.toString())

        return targetPath
    }

    /**
     * Generate user model class.
     */
    fun generateUserModel(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val userPackage = "$basePackage.entity"
        val packagePath = userPackage.replace('.', '/')

        val targetPath = Paths.get(sourceRoot, packagePath, "User.java").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(USER_MODEL_TEMPLATE)
        val dataModel = mutableMapOf<String, Any>()
        dataModel.putAll(createDataModel(entityMetadata, packageConfig))
        dataModel["packageName"] = userPackage

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)

        // Create the file
        val file = File(targetPath)
        file.parentFile.mkdirs()
        file.writeText(writer.toString())

        return targetPath
    }

    /**
     * Generate user repository interface.
     */
    fun generateUserRepository(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val userPackage = "$basePackage.repository"
        val packagePath = userPackage.replace('.', '/')

        val targetPath = Paths.get(sourceRoot, packagePath, "UserRepository.java").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(USER_REPOSITORY_TEMPLATE)
        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["packageName"] = userPackage

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)

        // Create the file
        val file = File(targetPath)
        file.parentFile.mkdirs()
        file.writeText(writer.toString())

        return targetPath
    }

    /**
     * Generate user service class.
     */
    fun generateUserService(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val userPackage = "$basePackage.service"
        val packagePath = userPackage.replace('.', '/')

        val targetPath = Paths.get(sourceRoot, packagePath, "UserService.java").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(USER_SERVICE_TEMPLATE)
        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["packageName"] = userPackage

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)

        // Create the file
        val file = File(targetPath)
        file.parentFile.mkdirs()
        file.writeText(writer.toString())

        return targetPath
    }

    /**
     * Generate authentication controller class.
     */
    fun generateAuthController(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRoot = getSourceRootDir(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val controllerPackage = "$basePackage.controller"
        val packagePath = controllerPackage.replace('.', '/')

        val targetPath = Paths.get(sourceRoot, packagePath, "AuthController.java").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(AUTH_CONTROLLER_TEMPLATE)
        val dataModel = createDataModel(entityMetadata, packageConfig)
        dataModel["packageName"] = controllerPackage

        val writer = java.io.StringWriter()
        template.process(dataModel, writer)

        // Create the file
        val file = File(targetPath)
        file.parentFile.mkdirs()
        file.writeText(writer.toString())

        return targetPath
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

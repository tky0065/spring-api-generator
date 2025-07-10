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
        // Templates Java
        const val JWT_UTIL_TEMPLATE_JAVA = "JwtUtil.java.ft"
        const val USER_DETAILS_SERVICE_TEMPLATE_JAVA = "CustomUserDetailsService.java.ft"
        const val USER_MODEL_TEMPLATE_JAVA = "User.java.ft"
        const val USER_REPOSITORY_TEMPLATE_JAVA = "UserRepository.java.ft"
        const val USER_SERVICE_TEMPLATE_JAVA = "UserService.java.ft"
        const val AUTH_CONTROLLER_TEMPLATE_JAVA = "AuthController.java.ft"

        // Templates Kotlin
        const val JWT_UTIL_TEMPLATE_KOTLIN = "JwtUtil.kt.ft"
        const val USER_DETAILS_SERVICE_TEMPLATE_KOTLIN = "CustomUserDetailsService.kt.ft"
        const val USER_MODEL_TEMPLATE_KOTLIN = "User.kt.ft"
        const val USER_REPOSITORY_TEMPLATE_KOTLIN = "UserRepository.kt.ft"
        const val USER_SERVICE_TEMPLATE_KOTLIN = "UserService.kt.ft"
        const val AUTH_CONTROLLER_TEMPLATE_KOTLIN = "AuthController.kt.ft"
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

    /**
     * Détermine le template JWT à utiliser en fonction du type de projet.
     */
    private fun getJwtUtilTemplate(project: Project): String {
        return if (com.enokdev.springapigenerator.service.ProjectTypeDetectionService.shouldGenerateKotlinCode(project)) {
            JWT_UTIL_TEMPLATE_KOTLIN
        } else {
            JWT_UTIL_TEMPLATE_JAVA
        }
    }

    /**
     * Détermine le template CustomUserDetailsService à utiliser en fonction du type de projet.
     */
    private fun getUserDetailsServiceTemplate(project: Project): String {
        return if (com.enokdev.springapigenerator.service.ProjectTypeDetectionService.shouldGenerateKotlinCode(project)) {
            USER_DETAILS_SERVICE_TEMPLATE_KOTLIN
        } else {
            USER_DETAILS_SERVICE_TEMPLATE_JAVA
        }
    }

    /**
     * Détermine le template User à utiliser en fonction du type de projet.
     */
    private fun getUserModelTemplate(project: Project): String {
        return if (com.enokdev.springapigenerator.service.ProjectTypeDetectionService.shouldGenerateKotlinCode(project)) {
            USER_MODEL_TEMPLATE_KOTLIN
        } else {
            USER_MODEL_TEMPLATE_JAVA
        }
    }

    /**
     * Détermine le template UserRepository à utiliser en fonction du type de projet.
     */
    private fun getUserRepositoryTemplate(project: Project): String {
        return if (com.enokdev.springapigenerator.service.ProjectTypeDetectionService.shouldGenerateKotlinCode(project)) {
            USER_REPOSITORY_TEMPLATE_KOTLIN
        } else {
            USER_REPOSITORY_TEMPLATE_JAVA
        }
    }

    /**
     * Détermine le template UserService à utiliser en fonction du type de projet.
     */
    private fun getUserServiceTemplate(project: Project): String {
        return if (com.enokdev.springapigenerator.service.ProjectTypeDetectionService.shouldGenerateKotlinCode(project)) {
            USER_SERVICE_TEMPLATE_KOTLIN
        } else {
            USER_SERVICE_TEMPLATE_JAVA
        }
    }

    /**
     * Détermine le template AuthController à utiliser en fonction du type de projet.
     */
    private fun getAuthControllerTemplate(project: Project): String {
        return if (com.enokdev.springapigenerator.service.ProjectTypeDetectionService.shouldGenerateKotlinCode(project)) {
            AUTH_CONTROLLER_TEMPLATE_KOTLIN
        } else {
            AUTH_CONTROLLER_TEMPLATE_JAVA
        }
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
        val sourceRoot = getSourceRootDirForProject(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val securityPackage = "$basePackage.config.security"
        val packagePath = securityPackage.replace('.', '/')
        val extension = getFileExtensionForProject(project)

        val targetPath = Paths.get(sourceRoot, packagePath, "JwtUtil.$extension").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(getJwtUtilTemplate(project))
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
        val sourceRoot = getSourceRootDirForProject(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val securityPackage = "$basePackage.config.security"
        val packagePath = securityPackage.replace('.', '/')
        val extension = getFileExtensionForProject(project)

        val targetPath = Paths.get(sourceRoot, packagePath, "CustomUserDetailsService.$extension").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(getUserDetailsServiceTemplate(project))
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
        val sourceRoot = getSourceRootDirForProject(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val userPackage = "$basePackage.entity"
        val packagePath = userPackage.replace('.', '/')
        val extension = getFileExtensionForProject(project)

        val targetPath = Paths.get(sourceRoot, packagePath, "User.$extension").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(getUserModelTemplate(project))
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
        val sourceRoot = getSourceRootDirForProject(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val userPackage = "$basePackage.repository"
        val packagePath = userPackage.replace('.', '/')
        val extension = getFileExtensionForProject(project)

        val targetPath = Paths.get(sourceRoot, packagePath, "UserRepository.$extension").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(getUserRepositoryTemplate(project))
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
        val sourceRoot = getSourceRootDirForProject(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val userPackage = "$basePackage.service"
        val packagePath = userPackage.replace('.', '/')
        val extension = getFileExtensionForProject(project)

        val targetPath = Paths.get(sourceRoot, packagePath, "UserService.$extension").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(getUserServiceTemplate(project))
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
        val sourceRoot = getSourceRootDirForProject(project)
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.entityBasePackage
        val controllerPackage = "$basePackage.controller"
        val packagePath = controllerPackage.replace('.', '/')
        val extension = getFileExtensionForProject(project)

        val targetPath = Paths.get(sourceRoot, packagePath, "AuthController.$extension").toString()

        // Generate the content using FreeMarker template
        val cfg = Configuration(Configuration.VERSION_2_3_30)
        val classLoader = javaClass.classLoader
        cfg.setClassLoaderForTemplateLoading(classLoader, "templates")

        val template = cfg.getTemplate(getAuthControllerTemplate(project))
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

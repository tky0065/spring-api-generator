package com.enokdev.springapigenerator.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Service for managing template customization and template directory resolution.
 * Enhanced to be more flexible with annotation requirements.
 */
@Service(Service.Level.PROJECT)
class TemplateCustomizationService(private val project: Project) {
    private val logger = Logger.getInstance(javaClass)

    companion object {
        private const val PROJECT_TEMPLATES_DIR = ".idea/spring-api-generator/templates"
        private const val USER_TEMPLATES_DIR = ".spring-api-generator/templates"
        
        // List of template files that benefit from annotations (but don't require them)
        private val ANNOTATION_ENHANCED_TEMPLATES = listOf(
            "Controller.java.ft", "Controller.kt.ft",
            "Repository.java.ft", "Repository.kt.ft",
            "ServiceImpl.java.ft", "ServiceImpl.kt.ft"
        )
        
        // Templates that work fine without any annotations
        private val ANNOTATION_OPTIONAL_TEMPLATES = listOf(
            "DTO.java.ft", "DTO.kt.ft",
            "Service.java.ft", "Service.kt.ft",
            "Entity.java.ft", "Entity.kt.ft"
        )
    }

    /**
     * Get the project-specific templates directory.
     */
    fun getProjectTemplatesDirectory(): String {
        val basePath = project.basePath ?: throw RuntimeException("Project base path not found")
        return Paths.get(basePath, PROJECT_TEMPLATES_DIR).toString()
    }

    /**
     * Get the user's home templates directory.
     */
    fun getUserTemplatesDirectory(): String {
        val userHome = System.getProperty("user.home")
        return Paths.get(userHome, USER_TEMPLATES_DIR).toString()
    }

    /**
     * Check if custom templates are available for the project.
     */
    fun hasCustomTemplates(): Boolean {
        val projectDir = File(getProjectTemplatesDirectory())
        val userDir = File(getUserTemplatesDirectory())
        return projectDir.exists() || userDir.exists()
    }

    /**
     * Create the project templates directory if it doesn't exist.
     */
    fun createProjectTemplatesDirectory(): Boolean {
        val projectDir = File(getProjectTemplatesDirectory())
        return if (!projectDir.exists()) {
            projectDir.mkdirs()
        } else {
            true
        }
    }

    /**
     * List available template files in the project directory.
     */
    fun getProjectTemplateFiles(): List<File> {
        val projectDir = File(getProjectTemplatesDirectory())
        return if (projectDir.exists()) {
            projectDir.listFiles { file -> file.isFile && file.name.endsWith(".ft") }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * List available template files in the user directory.
     */
    fun getUserTemplateFiles(): List<File> {
        val userDir = File(getUserTemplatesDirectory())
        return if (userDir.exists()) {
            userDir.listFiles { file -> file.isFile && file.name.endsWith(".ft") }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    /**
     * Check if a template can be used for code generation.
     * Now more permissive - templates can be used even without annotations.
     *
     * @param templateFile The template file to check
     * @return true if the template can be used, false if it's corrupted or missing
     */
    fun canTemplateBeUsed(templateFile: File): Boolean {
        if (!templateFile.exists() || !templateFile.isFile) {
            return false
        }
        
        // Basic check - file must be readable and have some content
        return try {
            val content = templateFile.readText()
            content.isNotBlank() && content.length > 10
        } catch (e: Exception) {
            logger.warn("Cannot read template file: ${templateFile.absolutePath}", e)
            false
        }
    }

    /**
     * Get validation status for a template - informational only, doesn't block usage.
     *
     * @param templateFile The template file to validate
     * @return A map with validation info (warnings, suggestions, etc.)
     */
    fun getTemplateValidationInfo(templateFile: File): Map<String, Any> {
        val info = mutableMapOf<String, Any>()
        val validator = TemplateValidator.getInstance()
        
        if (!templateFile.exists()) {
            info["status"] = "missing"
            return info
        }
        
        val missingAnnotations = validator.validateTemplate(templateFile)
        val templateName = templateFile.name
        
        info["status"] = "usable"
        info["hasAnnotations"] = missingAnnotations.isEmpty()
        
        if (missingAnnotations.isNotEmpty()) {
            if (templateName in ANNOTATION_ENHANCED_TEMPLATES) {
                info["suggestion"] = "Could benefit from annotations: ${missingAnnotations.joinToString(", ")}"
                info["severity"] = "info"
            } else {
                info["suggestion"] = "Annotations optional for this template type"
                info["severity"] = "none"
            }
            info["missingAnnotations"] = missingAnnotations
        }
        
        return info
    }

    /**
     * Reset a specific template to the default built-in version.
     * Updated comment to reflect that this is for fixing corrupted templates, not just missing annotations.
     *
     * @param templateName The name of the template to reset (e.g., "Controller.java.ft")
     * @param targetDirectory The directory to save the reset template to (project or user)
     * @return true if the template was successfully reset, false otherwise
     */
    fun resetTemplateToDefault(templateName: String, targetDirectory: File): Boolean {
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs()
        }
        
        val targetFile = File(targetDirectory, templateName)
        
        // Get the default template from resources
        val resourcePath = "templates/$templateName"
        val inputStream = javaClass.classLoader.getResourceAsStream(resourcePath)
        
        if (inputStream == null) {
            logger.error("Failed to find default template: $resourcePath")
            return false
        }
        
        try {
            // Copy the default template to the target file
            Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            logger.info("Reset template $templateName to default version in ${targetDirectory.absolutePath}")
            return true
        } catch (e: Exception) {
            logger.error("Failed to reset template $templateName: ${e.message}", e)
            return false
        } finally {
            inputStream.close()
        }
    }
    
    /**
     * Reset templates that are corrupted or missing to their default versions.
     * No longer specifically for annotation issues - focuses on template functionality.
     *
     * @param useProjectDir If true, reset templates in the project directory, otherwise in the user directory
     * @return The number of templates successfully reset
     */
    fun resetCorruptedTemplates(useProjectDir: Boolean = true): Int {
        val targetDir = if (useProjectDir) {
            File(getProjectTemplatesDirectory())
        } else {
            File(getUserTemplatesDirectory())
        }
        
        var successCount = 0
        val allTemplates = ANNOTATION_ENHANCED_TEMPLATES + ANNOTATION_OPTIONAL_TEMPLATES

        for (templateName in allTemplates) {
            val templateFile = File(targetDir, templateName)
            if (!canTemplateBeUsed(templateFile)) {
                if (resetTemplateToDefault(templateName, targetDir)) {
                    successCount++
                }
            }
        }
        
        return successCount
    }

    /**
     * Check if any templates need attention (corrupted, missing, etc.)
     * Returns information without blocking generation.
     */
    fun getTemplateHealthReport(): Map<String, Any> {
        val report = mutableMapOf<String, Any>()
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val projectDir = File(getProjectTemplatesDirectory())
        val userDir = File(getUserTemplatesDirectory())

        val allTemplates = ANNOTATION_ENHANCED_TEMPLATES + ANNOTATION_OPTIONAL_TEMPLATES

        for (templateName in allTemplates) {
            val projectTemplate = File(projectDir, templateName)
            val userTemplate = File(userDir, templateName)

            val hasProject = canTemplateBeUsed(projectTemplate)
            val hasUser = canTemplateBeUsed(userTemplate)

            if (!hasProject && !hasUser) {
                issues.add("Template '$templateName' not found or corrupted")
            } else {
                val templateFile = if (hasProject) projectTemplate else userTemplate
                val validationInfo = getTemplateValidationInfo(templateFile)

                if (validationInfo["severity"] == "info") {
                    warnings.add("${validationInfo["suggestion"]}")
                }
            }
        }

        report["issues"] = issues
        report["warnings"] = warnings
        report["status"] = if (issues.isEmpty()) "healthy" else "needs_attention"

        return report
    }
}

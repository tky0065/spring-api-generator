package com.enokdev.springapigenerator.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Paths

/**
 * Service for managing template customization and template directory resolution.
 */
@Service(Service.Level.PROJECT)
class TemplateCustomizationService(private val project: Project) {

    companion object {
        private const val PROJECT_TEMPLATES_DIR = ".idea/spring-api-generator/templates"
        private const val USER_TEMPLATES_DIR = ".spring-api-generator/templates"
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
}

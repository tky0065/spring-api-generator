package com.enokdev.springapigenerator.generator

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.IncrementalUpdateService
import com.enokdev.springapigenerator.service.CodeStyleDetector
import com.enokdev.springapigenerator.service.CodeStyleAdapter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import java.io.File
import java.nio.file.Paths

/**
 * Enhanced code generator that supports incremental updates and style adaptation.
 */
abstract class IncrementalCodeGenerator(
    javaTemplateName: String
) : AbstractTemplateCodeGenerator(javaTemplateName) {

    private val incrementalUpdateService = IncrementalUpdateService()

    /**
     * Generate code with incremental update support.
     */
    override fun generate(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val targetFilePath = getTargetFilePath(project, entityMetadata, packageConfig)
        val targetFile = File(targetFilePath)

        // Check if file already exists
        if (targetFile.exists()) {
            return generateIncrementalUpdate(project, entityMetadata, packageConfig, targetFile)
        } else {
            return generateNewFile(project, entityMetadata, packageConfig)
        }
    }

    /**
     * Generate code for a new file with markers.
     */
    private fun generateNewFile(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        // Generate new code using parent implementation
        val newCode = super.generate(project, entityMetadata, packageConfig)

        // Wrap with generated markers for future incremental updates
        return incrementalUpdateService.wrapWithGeneratedMarkers(newCode)
    }

    /**
     * Generate incremental update preserving manual changes.
     */
    private fun generateIncrementalUpdate(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        existingFile: File
    ): String {
        val existingContent = existingFile.readText()
        val newGeneratedCode = super.generate(project, entityMetadata, packageConfig)

        // Use incremental update service to merge changes
        val updateStrategy = IncrementalUpdateService.UpdateStrategy(
            preserveManualChanges = true,
            updateGeneratedSections = true,
            addMissingGeneratedCode = true,
            removeObsoleteGeneratedCode = false
        )

        return incrementalUpdateService.mergeCode(existingContent, newGeneratedCode, updateStrategy)
    }

    /**
     * Generate specific sections of code (methods, fields, etc.) with markers.
     */
    protected fun generateWithSectionMarkers(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val codeStyleDetector = CodeStyleDetector()
        val styleConfig = codeStyleDetector.detectCodeStyle(project)
        val styleAdapter = CodeStyleAdapter(styleConfig)

        val sections = mutableListOf<String>()

        // Generate fields section
        val fieldsCode = generateFieldsSection(entityMetadata, styleAdapter)
        if (fieldsCode.isNotEmpty()) {
            sections.add(incrementalUpdateService.wrapFieldWithMarkers(fieldsCode, "fields"))
        }

        // Generate getters and setters section
        val gettersSettersCode = generateGettersSettersSection(entityMetadata, styleAdapter)
        if (gettersSettersCode.isNotEmpty()) {
            sections.add(incrementalUpdateService.wrapMethodWithMarkers(gettersSettersCode, "getters_setters"))
        }

        // Generate equals and hashCode section
        val equalsHashCodeCode = generateEqualsHashCodeSection(entityMetadata, styleAdapter)
        if (equalsHashCodeCode.isNotEmpty()) {
            sections.add(incrementalUpdateService.wrapMethodWithMarkers(equalsHashCodeCode, "equals_hashcode"))
        }

        // Generate toString section
        val toStringCode = generateToStringSection(entityMetadata, styleAdapter)
        if (toStringCode.isNotEmpty()) {
            sections.add(incrementalUpdateService.wrapMethodWithMarkers(toStringCode, "toString"))
        }

        return sections.joinToString("\n\n")
    }

    /**
     * Update only specific sections of existing code.
     */
    fun updateSpecificSections(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        sectionsToUpdate: Set<String>
    ): String {
        val targetFilePath = getTargetFilePath(project, entityMetadata, packageConfig)
        val targetFile = File(targetFilePath)

        if (!targetFile.exists()) {
            return generate(project, entityMetadata, packageConfig)
        }

        var content = targetFile.readText()
        val codeStyleDetector = CodeStyleDetector()
        val styleConfig = codeStyleDetector.detectCodeStyle(project)
        val styleAdapter = CodeStyleAdapter(styleConfig)

        sectionsToUpdate.forEach { sectionName ->
            val newSectionContent = when (sectionName) {
                "fields" -> generateFieldsSection(entityMetadata, styleAdapter)
                "getters_setters" -> generateGettersSettersSection(entityMetadata, styleAdapter)
                "equals_hashcode" -> generateEqualsHashCodeSection(entityMetadata, styleAdapter)
                "toString" -> generateToStringSection(entityMetadata, styleAdapter)
                else -> ""
            }

            if (newSectionContent.isNotEmpty()) {
                content = incrementalUpdateService.updateGeneratedSection(content, sectionName, newSectionContent)
            }
        }

        return content
    }

    /**
     * Generate fields section with proper styling for Java or Kotlin.
     */
    protected open fun generateFieldsSection(entityMetadata: EntityMetadata, styleAdapter: CodeStyleAdapter): String {
        // Cette méthode sera surchargée par les générateurs spécialisés
        // Le code sera adapté selon le type de projet (Java/Kotlin) dans les templates
        return ""
    }

    /**
     * Generate getters and setters section with proper styling for Java or Kotlin.
     */
    protected open fun generateGettersSettersSection(entityMetadata: EntityMetadata, styleAdapter: CodeStyleAdapter): String {
        // Cette méthode sera surchargée par les générateurs spécialisés
        // Le code sera adapté selon le type de projet (Java/Kotlin) dans les templates
        return ""
    }

    /**
     * Generate equals and hashCode methods with proper styling for Java or Kotlin.
     */
    protected open fun generateEqualsHashCodeSection(entityMetadata: EntityMetadata, styleAdapter: CodeStyleAdapter): String {
        // Cette méthode sera surchargée par les générateurs spécialisés
        // Le code sera adapté selon le type de projet (Java/Kotlin) dans les templates
        return ""
    }

    /**
     * Generate toString method with proper styling for Java or Kotlin.
     */
    protected open fun generateToStringSection(entityMetadata: EntityMetadata, styleAdapter: CodeStyleAdapter): String {
        // Cette méthode sera surchargée par les générateurs spécialisés
        // Le code sera adapté selon le type de projet (Java/Kotlin) dans les templates
        return ""
    }
}

package com.enokdev.springapigenerator.action

import com.enokdev.springapigenerator.generator.impl.*
import com.enokdev.springapigenerator.generator.*
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.*
import com.enokdev.springapigenerator.ui.GeneratorConfigDialog
import com.enokdev.springapigenerator.ui.LanguageSelectionDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Enhanced action for generating Spring REST code from JPA entities.
 * Supports both Java and Kotlin projects with intelligent language detection.
 */
class GenerateSpringCodeAction : AnAction() {

    /**
     * Called when the action is performed by the user.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        try {
            // Detect project language info
            val languageInfo = ProjectTypeDetectionService.getProjectLanguageInfo(project)

            // Create language preference service directly instead of using getInstance
            val languagePrefs = LanguagePreferenceService()

            // Get target language preference
            val targetLanguage = determineTargetLanguage(project, languageInfo, languagePrefs)

            // Find the entity class - create service directly
            val entityDetectionService = EntityDetectionService(project)
            val psiClass = findEntityClass(psiFile, entityDetectionService)

            if (psiClass == null) {
                showNoEntityFoundError(project, psiFile.name)
                return
            }

            // Analyze the entity - create analyzer directly
            val entityAnalyzer = EntityAnalyzer()
            val entityMetadata = ReadAction.compute<EntityMetadata, Throwable> {
                entityAnalyzer.analyzeEntity(psiClass)
            }

            // Show configuration dialog with corrected parameters
            showConfigurationDialog(project, entityMetadata, targetLanguage)

        } catch (e: Exception) {
            showGenerationError(project, e)
        }
    }

    /**
     * Determines the target language for code generation.
     */
    private fun determineTargetLanguage(
        project: Project,
        languageInfo: ProjectTypeDetectionService.ProjectLanguageInfo,
        languagePrefs: LanguagePreferenceService
    ): String? {
        // For mixed projects, prompt user to choose language
        if (languageInfo.isMixed) {
            return showLanguageSelectionDialog(project, languageInfo)
        }

        // Auto-detect based on project
        return when (languageInfo.primaryLanguage) {
            ProjectTypeDetectionService.ProjectLanguage.KOTLIN -> "kotlin"
            ProjectTypeDetectionService.ProjectLanguage.JAVA -> "java"
            ProjectTypeDetectionService.ProjectLanguage.MIXED -> {
                // For mixed without user preference, use the majority language
                if (languageInfo.kotlinRatio > 0.5) "kotlin" else "java"
            }
        }
    }

    /**
     * Shows language selection dialog for mixed projects.
     */
    private fun showLanguageSelectionDialog(
        project: Project,
        languageInfo: ProjectTypeDetectionService.ProjectLanguageInfo
    ): String? {
        val dialog = LanguageSelectionDialog(project, languageInfo)
        return if (dialog.showAndGet()) {
            dialog.getSelectedLanguage()
        } else {
            null // User cancelled
        }
    }

    /**
     * Finds the entity class in the given file.
     */
    private fun findEntityClass(psiFile: com.intellij.psi.PsiFile, entityDetectionService: EntityDetectionService): PsiClass? {
        return ReadAction.compute<PsiClass?, Throwable> {
            when {
                psiFile is PsiJavaFile -> {
                    val classes = psiFile.classes
                    classes.find { entityDetectionService.isJpaEntity(it) }
                }
                psiFile.name.endsWith(".kt") -> {
                    // For Kotlin files, try to find the corresponding Java light class
                    findKotlinEntityClass(psiFile, entityDetectionService)
                }
                else -> null
            }
        }
    }

    /**
     * Finds entity class from Kotlin file.
     */
    private fun findKotlinEntityClass(psiFile: com.intellij.psi.PsiFile, entityDetectionService: EntityDetectionService): PsiClass? {
        try {
            val fileContent = psiFile.text
            val packageName = extractPackageFromKotlinFile(fileContent)
            val className = extractClassNameFromKotlinFile(fileContent)

            if (className.isNotEmpty()) {
                val fullyQualifiedName = if (packageName.isNotEmpty()) "$packageName.$className" else className
                val javaPsiFacade = com.intellij.psi.JavaPsiFacade.getInstance(psiFile.project)
                val psiClass = javaPsiFacade.findClass(fullyQualifiedName, com.intellij.psi.search.GlobalSearchScope.allScope(psiFile.project))

                return if (psiClass != null && entityDetectionService.isJpaEntity(psiClass)) {
                    psiClass
                } else null
            }
        } catch (e: Exception) {
            // Log error but don't fail completely
            com.intellij.openapi.diagnostic.Logger.getInstance(this::class.java).warn("Error processing Kotlin file: ${e.message}")
        }
        return null
    }

    /**
     * Extracts package name from Kotlin file content.
     */
    private fun extractPackageFromKotlinFile(content: String): String {
        val packagePatterns = listOf(
            Regex("^\\s*package\\s+([\\w.]+)\\s*$", RegexOption.MULTILINE),
            Regex("package\\s+([\\w.]+)", RegexOption.MULTILINE)
        )

        for (pattern in packagePatterns) {
            val match = pattern.find(content)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return ""
    }

    /**
     * Extracts class name from Kotlin file content.
     */
    private fun extractClassNameFromKotlinFile(content: String): String {
        val classPatterns = listOf(
            Regex("@Entity\\s*\\n\\s*(?:@[^\\n]*\\s*\\n\\s*)*(?:data\\s+)?class\\s+(\\w+)", RegexOption.MULTILINE),
            Regex("(?:data\\s+)?class\\s+(\\w+)\\s*\\(", RegexOption.MULTILINE),
            Regex("class\\s+(\\w+)", RegexOption.MULTILINE)
        )

        for (pattern in classPatterns) {
            val match = pattern.find(content)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return ""
    }

    /**
     * Shows the configuration dialog with corrected parameters.
     */
    private fun showConfigurationDialog(
        project: Project,
        entityMetadata: EntityMetadata,
        targetLanguage: String?
    ) {
        // Use the existing GeneratorConfigDialog constructor
        val dialog = GeneratorConfigDialog(project, entityMetadata)
        if (dialog.showAndGet()) {
            val selectedComponents = dialog.getSelectedComponents()
            val packageConfig = createPackageConfiguration(entityMetadata)

            generateCode(project, entityMetadata, selectedComponents, packageConfig, targetLanguage)
        }
    }

    /**
     * Creates package configuration from entity metadata.
     */
    private fun createPackageConfiguration(entityMetadata: EntityMetadata): Map<String, String> {
        return mapOf(
            "basePackage" to entityMetadata.entityBasePackage,
            "domainPackage" to entityMetadata.domainPackage,
            "dtoPackage" to entityMetadata.dtoPackage,
            "repositoryPackage" to entityMetadata.repositoryPackage,
            "servicePackage" to entityMetadata.servicePackage,
            "mapperPackage" to entityMetadata.mapperPackage,
            "controllerPackage" to entityMetadata.controllerPackage
        )
    }

    /**
     * Generates the selected code components.
     */
    private fun generateCode(
        project: Project,
        entityMetadata: EntityMetadata,
        selectedComponents: Set<String>,
        packageConfig: Map<String, String>,
        targetLanguage: String?
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val generators = createGenerators(selectedComponents)
                val generatedFiles = mutableListOf<String>()

                for (generator in generators) {
                    try {
                        val code = if (generator is AbstractTemplateCodeGenerator && targetLanguage != null) {
                            generator.generateWithLanguage(project, entityMetadata, packageConfig, targetLanguage)
                        } else {
                            generator.generate(project, entityMetadata, packageConfig)
                        }

                        val targetPath = generator.getTargetFilePath(project, entityMetadata, packageConfig)
                        writeCodeToFile(targetPath, code)
                        generatedFiles.add(targetPath)

                    } catch (e: Exception) {
                        val generatorName = generator.javaClass.simpleName
                        Messages.showErrorDialog(
                            project,
                            "Failed to generate $generatorName: ${e.message}",
                            "Generation Error"
                        )
                    }
                }

                showSuccessMessage(project, generatedFiles, targetLanguage)

            } catch (e: Exception) {
                showGenerationError(project, e)
            }
        }
    }

    /**
     * Creates generators based on selected components.
     */
    private fun createGenerators(selectedComponents: Set<String>): List<CodeGenerator> {
        val generators = mutableListOf<CodeGenerator>()

        selectedComponents.forEach { component ->
            when (component.lowercase()) {
                "controller" -> generators.add(ControllerGenerator())
                "service" -> generators.add(ServiceGenerator())
                "repository" -> generators.add(RepositoryGenerator())
                "dto" -> generators.add(DtoGenerator())
                "mapper" -> generators.add(MapperGenerator())
                "test" -> generators.add(TestGenerator())
                "swaggerconfig" -> generators.add(SwaggerConfigGenerator())
                "globalexceptionhandler" -> generators.add(GlobalExceptionHandlerGenerator())
                "securityconfig" -> generators.add(SecurityConfigGenerator())
                "graphql" -> generators.add(GraphQLGenerator())
                "openapiconfig" -> generators.add(OpenApiConfigGenerator())
            }
        }

        return generators
    }

    /**
     * Writes generated code to a file.
     */
    private fun writeCodeToFile(filePath: String, code: String) {
        val file = File(filePath)
        file.parentFile?.mkdirs()
        file.writeText(code)

        // Refresh the virtual file system
        LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
    }

    /**
     * Called to update UI components when action is visible.
     */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        // Enable for Java and Kotlin files in a project
        e.presentation.isEnabledAndVisible = project != null &&
            (psiFile is PsiJavaFile || (psiFile != null && psiFile.name.endsWith(".kt")))
    }

    /**
     * Which thread the update method will be called on.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    /**
     * Shows success message with language info.
     */
    private fun showSuccessMessage(project: Project, generatedFiles: List<String>, targetLanguage: String?) {
        val languageText = when (targetLanguage?.lowercase()) {
            "kotlin" -> "Kotlin"
            "java" -> "Java"
            else -> "code"
        }

        Messages.showInfoMessage(
            project,
            "Successfully generated ${generatedFiles.size} $languageText files:\n" +
            generatedFiles.joinToString("\n") { "• ${File(it).name}" },
            "Code Generation Complete"
        )
    }

    /**
     * Shows error when no entity is found.
     */
    private fun showNoEntityFoundError(project: Project, fileName: String) {
        val extension = if (fileName.endsWith(".kt")) "Kotlin" else "Java"
        Messages.showErrorDialog(
            project,
            "No JPA entity found in the selected $extension file. " +
            "Make sure the class is annotated with @Entity and properly configured.",
            "No Entity Found"
        )
    }

    /**
     * Shows generation error with helpful information.
     */
    private fun showGenerationError(project: Project, error: Exception) {
        Messages.showErrorDialog(
            project,
            "Failed to generate Spring code: ${error.message}\n\n" +
            "Please check that:\n" +
            "• The selected file contains a valid JPA entity\n" +
            "• All required dependencies are present\n" +
            "• The project structure is correct",
            "Generation Error"
        )
    }
}

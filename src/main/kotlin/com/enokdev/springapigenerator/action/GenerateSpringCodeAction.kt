package com.enokdev.springapigenerator.action

import com.enokdev.springapigenerator.generator.*
import com.enokdev.springapigenerator.generator.impl.*
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.*
import com.enokdev.springapigenerator.ui.GeneratorConfigDialog
import com.enokdev.springapigenerator.ui.LanguageSelectionDialog
import com.enokdev.springapigenerator.util.TemplateAnnotationFixer
import com.enokdev.springapigenerator.util.AnnotationInjector
import com.enokdev.springapigenerator.util.ForceAnnotationInjector
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
            // FORCER LA CORRECTION DES ANNOTATIONS EN PREMIER
            TemplateAnnotationFixer.applyAllAnnotationFixes(project)

            // Initialize flexible annotation system
            val annotationService = project.getService(AnnotationFlexibilityService::class.java)
            annotationService?.initializeFlexibilitySettings()

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

            // Check if class is compatible for generation (with flexible annotation support)
            val hasAnnotations = entityDetectionService.isJpaEntity(psiClass)
            val isCompatible = annotationService?.isClassCompatibleForGeneration(
                psiClass.name ?: "Unknown",
                hasAnnotations
            ) ?: true

            if (!isCompatible) {
                // Show suggestions to make class compatible
                val suggestions = annotationService?.getSuggestionsForClass(
                    psiClass.name ?: "Unknown",
                    "Entity",
                    hasAnnotations
                ) ?: emptyList()

                showCompatibilityWarning(project, psiClass.name ?: "Unknown", suggestions)
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
            // FIX: Use the package configuration from the dialog instead of ignoring user input
            val packageConfig = dialog.getPackageConfig()

            // Handle dependencies and advanced features
            handleDependencies(project, dialog)

            generateCode(project, entityMetadata, selectedComponents, packageConfig, targetLanguage, dialog)
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
        targetLanguage: String?,
        dialog: GeneratorConfigDialog
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                // Create enhanced package configuration with custom query methods setting
                val enhancedPackageConfig = packageConfig.toMutableMap()
                enhancedPackageConfig["generateCustomQueryMethods"] = dialog.shouldGenerateCustomQueryMethods().toString()

                // Create generators based on selected components and dialog configuration
                val generators = createGenerators(selectedComponents, dialog)
                val generatedFiles = mutableListOf<String>()

                for (generator in generators) {
                    try {
                        val code = if (generator is AbstractTemplateCodeGenerator && targetLanguage != null) {
                            generator.generateWithLanguage(project, entityMetadata, enhancedPackageConfig, targetLanguage)
                        } else {
                            generator.generate(project, entityMetadata, enhancedPackageConfig)
                        }

                        val targetPath = generator.getTargetFilePath(project, entityMetadata, enhancedPackageConfig)
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

                // Generate advanced features if enabled
                generateAdvancedFeatures(project, entityMetadata, enhancedPackageConfig, dialog, generatedFiles)

                showSuccessMessage(project, generatedFiles, targetLanguage)

            } catch (e: Exception) {
                showGenerationError(project, e)
            }
        }
    }

    /**
     * Creates generators based on selected components and dialog configuration.
     */
    private fun createGenerators(selectedComponents: Set<String>, dialog: GeneratorConfigDialog): List<CodeGenerator> {
        val generators = mutableListOf<CodeGenerator>()

        selectedComponents.forEach { component ->
            when (component.lowercase()) {
                "controller" -> generators.add(ControllerGenerator())
                "service" -> {
                    // Add both service interface and implementation generators
                    generators.add(ServiceGenerator())
                    generators.add(ServiceImplGenerator())
                }
                "repository" -> {
                    // Use custom query methods configuration from dialog
                    val repositoryGenerator = RepositoryGenerator()
                    generators.add(repositoryGenerator)
                }
                "dto" -> generators.add(DtoGenerator())
                "mapper" -> generators.add(MapperGenerator())
                "test" -> generators.add(TestGenerator())
            }
        }

        // Add additional generators based on dialog configuration
        if (dialog.shouldAddSwagger()) {
            generators.add(SwaggerConfigGenerator())
        }

        if (dialog.shouldAddSpringSecurity()) {
            generators.add(SecurityConfigGenerator())
            generators.add(GlobalExceptionHandlerGenerator())
        }

        if (dialog.shouldAddGraphQL()) {
            generators.add(GraphQLGenerator())
        }

        if (dialog.shouldAddOpenApi()) {
            generators.add(OpenApiConfigGenerator())
        }

        return generators
    }

    /**
     * Generates advanced features if enabled in the dialog.
     */
    private fun generateAdvancedFeatures(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        dialog: GeneratorConfigDialog,
        generatedFiles: MutableList<String>
    ) {
        try {
            // Generate schema migrations if enabled
            if (dialog.shouldGenerateSchemaMigration()) {
                val migrationGenerator = SchemaMigrationGenerator()
                val migrationCode = migrationGenerator.generateEntityMigration(project, entityMetadata, packageConfig)

                // Create migration file path
                val migrationFileName = "V${System.currentTimeMillis()}_create_${entityMetadata.entityNameLower}_table.sql"
                val migrationPath = Paths.get(
                    getProjectResourcesDir(project),
                    "db", "migration", migrationFileName
                ).toString()

                writeCodeToFile(migrationPath, migrationCode)
                generatedFiles.add(migrationPath)
            }

            // Generate advanced JPA features if enabled
            if (dialog.shouldGenerateAdvancedJpa()) {
                generateAdvancedJpaFeatures(project, entityMetadata, packageConfig, generatedFiles)
            }

        } catch (e: Exception) {
            Messages.showWarningDialog(
                project,
                "Some advanced features could not be generated: ${e.message}",
                "Advanced Features Warning"
            )
        }
    }

    /**
     * Generates advanced JPA features like composite keys and custom repositories.
     */
    private fun generateAdvancedJpaFeatures(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        generatedFiles: MutableList<String>
    ) {
        try {
            val sourceRoot = getProjectSourceDir(project)
            val basePackagePath = packageConfig["basePackage"]?.replace(".", "/") ?: ""

            // Create a style adapter for the advanced generators
            val codeStyleDetector = CodeStyleDetector()
            val styleConfig = codeStyleDetector.detectCodeStyle(project)
            val styleAdapter = CodeStyleAdapter(styleConfig)

            // Check if entity has potential for composite key (multiple ID-like fields)
            val hasMultipleKeys = entityMetadata.fields.count { field ->
                field.name.lowercase().contains("id") ||
                field.name.lowercase() == "key" ||
                !field.nullable
            } > 1

            // Generate composite key if needed
            if (hasMultipleKeys) {
                val compositeKeyGenerator = CompositeKeyGenerator()
                val outputDir = File(sourceRoot, basePackagePath + "/model")
                outputDir.mkdirs()

                val compositeKeyFile = compositeKeyGenerator.generateCompositeKey(
                    entityMetadata, packageConfig, styleAdapter, project, outputDir
                )
                generatedFiles.add(compositeKeyFile.absolutePath)
            }

            // Check if entity has embedded-like fields (complex value objects)
            val hasEmbeddedFields = entityMetadata.fields.any { field ->
                !field.isSimpleType && field.relationType == com.enokdev.springapigenerator.model.RelationType.NONE
            }

            // Generate embedded ID if needed
            if (hasEmbeddedFields) {
                val embeddedIdGenerator = EmbeddedIdGenerator()
                val outputDir = File(sourceRoot, basePackagePath + "/model")
                outputDir.mkdirs()

                val embeddedIdFile = embeddedIdGenerator.generateEmbeddedId(
                    entityMetadata, packageConfig, styleAdapter, project, outputDir
                )
                generatedFiles.add(embeddedIdFile.absolutePath)
            }

            // Generate advanced JPA entity features using standard generator interface
            try {
                val advancedJpaGenerator = AdvancedJpaEntityGenerator()
                val advancedJpaCode = advancedJpaGenerator.generate(project, entityMetadata, packageConfig)
                val advancedJpaPath = Paths.get(
                    sourceRoot,
                    basePackagePath,
                    "model", "${entityMetadata.className}Advanced.java"
                ).toString()
                writeCodeToFile(advancedJpaPath, advancedJpaCode)
                generatedFiles.add(advancedJpaPath)
            } catch (e: Exception) {
                // Advanced JPA generator might not be available, skip silently
            }

            // Generate entity with relationships using standard generator interface
            try {
                val entityWithRelationshipsGenerator = EntityWithRelationshipsGenerator()
                val entityRelationshipsCode = entityWithRelationshipsGenerator.generate(project, entityMetadata, packageConfig)
                val entityRelationshipsPath = Paths.get(
                    sourceRoot,
                    basePackagePath,
                    "model", "${entityMetadata.className}WithRelationships.java"
                ).toString()
                writeCodeToFile(entityRelationshipsPath, entityRelationshipsCode)
                generatedFiles.add(entityRelationshipsPath)
            } catch (e: Exception) {
                // Entity with relationships generator might not be available, skip silently
            }

            // Check if entity has complex ID logic (non-simple ID type)
            val hasComplexId = entityMetadata.fields.any { field ->
                field.name.lowercase().contains("id") && !field.isSimpleType
            }

            // Generate custom ID repository if needed
            if (hasComplexId) {
                try {
                    val customIdRepositoryGenerator = CustomIdRepositoryGenerator()
                    val outputDir = File(sourceRoot, packageConfig["repositoryPackage"]?.replace(".", "/") ?: "")
                    outputDir.mkdirs()

                    val customIdRepositoryFile = customIdRepositoryGenerator.generateRepository(
                        entityMetadata, packageConfig, styleAdapter, project, outputDir
                    )
                    generatedFiles.add(customIdRepositoryFile.absolutePath)
                } catch (e: Exception) {
                    // Custom ID repository generator might not be available, skip silently
                }
            }

        } catch (e: Exception) {
            throw RuntimeException("Failed to generate advanced JPA features: ${e.message}", e)
        }
    }

    /**
     * Gets the project resources directory.
     */
    private fun getProjectResourcesDir(project: Project): String {
        val projectPath = project.basePath ?: ""
        return Paths.get(projectPath, "src", "main", "resources").toString()
    }

    /**
     * Gets the project source directory.
     */
    private fun getProjectSourceDir(project: Project): String {
        val projectPath = project.basePath ?: ""
        return Paths.get(projectPath, "src", "main", "java").toString()
    }

    /**
     * Writes generated code to a file.
     */
    private fun writeCodeToFile(filePath: String, code: String) {
        val file = File(filePath)
        file.parentFile?.mkdirs()
        
        // INJECTION ULTRA-ROBUSTE D'ANNOTATIONS - APPROCHE GARANTIE
        val fileName = file.name
        var finalCode = code

        // Utiliser le nouveau ForceAnnotationInjector qui est beaucoup plus robuste
        finalCode = ForceAnnotationInjector.forceAnnotationsInAllFiles(finalCode, fileName)

        file.writeText(finalCode)

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

    /**
     * Handles dependencies and advanced features based on dialog configuration.
     */
    private fun handleDependencies(project: Project, dialog: GeneratorConfigDialog) {
        // Add MapStruct dependency if needed
        if (dialog.shouldAddMapstruct()) {
            val (buildSystem, dependency) = dialog.getMapstructDependencyInfo()
            showDependencyInfo(project, "MapStruct", buildSystem, dependency)
        }

        // Add Swagger dependency if needed
        if (dialog.shouldAddSwagger()) {
            val (buildSystem, dependency) = dialog.getSwaggerDependencyInfo()
            showDependencyInfo(project, "Swagger/OpenAPI", buildSystem, dependency)
        }

        // Add Spring Security dependency if needed
        if (dialog.shouldAddSpringSecurity()) {
            val (buildSystem, dependency) = dialog.getSpringSecurityDependencyInfo()
            showDependencyInfo(project, "Spring Security", buildSystem, dependency)
        }

        // Add GraphQL dependency if needed
        if (dialog.shouldAddGraphQL()) {
            val (buildSystem, dependency) = dialog.getGraphQLDependencyInfo()
            showDependencyInfo(project, "GraphQL", buildSystem, dependency)
        }

        // Add OpenAPI dependency if needed
        if (dialog.shouldAddOpenApi()) {
            val (buildSystem, dependency) = dialog.getOpenApiDependencyInfo()
            showDependencyInfo(project, "OpenAPI 3.0", buildSystem, dependency)
        }
    }

    /**
     * Shows dependency information to the user.
     */
    private fun showDependencyInfo(project: Project, dependencyName: String, buildSystem: String, dependency: String) {
        Messages.showInfoMessage(
            project,
            "Please add the following $dependencyName dependency to your $buildSystem build file:\n\n$dependency",
            "Add $dependencyName Dependency"
        )
    }

    /**
     * Shows compatibility warning with suggestions to fix annotation issues.
     */
    private fun showCompatibilityWarning(project: Project, className: String, suggestions: List<String>) {
        val message = buildString {
            append("La classe '$className' peut ne pas être entièrement compatible pour la génération.\n\n")
            if (suggestions.isNotEmpty()) {
                append("Suggestions :\n")
                suggestions.forEach { suggestion ->
                    append("• $suggestion\n")
                }
            }
            append("\nVoulez-vous continuer la génération en mode flexible ?")
        }

        val result = Messages.showYesNoDialog(
            project,
            message,
            "Compatibilité des Annotations",
            "Continuer",
            "Annuler",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            // Enable flexible mode and try again
            val annotationService = project.getService(AnnotationFlexibilityService::class.java)
            annotationService?.setStrictAnnotationValidation(false)

            // User chose to continue, so we can proceed with generation
            // The calling method will handle the actual generation
        }
    }
}

package com.enokdev.springapigenerator.action

import com.enokdev.springapigenerator.service.EntityAnalyzer
import com.enokdev.springapigenerator.service.EntityDetectionService
import com.enokdev.springapigenerator.ui.GeneratorConfigDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.openapi.ui.Messages
import com.enokdev.springapigenerator.generator.impl.*
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import java.nio.file.Paths


/**
 * Action for generating Spring REST code from JPA entities.
 * This action is shown in the context menu when right-clicking on a Java file.
 */
class GenerateSpringCodeAction : AnAction() {

    /**
     * Called when the action is performed by the user.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        if (psiFile !is PsiJavaFile) {
            Messages.showErrorDialog(
                project,
                "Please select a Java file containing a JPA entity.",
                "Wrong File Type"
            )
            return
        }

        val classes = psiFile.classes
        if (classes.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "No classes found in the selected file.",
                "No Class Found"
            )
            return
        }

        // Try to find an entity class in the file
        val entityDetectionService = EntityDetectionService(project)
        val entityClass = ReadAction.compute<PsiClass?, Throwable> {
            classes.find { entityDetectionService.isJpaEntity(it) }
        }

        if (entityClass == null) {
            Messages.showErrorDialog(
                project,
                "No JPA entity found in the selected file. Make sure the class has @Entity and @Id annotations.",
                "No Entity Found"
            )
            return
        }

        // Analyze the entity and extract metadata
        val entityAnalyzer = EntityAnalyzer()
        val entityMetadata = ReadAction.compute<EntityMetadata, Throwable> {
            entityAnalyzer.analyzeEntity(entityClass)
        }

        // Show configuration dialog
        val dialog = GeneratorConfigDialog(project, entityMetadata)
        if (dialog.showAndGet()) {
            // User confirmed, generate code based on configuration
            val selectedComponents = dialog.getSelectedComponents()
            val packageConfig = dialog.getPackageConfig()

            try {
                // Check if MapStruct should be added
                if (dialog.shouldAddMapstruct()) {
                    val (buildSystemType, dependency) = dialog.getMapstructDependencyInfo()
                    val result = Messages.showOkCancelDialog(
                        project,
                        """
                        MapStruct n'a pas été détecté dans votre projet. Voulez-vous ajouter MapStruct 1.6.3 à votre projet?
                        
                        La dépendance suivante sera nécessaire pour $buildSystemType:
                        
                        $dependency
                        """.trimIndent(),
                        "Ajouter MapStruct 1.6.3",
                        "Ajouter",
                        "Non",
                        Messages.getQuestionIcon()
                    )

                    if (result == Messages.OK) {
                        addMapstructDependency(project, buildSystemType)
                    }
                }

                // Check if Swagger should be added
                if (dialog.shouldAddSwagger()) {
                    val (buildSystemType, dependency) = dialog.getSwaggerDependencyInfo()
                    val result = Messages.showOkCancelDialog(
                        project,
                        """
                        Swagger/OpenAPI n'a pas été détecté dans votre projet. Voulez-vous ajouter SpringDoc OpenAPI 2.8.0 à votre projet?
                        
                        La dépendance suivante sera nécessaire pour $buildSystemType:
                        
                        $dependency
                        """.trimIndent(),
                        "Ajouter Swagger/OpenAPI",
                        "Ajouter",
                        "Non",
                        Messages.getQuestionIcon()
                    )

                    if (result == Messages.OK) {
                        addSwaggerDependency(project, buildSystemType)
                    }
                }

                // Generate code for each selected component
                val generatedFiles = mutableListOf<String>()
                val progressTitle = "Generating Spring Boot Code"
                val progressIndicator = com.intellij.openapi.progress.ProgressManager.getInstance().run(
                    object : com.intellij.openapi.progress.Task.Backgroundable(project, progressTitle, false) {
                        override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                            indicator.isIndeterminate = false
                            val totalSteps = selectedComponents.size
                            var currentStep = 0

                            if (selectedComponents.contains("dto")) {
                                indicator.text = "Generating DTOs..."
                                indicator.fraction = (++currentStep).toDouble() / totalSteps
                                val dtoGenerator = DtoGenerator()
                                val dtoContent = dtoGenerator.generate(project, entityMetadata, packageConfig)
                                val dtoFile = dtoGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                writeToFile(project, dtoFile, dtoContent)
                                generatedFiles.add(dtoFile)
                            }

                            if (selectedComponents.contains("mapper")) {
                                indicator.text = "Generating Mappers..."
                                indicator.fraction = (++currentStep).toDouble() / totalSteps
                                val mapperGenerator = MapperGenerator()
                                val mapperContent = mapperGenerator.generate(project, entityMetadata, packageConfig)
                                val mapperFile = mapperGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                writeToFile(project, mapperFile, mapperContent)
                                generatedFiles.add(mapperFile)
                            }

                            if (selectedComponents.contains("repository")) {
                                indicator.text = "Generating Repositories..."
                                indicator.fraction = (++currentStep).toDouble() / totalSteps
                                val repositoryGenerator = RepositoryGenerator()
                                val repositoryContent = repositoryGenerator.generate(project, entityMetadata, packageConfig)
                                val repositoryFile = repositoryGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                writeToFile(project, repositoryFile, repositoryContent)
                                generatedFiles.add(repositoryFile)
                            }

                            if (selectedComponents.contains("service")) {
                                indicator.text = "Generating Services..."
                                indicator.fraction = (++currentStep).toDouble() / totalSteps
                                val serviceGenerator = ServiceGenerator()
                                val serviceContent = serviceGenerator.generate(project, entityMetadata, packageConfig)
                                val serviceFile = serviceGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                writeToFile(project, serviceFile, serviceContent)
                                // ServiceImpl is handled internally by ServiceGenerator
                                generatedFiles.add(serviceFile)
                            }

                            if (selectedComponents.contains("controller")) {
                                indicator.text = "Generating Controllers..."
                                indicator.fraction = (++currentStep).toDouble() / totalSteps
                                val controllerGenerator = ControllerGenerator()
                                val controllerContent = controllerGenerator.generate(project, entityMetadata, packageConfig)
                                val controllerFile = controllerGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                writeToFile(project, controllerFile, controllerContent)
                                generatedFiles.add(controllerFile)

                                // Relationship controller generation has been disabled
                                // Users will implement these controllers manually if needed
                            }

                            if (selectedComponents.contains("test")) {
                                indicator.text = "Generating Tests..."
                                indicator.fraction = (++currentStep).toDouble() / totalSteps
                                val testGenerator = TestGenerator()
                                val testContent = testGenerator.generate(project, entityMetadata, packageConfig)
                                val testFile = testGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                writeToFile(project, testFile, testContent)
                                generatedFiles.add(testFile)
                            }

                            // Generate Swagger configuration if needed
                            ReadAction.compute<Boolean, Throwable> {
                                val hasSwaggerConfig = FilenameIndex.getFilesByName(
                                    project,
                                    "SwaggerConfig.java",
                                    GlobalSearchScope.projectScope(project)
                                ).any()
                                selectedComponents.contains("controller") && !hasSwaggerConfig
                            }.let { shouldGenerateSwagger ->
                                if (shouldGenerateSwagger) {
                                    indicator.text = "Generating Swagger Configuration..."
                                    val swaggerGenerator = SwaggerConfigGenerator()
                                    val swaggerContent = swaggerGenerator.generate(project, entityMetadata, packageConfig)
                                    val swaggerFile = swaggerGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                    writeToFile(project, swaggerFile, swaggerContent)
                                    generatedFiles.add(swaggerFile)
                                }
                            }

                            // Generate Global Exception Handler if needed
                            ReadAction.compute<Boolean, Throwable> {
                                val hasExceptionHandler = FilenameIndex.getFilesByName(
                                    project,
                                    "GlobalExceptionHandler.java",
                                    GlobalSearchScope.projectScope(project)
                                ).any()
                                selectedComponents.contains("controller") && !hasExceptionHandler
                            }.let { shouldGenerateExceptionHandler ->
                                if (shouldGenerateExceptionHandler) {
                                    indicator.text = "Generating Exception Handler..."
                                    val exceptionGenerator = GlobalExceptionHandlerGenerator()
                                    val exceptionContent = exceptionGenerator.generate(project, entityMetadata, packageConfig)
                                    val exceptionFile = exceptionGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                    writeToFile(project, exceptionFile, exceptionContent)
                                    generatedFiles.add(exceptionFile)
                                }
                            }

                            indicator.text = "Refreshing Files..."
                            indicator.fraction = 1.0
                            ApplicationManager.getApplication().invokeLater {
                                WriteAction.runAndWait<Throwable> {
                                    VfsUtil.markDirtyAndRefresh(
                                        true, true, true,
                                        LocalFileSystem.getInstance().findFileByPath(project.basePath!!)
                                    )
                                }
                            }
                        }

                        override fun onSuccess() {
                            ApplicationManager.getApplication().invokeLater {
                                Messages.showInfoMessage(
                                    project,
                                    "Successfully generated ${generatedFiles.size} files for entity ${entityMetadata.className}",
                                    "Code Generation Complete"
                                )

                                // Open the first generated file in the editor
                                if (generatedFiles.isNotEmpty()) {
                                    val file = LocalFileSystem.getInstance().findFileByPath(generatedFiles.first())
                                    if (file != null) {
                                        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(file, true)
                                    }
                                }
                            }
                        }

                        override fun onThrowable(error: Throwable) {
                            ApplicationManager.getApplication().invokeLater {
                                Messages.showErrorDialog(
                                    project,
                                    "Error generating code: ${error.message}",
                                    "Generation Error"
                                )
                                error.printStackTrace()
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Error generating code: ${e.message}",
                    "Generation Error"
                )
                e.printStackTrace()
            }
        }
    }

    /**
     * Write content to a file, ensuring directories exist.
     */
    private fun writeToFile(project: Project, filePath: String, content: String) {
        val file = File(filePath)
        file.parentFile.mkdirs()
        file.writeText(content)

        // Refresh the file in IDE using proper write action
        ApplicationManager.getApplication().invokeLater {
            WriteAction.runAndWait<Throwable> {
                LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
            }
        }
    }

    /**
     * Adds MapStruct dependency to the build file
     */
    private fun addMapstructDependency(project: Project, buildSystemType: String) {
        val basePath = project.basePath ?: return

        when (buildSystemType) {
            "Maven" -> {
                val pomXml = File(Paths.get(basePath, "pom.xml").toString())
                if (pomXml.exists()) {
                    val content = pomXml.readText()
                    // Simple approach to add dependencies, for a more robust solution a proper XML parser would be needed
                    val dependenciesTag = "<dependencies>"
                    val index = content.indexOf(dependenciesTag)
                    if (index != -1) {
                        val updatedContent = StringBuilder(content).insert(
                            index + dependenciesTag.length,
                            """
                            
                            <!-- MapStruct for object mapping -->
                            <dependency>
                                <groupId>org.mapstruct</groupId>
                                <artifactId>mapstruct</artifactId>
                                <version>1.6.3</version>
                            </dependency>
                            <dependency>
                                <groupId>org.mapstruct</groupId>
                                <artifactId>mapstruct-processor</artifactId>
                                <version>1.6.3</version>
                                <scope>provided</scope>
                            </dependency>
                            """
                        ).toString()
                        pomXml.writeText(updatedContent)
                    }
                }
            }
            "Gradle Kotlin" -> {
                val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
                if (buildGradleKts.exists()) {
                    val content = buildGradleKts.readText()
                    val dependenciesBlock = "dependencies {"
                    val index = content.indexOf(dependenciesBlock)
                    if (index != -1) {
                        val updatedContent = StringBuilder(content).insert(
                            index + dependenciesBlock.length,
                            """
                            
                            // MapStruct for object mapping
                            implementation("org.mapstruct:mapstruct:1.6.3")
                            annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
                            """
                        ).toString()
                        buildGradleKts.writeText(updatedContent)
                    }
                }
            }
            else -> {
                val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
                if (buildGradle.exists()) {
                    val content = buildGradle.readText()
                    val dependenciesBlock = "dependencies {"
                    val index = content.indexOf(dependenciesBlock)
                    if (index != -1) {
                        val updatedContent = StringBuilder(content).insert(
                            index + dependenciesBlock.length,
                            """
                            
                            // MapStruct for object mapping
                            implementation 'org.mapstruct:mapstruct:1.6.3'
                            annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'
                            """
                        ).toString()
                        buildGradle.writeText(updatedContent)
                    }
                }
            }
        }

        // Refresh files in IDE
        ApplicationManager.getApplication().invokeLater {
            WriteAction.runAndWait<Throwable> {
                LocalFileSystem.getInstance().refresh(false)
            }
        }
    }

    /**
     * Adds Swagger dependency to the build file
     */
    private fun addSwaggerDependency(project: Project, buildSystemType: String) {
        val basePath = project.basePath ?: return

        when (buildSystemType) {
            "Maven" -> {
                val pomXml = File(Paths.get(basePath, "pom.xml").toString())
                if (pomXml.exists()) {
                    val content = pomXml.readText()
                    // Simple approach to add dependencies, for a more robust solution a proper XML parser would be needed
                    val dependenciesTag = "<dependencies>"
                    val index = content.indexOf(dependenciesTag)
                    if (index != -1) {
                        val updatedContent = StringBuilder(content).insert(
                            index + dependenciesTag.length,
                            """
                            
                            <!-- SpringDoc OpenAPI for API documentation -->
                            <dependency>
                                <groupId>org.springdoc</groupId>
                                <artifactId>springdoc-openapi-ui</artifactId>
                                <version>2.8.0</version>
                            </dependency>
                            """
                        ).toString()
                        pomXml.writeText(updatedContent)
                    }
                }
            }
            "Gradle Kotlin" -> {
                val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
                if (buildGradleKts.exists()) {
                    val content = buildGradleKts.readText()
                    val dependenciesBlock = "dependencies {"
                    val index = content.indexOf(dependenciesBlock)
                    if (index != -1) {
                        val updatedContent = StringBuilder(content).insert(
                            index + dependenciesBlock.length,
                            """
                            
                            // SpringDoc OpenAPI for API documentation
                            implementation("org.springdoc:springdoc-openapi-ui:2.8.0")
                            """
                        ).toString()
                        buildGradleKts.writeText(updatedContent)
                    }
                }
            }
            else -> {
                val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
                if (buildGradle.exists()) {
                    val content = buildGradle.readText()
                    val dependenciesBlock = "dependencies {"
                    val index = content.indexOf(dependenciesBlock)
                    if (index != -1) {
                        val updatedContent = StringBuilder(content).insert(
                            index + dependenciesBlock.length,
                            """
                            
                            // SpringDoc OpenAPI for API documentation
                            implementation 'org.springdoc:springdoc-openapi-ui:2.8.0'
                            """
                        ).toString()
                        buildGradle.writeText(updatedContent)
                    }
                }
            }
        }

        // Refresh files in IDE
        ApplicationManager.getApplication().invokeLater {
            WriteAction.runAndWait<Throwable> {
                LocalFileSystem.getInstance().refresh(false)
            }
        }
    }

    /**
     * Called to update UI components when action is visible.
     */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        // Enable only for Java files in a project
        e.presentation.isEnabledAndVisible = project != null && psiFile is PsiJavaFile
    }

    /**
     * Which thread the update method will be called on.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

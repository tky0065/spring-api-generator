package com.enokdev.springapigenerator.action

import com.enokdev.springapigenerator.generator.impl.*
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.EntityAnalyzer
import com.enokdev.springapigenerator.service.EntityDetectionService
import com.enokdev.springapigenerator.ui.GeneratorConfigDialog
import com.enokdev.springapigenerator.util.BuildSystemHelper
import com.enokdev.springapigenerator.util.FileHelper
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File


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

        // Accepter à la fois les fichiers Java et Kotlin
        val entityDetectionService = EntityDetectionService(project)
        val psiClass = when {
            psiFile is PsiJavaFile -> {
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
                ReadAction.compute<PsiClass?, Throwable> {
                    classes.find { entityDetectionService.isJpaEntity(it) }
                }
            }
            psiFile.name.endsWith(".kt") -> {
                // Pour les fichiers Kotlin, il faut vérifier les classes Kotlin
                ReadAction.compute<PsiClass?, Throwable> {
                    val classes = psiFile.children.filterIsInstance<PsiClass>()
                    classes.find { entityDetectionService.isJpaEntity(it) }
                }
            }
            else -> {
                Messages.showErrorDialog(
                    project,
                    "Please select a Java or Kotlin file containing a JPA entity.",
                    "Wrong File Type"
                )
                return
            }
        }

        if (psiClass == null) {
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
            entityAnalyzer.analyzeEntity(psiClass)
        }

        // Show configuration dialog
        val dialog = GeneratorConfigDialog(project, entityMetadata)
        if (dialog.showAndGet()) {
            // User confirmed, generate code based on configuration
            val selectedComponents = dialog.getSelectedComponents()
            val packageConfig = dialog.getPackageConfig()

            // Déclarer securityConfig ici pour qu'il soit disponible dans tout le scope
            var securityConfig = dialog.getSecurityConfig()

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
                        BuildSystemHelper.addMapstructDependency(project, buildSystemType)
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
                        BuildSystemHelper.addSwaggerDependency(project, buildSystemType)
                    }
                }

                // Check if Spring Security should be added
                if (dialog.shouldAddSpringSecurity()) {
                    val (buildSystemType, dependency) = dialog.getSpringSecurityDependencyInfo()
                    val result = Messages.showOkCancelDialog(
                        project,
                        """
                        Spring Security n'a pas été détecté dans votre projet. Voulez-vous ajouter Spring Security avec JWT à votre projet?
                        
                        Les dépendances suivantes seront nécessaires pour $buildSystemType:
                        
                        $dependency
                        """.trimIndent(),
                        "Ajouter Spring Security",
                        "Ajouter",
                        "Non",
                        Messages.getQuestionIcon()
                    )

                    if (result == Messages.OK) {
                        BuildSystemHelper.addSpringSecurityDependency(project, buildSystemType)

                        // Generate security files
                        val securityConfig = dialog.getSecurityConfig()
                        if (securityConfig != null) {
                            WriteCommandAction.runWriteCommandAction(project) {
                                val securityGenerator = SecurityConfigGenerator()

                                // Generate the main security config
                                val securityConfigContent = securityGenerator.generate(project, entityMetadata, packageConfig)
                                val securityConfigPath = securityGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                val securityConfigFile = File(securityConfigPath)
                                securityConfigFile.parentFile.mkdirs()
                                securityConfigFile.writeText(securityConfigContent)

                                // Generate User management components
                                securityGenerator.generateUserModel(project, entityMetadata, packageConfig)
                                securityGenerator.generateUserRepository(project, entityMetadata, packageConfig)
                                securityGenerator.generateUserService(project, entityMetadata, packageConfig)
                                securityGenerator.generateAuthController(project, entityMetadata, packageConfig)

                                // Generate JWT util if needed
                                if (securityConfig.securityLevel == SecurityConfigGenerator.SecurityLevel.JWT) {
                                    securityGenerator.generateJwtUtil(project, entityMetadata, packageConfig)
                                }

                                // Generate user details service if requested
                                if (securityConfig.generateUserDetailsService) {
                                    securityGenerator.generateUserDetailsService(project, entityMetadata, packageConfig)
                                }

                                // Refresh the project view
                                LocalFileSystem.getInstance().refresh(true)

                                // Show confirmation message
                                ApplicationManager.getApplication().invokeLater {
                                    Messages.showInfoMessage(
                                        project,
                                        "Spring Security configuration files have been generated successfully.",
                                        "Spring Security Generated"
                                    )
                                }
                            }
                        }
                    }
                }

                // Check if GraphQL should be added
                val graphqlOption = dialog.getGraphQLOption()
                if (graphqlOption == true) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        // Ajouter GraphQL dependencies automatiquement
                        BuildSystemHelper.addGraphQLDependency(project, BuildSystemHelper.detectBuildSystemType(project))

                        val graphQLGenerator = GraphQLGenerator()

                        // Generate GraphQL schema
                        val schemaContent = graphQLGenerator.generateSchema(project, entityMetadata, packageConfig)
                        val schemaPath = graphQLGenerator.getSchemaFilePath(project, packageConfig)
                        val schemaFile = File(schemaPath)
                        schemaFile.parentFile.mkdirs()
                        schemaFile.writeText(schemaContent)

                        // Generate GraphQL config
                        val configContent = graphQLGenerator.generateConfig(project, entityMetadata, packageConfig)
                        val configPath = graphQLGenerator.getConfigFilePath(project, packageConfig)
                        val configFile = File(configPath)
                        configFile.parentFile.mkdirs()
                        configFile.writeText(configContent)

                        // Generate GraphQL controller
                        val controllerContent = graphQLGenerator.generateController(project, entityMetadata, packageConfig)
                        val controllerPath = graphQLGenerator.getControllerFilePath(project, entityMetadata, packageConfig)
                        val controllerFile = File(controllerPath)
                        controllerFile.parentFile.mkdirs()
                        controllerFile.writeText(controllerContent)

                        // Toujours générer les fichiers de sécurité pour GraphQL
                        val securityGenerator = SecurityConfigGenerator()

                        // Generate the main security config
                        val securityConfigContent = securityGenerator.generate(project, entityMetadata, packageConfig)
                        val securityConfigPath = securityGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                        val securityConfigFile = File(securityConfigPath)
                        securityConfigFile.parentFile.mkdirs()
                        securityConfigFile.writeText(securityConfigContent)

                        // Generate User management components
                        securityGenerator.generateUserModel(project, entityMetadata, packageConfig)
                        securityGenerator.generateUserRepository(project, entityMetadata, packageConfig)
                        securityGenerator.generateUserService(project, entityMetadata, packageConfig)
                        securityGenerator.generateAuthController(project, entityMetadata, packageConfig)

                        // Generate JWT util
                        securityGenerator.generateJwtUtil(project, entityMetadata, packageConfig)

                        // Generate user details service
                        securityGenerator.generateUserDetailsService(project, entityMetadata, packageConfig)

                        // Ajouter Spring Security dependencies automatiquement
                        BuildSystemHelper.addSpringSecurityDependency(project, BuildSystemHelper.detectBuildSystemType(project))

                        // Refresh the project view
                        LocalFileSystem.getInstance().refresh(true)

                        // Show confirmation message
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showInfoMessage(
                                project,
                                "GraphQL files and Security files have been generated successfully.",
                                "GraphQL and Security Generated"
                            )
                        }
                    }
                }

                // Check if OpenAPI 3.0 should be added
                if (dialog.shouldAddOpenApi()) {
                    val openApiInfo = dialog.getOpenApiDependencyInfo()
                    val buildSystemType = openApiInfo.first
                    val dependency = openApiInfo.second
                    val result = Messages.showOkCancelDialog(
                        project,
                        """
                        OpenAPI 3.0 n'a pas été détecté dans votre projet. Voulez-vous ajouter SpringDoc OpenAPI 3.0 à votre projet?
                        
                        La dépendance suivante sera nécessaire pour $buildSystemType:
                        
                        $dependency
                        """.trimIndent(),
                        "Ajouter OpenAPI 3.0",
                        "Ajouter",
                        "Non",
                        Messages.getQuestionIcon()
                    )

                    if (result == Messages.OK) {
                        BuildSystemHelper.addOpenApiDependency(project, buildSystemType)
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
                                FileHelper.writeToFile(project, dtoFile, dtoContent)
                                generatedFiles.add(dtoFile)
                            }

                            if (selectedComponents.contains("mapper")) {
                                indicator.text = "Generating Mappers..."
                                indicator.fraction = (++currentStep).toDouble() / totalSteps
                                val mapperGenerator = MapperGenerator()
                                val mapperContent = mapperGenerator.generate(project, entityMetadata, packageConfig)
                                val mapperFile = mapperGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                FileHelper.writeToFile(project, mapperFile, mapperContent)
                                generatedFiles.add(mapperFile)
                            }

                            if (selectedComponents.contains("repository")) {
                                indicator.text = "Generating Repositories..."
                                indicator.fraction = (++currentStep).toDouble() / totalSteps
                                val repositoryGenerator = RepositoryGenerator()
                                val repositoryContent = repositoryGenerator.generate(project, entityMetadata, packageConfig)
                                val repositoryFile = repositoryGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                FileHelper.writeToFile(project, repositoryFile, repositoryContent)
                                generatedFiles.add(repositoryFile)
                            }

                            if (selectedComponents.contains("service")) {
                                indicator.text = "Generating Services..."
                                indicator.fraction = (++currentStep).toDouble() / totalSteps
                                val serviceGenerator = ServiceGenerator()
                                val serviceContent = serviceGenerator.generate(project, entityMetadata, packageConfig)
                                val serviceFile = serviceGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                FileHelper.writeToFile(project, serviceFile, serviceContent)
                                // ServiceImpl is handled internally by ServiceGenerator
                                generatedFiles.add(serviceFile)
                            }

                            if (selectedComponents.contains("controller")) {
                                indicator.text = "Generating Controllers..."
                                indicator.fraction = (++currentStep).toDouble() / totalSteps
                                val controllerGenerator = ControllerGenerator()
                                val controllerContent = controllerGenerator.generate(project, entityMetadata, packageConfig)
                                val controllerFile = controllerGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                FileHelper.writeToFile(project, controllerFile, controllerContent)
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
                                FileHelper.writeToFile(project, testFile, testContent)
                                generatedFiles.add(testFile)
                            }

                            // Generate Swagger configuration if needed
                            ReadAction.compute<Boolean, Throwable> {
                                // Utilisation de getVirtualFilesByName avec les bons paramètres
                                val hasSwaggerConfig = FilenameIndex.getVirtualFilesByName(
                                    "SwaggerConfig.java",
                                    true, // caseSensitively
                                    GlobalSearchScope.projectScope(project)
                                ).any()

                                selectedComponents.contains("controller") && !hasSwaggerConfig
                            }.let { shouldGenerateSwagger ->
                                if (shouldGenerateSwagger) {
                                    indicator.text = "Generating Swagger Configuration..."
                                    val swaggerGenerator = SwaggerConfigGenerator()
                                    val swaggerContent = swaggerGenerator.generate(project, entityMetadata, packageConfig)
                                    val swaggerFile = swaggerGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                    FileHelper.writeToFile(project, swaggerFile, swaggerContent)
                                    generatedFiles.add(swaggerFile)
                                }
                            }

                            // Generate Global Exception Handler if needed
                            ReadAction.compute<Boolean, Throwable> {
                                // Utilisation de getVirtualFilesByName avec les bons paramètres
                                val hasExceptionHandler = FilenameIndex.getVirtualFilesByName(
                                    "GlobalExceptionHandler.java",
                                    true, // caseSensitively
                                    GlobalSearchScope.projectScope(project)
                                ).any()

                                selectedComponents.contains("controller") && !hasExceptionHandler
                            }.let { shouldGenerateExceptionHandler ->
                                if (shouldGenerateExceptionHandler) {
                                    indicator.text = "Generating Exception Handler..."
                                    val exceptionGenerator = GlobalExceptionHandlerGenerator()
                                    val exceptionContent = exceptionGenerator.generate(project, entityMetadata, packageConfig)
                                    val exceptionFile = exceptionGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                    FileHelper.writeToFile(project, exceptionFile, exceptionContent)
                                    generatedFiles.add(exceptionFile)
                                }
                            }

                            // Generate OpenAPI 3.0 configuration if needed
                            ReadAction.compute<Boolean, Throwable> {
                                val hasOpenApiConfig = FilenameIndex.getVirtualFilesByName(
                                    "OpenApiConfig.java",
                                    true, // caseSensitively
                                    GlobalSearchScope.projectScope(project)
                                ).any()

                                dialog.shouldAddOpenApi() && !hasOpenApiConfig
                            }.let { shouldGenerateOpenApi ->
                                if (shouldGenerateOpenApi) {
                                    indicator.text = "Generating OpenAPI 3.0 Configuration..."
                                    val openApiGenerator = OpenApiConfigGenerator()
                                    val openApiContent = openApiGenerator.generate(project, entityMetadata, packageConfig)
                                    val openApiFile = openApiGenerator.getTargetFilePath(project, entityMetadata, packageConfig)
                                    FileHelper.writeToFile(project, openApiFile, openApiContent)
                                    generatedFiles.add(openApiFile)
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
}

package com.enokdev.springapigenerator.ui

import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import java.io.File
import java.nio.file.Paths

/**
 * Dialog for configuring code generation options.
 */
class GeneratorConfigDialog(
    private val project: Project,
    private val entityMetadata: EntityMetadata
) : DialogWrapper(project, true) {

    private val controllerCheckbox = JBCheckBox("Controller", true)
    private val serviceCheckbox = JBCheckBox("Service", true)
    private val dtoCheckbox = JBCheckBox("DTO", true)
    private val repositoryCheckbox = JBCheckBox("Repository", true)
    private val mapperCheckbox = JBCheckBox("Mapper", true)
    private val testCheckbox = JBCheckBox("Tests", true)
    private val useMapstructCheckbox = JBCheckBox("Use MapStruct 1.6.3", false)
    private val useSwaggerCheckbox = JBCheckBox("Use Swagger/OpenAPI 2.8.9", false)
    private val useOpenApiCheckbox = JBCheckBox("Use OpenAPI 3.0 Documentation", false)
    private val useSpringSecurityCheckbox = JBCheckBox("Add Spring Security", false)
    private val configureSecurityButton = JButton("Configure Security")
    private val useGraphQLCheckbox = JBCheckBox("Add GraphQL Support", false)
    private val configureGraphQLButton = JButton("Configure GraphQL")

    // Package configuration fields
    private val basePackageField = JBTextField(entityMetadata.entityBasePackage)
    private val domainPackageField = JBTextField(entityMetadata.domainPackage)
    private val dtoPackageField = JBTextField(entityMetadata.dtoPackage)
    private val controllerPackageField = JBTextField(entityMetadata.controllerPackage)
    private val servicePackageField = JBTextField(entityMetadata.servicePackage)
    private val repositoryPackageField = JBTextField(entityMetadata.repositoryPackage)
    private val mapperPackageField = JBTextField(entityMetadata.mapperPackage)

    // Flags to track whether dependencies should be added
    private var shouldAddMapstruct = false
    private var shouldAddSwagger = false
    private var shouldAddSpringSecurity = false

    // Security configuration
    private var securityConfig: SecurityConfigDialog.SecurityConfig? = null

    // GraphQL configuration
    private var shouldAddGraphQL = false
    private var graphQLConfig: GraphQLConfigDialog.GraphQLConfig? = null

    init {
        title = "Generate Spring Boot Code"
        setOKButtonText("Generate")

        // Add listener to mapper checkbox to enable/disable MapStruct checkbox
        mapperCheckbox.addChangeListener {
            useMapstructCheckbox.isEnabled = mapperCheckbox.isSelected
            if (!mapperCheckbox.isSelected) {
                useMapstructCheckbox.isSelected = false
            }
        }

        // Add listener to controller checkbox to enable/disable Swagger checkbox
        controllerCheckbox.addChangeListener {
            useSwaggerCheckbox.isEnabled = controllerCheckbox.isSelected
            if (!controllerCheckbox.isSelected) {
                useSwaggerCheckbox.isSelected = false
            }
        }

        // Configure the Security button action
        configureSecurityButton.isEnabled = false
        useSpringSecurityCheckbox.addChangeListener {
            configureSecurityButton.isEnabled = useSpringSecurityCheckbox.isSelected
        }

        configureSecurityButton.addActionListener {
            val dialog = SecurityConfigDialog(project)
            if (dialog.showAndGet()) {
                securityConfig = SecurityConfigDialog.SecurityConfig(
                    securityLevel = dialog.getSecurityLevel(),
                    generateUserDetailsService = dialog.shouldGenerateUserDetailsService(),
                    secureControllers = dialog.shouldSecureControllers(),
                    configureSwagger = dialog.shouldConfigureSwagger(),
                    packageSuffix = dialog.getPackageSuffix()
                )
            }
        }

        // Configure the GraphQL button action
        configureGraphQLButton.isEnabled = false
        useGraphQLCheckbox.addChangeListener {
            configureGraphQLButton.isEnabled = useGraphQLCheckbox.isSelected
        }

        configureGraphQLButton.addActionListener {
            val dialog = GraphQLConfigDialog(project)
            if (dialog.showAndGet()) {
                graphQLConfig = GraphQLConfigDialog.GraphQLConfig(
                    generateSchema = dialog.shouldGenerateSchema(),
                    generateQueryResolver = dialog.shouldGenerateQueryResolver(),
                    generateMutationResolver = dialog.shouldGenerateMutationResolver(),
                    generateConfig = dialog.shouldGenerateConfig(),
                    addSubscriptions = dialog.shouldAddSubscriptions()
                )
            }
        }

        // Check if MapStruct is already in the project
        checkMapstructDependency()

        // Check if Swagger is already in the project
        checkSwaggerDependency()

        // Check if Spring Security is already in the project
        checkSpringSecurityDependency()

        // Check if GraphQL is already in the project
        checkGraphQLDependency()

        init()
    }

    /**
     * Checks if MapStruct is already present in build files
     */
    private fun checkMapstructDependency() {
        val basePath = project.basePath ?: return
        var hasMapstruct = false

        // Check in build.gradle
        val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
        if (buildGradle.exists()) {
            val content = buildGradle.readText()
            if (content.contains("mapstruct") || content.contains("org.mapstruct")) {
                hasMapstruct = true
            }
        }

        // Check in build.gradle.kts
        val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
        if (buildGradleKts.exists() && !hasMapstruct) {
            val content = buildGradleKts.readText()
            if (content.contains("mapstruct") || content.contains("org.mapstruct")) {
                hasMapstruct = true
            }
        }

        // Check in pom.xml
        val pomXml = File(Paths.get(basePath, "pom.xml").toString())
        if (pomXml.exists() && !hasMapstruct) {
            val content = pomXml.readText()
            if (content.contains("mapstruct") || content.contains("org.mapstruct")) {
                hasMapstruct = true
            }
        }

        // If MapStruct is not found, suggest adding it
        if (!hasMapstruct) {
            useMapstructCheckbox.isSelected = true
            shouldAddMapstruct = true
        } else {
            useMapstructCheckbox.isSelected = false
            useMapstructCheckbox.isEnabled = false
            useMapstructCheckbox.text = "MapStruct already detected"
        }
    }

    /**
     * Checks if Swagger/OpenAPI is already present in build files
     */
    private fun checkSwaggerDependency() {
        val basePath = project.basePath ?: return
        var hasSwagger = false

        // Check in build.gradle
        val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
        if (buildGradle.exists()) {
            val content = buildGradle.readText()
            if (content.contains("springdoc-openapi") || content.contains("springfox")) {
                hasSwagger = true
            }
        }

        // Check in build.gradle.kts
        val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
        if (buildGradleKts.exists() && !hasSwagger) {
            val content = buildGradleKts.readText()
            if (content.contains("springdoc-openapi") || content.contains("springfox")) {
                hasSwagger = true
            }
        }

        // Check in pom.xml
        val pomXml = File(Paths.get(basePath, "pom.xml").toString())
        if (pomXml.exists() && !hasSwagger) {
            val content = pomXml.readText()
            if (content.contains("springdoc-openapi") || content.contains("springfox")) {
                hasSwagger = true
            }
        }

        // If Swagger is not found, suggest adding it
        if (!hasSwagger) {
            useSwaggerCheckbox.isSelected = true
            shouldAddSwagger = true
        } else {
            useSwaggerCheckbox.isSelected = false
            useSwaggerCheckbox.isEnabled = false
            useSwaggerCheckbox.text = "Swagger/OpenAPI already detected"
        }
    }

    /**
     * Checks if Spring Security is already present in build files
     */
    private fun checkSpringSecurityDependency() {
        val basePath = project.basePath ?: return
        var hasSpringSecurity = false

        // Check in build.gradle
        val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
        if (buildGradle.exists()) {
            val content = buildGradle.readText()
            if (content.contains("spring-boot-starter-security") || content.contains("spring-security-core")) {
                hasSpringSecurity = true
            }
        }

        // Check in build.gradle.kts
        val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
        if (buildGradleKts.exists() && !hasSpringSecurity) {
            val content = buildGradleKts.readText()
            if (content.contains("spring-boot-starter-security") || content.contains("spring-security-core")) {
                hasSpringSecurity = true
            }
        }

        // Check in pom.xml
        val pomXml = File(Paths.get(basePath, "pom.xml").toString())
        if (pomXml.exists() && !hasSpringSecurity) {
            val content = pomXml.readText()
            if (content.contains("spring-boot-starter-security") || content.contains("spring-security-core")) {
                hasSpringSecurity = true
            }
        }

        // If Spring Security is not found, enable the checkbox
        if (!hasSpringSecurity) {
            useSpringSecurityCheckbox.isEnabled = true
            shouldAddSpringSecurity = true
        } else {
            useSpringSecurityCheckbox.isSelected = true
            useSpringSecurityCheckbox.isEnabled = false
            useSpringSecurityCheckbox.text = "Spring Security already detected"
            configureSecurityButton.isEnabled = true
        }
    }

    /**
     * Checks if GraphQL is already present in build files
     */
    private fun checkGraphQLDependency() {
        val basePath = project.basePath ?: return
        var hasGraphQL = false

        // Check in build.gradle
        val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
        if (buildGradle.exists()) {
            val content = buildGradle.readText()
            if (content.contains("spring-boot-starter-graphql") || content.contains("graphql-java")) {
                hasGraphQL = true
            }
        }

        // Check in build.gradle.kts
        val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
        if (buildGradleKts.exists() && !hasGraphQL) {
            val content = buildGradleKts.readText()
            if (content.contains("spring-boot-starter-graphql") || content.contains("graphql-java")) {
                hasGraphQL = true
            }
        }

        // Check in pom.xml
        val pomXml = File(Paths.get(basePath, "pom.xml").toString())
        if (pomXml.exists() && !hasGraphQL) {
            val content = pomXml.readText()
            if (content.contains("spring-boot-starter-graphql") || content.contains("graphql-java")) {
                hasGraphQL = true
            }
        }

        // If GraphQL is not found, enable the checkbox
        if (!hasGraphQL) {
            useGraphQLCheckbox.isEnabled = true
            useGraphQLCheckbox.isSelected = false
            shouldAddGraphQL = false
        } else {
            useGraphQLCheckbox.isSelected = true
            useGraphQLCheckbox.isEnabled = false
            useGraphQLCheckbox.text = "GraphQL already detected"
            configureGraphQLButton.isEnabled = true
        }
    }

    /**
     * Gets the selected components to generate.
     */
    fun getSelectedComponents(): Set<String> {
        val components = mutableSetOf<String>()
        if (controllerCheckbox.isSelected) components.add("controller")
        if (serviceCheckbox.isSelected) components.add("service")
        if (dtoCheckbox.isSelected) components.add("dto")
        if (repositoryCheckbox.isSelected) components.add("repository")
        if (mapperCheckbox.isSelected) components.add("mapper")
        if (testCheckbox.isSelected) components.add("test")
        return components
    }

    /**
     * Gets the package configuration.
     */
    fun getPackageConfig(): Map<String, String> {
        return mapOf(
            "basePackage" to basePackageField.text,
            "domainPackage" to domainPackageField.text,
            "dtoPackage" to dtoPackageField.text,
            "controllerPackage" to controllerPackageField.text,
            "servicePackage" to servicePackageField.text,
            "repositoryPackage" to repositoryPackageField.text,
            "mapperPackage" to mapperPackageField.text
        )
    }

    /**
     * Check if MapStruct should be added to the project
     */
    fun shouldAddMapstruct(): Boolean {
        return mapperCheckbox.isSelected && useMapstructCheckbox.isSelected && shouldAddMapstruct
    }

    /**
     * Check if Swagger should be added to the project
     */
    fun shouldAddSwagger(): Boolean {
        return controllerCheckbox.isSelected && useSwaggerCheckbox.isSelected && shouldAddSwagger
    }

    /**
     * Check if Spring Security should be added and configured
     */
    fun shouldAddSpringSecurity(): Boolean {
        return useSpringSecurityCheckbox.isSelected && shouldAddSpringSecurity
    }

    /**
     * Check if GraphQL should be added to the project
     */
    fun shouldAddGraphQL(): Boolean {
        return useGraphQLCheckbox.isSelected
    }

    /**
     * Check if OpenAPI 3.0 should be added to the project
     */
    fun shouldAddOpenApi(): Boolean {
        return controllerCheckbox.isSelected && useOpenApiCheckbox.isSelected
    }

    /**
     * Returns the MapStruct dependency information based on build system
     */
    fun getMapstructDependencyInfo(): Pair<String, String> {
        val basePath = project.basePath ?: return Pair("", "")

        // Check if using Gradle or Maven
        val isMaven = File(Paths.get(basePath, "pom.xml").toString()).exists()
        val isGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString()).exists()

        return when {
            isMaven -> {
                val dependency = """
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
                Pair("Maven", dependency)
            }
            isGradleKts -> {
                val dependency = """
                implementation("org.mapstruct:mapstruct:1.6.3")
                annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
                """
                Pair("Gradle Kotlin", dependency)
            }
            else -> {
                val dependency = """
                implementation 'org.mapstruct:mapstruct:1.6.3'
                annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'
                """
                Pair("Gradle Groovy", dependency)
            }
        }
    }

    /**
     * Returns the Swagger dependency information based on build system
     */
    fun getSwaggerDependencyInfo(): Pair<String, String> {
        val basePath = project.basePath ?: return Pair("", "")

        // Check if using Gradle or Maven
        val isMaven = File(Paths.get(basePath, "pom.xml").toString()).exists()
        val isGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString()).exists()

        return when {
            isMaven -> {
                val dependency = """
                <dependency>
                    <groupId>org.springdoc</groupId>
                    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                    <version>2.8.9</version>
                </dependency>
                """
                Pair("Maven", dependency)
            }
            isGradleKts -> {
                val dependency = """
                implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
                """
                Pair("Gradle Kotlin", dependency)
            }
            else -> {
                val dependency = """
                implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9'
                """
                Pair("Gradle Groovy", dependency)
            }
        }
    }

    /**
     * Returns the Spring Security configuration if enabled
     */
    fun getSecurityConfig(): SecurityConfigDialog.SecurityConfig? {
        return if (useSpringSecurityCheckbox.isSelected) securityConfig else null
    }

    /**
     * Returns the GraphQL configuration if enabled
     */
    fun getGraphQLConfig(): GraphQLConfigDialog.GraphQLConfig? {
        return if (useGraphQLCheckbox.isSelected) graphQLConfig else null
    }

    /**
     * Returns the Spring Security dependency information based on build system
     */
    fun getSpringSecurityDependencyInfo(): Pair<String, String> {
        val basePath = project.basePath ?: return Pair("", "")

        // Check if using Gradle or Maven
        val isMaven = File(Paths.get(basePath, "pom.xml").toString()).exists()
        val isGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString()).exists()

        return when {
            isMaven -> {
                val dependency = """
                <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-security</artifactId>
                </dependency>
                <dependency>
                    <groupId>io.jsonwebtoken</groupId>
                    <artifactId>jjwt-api</artifactId>
                    <version>0.12.6</version>
                </dependency>
                <dependency>
                    <groupId>io.jsonwebtoken</groupId>
                    <artifactId>jjwt-impl</artifactId>
                    <version>0.12.6</version>
                    <scope>runtime</scope>
                </dependency>
                <dependency>
                    <groupId>io.jsonwebtoken</groupId>
                    <artifactId>jjwt-jackson</artifactId>
                    <version>0.12.6</version>
                    <scope>runtime</scope>
                </dependency>
                """
                Pair("Maven", dependency)
            }
            isGradleKts -> {
                val dependency = """
                implementation("org.springframework.boot:spring-boot-starter-security")
                implementation("io.jsonwebtoken:jjwt-api:0.12.6")
                runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
                runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
                """
                Pair("Gradle Kotlin", dependency)
            }
            else -> {
                val dependency = """
                implementation 'org.springframework.boot:spring-boot-starter-security'
                implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
                runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
                runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
                """
                Pair("Gradle Groovy", dependency)
            }
        }
    }

    /**
     * Returns the GraphQL dependency information based on build system
     */
    fun getGraphQLDependencyInfo(): Pair<String, String> {
        val basePath = project.basePath ?: return Pair("", "")

        // Check if using Gradle or Maven
        val isMaven = File(Paths.get(basePath, "pom.xml").toString()).exists()
        val isGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString()).exists()

        return when {
            isMaven -> {
                val dependency = """
                <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-graphql</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.springframework.graphql</groupId>
                    <artifactId>spring-graphql-test</artifactId>
                    <scope>test</scope>
                </dependency>
                <dependency>
                    <groupId>com.graphql-java</groupId>
                    <artifactId>graphql-java-extended-scalars</artifactId>
                    <version>20.0</version>
                </dependency>
                """
                Pair("Maven", dependency)
            }
            isGradleKts -> {
                val dependency = """
                implementation("org.springframework.boot:spring-boot-starter-graphql")
                testImplementation("org.springframework.graphql:spring-graphql-test")
                implementation("com.graphql-java:graphql-java-extended-scalars:20.0")
                """
                Pair("Gradle Kotlin", dependency)
            }
            else -> {
                val dependency = """
                implementation 'org.springframework.boot:spring-boot-starter-graphql'
                testImplementation 'org.springframework.graphql:spring-graphql-test'
                implementation 'com.graphql-java:graphql-java-extended-scalars:20.0'
                """
                Pair("Gradle Groovy", dependency)
            }
        }
    }

    /**
     * Returns the OpenAPI 3.0 dependency information based on build system
     */
    fun getOpenApiDependencyInfo(): Pair<String, String> {
        val basePath = project.basePath ?: return Pair("", "")

        // Check if using Gradle or Maven
        val isMaven = File(Paths.get(basePath, "pom.xml").toString()).exists()
        val isGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString()).exists()

        return when {
            isMaven -> {
                val dependency = """
                <dependency>
                    <groupId>org.springdoc</groupId>
                    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                    <version>2.8.9</version>
                </dependency>
                """
                Pair("Maven", dependency)
            }
            isGradleKts -> {
                val dependency = """
                implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
                """
                Pair("Gradle Kotlin", dependency)
            }
            else -> {
                val dependency = """
                implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9'
                """
                Pair("Gradle Groovy", dependency)
            }
        }
    }

    /**
     * Creates the dialog UI.
     */
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(600, 400)

        val tabbedPane = JTabbedPane()
        tabbedPane.add("Components", createComponentsPanel())
        tabbedPane.add("Packages", createPackagesPanel())

        // Entity information at the top
        val entityInfoPanel = createEntityInfoPanel()

        mainPanel.add(entityInfoPanel, BorderLayout.NORTH)
        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        return mainPanel
    }

    /**
     * Creates the panel displaying entity information.
     */
    private fun createEntityInfoPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Entity:"), JBLabel(entityMetadata.className))
            .addLabeledComponent(JBLabel("Package:"), JBLabel(entityMetadata.packageName))
            .addLabeledComponent(JBLabel("ID Type:"), JBLabel(entityMetadata.idType.substringAfterLast(".")))
            .addLabeledComponent(JBLabel("Table:"), JBLabel(entityMetadata.tableName))
            .panel
            .apply {
                border = JBUI.Borders.empty(10)
            }
    }

    /**
     * Creates the components selection panel.
     */
    private fun createComponentsPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(10)

        panel.add(createCheckboxPanel("Select components to generate:", listOf(
            controllerCheckbox,
            serviceCheckbox,
            dtoCheckbox,
            repositoryCheckbox,
            mapperCheckbox,
            testCheckbox
        )))

        // MapStruct checkbox
        panel.add(Box.createVerticalStrut(10))
        panel.add(useMapstructCheckbox)

        // Swagger checkbox
        panel.add(Box.createVerticalStrut(5))
        panel.add(useSwaggerCheckbox)

        // OpenAPI 3.0 checkbox
        panel.add(Box.createVerticalStrut(5))
        panel.add(useOpenApiCheckbox)

        // Spring Security checkbox
        panel.add(Box.createVerticalStrut(5))
        panel.add(useSpringSecurityCheckbox)
        panel.add(configureSecurityButton)

        // GraphQL checkbox
        panel.add(Box.createVerticalStrut(5))
        panel.add(useGraphQLCheckbox)
        panel.add(configureGraphQLButton)

        return panel
    }

    /**
     * Creates the packages configuration panel.
     */
    private fun createPackagesPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Base Package:", basePackageField)
            .addLabeledComponent("Entity Package:", domainPackageField)
            .addLabeledComponent("DTO Package:", dtoPackageField)
            .addLabeledComponent("Controller Package:", controllerPackageField)
            .addLabeledComponent("Service Package:", servicePackageField)
            .addLabeledComponent("Repository Package:", repositoryPackageField)
            .addLabeledComponent("Mapper Package:", mapperPackageField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply {
                border = JBUI.Borders.empty(10)
            }
    }

    /**
     * Creates a panel with checkboxes.
     */
    private fun createCheckboxPanel(title: String, checkboxes: List<JBCheckBox>): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.add(JBLabel(title).apply { alignmentX = Component.LEFT_ALIGNMENT })

        panel.add(Box.createVerticalStrut(10))

        checkboxes.forEach { checkbox ->
            checkbox.alignmentX = Component.LEFT_ALIGNMENT
            panel.add(checkbox)
            panel.add(Box.createVerticalStrut(5))
        }

        panel.add(Box.createVerticalGlue())

        return panel
    }

    /**
     * Validates the user input.
     */
    override fun doValidate(): ValidationInfo? {
        // Ensure at least one component is selected
        if (getSelectedComponents().isEmpty()) {
            return ValidationInfo("Please select at least one component to generate", controllerCheckbox)
        }

        // Validate package names
        val packageFields = listOf(
            basePackageField, domainPackageField, dtoPackageField, controllerPackageField,
            servicePackageField, repositoryPackageField, mapperPackageField
        )

        for (field in packageFields) {
            val text = field.text
            if (text.isBlank()) {
                return ValidationInfo("Package name cannot be empty", field)
            }

            if (!text.matches(Regex("^[a-z]+(\\.[a-z][a-z0-9]*)*$"))) {
                return ValidationInfo("Invalid package name", field)
            }
        }

        return null
    }

    /**
     * Get whether GraphQL support should be added.
     *
     * @return true if GraphQL support should be added, false otherwise
     */
    fun getGraphQLOption(): Boolean {
        return useGraphQLCheckbox.isSelected
    }
}

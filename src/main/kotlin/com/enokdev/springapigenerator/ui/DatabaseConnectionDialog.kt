package com.enokdev.springapigenerator.ui

import com.enokdev.springapigenerator.service.DatabaseConnectionService.DatabaseType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * Dialog for database connection configuration.
 */
class DatabaseConnectionDialog(project: Project) : DialogWrapper(project, true) {

    private val databaseTypeComboBox = ComboBox<DatabaseType>().apply {
        addItem(DatabaseType.MYSQL)
        addItem(DatabaseType.POSTGRESQL)
        addItem(DatabaseType.H2)
    }
    private val hostField = JBTextField("localhost")
    private val portField = JBTextField("3306") // Default to MySQL port
    private val databaseField = JBTextField()
    private val usernameField = JBTextField("root")
    private val passwordField = JBPasswordField()
    private val tablePatternField = JBTextField("%")
    private val basePackageField = JBTextField("com.example")

    init {
        title = "Generate Entities from Database"
        setOKButtonText("Connect and Generate")

        // Set default port when database type changes
        databaseTypeComboBox.addActionListener {
            val selectedType = databaseTypeComboBox.selectedItem as DatabaseType
            portField.text = when (selectedType) {
                DatabaseType.MYSQL -> "3306"
                DatabaseType.POSTGRESQL -> "5432"
                DatabaseType.H2 -> "9092"
            }
        }

        init()
    }

    /**
     * Data class to hold connection parameters.
     */
    data class ConnectionParameters(
        val type: DatabaseType,
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String,
        val tablePattern: String
    )

    /**
     * Get entered connection parameters.
     */
    fun getConnectionParameters(): ConnectionParameters {
        return ConnectionParameters(
            type = databaseTypeComboBox.selectedItem as DatabaseType,
            host = hostField.text,
            port = portField.text.toIntOrNull() ?: 0,
            database = databaseField.text,
            username = usernameField.text,
            password = String(passwordField.password),
            tablePattern = tablePatternField.text.ifEmpty { "%" }
        )
    }

    /**
     * Get entered base package name.
     */
    fun getBasePackage(): String {
        return basePackageField.text
    }

    /**
     * Creates the dialog UI.
     */
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(500, 350)

        val tabbedPane = JTabbedPane()
        tabbedPane.add("Connection", createConnectionPanel())
        tabbedPane.add("Generation", createGenerationPanel())

        mainPanel.add(createInfoPanel(), BorderLayout.NORTH)
        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        return mainPanel
    }

    /**
     * Creates the information panel.
     */
    private fun createInfoPanel(): JComponent {
        val infoText = "Connect to your database to generate JPA entity classes."
        val panel = JPanel(BorderLayout())
        panel.add(JBLabel(infoText), BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(10)
        return panel
    }

    /**
     * Creates the database connection panel.
     */
    private fun createConnectionPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Database Type:", databaseTypeComboBox)
            .addLabeledComponent("Host:", hostField)
            .addLabeledComponent("Port:", portField)
            .addLabeledComponent("Database:", databaseField)
            .addLabeledComponent("Username:", usernameField)
            .addLabeledComponent("Password:", passwordField)
            .addLabeledComponent("Table Pattern:", tablePatternField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply {
                border = JBUI.Borders.empty(10)
            }
    }

    /**
     * Creates the entity generation panel.
     */
    private fun createGenerationPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Base Package:", basePackageField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply {
                border = JBUI.Borders.empty(10)
            }
    }

    /**
     * Validates the user input.
     */
    override fun doValidate(): ValidationInfo? {
        if (hostField.text.isBlank()) {
            return ValidationInfo("Host cannot be empty", hostField)
        }

        if (portField.text.toIntOrNull() == null) {
            return ValidationInfo("Port must be a number", portField)
        }

        if (databaseField.text.isBlank()) {
            return ValidationInfo("Database name cannot be empty", databaseField)
        }

        if (usernameField.text.isBlank()) {
            return ValidationInfo("Username cannot be empty", usernameField)
        }

        if (basePackageField.text.isBlank() || !basePackageField.text.matches(Regex("^[a-z]+(\\.[a-z][a-z0-9]*)*$"))) {
            return ValidationInfo("Invalid package name", basePackageField)
        }

        return null
    }
}

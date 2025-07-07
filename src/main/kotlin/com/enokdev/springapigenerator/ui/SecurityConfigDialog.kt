package com.enokdev.springapigenerator.ui

import com.enokdev.springapigenerator.generator.impl.SecurityConfigGenerator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.TitledBorder

/**
 * Dialog to configure security options for generated code.
 */
class SecurityConfigDialog(private val project: Project) : DialogWrapper(project) {

    private val rbBasicAuth = JBRadioButton("Basic Authentication")
    private val rbJwtAuth = JBRadioButton("JWT Authentication")
    private val rbRoleBased = JBRadioButton("Role-based Security")

    private val cbGenerateUserService = JBCheckBox("Generate User Details Service")
    private val cbSecureControllers = JBCheckBox("Add Security to Controllers")
    private val cbConfigureSwagger = JBCheckBox("Configure Swagger for Security")

    private val packageNameField = JTextField()

    private val authGroup = ButtonGroup()

    init {
        title = "Configure Spring Security"
        init()

        // Set defaults
        rbJwtAuth.isSelected = true
        cbGenerateUserService.isSelected = true
        cbSecureControllers.isSelected = true
        cbConfigureSwagger.isSelected = true
        packageNameField.text = "config.security"
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = java.awt.Dimension(400, 300)

        // Auth type panel
        val authPanel = JPanel(GridBagLayout())
        authPanel.border = TitledBorder("Authentication Type")

        authGroup.add(rbBasicAuth)
        authGroup.add(rbJwtAuth)
        authGroup.add(rbRoleBased)

        val gbc = GridBagConstraints()
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0

        authPanel.add(rbBasicAuth, gbc)

        gbc.gridy++
        authPanel.add(rbJwtAuth, gbc)

        gbc.gridy++
        authPanel.add(rbRoleBased, gbc)

        // Options panel
        val optionsPanel = JPanel(GridBagLayout())
        optionsPanel.border = TitledBorder("Options")

        gbc.gridy = 0
        optionsPanel.add(cbGenerateUserService, gbc)

        gbc.gridy++
        optionsPanel.add(cbSecureControllers, gbc)

        gbc.gridy++
        optionsPanel.add(cbConfigureSwagger, gbc)

        // Package name panel
        val packagePanel = JPanel(GridBagLayout())
        packagePanel.border = TitledBorder("Package Configuration")

        gbc.gridy = 0
        packagePanel.add(JBLabel("Security package suffix:"), gbc)

        gbc.gridy++
        packagePanel.add(packageNameField, gbc)

        // Add all panels to main panel
        panel.add(authPanel, BorderLayout.NORTH)

        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(optionsPanel, BorderLayout.NORTH)
        centerPanel.add(packagePanel, BorderLayout.CENTER)

        panel.add(centerPanel, BorderLayout.CENTER)

        return panel
    }

    /**
     * Returns the selected security level.
     */
    fun getSecurityLevel(): SecurityConfigGenerator.SecurityLevel {
        return when {
            rbBasicAuth.isSelected -> SecurityConfigGenerator.SecurityLevel.BASIC
            rbRoleBased.isSelected -> SecurityConfigGenerator.SecurityLevel.ROLE_BASED
            else -> SecurityConfigGenerator.SecurityLevel.JWT
        }
    }

    /**
     * Returns whether to generate a user details service.
     */
    fun shouldGenerateUserDetailsService(): Boolean {
        return cbGenerateUserService.isSelected
    }

    /**
     * Returns whether to add security annotations to controllers.
     */
    fun shouldSecureControllers(): Boolean {
        return cbSecureControllers.isSelected
    }

    /**
     * Returns whether to configure Swagger for security.
     */
    fun shouldConfigureSwagger(): Boolean {
        return cbConfigureSwagger.isSelected
    }

    /**
     * Returns the security package suffix.
     */
    fun getPackageSuffix(): String {
        return packageNameField.text.trim()
    }

    /**
     * Data class to hold security configuration options.
     */
    data class SecurityConfig(
        val securityLevel: SecurityConfigGenerator.SecurityLevel,
        val generateUserDetailsService: Boolean,
        val secureControllers: Boolean,
        val configureSwagger: Boolean,
        val packageSuffix: String
    )
}

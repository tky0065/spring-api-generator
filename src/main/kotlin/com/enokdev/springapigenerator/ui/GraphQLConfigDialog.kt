package com.enokdev.springapigenerator.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.border.TitledBorder

/**
 * Dialog to configure GraphQL options for generated code.
 */
class GraphQLConfigDialog(private val project: Project) : DialogWrapper(project) {

    private val cbGenerateSchema = JBCheckBox("Generate GraphQL Schema", true)
    private val cbGenerateQueryResolver = JBCheckBox("Generate Query Resolver", true)
    private val cbGenerateMutationResolver = JBCheckBox("Generate Mutation Resolver", true)
    private val cbGenerateConfig = JBCheckBox("Generate GraphQL Configuration", true)
    private val cbAddSubscriptions = JBCheckBox("Add Subscription Support", false)

    init {
        title = "Configure GraphQL"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = java.awt.Dimension(400, 250)

        // Main options panel
        val optionsPanel = JPanel(GridBagLayout())
        optionsPanel.border = TitledBorder("GraphQL Components")

        val gbc = GridBagConstraints()
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0

        optionsPanel.add(cbGenerateSchema, gbc)

        gbc.gridy++
        optionsPanel.add(cbGenerateQueryResolver, gbc)

        gbc.gridy++
        optionsPanel.add(cbGenerateMutationResolver, gbc)

        gbc.gridy++
        optionsPanel.add(cbGenerateConfig, gbc)

        gbc.gridy++
        optionsPanel.add(cbAddSubscriptions, gbc)

        // Add information labels
        val infoPanel = JPanel(BorderLayout())
        infoPanel.add(JBLabel("<html><body><p>GraphQL requires Spring GraphQL dependency.</p>" +
                "<p>The plugin will automatically add the necessary dependencies to your project.</p></body></html>"),
                BorderLayout.CENTER)

        panel.add(optionsPanel, BorderLayout.NORTH)
        panel.add(infoPanel, BorderLayout.CENTER)

        return panel
    }

    /**
     * Returns whether to generate GraphQL schema.
     */
    fun shouldGenerateSchema(): Boolean {
        return cbGenerateSchema.isSelected
    }

    /**
     * Returns whether to generate query resolver.
     */
    fun shouldGenerateQueryResolver(): Boolean {
        return cbGenerateQueryResolver.isSelected
    }

    /**
     * Returns whether to generate mutation resolver.
     */
    fun shouldGenerateMutationResolver(): Boolean {
        return cbGenerateMutationResolver.isSelected
    }

    /**
     * Returns whether to generate GraphQL config.
     */
    fun shouldGenerateConfig(): Boolean {
        return cbGenerateConfig.isSelected
    }

    /**
     * Returns whether to add subscription support.
     */
    fun shouldAddSubscriptions(): Boolean {
        return cbAddSubscriptions.isSelected
    }

    /**
     * Data class to hold GraphQL configuration options.
     */
    data class GraphQLConfig(
        val generateSchema: Boolean,
        val generateQueryResolver: Boolean,
        val generateMutationResolver: Boolean,
        val generateConfig: Boolean,
        val addSubscriptions: Boolean
    )
}

package com.enokdev.springapigenerator.ui

import com.enokdev.springapigenerator.service.ProjectTypeDetectionService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.*

/**
 * Dialog for selecting target language in mixed Java/Kotlin projects.
 */
class LanguageSelectionDialog(
    private val project: Project,
    private val languageInfo: ProjectTypeDetectionService.ProjectLanguageInfo
) : DialogWrapper(project) {

    private lateinit var javaRadio: JBRadioButton
    private lateinit var kotlinRadio: JBRadioButton
    private lateinit var rememberChoice: JCheckBox

    init {
        title = "Select Target Language"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        // Main content
        val contentPanel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("This project contains both Java and Kotlin code."))
            .addComponent(JBLabel("Please select the language for generated code:"))
            .addVerticalGap(10)
            .addComponent(createLanguageSelectionPanel())
            .addVerticalGap(10)
            .addComponent(createProjectInfoPanel())
            .addVerticalGap(10)
            .addComponent(createRememberChoicePanel())
            .panel

        panel.add(contentPanel, BorderLayout.CENTER)
        return panel
    }

    private fun createLanguageSelectionPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createTitledBorder("Target Language")

        javaRadio = JBRadioButton("Java", !shouldDefaultToKotlin())
        kotlinRadio = JBRadioButton("Kotlin", shouldDefaultToKotlin())

        val group = ButtonGroup()
        group.add(javaRadio)
        group.add(kotlinRadio)

        panel.add(javaRadio)
        panel.add(kotlinRadio)

        return panel
    }

    private fun createProjectInfoPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createTitledBorder("Project Information")

        val javaFiles = languageInfo.javaFileCount
        val kotlinFiles = languageInfo.kotlinFileCount
        val kotlinPercentage = (languageInfo.kotlinRatio * 100).toInt()

        panel.add(JBLabel("Java files: $javaFiles"))
        panel.add(JBLabel("Kotlin files: $kotlinFiles"))
        panel.add(JBLabel("Kotlin ratio: $kotlinPercentage%"))

        return panel
    }

    private fun createRememberChoicePanel(): JComponent {
        rememberChoice = JCheckBox("Remember my choice for this project", false)
        return rememberChoice
    }

    private fun shouldDefaultToKotlin(): Boolean {
        return languageInfo.kotlinRatio > 0.5
    }

    fun getSelectedLanguage(): String {
        return if (kotlinRadio.isSelected) "kotlin" else "java"
    }

    fun shouldRememberChoice(): Boolean {
        return rememberChoice.isSelected
    }

    override fun doOKAction() {
        // Save preference if user chose to remember
        if (shouldRememberChoice()) {
            val languagePrefs = com.enokdev.springapigenerator.service.LanguagePreferenceService.getInstance(project)
            languagePrefs.setDefaultForMixed(getSelectedLanguage())
            languagePrefs.setAlwaysAsk(false)
        }
        super.doOKAction()
    }
}

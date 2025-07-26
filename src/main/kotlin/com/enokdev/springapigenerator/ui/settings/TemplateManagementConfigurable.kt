package com.enokdev.springapigenerator.ui.settings

import com.enokdev.springapigenerator.service.TemplateCustomizationService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Configuration panel for Spring API Generator template management.
 * This provides a UI for managing custom FreeMarker templates used in code generation.
 */
class TemplateManagementConfigurable(private val project: Project) : Configurable {

    private var settingsComponent: TemplateManagementSettingsComponent? = null

    override fun getDisplayName(): String {
        return "Spring API Generator Templates"
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return settingsComponent?.getPreferredFocusedComponent()
    }

    override fun createComponent(): JComponent? {
        settingsComponent = TemplateManagementSettingsComponent(project)
        return settingsComponent?.getPanel()
    }

    override fun isModified(): Boolean {
        return settingsComponent?.isModified() ?: false
    }

    override fun apply() {
        settingsComponent?.apply()
    }

    override fun reset() {
        settingsComponent?.reset()
    }

    override fun disposeUIResources() {
        settingsComponent?.dispose()
        settingsComponent = null
    }
}

/**
 * UI component for template management settings.
 */
class TemplateManagementSettingsComponent(private val project: Project) {

    private val templateService = project.getService(TemplateCustomizationService::class.java)
    private val mainPanel: JPanel = JPanel(BorderLayout())
    private val templateTable: JBTable
    private val templateTableModel: DefaultTableModel
    private val templateEditor: EditorEx
    private val previewPanel: JPanel = JPanel(BorderLayout())
    private val statusLabel: JBLabel = JBLabel("Ready")
    
    private var isModified = false
    private var currentTemplate: TemplateInfo? = null
    private var originalTemplateContent: String = ""

    init {
        // Initialize template table
        templateTableModel = DefaultTableModel(arrayOf("Name", "Type", "Location"), 0)
        templateTable = JBTable(templateTableModel)
        templateTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        
        // Initialize template editor
        val editorFactory = EditorFactory.getInstance()
        val document = editorFactory.createDocument("")
        templateEditor = editorFactory.createEditor(document, project) as EditorEx
        
        // Set FreeMarker file type for syntax highlighting
        val fileType = FileTypeManager.getInstance().getFileTypeByExtension("ftl")
        templateEditor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType)

        setupUI()
        setupEventHandlers()
        loadTemplates()
    }

    private fun setupUI() {
        // Create toolbar
        val toolbar = createToolbar()
        
        // Create main splitter
        val mainSplitter = JBSplitter(false, 0.3f)
        
        // Left panel - Template list
        val leftPanel = JPanel(BorderLayout())
        leftPanel.add(JBLabel("Available templates"), BorderLayout.NORTH)
        leftPanel.add(JBScrollPane(templateTable), BorderLayout.CENTER)
        
        // Right panel - Editor and preview
        val rightSplitter = JBSplitter(true, 0.6f)
        
        // Editor panel
        val editorPanel = JPanel(BorderLayout())
        editorPanel.add(JBLabel("Template editor"), BorderLayout.NORTH)
        editorPanel.add(templateEditor.component, BorderLayout.CENTER)
        
        // Preview panel
        previewPanel.add(JBLabel("Preview"), BorderLayout.NORTH)
        val previewContent = JTextArea("Select a template to see its content...")
        previewContent.isEditable = false
        previewContent.background = templateEditor.backgroundColor
        previewPanel.add(JBScrollPane(previewContent), BorderLayout.CENTER)
        
        rightSplitter.firstComponent = editorPanel
        rightSplitter.secondComponent = previewPanel
        
        mainSplitter.firstComponent = leftPanel
        mainSplitter.secondComponent = rightSplitter
        
        // Status bar
        val statusPanel = JPanel(BorderLayout())
        statusPanel.border = JBUI.Borders.empty(5)
        statusPanel.add(statusLabel, BorderLayout.WEST)
        
        // Assemble main panel
        mainPanel.add(toolbar, BorderLayout.NORTH)
        mainPanel.add(mainSplitter, BorderLayout.CENTER)
        mainPanel.add(statusPanel, BorderLayout.SOUTH)
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        
        val newButton = JButton("New Template")
        val editButton = JButton("Edit")
        val deleteButton = JButton("Delete")
        val duplicateButton = JButton("Duplicate")
        val importButton = JButton("Import")
        val exportButton = JButton("Export")
        val reloadButton = JButton("Reload")
        
        toolbar.add(newButton)
        toolbar.add(editButton)
        toolbar.add(deleteButton)
        toolbar.add(JSeparator(SwingConstants.VERTICAL))
        toolbar.add(duplicateButton)
        toolbar.add(JSeparator(SwingConstants.VERTICAL))
        toolbar.add(importButton)
        toolbar.add(exportButton)
        toolbar.add(JSeparator(SwingConstants.VERTICAL))
        toolbar.add(reloadButton)
        
        // Button actions
        newButton.addActionListener { createNewTemplate() }
        editButton.addActionListener { editSelectedTemplate() }
        deleteButton.addActionListener { deleteSelectedTemplate() }
        duplicateButton.addActionListener { duplicateSelectedTemplate() }
        importButton.addActionListener { importTemplate() }
        exportButton.addActionListener { exportSelectedTemplate() }
        reloadButton.addActionListener { loadTemplates() }
        
        return toolbar
    }

    private fun setupEventHandlers() {
        // Table selection listener
        templateTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = templateTable.selectedRow
                if (selectedRow >= 0) {
                    loadSelectedTemplate(selectedRow)
                }
            }
        }
        
        // Document change listener for editor
        templateEditor.document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                if (currentTemplate != null) {
                    isModified = true
                    statusLabel.text = "Modified - ${currentTemplate!!.name}"
                }
            }
        })
    }

    private fun loadTemplates() {
        templateTableModel.rowCount = 0
        
        // Load built-in templates
        val builtInTemplates = getBuiltInTemplates()
        for (template in builtInTemplates) {
            templateTableModel.addRow(arrayOf(template.name, "Built-in", template.location))
        }
        
        // Load project templates
        val projectTemplates = templateService.getProjectTemplateFiles()
        for (file in projectTemplates) {
            templateTableModel.addRow(arrayOf(file.name, "Project", file.absolutePath))
        }
        
        // Load user templates
        val userTemplates = templateService.getUserTemplateFiles()
        for (file in userTemplates) {
            templateTableModel.addRow(arrayOf(file.name, "User", file.absolutePath))
        }
        
        statusLabel.text = "Loaded ${templateTableModel.rowCount} templates"
    }

    private fun getBuiltInTemplates(): List<TemplateInfo> {
        val templates = mutableListOf<TemplateInfo>()
        val templateNames = listOf(
            "Controller.java.ft", "Controller.kt.ft",
            "Service.java.ft", "Service.kt.ft",
            "Repository.java.ft", "Repository.kt.ft",
            "DTO.java.ft", "DTO.kt.ft",
            "Mapper.java.ft", "Mapper.kt.ft",
            "Test.java.ft", "Test.kt.ft",
            "GraphQLController.java.ft", "GraphQLController.kt.ft",
            "GraphQLSchema.graphqls.ft"
        )
        
        for (name in templateNames) {
            templates.add(TemplateInfo(name, "Built-in", "classpath:/templates/$name"))
        }
        
        return templates
    }

    private fun loadSelectedTemplate(row: Int) {
        val name = templateTableModel.getValueAt(row, 0) as String
        val type = templateTableModel.getValueAt(row, 1) as String
        val location = templateTableModel.getValueAt(row, 2) as String
        
        currentTemplate = TemplateInfo(name, type, location)
        
        try {
            val content = when (type) {
                "Built-in" -> loadBuiltInTemplate(name)
                else -> File(location).readText()
            }
            
            originalTemplateContent = content
            ApplicationManager.getApplication().runWriteAction {
                templateEditor.document.setText(content)
            }
            
            isModified = false
            statusLabel.text = "Loaded: $name"
            
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to load template: ${e.message}", "Error")
            statusLabel.text = "Error loading template"
        }
    }

    private fun loadBuiltInTemplate(name: String): String {
        val resourcePath = "/templates/$name"
        val inputStream = javaClass.getResourceAsStream(resourcePath)
        return inputStream?.bufferedReader()?.readText() ?: "Template not found"
    }

    private fun createNewTemplate() {
        val name = Messages.showInputDialog(
            project,
            "Enter template name (with .ft extension):",
            "New Template",
            Messages.getQuestionIcon()
        )
        
        if (name.isNullOrBlank()) return
        
        if (!name.endsWith(".ft")) {
            Messages.showErrorDialog(project, "Template name must end with .ft", "Invalid Name")
            return
        }
        
        templateService.createProjectTemplatesDirectory()
        val file = File(templateService.getProjectTemplatesDirectory(), name)
        
        if (file.exists()) {
            Messages.showErrorDialog(project, "Template already exists", "Error")
            return
        }
        
        try {
            file.writeText("# New template\n# Add your FreeMarker template content here\n")
            loadTemplates()
            statusLabel.text = "Created: $name"
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to create template: ${e.message}", "Error")
        }
    }

    private fun editSelectedTemplate() {
        val selectedRow = templateTable.selectedRow
        if (selectedRow < 0) {
            Messages.showInfoMessage(project, "Please select a template to edit", "No Selection")
            return
        }
        
        val type = templateTableModel.getValueAt(selectedRow, 1) as String
        if (type == "Built-in") {
            Messages.showInfoMessage(project, "Built-in templates cannot be edited directly. Use Duplicate to create a custom version.", "Cannot Edit")
            return
        }
        
        templateEditor.isViewer = false
        statusLabel.text = "Editing: ${currentTemplate?.name}"
    }

    private fun deleteSelectedTemplate() {
        val selectedRow = templateTable.selectedRow
        if (selectedRow < 0) return
        
        val name = templateTableModel.getValueAt(selectedRow, 0) as String
        val type = templateTableModel.getValueAt(selectedRow, 1) as String
        val location = templateTableModel.getValueAt(selectedRow, 2) as String
        
        if (type == "Built-in") {
            Messages.showInfoMessage(project, "Built-in templates cannot be deleted", "Cannot Delete")
            return
        }
        
        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete '$name'?",
            "Confirm Delete",
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            try {
                File(location).delete()
                loadTemplates()
                statusLabel.text = "Deleted: $name"
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to delete template: ${e.message}", "Error")
            }
        }
    }

    private fun duplicateSelectedTemplate() {
        val selectedRow = templateTable.selectedRow
        if (selectedRow < 0) return
        
        val originalName = templateTableModel.getValueAt(selectedRow, 0) as String
        val newName = Messages.showInputDialog(
            project,
            "Enter new template name:",
            "Duplicate Template",
            Messages.getQuestionIcon(),
            "copy_of_$originalName",
            null
        )
        
        if (newName.isNullOrBlank()) return
        
        try {
            templateService.createProjectTemplatesDirectory()
            val newFile = File(templateService.getProjectTemplatesDirectory(), newName)
            val content = templateEditor.document.text
            newFile.writeText(content)
            loadTemplates()
            statusLabel.text = "Duplicated: $newName"
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to duplicate template: ${e.message}", "Error")
        }
    }

    private fun importTemplate() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, true)
        descriptor.title = "Import Templates"
        descriptor.description = "Select FreeMarker template files (.ft) to import"
        descriptor.withFileFilter { it.extension == "ft" }
        
        val files = FileChooser.chooseFiles(descriptor, project, null)
        if (files.isEmpty()) return
        
        templateService.createProjectTemplatesDirectory()
        val targetDir = File(templateService.getProjectTemplatesDirectory())
        
        for (file in files) {
            try {
                val targetFile = File(targetDir, file.name)
                file.inputStream.copyTo(targetFile.outputStream())
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to import ${file.name}: ${e.message}", "Import Error")
            }
        }
        
        loadTemplates()
        statusLabel.text = "Imported ${files.size} template(s)"
    }

    private fun exportSelectedTemplate() {
        val selectedRow = templateTable.selectedRow
        if (selectedRow < 0) return
        
        val name = templateTableModel.getValueAt(selectedRow, 0) as String
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
        descriptor.title = "Export Template"
        
        val targetDir = FileChooser.chooseFile(descriptor, project, null)
        if (targetDir != null) {
            try {
                val content = templateEditor.document.text
                val targetFile = File(targetDir.path, name)
                targetFile.writeText(content)
                statusLabel.text = "Exported: $name"
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to export template: ${e.message}", "Export Error")
            }
        }
    }

    fun getPanel(): JPanel = mainPanel

    fun getPreferredFocusedComponent(): JComponent = templateTable

    fun isModified(): Boolean = isModified

    fun apply() {
        if (isModified && currentTemplate != null) {
            try {
                val content = templateEditor.document.text
                if (currentTemplate!!.type != "Built-in") {
                    File(currentTemplate!!.location).writeText(content)
                    originalTemplateContent = content
                    isModified = false
                    statusLabel.text = "Saved: ${currentTemplate!!.name}"
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to save template: ${e.message}", "Save Error")
            }
        }
    }

    fun reset() {
        if (currentTemplate != null) {
            ApplicationManager.getApplication().runWriteAction {
                templateEditor.document.setText(originalTemplateContent)
            }
            isModified = false
            statusLabel.text = "Reset: ${currentTemplate!!.name}"
        }
    }

    fun dispose() {
        EditorFactory.getInstance().releaseEditor(templateEditor)
    }

    private data class TemplateInfo(
        val name: String,
        val type: String,
        val location: String
    )
}

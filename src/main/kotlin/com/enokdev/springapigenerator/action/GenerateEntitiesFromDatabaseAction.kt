package com.enokdev.springapigenerator.action

import com.enokdev.springapigenerator.generator.impl.EntityFromSchemaGenerator
import com.enokdev.springapigenerator.service.DatabaseConnectionService
import com.enokdev.springapigenerator.service.SchemaExtractor
import com.enokdev.springapigenerator.ui.DatabaseConnectionDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

/**
 * Action for generating JPA entities from database schema.
 * This action is shown in the 'Generate' menu and in the context menu
 * when right-clicking on a package in the Project view.
 */
class GenerateEntitiesFromDatabaseAction : AnAction() {

    /**
     * Called when the action is performed by the user.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Show connection dialog
        val dialog = DatabaseConnectionDialog(project)
        if (dialog.showAndGet()) {
            // User confirmed, proceed with connection
            val connectionParams = dialog.getConnectionParameters()
            val basePackage = dialog.getBasePackage()

            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Connecting to Database...", false) {
                    override fun run(indicator: ProgressIndicator) {
                        try {
                            // Connect to database
                            indicator.text = "Connecting to database..."
                            indicator.isIndeterminate = true

                            val connectionService = DatabaseConnectionService(project)
                            val connection = connectionService.createConnection(
                                connectionParams.type,
                                connectionParams.host,
                                connectionParams.port,
                                connectionParams.database,
                                connectionParams.username,
                                connectionParams.password
                            )

                            if (connection == null) {
                                ApplicationManager.getApplication().invokeLater {
                                    Messages.showErrorDialog(
                                        project,
                                        "Failed to connect to database. Please check your connection parameters.",
                                        "Database Connection Error"
                                    )
                                }
                                return
                            }

                            // Extract schema
                            indicator.text = "Extracting database schema..."
                            val schemaExtractor = SchemaExtractor()
                            val tables = schemaExtractor.extractTables(
                                connection,
                                catalog = null,
                                schemaPattern = null,
                                tableNamePattern = connectionParams.tablePattern
                            )

                            if (tables.isEmpty()) {
                                ApplicationManager.getApplication().invokeLater {
                                    Messages.showWarningDialog(
                                        project,
                                        "No tables found in the database schema.",
                                        "No Tables Found"
                                    )
                                }
                                connection.close()
                                return
                            }

                            // Generate entities
                            indicator.text = "Generating JPA entities..."
                            val generator = EntityFromSchemaGenerator(project)
                            val generatedEntities = generator.generateEntities(tables, basePackage)

                            // Write entities to files
                            indicator.text = "Writing entity files..."
                            val createdFiles = generator.writeEntitiesToFiles(generatedEntities)

                            // Close connection
                            connection.close()

                            // Refresh files in IDE
                            ApplicationManager.getApplication().invokeLater {
                                WriteAction.runAndWait<Throwable> {
                                    val projectDir = LocalFileSystem.getInstance().findFileByPath(project.basePath!!)
                                    if (projectDir != null) {
                                        VfsUtil.markDirtyAndRefresh(true, true, true, projectDir)
                                    }

                                    Messages.showInfoMessage(
                                        project,
                                        "Generated ${createdFiles.size} entity files from database schema.",
                                        "Entity Generation Complete"
                                    )
                                }
                            }
                        } catch (ex: Exception) {
                            ApplicationManager.getApplication().invokeLater {
                                Messages.showErrorDialog(
                                    project,
                                    "Error generating entities: ${ex.message}",
                                    "Generation Error"
                                )
                                ex.printStackTrace()
                            }
                        }
                    }
                }
            )
        }
    }

    /**
     * Which thread the update method will be called on.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

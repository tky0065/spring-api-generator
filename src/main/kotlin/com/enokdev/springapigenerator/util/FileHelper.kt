package com.enokdev.springapigenerator.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/**
 * Classe utilitaire pour les opérations sur les fichiers du projet.
 */
class FileHelper {
    companion object {
        /**
         * Écrit du contenu dans un fichier, en s'assurant que les répertoires existent.
         * Rafraîchit également le fichier dans l'IDE.
         *
         * @param project Le projet IntelliJ
         * @param filePath Le chemin absolu du fichier à écrire
         * @param content Le contenu à écrire dans le fichier
         */
        fun writeToFile(project: Project, filePath: String, content: String) {
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
    }
}

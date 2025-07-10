package com.enokdev.springapigenerator.service

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

/**
 * Service for detecting whether the project is primarily a Kotlin or Java project.
 * This helps the code generator to generate code in the appropriate language.
 */
class ProjectTypeDetectionService {

    companion object {
        private const val KOTLIN_THRESHOLD = 0.5 // If more than 50% of files are Kotlin, consider it a Kotlin project

        /**
         * Determines if the project is primarily a Kotlin project.
         *
         * @param project The IntelliJ project to analyze
         * @return true if the project is primarily Kotlin, false otherwise
         */
        fun isKotlinProject(project: Project): Boolean {
            // Count Kotlin and Java files in the project
            // Get the Kotlin file type by its extension
            val kotlinFileType = FileTypeManager.getInstance().getFileTypeByExtension("kt")
            val kotlinFiles = FileTypeIndex.getFiles(kotlinFileType, GlobalSearchScope.projectScope(project))
            val javaFiles = FileTypeIndex.getFiles(com.intellij.ide.highlighter.JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

            // If there are no Kotlin files, it's definitely not a Kotlin project
            if (kotlinFiles.isEmpty()) {
                return false
            }

            // If there are no Java files but there are Kotlin files, it's definitely a Kotlin project
            if (javaFiles.isEmpty()) {
                return true
            }

            // Calculate the ratio of Kotlin to total files
            val kotlinRatio = kotlinFiles.size.toDouble() / (kotlinFiles.size + javaFiles.size)

            return kotlinRatio >= KOTLIN_THRESHOLD
        }

        /**
         * Checks if the project has Kotlin configured in the build system.
         *
         * @param project The IntelliJ project to analyze
         * @return true if Kotlin is configured in the build system, false otherwise
         */
        fun hasKotlinBuildConfiguration(project: Project): Boolean {
            val buildFiles = listOf("build.gradle", "build.gradle.kts", "pom.xml")
            val projectDir = project.basePath?.let { File(it) } ?: return false

            for (buildFile in buildFiles) {
                val file = File(projectDir, buildFile)
                if (file.exists()) {
                    val content = file.readText()
                    if (buildFile.endsWith(".xml") && content.contains("<artifactId>kotlin-stdlib</artifactId>")) {
                        return true
                    } else if (content.contains("kotlin") || content.contains("org.jetbrains.kotlin")) {
                        return true
                    }
                }
            }

            return false
        }

        /**
         * Makes the final determination if we should generate Kotlin code.
         * Considers both file count and build system configuration.
         *
         * @param project The IntelliJ project
         * @return true if Kotlin code should be generated, false for Java
         */
        fun shouldGenerateKotlinCode(project: Project): Boolean {
            // If both conditions are met, definitely generate Kotlin
            if (isKotlinProject(project) && hasKotlinBuildConfiguration(project)) {
                return true
            }

            // If at least the project structure suggests Kotlin, go with Kotlin
            return isKotlinProject(project)
        }
    }
}

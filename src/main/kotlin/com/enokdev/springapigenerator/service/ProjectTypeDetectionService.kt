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
        private const val KOTLIN_THRESHOLD = 0.3 // If more than 30% of files are Kotlin, consider it a Kotlin project

        /**
         * Determines if the project is primarily a Kotlin project.
         *
         * @param project The IntelliJ project to analyze
         * @return true if the project is primarily Kotlin, false otherwise
         */
        fun isKotlinProject(project: Project): Boolean {
            return detectProjectLanguage(project) == ProjectLanguage.KOTLIN
        }

        /**
         * Unified method for both isKotlinProject and shouldGenerateKotlinCode.
         * This ensures consistency across the plugin.
         */
        fun shouldGenerateKotlinCode(project: Project): Boolean {
            return isKotlinProject(project)
        }

        /**
         * Detects the primary language of the project with improved logic.
         */
        fun detectProjectLanguage(project: Project): ProjectLanguage {
            // 1. Check build configuration first (most reliable)
            val buildLanguage = detectLanguageFromBuildFiles(project)
            if (buildLanguage != ProjectLanguage.MIXED) {
                return buildLanguage
            }

            // 2. Check source files ratio
            val kotlinFileType = FileTypeManager.getInstance().getFileTypeByExtension("kt")
            val kotlinFiles = FileTypeIndex.getFiles(kotlinFileType, GlobalSearchScope.projectScope(project))
            val javaFiles = FileTypeIndex.getFiles(com.intellij.ide.highlighter.JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

            // If there are no Kotlin files, it's definitely Java
            if (kotlinFiles.isEmpty()) {
                return ProjectLanguage.JAVA
            }

            // If there are no Java files but there are Kotlin files, it's definitely Kotlin
            if (javaFiles.isEmpty()) {
                return ProjectLanguage.KOTLIN
            }

            // Calculate the ratio of Kotlin to total files
            val kotlinRatio = kotlinFiles.size.toDouble() / (kotlinFiles.size + javaFiles.size)

            return when {
                kotlinRatio >= KOTLIN_THRESHOLD -> ProjectLanguage.KOTLIN
                kotlinRatio > 0 -> ProjectLanguage.MIXED
                else -> ProjectLanguage.JAVA
            }
        }

        /**
         * Checks if the project has Kotlin configured in the build system.
         */
        private fun detectLanguageFromBuildFiles(project: Project): ProjectLanguage {
            val projectPath = project.basePath ?: return ProjectLanguage.MIXED

            // Check for Kotlin in Gradle build files
            val buildGradleKts = File(projectPath, "build.gradle.kts")
            val buildGradle = File(projectPath, "build.gradle")

            when {
                buildGradleKts.exists() -> {
                    val content = buildGradleKts.readText()
                    return when {
                        content.contains("kotlin(\"jvm\")") || content.contains("id(\"org.jetbrains.kotlin.jvm\")") -> ProjectLanguage.KOTLIN
                        content.contains("java") -> ProjectLanguage.MIXED
                        else -> ProjectLanguage.KOTLIN // .kts implies Kotlin
                    }
                }
                buildGradle.exists() -> {
                    val content = buildGradle.readText()
                    return when {
                        content.contains("org.jetbrains.kotlin") -> {
                            if (content.contains("java")) ProjectLanguage.MIXED else ProjectLanguage.KOTLIN
                        }
                        else -> ProjectLanguage.JAVA
                    }
                }
            }

            // Check for Maven pom.xml
            val pomXml = File(projectPath, "pom.xml")
            if (pomXml.exists()) {
                val content = pomXml.readText()
                return when {
                    content.contains("kotlin-maven-plugin") -> {
                        if (content.contains("maven-compiler-plugin")) ProjectLanguage.MIXED else ProjectLanguage.KOTLIN
                    }
                    else -> ProjectLanguage.JAVA
                }
            }

            return ProjectLanguage.MIXED
        }

        /**
         * Gets the source root directory for the detected language.
         */
        fun getSourceRootForLanguage(project: Project, language: ProjectLanguage? = null): String {
            val projectPath = project.basePath ?: throw RuntimeException("Project base path not found")
            val detectedLanguage = language ?: detectProjectLanguage(project)

            return when (detectedLanguage) {
                ProjectLanguage.KOTLIN -> "$projectPath/src/main/kotlin"
                ProjectLanguage.JAVA -> "$projectPath/src/main/java"
                ProjectLanguage.MIXED -> {
                    // For mixed projects, prefer the directory that exists or default to the detected primary language
                    val kotlinSrc = File("$projectPath/src/main/kotlin")
                    val javaSrc = File("$projectPath/src/main/java")

                    when {
                        kotlinSrc.exists() && shouldGenerateKotlinCode(project) -> "$projectPath/src/main/kotlin"
                        javaSrc.exists() -> "$projectPath/src/main/java"
                        shouldGenerateKotlinCode(project) -> "$projectPath/src/main/kotlin"
                        else -> "$projectPath/src/main/java"
                    }
                }
            }
        }

        /**
         * Gets the test root directory for the detected language.
         */
        fun getTestRootForLanguage(project: Project, language: ProjectLanguage? = null): String {
            val projectPath = project.basePath ?: throw RuntimeException("Project base path not found")
            val detectedLanguage = language ?: detectProjectLanguage(project)

            return when (detectedLanguage) {
                ProjectLanguage.KOTLIN -> "$projectPath/src/test/kotlin"
                ProjectLanguage.JAVA -> "$projectPath/src/test/java"
                ProjectLanguage.MIXED -> {
                    val kotlinTest = File("$projectPath/src/test/kotlin")
                    val javaTest = File("$projectPath/src/test/java")

                    when {
                        kotlinTest.exists() && shouldGenerateKotlinCode(project) -> "$projectPath/src/test/kotlin"
                        javaTest.exists() -> "$projectPath/src/test/java"
                        shouldGenerateKotlinCode(project) -> "$projectPath/src/test/kotlin"
                        else -> "$projectPath/src/test/java"
                    }
                }
            }
        }

        /**
         * Determines if both Java and Kotlin are present in the project.
         */
        fun isMixedLanguageProject(project: Project): Boolean {
            return detectProjectLanguage(project) == ProjectLanguage.MIXED
        }

        /**
         * Gets information about the project language configuration.
         */
        fun getProjectLanguageInfo(project: Project): ProjectLanguageInfo {
            val language = detectProjectLanguage(project)
            val kotlinFileType = FileTypeManager.getInstance().getFileTypeByExtension("kt")
            val kotlinFiles = FileTypeIndex.getFiles(kotlinFileType, GlobalSearchScope.projectScope(project))
            val javaFiles = FileTypeIndex.getFiles(com.intellij.ide.highlighter.JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

            return ProjectLanguageInfo(
                primaryLanguage = language,
                kotlinFileCount = kotlinFiles.size,
                javaFileCount = javaFiles.size,
                isMixed = language == ProjectLanguage.MIXED,
                kotlinRatio = if (kotlinFiles.size + javaFiles.size > 0) {
                    kotlinFiles.size.toDouble() / (kotlinFiles.size + javaFiles.size)
                } else 0.0
            )
        }
    }

    /**
     * Enum representing the primary language of a project.
     */
    enum class ProjectLanguage {
        JAVA, KOTLIN, MIXED
    }

    /**
     * Data class containing detailed information about project language configuration.
     */
    data class ProjectLanguageInfo(
        val primaryLanguage: ProjectLanguage,
        val kotlinFileCount: Int,
        val javaFileCount: Int,
        val isMixed: Boolean,
        val kotlinRatio: Double
    )
}

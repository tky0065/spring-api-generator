package com.enokdev.springapigenerator.service

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiField
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import java.io.File
import java.util.Properties

/**
 * Detects coding style conventions from existing project files, IDE settings,
 * and .editorconfig files. Supports matching to predefined code styles.
 */
class CodeStyleDetector {
    private val logger = Logger.getInstance(CodeStyleDetector::class.java)

    data class CodeStyleConfig(
        val indentationType: IndentationType = IndentationType.SPACES,
        val indentSize: Int = 4,
        val namingConvention: NamingConvention = NamingConvention.CAMEL_CASE,
        val bracketStyle: BracketStyle = BracketStyle.END_OF_LINE,
        val commentStyle: CommentStyle = CommentStyle.JAVADOC,
        val fieldPrefix: String = "",
        val useGetterSetterPrefix: Boolean = true,
        val packageStructure: PackageStructure = PackageStructure.LAYERED,
        val styleStandard: PredefinedCodeStyles.StyleStandard = PredefinedCodeStyles.StyleStandard.CUSTOM
    )

    enum class IndentationType { SPACES, TABS }
    enum class NamingConvention { CAMEL_CASE, SNAKE_CASE, PASCAL_CASE }
    enum class BracketStyle { END_OF_LINE, NEXT_LINE }
    enum class CommentStyle { JAVADOC, BLOCK, LINE }
    enum class PackageStructure { LAYERED, FEATURE_BASED }

    /**
     * Analyzes the project to detect coding style conventions.
     * Checks multiple sources in the following order of priority:
     * 1. Project-specific IDE settings
     * 2. .editorconfig files
     * 3. Analysis of existing code files
     * 4. Default settings
     */
    fun detectCodeStyle(project: Project): CodeStyleConfig {
        // Try to get style from IDE settings first
        try {
            val ideStyleConfig = detectIdeCodeStyle(project)
            if (ideStyleConfig != null) {
                logger.info("Using code style from IDE settings")
                return ideStyleConfig
            }
        } catch (e: Exception) {
            logger.warn("Failed to detect IDE code style: ${e.message}")
        }

        // Try to get style from .editorconfig
        try {
            val editorConfigStyle = detectEditorConfigStyle(project)
            if (editorConfigStyle != null) {
                logger.info("Using code style from .editorconfig")
                return editorConfigStyle
            }
        } catch (e: Exception) {
            logger.warn("Failed to detect .editorconfig style: ${e.message}")
        }

        // Fall back to analyzing project files
        logger.info("Detecting code style from project files")
        val javaFiles = findJavaFiles(project)

        if (javaFiles.isEmpty()) {
            logger.info("No Java files found, using default style")
            return CodeStyleConfig() // Return default if no Java files found
        }

        val indentationType = detectIndentationType(javaFiles)
        val indentSize = detectIndentSize(javaFiles)
        val namingConvention = detectNamingConvention(javaFiles)
        val bracketStyle = detectBracketStyle(javaFiles)
        val commentStyle = detectCommentStyle(javaFiles)
        val fieldPrefix = detectFieldPrefix(javaFiles)
        val useGetterSetterPrefix = detectGetterSetterPrefix(javaFiles)
        val packageStructure = detectPackageStructure(project)

        val config = CodeStyleConfig(
            indentationType = indentationType,
            indentSize = indentSize,
            namingConvention = namingConvention,
            bracketStyle = bracketStyle,
            commentStyle = commentStyle,
            fieldPrefix = fieldPrefix,
            useGetterSetterPrefix = useGetterSetterPrefix,
            packageStructure = packageStructure
        )

        // Try to match to a predefined style
        val styleStandard = PredefinedCodeStyles.findClosestStyle(config)
        logger.info("Detected style matches predefined style: $styleStandard")
        
        return config.copy(styleStandard = styleStandard)
    }

    private fun findJavaFiles(project: Project): List<PsiJavaFile> {
        val javaFiles = mutableListOf<PsiJavaFile>()
        val virtualFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

        virtualFiles.take(50).forEach { virtualFile -> // Limit to 50 files for performance
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            if (psiFile is PsiJavaFile) {
                javaFiles.add(psiFile)
            }
        }

        return javaFiles
    }

    private fun detectIndentationType(javaFiles: List<PsiJavaFile>): IndentationType {
        var spaceCount = 0
        var tabCount = 0

        javaFiles.forEach { file ->
            val lines = file.text.lines()
            lines.forEach { line ->
                if (line.startsWith("    ")) spaceCount++
                if (line.startsWith("\t")) tabCount++
            }
        }

        return if (spaceCount > tabCount) IndentationType.SPACES else IndentationType.TABS
    }

    private fun detectIndentSize(javaFiles: List<PsiJavaFile>): Int {
        val indentSizes = mutableListOf<Int>()

        javaFiles.forEach { file ->
            val lines = file.text.lines()
            var previousIndent = 0

            lines.forEach { line ->
                if (line.trim().isNotEmpty()) {
                    val leadingSpaces = line.length - line.trimStart().length
                    if (leadingSpaces > previousIndent && previousIndent == 0) {
                        indentSizes.add(leadingSpaces)
                    }
                    previousIndent = leadingSpaces
                }
            }
        }

        return indentSizes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 4
    }

    private fun detectNamingConvention(javaFiles: List<PsiJavaFile>): NamingConvention {
        var camelCaseCount = 0
        var snakeCaseCount = 0

        javaFiles.forEach { file ->
            file.classes.forEach { psiClass ->
                psiClass.methods.forEach { method ->
                    val name = method.name
                    if (name.contains("_")) snakeCaseCount++
                    else if (name.matches(Regex("^[a-z][a-zA-Z0-9]*$"))) camelCaseCount++
                }

                psiClass.fields.forEach { field ->
                    val name = field.name ?: ""
                    if (name.contains("_")) snakeCaseCount++
                    else if (name.matches(Regex("^[a-z][a-zA-Z0-9]*$"))) camelCaseCount++
                }
            }
        }

        return if (snakeCaseCount > camelCaseCount) NamingConvention.SNAKE_CASE else NamingConvention.CAMEL_CASE
    }

    private fun detectBracketStyle(javaFiles: List<PsiJavaFile>): BracketStyle {
        var endOfLineCount = 0
        var nextLineCount = 0

        javaFiles.forEach { file ->
            val text = file.text
            val methodPattern = Regex("""\)\s*\{""")
            val classPattern = Regex("""\s+\{""")

            endOfLineCount += methodPattern.findAll(text).count()
            endOfLineCount += classPattern.findAll(text).count()

            val nextLinePattern = Regex("""\)\s*\n\s*\{""")
            nextLineCount += nextLinePattern.findAll(text).count()
        }

        return if (nextLineCount > endOfLineCount) BracketStyle.NEXT_LINE else BracketStyle.END_OF_LINE
    }

    private fun detectCommentStyle(javaFiles: List<PsiJavaFile>): CommentStyle {
        var javadocCount = 0
        var blockCount = 0
        var lineCount = 0

        javaFiles.forEach { file ->
            val text = file.text
            javadocCount += text.split("/**").size - 1
            blockCount += text.split("/*").size - 1 - javadocCount
            lineCount += text.split("//").size - 1
        }

        return when {
            javadocCount >= blockCount && javadocCount >= lineCount -> CommentStyle.JAVADOC
            blockCount >= lineCount -> CommentStyle.BLOCK
            else -> CommentStyle.LINE
        }
    }

    private fun detectFieldPrefix(javaFiles: List<PsiJavaFile>): String {
        val prefixes = mutableMapOf<String, Int>()

        javaFiles.forEach { file ->
            file.classes.forEach { psiClass ->
                psiClass.fields.forEach { field ->
                    val name = field.name ?: ""
                    if (name.isNotEmpty()) {
                        // Check for common prefixes
                        when {
                            name.startsWith("m_") -> prefixes["m_"] = prefixes.getOrDefault("m_", 0) + 1
                            name.startsWith("_") -> prefixes["_"] = prefixes.getOrDefault("_", 0) + 1
                            name.startsWith("f") && name.length > 1 && name[1].isUpperCase() ->
                                prefixes["f"] = prefixes.getOrDefault("f", 0) + 1
                        }
                    }
                }
            }
        }

        return prefixes.maxByOrNull { it.value }?.key ?: ""
    }

    private fun detectGetterSetterPrefix(javaFiles: List<PsiJavaFile>): Boolean {
        var getterSetterCount = 0
        var methodCount = 0

        javaFiles.forEach { file ->
            file.classes.forEach { psiClass ->
                psiClass.methods.forEach { method ->
                    methodCount++
                    val name = method.name
                    if (name.startsWith("get") || name.startsWith("set") || name.startsWith("is")) {
                        getterSetterCount++
                    }
                }
            }
        }

        return if (methodCount > 0) getterSetterCount.toDouble() / methodCount > 0.3 else true
    }

    private fun detectPackageStructure(project: Project): PackageStructure {
        val javaFiles = findJavaFiles(project)
        var layeredCount = 0
        var featureBasedCount = 0

        javaFiles.forEach { file ->
            val packageName = file.packageName.lowercase()
            when {
                packageName.contains(".controller") || packageName.contains(".service") ||
                packageName.contains(".repository") || packageName.contains(".entity") -> layeredCount++
                packageName.matches(Regex(".*\\.[a-z]+\\.[a-z]+.*")) -> featureBasedCount++
            }
        }

        return if (featureBasedCount > layeredCount) PackageStructure.FEATURE_BASED else PackageStructure.LAYERED
    }
    
    /**
     * Detects code style from IntelliJ IDEA's code style settings.
     * 
     * @param project The project to detect code style for
     * @return The detected code style configuration, or null if it couldn't be detected
     */
    private fun detectIdeCodeStyle(project: Project): CodeStyleConfig? {
        try {
            val codeStyleSettings = CodeStyleSettingsManager.getInstance(project).currentSettings
            
            // Detect indentation type and size
            val indentOptions = codeStyleSettings.getIndentOptions(JavaFileType.INSTANCE)
            val indentationType = if (indentOptions.USE_TAB_CHARACTER) IndentationType.TABS else IndentationType.SPACES
            val indentSize = indentOptions.INDENT_SIZE
            
            // Detect bracket style
            val javaSettings = codeStyleSettings.getCommonSettings(JavaFileType.INSTANCE.language)
            val bracketStyle = if (javaSettings.BRACE_STYLE == 1) BracketStyle.NEXT_LINE else BracketStyle.END_OF_LINE
            
            // Detect naming convention (this is harder to detect from settings, using default)
            val namingConvention = NamingConvention.CAMEL_CASE
            
            // Create the config
            val config = CodeStyleConfig(
                indentationType = indentationType,
                indentSize = indentSize,
                bracketStyle = bracketStyle,
                namingConvention = namingConvention,
                // Other properties use defaults
                commentStyle = CommentStyle.JAVADOC,
                fieldPrefix = "",
                useGetterSetterPrefix = true,
                packageStructure = PackageStructure.LAYERED
            )
            
            // Try to match to a predefined style
            val styleStandard = PredefinedCodeStyles.findClosestStyle(config)
            
            return config.copy(styleStandard = styleStandard)
        } catch (e: Exception) {
            logger.warn("Failed to detect IDE code style: ${e.message}")
            return null
        }
    }
    
    /**
     * Detects code style from .editorconfig file if present in the project.
     * 
     * @param project The project to detect code style for
     * @return The detected code style configuration, or null if it couldn't be detected
     */
    private fun detectEditorConfigStyle(project: Project): CodeStyleConfig? {
        try {
            val projectDir = project.basePath ?: return null
            val editorConfigFile = File(projectDir, ".editorconfig")
            
            if (!editorConfigFile.exists()) {
                return null
            }
            
            val properties = Properties()
            editorConfigFile.inputStream().use { properties.load(it) }
            
            // Parse .editorconfig properties
            val indentStyle = properties.getProperty("indent_style")
            val indentSize = properties.getProperty("indent_size")?.toIntOrNull() ?: 4
            val endOfLine = properties.getProperty("end_of_line")
            
            // Map to our code style config
            val indentationType = when (indentStyle) {
                "tab" -> IndentationType.TABS
                "space" -> IndentationType.SPACES
                else -> IndentationType.SPACES // Default
            }
            
            // Create the config
            val config = CodeStyleConfig(
                indentationType = indentationType,
                indentSize = indentSize,
                // Other properties use defaults or are hard to determine from .editorconfig
                namingConvention = NamingConvention.CAMEL_CASE,
                bracketStyle = BracketStyle.END_OF_LINE,
                commentStyle = CommentStyle.JAVADOC,
                fieldPrefix = "",
                useGetterSetterPrefix = true,
                packageStructure = PackageStructure.LAYERED
            )
            
            // Try to match to a predefined style
            val styleStandard = PredefinedCodeStyles.findClosestStyle(config)
            
            return config.copy(styleStandard = styleStandard)
        } catch (e: Exception) {
            logger.warn("Failed to detect .editorconfig style: ${e.message}")
            return null
        }
    }
}

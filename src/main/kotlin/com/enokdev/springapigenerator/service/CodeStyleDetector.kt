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

/**
 * Detects coding style conventions from existing project files.
 */
class CodeStyleDetector {

    data class CodeStyleConfig(
        val indentationType: IndentationType = IndentationType.SPACES,
        val indentSize: Int = 4,
        val namingConvention: NamingConvention = NamingConvention.CAMEL_CASE,
        val bracketStyle: BracketStyle = BracketStyle.END_OF_LINE,
        val commentStyle: CommentStyle = CommentStyle.JAVADOC,
        val fieldPrefix: String = "",
        val useGetterSetterPrefix: Boolean = true,
        val packageStructure: PackageStructure = PackageStructure.LAYERED
    )

    enum class IndentationType { SPACES, TABS }
    enum class NamingConvention { CAMEL_CASE, SNAKE_CASE, PASCAL_CASE }
    enum class BracketStyle { END_OF_LINE, NEXT_LINE }
    enum class CommentStyle { JAVADOC, BLOCK, LINE }
    enum class PackageStructure { LAYERED, FEATURE_BASED }

    /**
     * Analyzes the project to detect coding style conventions.
     */
    fun detectCodeStyle(project: Project): CodeStyleConfig {
        val javaFiles = findJavaFiles(project)

        if (javaFiles.isEmpty()) {
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

        return CodeStyleConfig(
            indentationType = indentationType,
            indentSize = indentSize,
            namingConvention = namingConvention,
            bracketStyle = bracketStyle,
            commentStyle = commentStyle,
            fieldPrefix = fieldPrefix,
            useGetterSetterPrefix = useGetterSetterPrefix,
            packageStructure = packageStructure
        )
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
}

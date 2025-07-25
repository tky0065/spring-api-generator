package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.service.CodeStyleDetector.*

/**
 * Adapts generated code to match the project's coding style.
 */
class CodeStyleAdapter(private val styleConfig: CodeStyleConfig) {

    /**
     * Applies the detected code style to the generated code.
     */
    fun adaptCode(generatedCode: String): String {
        var adaptedCode = generatedCode

        adaptedCode = adaptIndentation(adaptedCode)
        adaptedCode = adaptBracketStyle(adaptedCode)
        adaptedCode = adaptNamingConventions(adaptedCode)
        adaptedCode = adaptCommentStyle(adaptedCode)

        return adaptedCode
    }

    /**
     * Adapts field names according to the detected naming convention and prefix.
     */
    fun adaptFieldName(fieldName: String): String {
        var adaptedName = when (styleConfig.namingConvention) {
            NamingConvention.SNAKE_CASE -> camelToSnakeCase(fieldName)
            NamingConvention.PASCAL_CASE -> fieldName.replaceFirstChar { it.uppercase() }
            else -> fieldName
        }

        return styleConfig.fieldPrefix + adaptedName
    }

    /**
     * Adapts method names according to the detected naming convention.
     */
    fun adaptMethodName(methodName: String): String {
        return when (styleConfig.namingConvention) {
            NamingConvention.SNAKE_CASE -> camelToSnakeCase(methodName)
            NamingConvention.PASCAL_CASE -> methodName.replaceFirstChar { it.uppercase() }
            else -> methodName
        }
    }

    /**
     * Gets the indentation string based on the detected style.
     */
    fun getIndentation(level: Int = 1): String {
        val unit = when (styleConfig.indentationType) {
            IndentationType.TABS -> "\t"
            IndentationType.SPACES -> " ".repeat(styleConfig.indentSize)
        }
        return unit.repeat(level)
    }

    /**
     * Formats a class declaration with the appropriate bracket style.
     */
    fun formatClassDeclaration(className: String, modifiers: String = "public", interfaces: List<String> = emptyList()): String {
        val interfacesPart = if (interfaces.isNotEmpty()) " implements ${interfaces.joinToString(", ")}" else ""

        return when (styleConfig.bracketStyle) {
            BracketStyle.END_OF_LINE -> "$modifiers class $className$interfacesPart {"
            BracketStyle.NEXT_LINE -> "$modifiers class $className$interfacesPart\n{"
        }
    }

    /**
     * Formats a method declaration with the appropriate bracket style.
     */
    fun formatMethodDeclaration(methodSignature: String): String {
        return when (styleConfig.bracketStyle) {
            BracketStyle.END_OF_LINE -> "$methodSignature {"
            BracketStyle.NEXT_LINE -> "$methodSignature\n{"
        }
    }

    /**
     * Creates a formatted comment based on the detected comment style.
     */
    fun formatComment(content: String, isDocumentation: Boolean = false): String {
        return when (styleConfig.commentStyle) {
            CommentStyle.JAVADOC -> if (isDocumentation) {
                "/**\n * $content\n */"
            } else {
                "// $content"
            }
            CommentStyle.BLOCK -> "/* $content */"
            CommentStyle.LINE -> "// $content"
        }
    }

    /**
     * Formats getter method name based on the detected conventions.
     */
    fun formatGetterName(fieldName: String, isBoolean: Boolean = false): String {
        val cleanFieldName = fieldName.removePrefix(styleConfig.fieldPrefix)
        val capitalizedName = cleanFieldName.replaceFirstChar { it.uppercase() }

        val prefix = if (isBoolean && styleConfig.useGetterSetterPrefix) "is" else "get"
        val methodName = prefix + capitalizedName

        return adaptMethodName(methodName)
    }

    /**
     * Formats setter method name based on the detected conventions.
     */
    fun formatSetterName(fieldName: String): String {
        val cleanFieldName = fieldName.removePrefix(styleConfig.fieldPrefix)
        val capitalizedName = cleanFieldName.replaceFirstChar { it.uppercase() }
        val methodName = "set$capitalizedName"

        return adaptMethodName(methodName)
    }

    private fun adaptIndentation(code: String): String {
        val lines = code.lines()
        val adaptedLines = mutableListOf<String>()

        lines.forEach { line ->
            if (line.trim().isNotEmpty()) {
                val leadingSpaces = line.length - line.trimStart().length
                val indentLevel = leadingSpaces / 4 // Assume original uses 4 spaces
                val newIndent = getIndentation(indentLevel)
                adaptedLines.add(newIndent + line.trimStart())
            } else {
                adaptedLines.add(line)
            }
        }

        return adaptedLines.joinToString("\n")
    }

    private fun adaptBracketStyle(code: String): String {
        if (styleConfig.bracketStyle == BracketStyle.NEXT_LINE) {
            return code
                .replace(Regex("""\)\s*\{"""), ")\n{")
                .replace(Regex("""class\s+\w+.*\s*\{""")) { match ->
                    match.value.replace("{", "\n{")
                }
        }
        return code
    }

    private fun adaptNamingConventions(code: String): String {
        // This is a simplified implementation
        // In a real implementation, you'd need proper AST parsing
        return code
    }

    private fun adaptCommentStyle(code: String): String {
        when (styleConfig.commentStyle) {
            CommentStyle.BLOCK -> {
                return code.replace(Regex("""//\s*(.*)"""), "/* $1 */")
            }
            CommentStyle.LINE -> {
                return code.replace(Regex("""/\*\s*(.*?)\s*\*/"""), "// $1")
            }
            else -> return code
        }
    }

    private fun camelToSnakeCase(camelCase: String): String {
        return camelCase.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    }
}

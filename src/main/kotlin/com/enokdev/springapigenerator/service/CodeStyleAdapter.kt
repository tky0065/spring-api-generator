package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.service.CodeStyleDetector.*

/**
 * Adapts generated code to match the project's coding style.
 */
class CodeStyleAdapter(val styleConfig: CodeStyleConfig) {

    /**
     * Applies the detected code style to the generated code.
     */
    fun adaptCode(generatedCode: String): String {
        var adaptedCode = generatedCode

        adaptedCode = adaptIndentation(adaptedCode)
        adaptedCode = adaptBracketStyle(adaptedCode)
        adaptedCode = adaptNamingConventions(adaptedCode)
        adaptedCode = adaptCommentStyle(adaptedCode)
        adaptedCode = adaptAnnotations(adaptedCode)
        adaptedCode = adaptLambdaExpressions(adaptedCode)
        adaptedCode = adaptImportsAndPackages(adaptedCode)

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
    
    /**
     * Adapts annotations according to the detected style.
     * Handles annotation placement, line breaks, and formatting.
     */
    private fun adaptAnnotations(code: String): String {
        // Handle annotation placement based on style
        var adaptedCode = code
        
        when (styleConfig.styleStandard) {
            PredefinedCodeStyles.StyleStandard.GOOGLE -> {
                // Google style: annotations on same line for simple cases, separate lines for multiple
                adaptedCode = adaptedCode.replace(
                    Regex("@([A-Za-z0-9_]+)\\s*\\n\\s*@([A-Za-z0-9_]+)\\s*\\n\\s*([A-Za-z0-9_<>]+)"),
                    "@$1 @$2 $3"
                )
            }
            PredefinedCodeStyles.StyleStandard.KOTLIN_OFFICIAL -> {
                // Kotlin style: annotations on separate lines
                adaptedCode = adaptedCode.replace(
                    Regex("@([A-Za-z0-9_]+)\\s+@([A-Za-z0-9_]+)\\s+([A-Za-z0-9_<>]+)"),
                    "@$1\n${getIndentation()}@$2\n${getIndentation()}$3"
                )
            }
            else -> {
                // Default behavior: no specific adaptation
            }
        }
        
        // Format annotation parameters according to style
        if (styleConfig.bracketStyle == CodeStyleDetector.BracketStyle.NEXT_LINE) {
            // For next-line bracket style, put annotation parameters on new lines for complex annotations
            adaptedCode = adaptedCode.replace(
                Regex("@([A-Za-z0-9_]+)\\(([^)]{40,})\\)"),
                "@$1(\n${getIndentation(2)}$2\n${getIndentation()})"
            )
        }
        
        return adaptedCode
    }
    
    /**
     * Adapts lambda expressions according to the detected style.
     * Handles lambda formatting, parameter placement, and arrow style.
     */
    private fun adaptLambdaExpressions(code: String): String {
        var adaptedCode = code
        
        when (styleConfig.styleStandard) {
            PredefinedCodeStyles.StyleStandard.KOTLIN_OFFICIAL -> {
                // Kotlin style: space before and after arrow
                adaptedCode = adaptedCode.replace(
                    Regex("\\{\\s*([a-zA-Z0-9_]+)\\s*->"),
                    "{ $1 ->"
                )
                
                // For multiline lambdas, put arrow on its own line
                adaptedCode = adaptedCode.replace(
                    Regex("\\{\\s*([a-zA-Z0-9_]+(?:,\\s*[a-zA-Z0-9_]+){2,})\\s*->"),
                    "{\n${getIndentation()}$1\n${getIndentation()}->"
                )
            }
            PredefinedCodeStyles.StyleStandard.GOOGLE -> {
                // Google style: compact lambdas for simple cases
                adaptedCode = adaptedCode.replace(
                    Regex("\\{\\s*([a-zA-Z0-9_]+)\\s*->\\s*([^\\n}{]{1,30})\\s*}"),
                    "{ $1 -> $2 }"
                )
            }
            else -> {
                // Default behavior: ensure consistent spacing
                adaptedCode = adaptedCode.replace(
                    Regex("\\{([a-zA-Z0-9_]+)->"),
                    "{ $1 ->"
                )
            }
        }
        
        return adaptedCode
    }
    
    /**
     * Adapts imports and package declarations according to the detected style.
     * Handles import ordering, grouping, and formatting.
     */
    private fun adaptImportsAndPackages(code: String): String {
        var adaptedCode = code
        
        // Extract imports
        val importPattern = Regex("import\\s+([^;\\n]+)[;\\n]")
        val imports = importPattern.findAll(adaptedCode).map { it.groupValues[1].trim() }.toList()
        
        if (imports.isEmpty()) {
            return adaptedCode
        }
        
        // Sort and group imports according to style
        val sortedImports = when (styleConfig.styleStandard) {
            PredefinedCodeStyles.StyleStandard.GOOGLE -> {
                // Google style: alphabetical order with groups
                val javaImports = imports.filter { it.startsWith("java.") }.sorted()
                val androidImports = imports.filter { it.startsWith("android.") }.sorted()
                val thirdPartyImports = imports.filter { 
                    !it.startsWith("java.") && 
                    !it.startsWith("android.") && 
                    !it.startsWith("com.enokdev.") 
                }.sorted()
                val projectImports = imports.filter { it.startsWith("com.enokdev.") }.sorted()
                
                val result = mutableListOf<String>()
                if (javaImports.isNotEmpty()) {
                    result.addAll(javaImports)
                    result.add("") // Empty line between groups
                }
                if (androidImports.isNotEmpty()) {
                    result.addAll(androidImports)
                    result.add("")
                }
                if (thirdPartyImports.isNotEmpty()) {
                    result.addAll(thirdPartyImports)
                    result.add("")
                }
                if (projectImports.isNotEmpty()) {
                    result.addAll(projectImports)
                }
                result
            }
            PredefinedCodeStyles.StyleStandard.KOTLIN_OFFICIAL -> {
                // Kotlin style: alphabetical order without wildcards
                imports.sorted()
            }
            else -> {
                // Default: simple alphabetical order
                imports.sorted()
            }
        }
        
        // Replace imports in the code
        val importSection = sortedImports.joinToString("\n") { 
            if (it.isEmpty()) "" else "import $it;"
        }
        
        // Replace the import section
        val packagePattern = Regex("package\\s+[^;\\n]+[;\\n]\\s*")
        val packageMatch = packagePattern.find(adaptedCode)
        
        if (packageMatch != null) {
            val packageDeclaration = packageMatch.value
            val oldImportSection = adaptedCode.substring(
                packageMatch.range.last + 1, 
                adaptedCode.indexOf("class ").takeIf { it > 0 } ?: adaptedCode.indexOf("interface ").takeIf { it > 0 } ?: packageMatch.range.last + 100
            ).trim()
            
            adaptedCode = adaptedCode.replace(
                "$packageDeclaration$oldImportSection", 
                "$packageDeclaration\n$importSection\n\n"
            )
        }
        
        return adaptedCode
    }
}

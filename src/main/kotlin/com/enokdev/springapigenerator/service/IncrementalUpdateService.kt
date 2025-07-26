package com.enokdev.springapigenerator.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import java.io.File
import java.util.regex.Pattern

/**
 * Service for managing incremental code updates with preservation of manual changes.
 */
class IncrementalUpdateService {

    companion object {
        // Markers to identify generated sections
        const val GENERATED_SECTION_START = "// <generated>"
        const val GENERATED_SECTION_END = "// </generated>"
        const val GENERATED_METHOD_START = "// <generated-method>"
        const val GENERATED_METHOD_END = "// </generated-method>"
        const val GENERATED_FIELD_START = "// <generated-field>"
        const val GENERATED_FIELD_END = "// </generated-field>"

        // Pattern to match generated sections
        private val GENERATED_SECTION_PATTERN = Pattern.compile(
            "$GENERATED_SECTION_START(.*?)$GENERATED_SECTION_END",
            Pattern.DOTALL
        )

        private val GENERATED_METHOD_PATTERN = Pattern.compile(
            "$GENERATED_METHOD_START(.*?)$GENERATED_METHOD_END",
            Pattern.DOTALL
        )

        private val GENERATED_FIELD_PATTERN = Pattern.compile(
            "$GENERATED_FIELD_START(.*?)$GENERATED_FIELD_END",
            Pattern.DOTALL
        )
    }

    data class UpdateStrategy(
        val preserveManualChanges: Boolean = true,
        val updateGeneratedSections: Boolean = true,
        val addMissingGeneratedCode: Boolean = true,
        val removeObsoleteGeneratedCode: Boolean = false,
        val preserveManualChangesInGeneratedSections: Boolean = true,
        val detectSemanticSections: Boolean = true,
        val showConflictResolutionUI: Boolean = true
    )

    data class CodeSection(
        val type: SectionType,
        val name: String,
        val content: String,
        val startMarker: String,
        val endMarker: String,
        val startIndex: Int,
        val endIndex: Int
    )

    enum class SectionType {
        GENERATED_SECTION,
        GENERATED_METHOD,
        GENERATED_FIELD,
        MANUAL_CODE
    }

    /**
     * Merges new generated code with existing file content, preserving manual changes.
     * 
     * @param existingContent The existing content of the file
     * @param newGeneratedCode The newly generated code
     * @param updateStrategy The strategy to use for merging
     * @param project The project context for UI interactions (optional)
     * @return The merged content
     */
    fun mergeCode(
        existingContent: String,
        newGeneratedCode: String,
        updateStrategy: UpdateStrategy = UpdateStrategy(),
        project: Project? = null
    ): String {

        if (!hasGeneratedMarkers(existingContent)) {
            // If no markers exist, wrap the entire new code with markers
            return wrapWithGeneratedMarkers(newGeneratedCode)
        }

        val existingSections = parseCodeSections(existingContent)
        val newSections = parseCodeSections(newGeneratedCode)

        return buildMergedContent(existingSections, newSections, updateStrategy, project)
    }

    /**
     * Checks if the content contains generated section markers.
     */
    fun hasGeneratedMarkers(content: String): Boolean {
        return content.contains(GENERATED_SECTION_START) ||
               content.contains(GENERATED_METHOD_START) ||
               content.contains(GENERATED_FIELD_START)
    }

    /**
     * Wraps code with generated section markers.
     */
    fun wrapWithGeneratedMarkers(code: String): String {
        return "$GENERATED_SECTION_START\n$code\n$GENERATED_SECTION_END"
    }

    /**
     * Wraps a method with generated method markers.
     */
    fun wrapMethodWithMarkers(methodCode: String, methodName: String): String {
        return "$GENERATED_METHOD_START:$methodName\n$methodCode\n$GENERATED_METHOD_END:$methodName"
    }

    /**
     * Wraps a field with generated field markers.
     */
    fun wrapFieldWithMarkers(fieldCode: String, fieldName: String): String {
        return "$GENERATED_FIELD_START:$fieldName\n$fieldCode\n$GENERATED_FIELD_END:$fieldName"
    }

    /**
     * Extracts the content of a specific generated section.
     */
    fun extractGeneratedSection(content: String, sectionName: String): String? {
        val pattern = Pattern.compile(
            "$GENERATED_SECTION_START:$sectionName(.*?)$GENERATED_SECTION_END:$sectionName",
            Pattern.DOTALL
        )
        val matcher = pattern.matcher(content)
        return if (matcher.find()) {
            matcher.group(1).trim()
        } else null
    }

    /**
     * Updates a specific generated section in the content.
     */
    fun updateGeneratedSection(
        content: String,
        sectionName: String,
        newSectionContent: String
    ): String {
        val pattern = Pattern.compile(
            "($GENERATED_SECTION_START:$sectionName)(.*?)($GENERATED_SECTION_END:$sectionName)",
            Pattern.DOTALL
        )
        val matcher = pattern.matcher(content)

        return if (matcher.find()) {
            val newSection = "${matcher.group(1)}\n$newSectionContent\n${matcher.group(3)}"
            matcher.replaceFirst(newSection)
        } else {
            // Section doesn't exist, add it at the end
            "$content\n\n$GENERATED_SECTION_START:$sectionName\n$newSectionContent\n$GENERATED_SECTION_END:$sectionName"
        }
    }

    /**
     * Parses the content into sections (generated and manual).
     * Enhanced to identify semantic sections (methods, fields, classes) even without explicit markers.
     */
    private fun parseCodeSections(content: String): List<CodeSection> {
        val sections = mutableListOf<CodeSection>()
        var currentIndex = 0

        // Find all generated sections with explicit markers
        val generatedMatchers = listOf(
            GENERATED_SECTION_PATTERN.matcher(content),
            GENERATED_METHOD_PATTERN.matcher(content),
            GENERATED_FIELD_PATTERN.matcher(content)
        )

        val allMatches = mutableListOf<Triple<Int, Int, SectionType>>()

        generatedMatchers.forEachIndexed { index, matcher ->
            while (matcher.find()) {
                val sectionType = when (index) {
                    0 -> SectionType.GENERATED_SECTION
                    1 -> SectionType.GENERATED_METHOD
                    else -> SectionType.GENERATED_FIELD
                }
                allMatches.add(Triple(matcher.start(), matcher.end(), sectionType))
            }
        }

        // Sort matches by start position
        allMatches.sortBy { it.first }

        // Create sections for explicitly marked code
        allMatches.forEach { (start, end, type) ->
            // Add manual code before this generated section
            if (currentIndex < start) {
                val manualContent = content.substring(currentIndex, start).trim()
                if (manualContent.isNotEmpty()) {
                    // Try to identify semantic sections in the manual code
                    val semanticSections = identifySemanticSections(manualContent, currentIndex)
                    if (semanticSections.isNotEmpty()) {
                        sections.addAll(semanticSections)
                    } else {
                        sections.add(
                            CodeSection(
                                type = SectionType.MANUAL_CODE,
                                name = "manual_${sections.size}",
                                content = manualContent,
                                startMarker = "",
                                endMarker = "",
                                startIndex = currentIndex,
                                endIndex = start
                            )
                        )
                    }
                }
            }

            // Add the generated section
            val generatedContent = content.substring(start, end)
            val sectionName = extractSectionName(generatedContent, type)
            sections.add(
                CodeSection(
                    type = type,
                    name = sectionName,
                    content = generatedContent,
                    startMarker = getStartMarker(type),
                    endMarker = getEndMarker(type),
                    startIndex = start,
                    endIndex = end
                )
            )

            currentIndex = end
        }

        // Add remaining manual code
        if (currentIndex < content.length) {
            val manualContent = content.substring(currentIndex).trim()
            if (manualContent.isNotEmpty()) {
                // Try to identify semantic sections in the remaining manual code
                val semanticSections = identifySemanticSections(manualContent, currentIndex)
                if (semanticSections.isNotEmpty()) {
                    sections.addAll(semanticSections)
                } else {
                    sections.add(
                        CodeSection(
                            type = SectionType.MANUAL_CODE,
                            name = "manual_${sections.size}",
                            content = manualContent,
                            startMarker = "",
                            endMarker = "",
                            startIndex = currentIndex,
                            endIndex = content.length
                        )
                    )
                }
            }
        }

        return sections
    }
    
    /**
     * Identifies semantic sections (methods, fields, classes) in the given content.
     * 
     * @param content The content to analyze
     * @param startOffset The offset of the content in the original file
     * @return A list of identified semantic sections
     */
    private fun identifySemanticSections(content: String, startOffset: Int): List<CodeSection> {
        val sections = mutableListOf<CodeSection>()
        val lines = content.lines()
        
        // Method pattern: public|private|protected [static] [final] returnType methodName(...) {
        val methodPattern = """(public|private|protected)?\s*(static)?\s*(final)?\s*[\w<>[\],\s]+\s+(\w+)\s*\([^)]*\)\s*(\{|throws)""".toRegex()
        
        // Field pattern: public|private|protected [static] [final] type fieldName [= value];
        val fieldPattern = """(public|private|protected)?\s*(static)?\s*(final)?\s*[\w<>[\],\s]+\s+(\w+)\s*(=|;)""".toRegex()
        
        // Class/interface pattern: public|private|protected [static] [final] class|interface ClassName [extends|implements] {
        val classPattern = """(public|private|protected)?\s*(static)?\s*(final)?\s*(class|interface|enum)\s+(\w+)""".toRegex()
        
        // Import pattern: import package.name.Class;
        val importPattern = """import\s+[\w.]+\s*;""".toRegex()
        
        // Package pattern: package package.name;
        val packagePattern = """package\s+[\w.]+\s*;""".toRegex()
        
        // Track open braces to determine method/class boundaries
        var braceCount = 0
        var currentSectionStart = 0
        var currentSectionType: SectionType? = null
        var currentSectionName = ""
        
        for (i in lines.indices) {
            val line = lines[i]
            val trimmedLine = line.trim()
            
            // Count braces to track scope
            braceCount += trimmedLine.count { it == '{' }
            braceCount -= trimmedLine.count { it == '}' }
            
            // If we're not in a section, check if this line starts a new section
            if (currentSectionType == null) {
                when {
                    methodPattern.containsMatchIn(line) -> {
                        val match = methodPattern.find(line)
                        currentSectionType = SectionType.GENERATED_METHOD
                        currentSectionStart = i
                        currentSectionName = match?.groupValues?.get(4) ?: "unknown_method"
                    }
                    fieldPattern.containsMatchIn(line) && !line.contains("(") -> {
                        val match = fieldPattern.find(line)
                        currentSectionType = SectionType.GENERATED_FIELD
                        currentSectionStart = i
                        currentSectionName = match?.groupValues?.get(4) ?: "unknown_field"
                        
                        // If the field declaration ends with a semicolon, it's a complete section
                        if (trimmedLine.endsWith(";")) {
                            val sectionContent = lines.subList(currentSectionStart, i + 1).joinToString("\n")
                            val startIndex = startOffset + lines.subList(0, currentSectionStart).joinToString("\n").length
                            val endIndex = startIndex + sectionContent.length
                            
                            sections.add(
                                CodeSection(
                                    type = currentSectionType!!,
                                    name = currentSectionName,
                                    content = sectionContent,
                                    startMarker = "",
                                    endMarker = "",
                                    startIndex = startIndex,
                                    endIndex = endIndex
                                )
                            )
                            
                            currentSectionType = null
                            currentSectionName = ""
                        }
                    }
                    classPattern.containsMatchIn(line) -> {
                        val match = classPattern.find(line)
                        currentSectionType = SectionType.GENERATED_SECTION
                        currentSectionStart = i
                        currentSectionName = match?.groupValues?.get(5) ?: "unknown_class"
                    }
                    importPattern.containsMatchIn(line) || packagePattern.containsMatchIn(line) -> {
                        // Handle imports and package declarations as single-line sections
                        val sectionContent = line
                        val startIndex = startOffset + lines.subList(0, i).joinToString("\n").length
                        val endIndex = startIndex + sectionContent.length
                        
                        sections.add(
                            CodeSection(
                                type = SectionType.MANUAL_CODE,
                                name = if (importPattern.containsMatchIn(line)) "import" else "package",
                                content = sectionContent,
                                startMarker = "",
                                endMarker = "",
                                startIndex = startIndex,
                                endIndex = endIndex
                            )
                        )
                    }
                }
            } else if (braceCount == 0 && currentSectionType != null && trimmedLine.contains("}")) {
                // End of a method or class section
                val sectionContent = lines.subList(currentSectionStart, i + 1).joinToString("\n")
                val startIndex = startOffset + lines.subList(0, currentSectionStart).joinToString("\n").length
                val endIndex = startIndex + sectionContent.length
                
                sections.add(
                    CodeSection(
                        type = currentSectionType!!,
                        name = currentSectionName,
                        content = sectionContent,
                        startMarker = "",
                        endMarker = "",
                        startIndex = startIndex,
                        endIndex = endIndex
                    )
                )
                
                currentSectionType = null
                currentSectionName = ""
            }
        }
        
        // If we have an unfinished section at the end, add it as manual code
        if (currentSectionType != null) {
            val sectionContent = lines.subList(currentSectionStart, lines.size).joinToString("\n")
            val startIndex = startOffset + lines.subList(0, currentSectionStart).joinToString("\n").length
            val endIndex = startIndex + sectionContent.length
            
            sections.add(
                CodeSection(
                    type = SectionType.MANUAL_CODE,
                    name = "manual_unfinished",
                    content = sectionContent,
                    startMarker = "",
                    endMarker = "",
                    startIndex = startIndex,
                    endIndex = endIndex
                )
            )
        }
        
        return sections
    }

    /**
     * Builds merged content from existing and new sections.
     * 
     * @param existingSections The existing sections from the file
     * @param newSections The newly generated sections
     * @param strategy The strategy to use for merging
     * @param project The project context for UI interactions (optional)
     * @return The merged content
     */
    private fun buildMergedContent(
        existingSections: List<CodeSection>,
        newSections: List<CodeSection>,
        strategy: UpdateStrategy,
        project: Project? = null
    ): String {
        val result = StringBuilder()
        val processedGeneratedSections = mutableSetOf<String>()

        // Process existing sections
        existingSections.forEach { section ->
            when (section.type) {
                SectionType.MANUAL_CODE -> {
                    if (strategy.preserveManualChanges) {
                        result.append(section.content).append("\n")
                    }
                }
                SectionType.GENERATED_SECTION,
                SectionType.GENERATED_METHOD,
                SectionType.GENERATED_FIELD -> {
                    if (strategy.updateGeneratedSections) {
                        // Find corresponding new section
                        val newSection = newSections.find { it.name == section.name && it.type == section.type }
                        if (newSection != null) {
                            if (strategy.preserveManualChangesInGeneratedSections) {
                                // Detect and preserve manual changes
                                val mergedContent = mergeWithManualChanges(
                                    section.content,
                                    newSection.content,
                                    section.name,
                                    section.type,
                                    strategy,
                                    project
                                )
                                result.append(mergedContent).append("\n")
                            } else {
                                result.append(newSection.content).append("\n")
                            }
                            processedGeneratedSections.add("${newSection.type.name}:${newSection.name}")
                        } else if (!strategy.removeObsoleteGeneratedCode) {
                            result.append(section.content).append("\n")
                        }
                    } else {
                        result.append(section.content).append("\n")
                    }
                    processedGeneratedSections.add("${section.type.name}:${section.name}")
                }
            }
        }

        // Add new generated sections that weren't processed
        if (strategy.addMissingGeneratedCode) {
            newSections.forEach { section ->
                val sectionKey = "${section.type.name}:${section.name}"
                if (!processedGeneratedSections.contains(sectionKey)) {
                    result.append(section.content).append("\n")
                }
            }
        }

        return result.toString().trim()
    }
    
    /**
     * Merges new generated code with existing content, preserving manual changes.
     * Uses a line-by-line comparison to identify and preserve manual changes.
     */
    private fun mergeWithManualChanges(
        existingContent: String,
        newContent: String,
        sectionName: String,
        sectionType: SectionType,
        strategy: UpdateStrategy,
        project: Project?
    ): String {
        // Extract the actual content without markers
        val existingLines = extractContentWithoutMarkers(existingContent, sectionType).lines()
        val newLines = extractContentWithoutMarkers(newContent, sectionType).lines()
        
        // If content is identical, no need to merge
        if (existingLines == newLines) {
            return newContent
        }
        
        // Detect manual changes using line-by-line comparison
        val manualChanges = detectManualChanges(existingLines, newLines)
        
        // If no manual changes, use the new content
        if (manualChanges.isEmpty()) {
            return newContent
        }
        
        // If there are conflicts and UI is enabled, show conflict resolution dialog
        if (strategy.showConflictResolutionUI && project != null && hasConflicts(manualChanges)) {
            return resolveConflictsWithUI(existingContent, newContent, manualChanges, sectionName, project)
        }
        
        // Apply manual changes to the new content
        return applyManualChanges(newContent, manualChanges, sectionType)
    }
    
    /**
     * Extracts the content without the section markers.
     */
    private fun extractContentWithoutMarkers(content: String, sectionType: SectionType): String {
        val lines = content.lines()
        
        // Find start and end marker lines
        val startMarker = getStartMarker(sectionType)
        val endMarker = getEndMarker(sectionType)
        
        val startIndex = lines.indexOfFirst { it.trim().startsWith(startMarker) }
        val endIndex = lines.indexOfLast { it.trim().startsWith(endMarker) }
        
        // If markers not found or invalid indices, return the original content
        if (startIndex < 0 || endIndex < 0 || startIndex >= endIndex) {
            return content
        }
        
        // Extract content between markers
        return lines.subList(startIndex + 1, endIndex).joinToString("\n")
    }
    
    /**
     * Detects manual changes by comparing existing lines with original generated lines.
     * Returns a list of manual changes with line numbers and content.
     */
    private fun detectManualChanges(existingLines: List<String>, originalLines: List<String>): List<ManualChange> {
        val manualChanges = mutableListOf<ManualChange>()
        val longestCommonSubsequence = computeLCS(existingLines, originalLines)
        
        var existingIndex = 0
        var originalIndex = 0
        var lcsIndex = 0
        
        while (existingIndex < existingLines.size || originalIndex < originalLines.size) {
            // Both lines match the LCS - no change
            if (lcsIndex < longestCommonSubsequence.size && 
                existingIndex < existingLines.size && 
                originalIndex < originalLines.size && 
                existingLines[existingIndex] == longestCommonSubsequence[lcsIndex] &&
                originalLines[originalIndex] == longestCommonSubsequence[lcsIndex]) {
                existingIndex++
                originalIndex++
                lcsIndex++
                continue
            }
            
            // Existing line matches LCS but original doesn't - deletion in original
            if (lcsIndex < longestCommonSubsequence.size && 
                existingIndex < existingLines.size && 
                existingLines[existingIndex] == longestCommonSubsequence[lcsIndex]) {
                manualChanges.add(ManualChange(
                    ChangeType.DELETION,
                    originalIndex,
                    originalLines.getOrNull(originalIndex) ?: "",
                    null
                ))
                originalIndex++
                continue
            }
            
            // Original line matches LCS but existing doesn't - addition in existing
            if (lcsIndex < longestCommonSubsequence.size && 
                originalIndex < originalLines.size && 
                originalLines[originalIndex] == longestCommonSubsequence[lcsIndex]) {
                manualChanges.add(ManualChange(
                    ChangeType.ADDITION,
                    existingIndex,
                    null,
                    existingLines[existingIndex]
                ))
                existingIndex++
                continue
            }
            
            // Neither matches LCS - modification
            if (existingIndex < existingLines.size && originalIndex < originalLines.size) {
                manualChanges.add(ManualChange(
                    ChangeType.MODIFICATION,
                    originalIndex,
                    originalLines[originalIndex],
                    existingLines[existingIndex]
                ))
                existingIndex++
                originalIndex++
                continue
            }
            
            // Remaining lines in existing - additions
            if (existingIndex < existingLines.size) {
                manualChanges.add(ManualChange(
                    ChangeType.ADDITION,
                    originalLines.size,
                    null,
                    existingLines[existingIndex]
                ))
                existingIndex++
                continue
            }
            
            // Remaining lines in original - deletions
            if (originalIndex < originalLines.size) {
                manualChanges.add(ManualChange(
                    ChangeType.DELETION,
                    originalIndex,
                    originalLines[originalIndex],
                    null
                ))
                originalIndex++
                continue
            }
        }
        
        return manualChanges
    }
    
    /**
     * Computes the Longest Common Subsequence of two lists of strings.
     */
    private fun computeLCS(a: List<String>, b: List<String>): List<String> {
        val lengths = Array(a.size + 1) { IntArray(b.size + 1) }
        
        // Fill the lengths array
        for (i in a.indices) {
            for (j in b.indices) {
                lengths[i + 1][j + 1] = if (a[i] == b[j]) {
                    lengths[i][j] + 1
                } else {
                    maxOf(lengths[i + 1][j], lengths[i][j + 1])
                }
            }
        }
        
        // Reconstruct the LCS
        val result = mutableListOf<String>()
        var i = a.size
        var j = b.size
        
        while (i > 0 && j > 0) {
            if (a[i - 1] == b[j - 1]) {
                result.add(0, a[i - 1])
                i--
                j--
            } else if (lengths[i - 1][j] > lengths[i][j - 1]) {
                i--
            } else {
                j--
            }
        }
        
        return result
    }
    
    /**
     * Checks if there are conflicts in the manual changes.
     * Uses a more sophisticated approach to determine what constitutes a conflict:
     * - Simple whitespace or formatting changes are not considered conflicts
     * - Comment changes are not considered conflicts
     * - Import statement changes are not considered conflicts
     * - Method signature changes (parameter names, return type) are considered conflicts
     * - Changes to field types are considered conflicts
     */
    private fun hasConflicts(manualChanges: List<ManualChange>): Boolean {
        return manualChanges.any { change ->
            if (change.type != ChangeType.MODIFICATION) {
                return@any false
            }
            
            val originalLine = change.originalLine ?: ""
            val newLine = change.newLine ?: ""
            
            // Ignore whitespace-only changes
            if (originalLine.trim() == newLine.trim()) {
                return@any false
            }
            
            // Ignore comment changes
            if (originalLine.trim().startsWith("//") && newLine.trim().startsWith("//")) {
                return@any false
            }
            
            // Ignore import statement changes
            if (originalLine.trim().startsWith("import ") && newLine.trim().startsWith("import ")) {
                return@any false
            }
            
            // Check for method signature changes
            val isMethodSignatureChange = isMethodSignatureChange(originalLine, newLine)
            
            // Check for field type changes
            val isFieldTypeChange = isFieldTypeChange(originalLine, newLine)
            
            // Consider it a conflict if it's a method signature or field type change
            isMethodSignatureChange || isFieldTypeChange
        }
    }
    
    /**
     * Checks if the change is a method signature change.
     */
    private fun isMethodSignatureChange(originalLine: String, newLine: String): Boolean {
        // Simple heuristic: if both lines contain parentheses and the text before the first parenthesis is different
        if (originalLine.contains("(") && newLine.contains("(")) {
            val originalBeforeParens = originalLine.substringBefore("(").trim()
            val newBeforeParens = newLine.substringBefore("(").trim()
            
            if (originalBeforeParens != newBeforeParens) {
                return true
            }
            
            // Check parameter list
            val originalParams = originalLine.substringAfter("(").substringBefore(")").trim()
            val newParams = newLine.substringAfter("(").substringBefore(")").trim()
            
            // If parameter count is different, it's a signature change
            val originalParamCount = if (originalParams.isEmpty()) 0 else originalParams.count { it == ',' } + 1
            val newParamCount = if (newParams.isEmpty()) 0 else newParams.count { it == ',' } + 1
            
            if (originalParamCount != newParamCount) {
                return true
            }
            
            // Check return type (if present)
            if (originalLine.contains("->") && newLine.contains("->")) {
                val originalReturnType = originalLine.substringAfter("->").trim()
                val newReturnType = newLine.substringAfter("->").trim()
                
                if (originalReturnType != newReturnType) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Checks if the change is a field type change.
     */
    private fun isFieldTypeChange(originalLine: String, newLine: String): Boolean {
        // Simple heuristic for field declarations
        val originalWords = originalLine.trim().split("\\s+".toRegex())
        val newWords = newLine.trim().split("\\s+".toRegex())
        
        // If both have at least 2 words (type and name) and the last word (name) is the same
        // but earlier words (type, modifiers) are different
        if (originalWords.size >= 2 && newWords.size >= 2 && 
            originalWords.last() == newWords.last() && 
            originalWords.dropLast(1) != newWords.dropLast(1)) {
            return true
        }
        
        return false
    }
    
    /**
     * Shows an enhanced conflict resolution dialog with detailed information and more granular control.
     * Provides options to resolve conflicts at different levels of granularity:
     * - Global level: Apply the same resolution strategy to all conflicts
     * - Section level: Resolve conflicts for specific sections (methods, fields, etc.)
     * - Line level: Resolve conflicts line by line
     */
    private fun resolveConflictsWithUI(
        existingContent: String,
        newContent: String,
        manualChanges: List<ManualChange>,
        sectionName: String,
        project: Project
    ): String {
        // Group conflicts by type for better presentation
        val methodSignatureChanges = mutableListOf<ManualChange>()
        val fieldTypeChanges = mutableListOf<ManualChange>()
        val otherChanges = mutableListOf<ManualChange>()
        
        manualChanges.forEach { change ->
            val originalLine = change.originalLine ?: ""
            val newLine = change.newLine ?: ""
            
            when {
                isMethodSignatureChange(originalLine, newLine) -> methodSignatureChanges.add(change)
                isFieldTypeChange(originalLine, newLine) -> fieldTypeChanges.add(change)
                else -> otherChanges.add(change)
            }
        }
        
        // Create a detailed conflict message
        val message = buildString {
            appendLine("Conflicts detected in section '$sectionName'.")
            appendLine("There are ${manualChanges.size} manual changes that conflict with the new generated code.")
            appendLine()
            
            if (methodSignatureChanges.isNotEmpty()) {
                appendLine("Method signature changes (${methodSignatureChanges.size}):")
                methodSignatureChanges.take(3).forEach { change ->
                    appendLine("- Original: ${change.originalLine?.trim()}")
                    appendLine("  New:      ${change.newLine?.trim()}")
                    appendLine()
                }
                if (methodSignatureChanges.size > 3) {
                    appendLine("  ... and ${methodSignatureChanges.size - 3} more")
                    appendLine()
                }
            }
            
            if (fieldTypeChanges.isNotEmpty()) {
                appendLine("Field type changes (${fieldTypeChanges.size}):")
                fieldTypeChanges.take(3).forEach { change ->
                    appendLine("- Original: ${change.originalLine?.trim()}")
                    appendLine("  New:      ${change.newLine?.trim()}")
                    appendLine()
                }
                if (fieldTypeChanges.size > 3) {
                    appendLine("  ... and ${fieldTypeChanges.size - 3} more")
                    appendLine()
                }
            }
            
            if (otherChanges.isNotEmpty()) {
                appendLine("Other changes (${otherChanges.size}):")
                otherChanges.take(3).forEach { change ->
                    appendLine("- Original: ${change.originalLine?.trim()}")
                    appendLine("  New:      ${change.newLine?.trim()}")
                    appendLine()
                }
                if (otherChanges.size > 3) {
                    appendLine("  ... and ${otherChanges.size - 3} more")
                    appendLine()
                }
            }
            
            appendLine("How would you like to resolve these conflicts?")
        }
        
        // Show dialog with enhanced options
        val options = arrayOf(
            "Keep All Manual Changes", 
            "Use All Generated Code", 
            "Merge (Keep Both Where Possible)",
            "Resolve Method Changes Only",
            "Resolve Field Changes Only",
            "Resolve Line by Line"
        )
        
        val choice = Messages.showDialog(
            project,
            message,
            "Enhanced Conflict Resolution",
            options,
            0, // Default option
            Messages.getQuestionIcon()
        )
        
        return when (choice) {
            0 -> existingContent // Keep all manual changes
            1 -> newContent // Use all generated code
            2 -> applyManualChanges(newContent, manualChanges, SectionType.GENERATED_SECTION) // Merge all
            3 -> resolveMethodChangesOnly(existingContent, newContent, methodSignatureChanges, fieldTypeChanges, otherChanges, sectionName, project)
            4 -> resolveFieldChangesOnly(existingContent, newContent, methodSignatureChanges, fieldTypeChanges, otherChanges, sectionName, project)
            5 -> resolveLineByLine(existingContent, newContent, manualChanges, sectionName, project)
            else -> newContent // Default to new content if dialog is cancelled
        }
    }
    
    /**
     * Resolves method signature changes only, keeping other manual changes.
     */
    private fun resolveMethodChangesOnly(
        existingContent: String,
        newContent: String,
        methodSignatureChanges: List<ManualChange>,
        fieldTypeChanges: List<ManualChange>,
        otherChanges: List<ManualChange>,
        sectionName: String,
        project: Project
    ): String {
        // Show dialog for method changes
        if (methodSignatureChanges.isEmpty()) {
            return applyManualChanges(newContent, fieldTypeChanges + otherChanges, SectionType.GENERATED_SECTION)
        }
        
        val message = "How would you like to resolve method signature changes?"
        val options = arrayOf("Keep Manual Methods", "Use Generated Methods", "Merge Methods")
        
        val choice = Messages.showDialog(
            project,
            message,
            "Method Conflict Resolution",
            options,
            0,
            Messages.getQuestionIcon()
        )
        
        // Apply the chosen resolution for method changes
        val resolvedContent = when (choice) {
            0 -> applyManualChanges(newContent, fieldTypeChanges + otherChanges, SectionType.GENERATED_SECTION) // Keep manual methods
            1 -> {
                // Use generated methods, but keep other manual changes
                val allChangesExceptMethods = fieldTypeChanges + otherChanges
                applyManualChanges(newContent, allChangesExceptMethods, SectionType.GENERATED_SECTION)
            }
            2 -> applyManualChanges(newContent, methodSignatureChanges + fieldTypeChanges + otherChanges, SectionType.GENERATED_SECTION) // Merge all
            else -> newContent // Default to new content if dialog is cancelled
        }
        
        return resolvedContent
    }
    
    /**
     * Resolves field type changes only, keeping other manual changes.
     */
    private fun resolveFieldChangesOnly(
        existingContent: String,
        newContent: String,
        methodSignatureChanges: List<ManualChange>,
        fieldTypeChanges: List<ManualChange>,
        otherChanges: List<ManualChange>,
        sectionName: String,
        project: Project
    ): String {
        // Show dialog for field changes
        if (fieldTypeChanges.isEmpty()) {
            return applyManualChanges(newContent, methodSignatureChanges + otherChanges, SectionType.GENERATED_SECTION)
        }
        
        val message = "How would you like to resolve field type changes?"
        val options = arrayOf("Keep Manual Fields", "Use Generated Fields", "Merge Fields")
        
        val choice = Messages.showDialog(
            project,
            message,
            "Field Conflict Resolution",
            options,
            0,
            Messages.getQuestionIcon()
        )
        
        // Apply the chosen resolution for field changes
        val resolvedContent = when (choice) {
            0 -> applyManualChanges(newContent, methodSignatureChanges + otherChanges, SectionType.GENERATED_SECTION) // Keep manual fields
            1 -> {
                // Use generated fields, but keep other manual changes
                val allChangesExceptFields = methodSignatureChanges + otherChanges
                applyManualChanges(newContent, allChangesExceptFields, SectionType.GENERATED_SECTION)
            }
            2 -> applyManualChanges(newContent, methodSignatureChanges + fieldTypeChanges + otherChanges, SectionType.GENERATED_SECTION) // Merge all
            else -> newContent // Default to new content if dialog is cancelled
        }
        
        return resolvedContent
    }
    
    /**
     * Resolves conflicts line by line, allowing the user to choose for each conflict.
     */
    private fun resolveLineByLine(
        existingContent: String,
        newContent: String,
        manualChanges: List<ManualChange>,
        sectionName: String,
        project: Project
    ): String {
        // Start with the new content
        var resultContent = newContent
        
        // Process each conflict
        for (change in manualChanges.filter { it.type == ChangeType.MODIFICATION }) {
            val originalLine = change.originalLine ?: ""
            val newLine = change.newLine ?: ""
            
            // Show dialog for this specific change
            val message = buildString {
                appendLine("Conflict in line ${change.lineNumber + 1}:")
                appendLine("Original: $originalLine")
                appendLine("New:      $newLine")
                appendLine("How would you like to resolve this conflict?")
            }
            
            val options = arrayOf("Keep Manual Change", "Use Generated Code", "Keep Both")
            
            val choice = Messages.showDialog(
                project,
                message,
                "Line Conflict Resolution",
                options,
                0,
                Messages.getQuestionIcon()
            )
            
            // Apply the chosen resolution for this change
            when (choice) {
                0 -> {
                    // Keep manual change - create a single-change list and apply it
                    val singleChange = listOf(change)
                    resultContent = applyManualChanges(resultContent, singleChange, SectionType.GENERATED_SECTION)
                }
                1 -> {
                    // Use generated code - do nothing as we're starting with the new content
                }
                2 -> {
                    // Keep both - add the manual line after the generated line
                    val lines = extractContentWithoutMarkers(resultContent, SectionType.GENERATED_SECTION).lines().toMutableList()
                    if (change.lineNumber < lines.size) {
                        lines.add(change.lineNumber + 1, "// Manual change: $newLine")
                    }
                    
                    // Reconstruct the content with markers
                    val startMarker = getStartMarker(SectionType.GENERATED_SECTION)
                    val endMarker = getEndMarker(SectionType.GENERATED_SECTION)
                    
                    resultContent = buildString {
                        appendLine(startMarker)
                        appendLine(lines.joinToString("\n"))
                        appendLine(endMarker)
                    }
                }
            }
        }
        
        return resultContent
    }
    
    /**
     * Applies manual changes to the new content.
     */
    private fun applyManualChanges(
        newContent: String,
        manualChanges: List<ManualChange>,
        sectionType: SectionType
    ): String {
        val newLines = extractContentWithoutMarkers(newContent, sectionType).lines().toMutableList()
        
        // Apply changes in reverse order to avoid index shifting
        manualChanges.sortedByDescending { it.lineNumber }.forEach { change ->
            when (change.type) {
                ChangeType.ADDITION -> {
                    if (change.lineNumber <= newLines.size) {
                        newLines.add(change.lineNumber, change.newLine ?: "")
                    } else {
                        newLines.add(change.newLine ?: "")
                    }
                }
                ChangeType.DELETION -> {
                    if (change.lineNumber < newLines.size) {
                        newLines.removeAt(change.lineNumber)
                    }
                }
                ChangeType.MODIFICATION -> {
                    if (change.lineNumber < newLines.size) {
                        newLines[change.lineNumber] = change.newLine ?: newLines[change.lineNumber]
                    }
                }
            }
        }
        
        // Reconstruct the content with markers
        val startMarker = getStartMarker(sectionType)
        val endMarker = getEndMarker(sectionType)
        
        return buildString {
            appendLine(startMarker)
            appendLine(newLines.joinToString("\n"))
            appendLine(endMarker)
        }
    }
    
    /**
     * Represents a manual change detected in the code.
     */
    data class ManualChange(
        val type: ChangeType,
        val lineNumber: Int,
        val originalLine: String?,
        val newLine: String?
    )
    
    /**
     * Types of changes that can be detected.
     */
    enum class ChangeType {
        ADDITION,
        DELETION,
        MODIFICATION
    }

    private fun extractSectionName(content: String, type: SectionType): String {
        val lines = content.lines()
        val startLine = lines.firstOrNull() ?: return "unnamed"

        val colonIndex = startLine.indexOf(':')
        return if (colonIndex > 0 && colonIndex < startLine.length - 1) {
            startLine.substring(colonIndex + 1).trim()
        } else {
            "unnamed_${type.name.lowercase()}"
        }
    }

    private fun getStartMarker(type: SectionType): String {
        return when (type) {
            SectionType.GENERATED_SECTION -> GENERATED_SECTION_START
            SectionType.GENERATED_METHOD -> GENERATED_METHOD_START
            SectionType.GENERATED_FIELD -> GENERATED_FIELD_START
            SectionType.MANUAL_CODE -> ""
        }
    }

    private fun getEndMarker(type: SectionType): String {
        return when (type) {
            SectionType.GENERATED_SECTION -> GENERATED_SECTION_END
            SectionType.GENERATED_METHOD -> GENERATED_METHOD_END
            SectionType.GENERATED_FIELD -> GENERATED_FIELD_END
            SectionType.MANUAL_CODE -> ""
        }
    }
}

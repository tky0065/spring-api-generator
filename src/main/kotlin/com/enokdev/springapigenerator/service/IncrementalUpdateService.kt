package com.enokdev.springapigenerator.service

import com.intellij.openapi.project.Project
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
        val removeObsoleteGeneratedCode: Boolean = false
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
     */
    fun mergeCode(
        existingContent: String,
        newGeneratedCode: String,
        updateStrategy: UpdateStrategy = UpdateStrategy()
    ): String {

        if (!hasGeneratedMarkers(existingContent)) {
            // If no markers exist, wrap the entire new code with markers
            return wrapWithGeneratedMarkers(newGeneratedCode)
        }

        val existingSections = parseCodeSections(existingContent)
        val newSections = parseCodeSections(newGeneratedCode)

        return buildMergedContent(existingSections, newSections, updateStrategy)
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
     */
    private fun parseCodeSections(content: String): List<CodeSection> {
        val sections = mutableListOf<CodeSection>()
        var currentIndex = 0

        // Find all generated sections
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

        // Create sections
        allMatches.forEach { (start, end, type) ->
            // Add manual code before this generated section
            if (currentIndex < start) {
                val manualContent = content.substring(currentIndex, start).trim()
                if (manualContent.isNotEmpty()) {
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

        return sections
    }

    /**
     * Builds merged content from existing and new sections.
     */
    private fun buildMergedContent(
        existingSections: List<CodeSection>,
        newSections: List<CodeSection>,
        strategy: UpdateStrategy
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
                            result.append(newSection.content).append("\n")
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

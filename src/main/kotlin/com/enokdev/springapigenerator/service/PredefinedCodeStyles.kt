package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.service.CodeStyleDetector.*

/**
 * Provides predefined code style configurations for popular coding standards.
 * These configurations can be used as defaults or for style matching.
 */
object PredefinedCodeStyles {

    /**
     * Enum representing well-known code style standards.
     */
    enum class StyleStandard {
        GOOGLE,
        SQUARE,
        KOTLIN_OFFICIAL,
        INTELLIJ_IDEA,
        ANDROID,
        SPRING,
        CUSTOM
    }

    /**
     * Google Java Style Guide configuration.
     * @see <a href="https://google.github.io/styleguide/javaguide.html">Google Java Style Guide</a>
     */
    val GOOGLE_STYLE = CodeStyleConfig(
        indentationType = IndentationType.SPACES,
        indentSize = 2,
        namingConvention = NamingConvention.CAMEL_CASE,
        bracketStyle = BracketStyle.END_OF_LINE,
        commentStyle = CommentStyle.JAVADOC,
        fieldPrefix = "",
        useGetterSetterPrefix = true,
        packageStructure = PackageStructure.LAYERED
    )

    /**
     * Square Java Code Style configuration.
     * @see <a href="https://github.com/square/java-code-styles">Square Java Code Styles</a>
     */
    val SQUARE_STYLE = CodeStyleConfig(
        indentationType = IndentationType.SPACES,
        indentSize = 2,
        namingConvention = NamingConvention.CAMEL_CASE,
        bracketStyle = BracketStyle.END_OF_LINE,
        commentStyle = CommentStyle.JAVADOC,
        fieldPrefix = "",
        useGetterSetterPrefix = true,
        packageStructure = PackageStructure.FEATURE_BASED
    )

    /**
     * Kotlin Official Style Guide configuration.
     * @see <a href="https://kotlinlang.org/docs/coding-conventions.html">Kotlin Coding Conventions</a>
     */
    val KOTLIN_OFFICIAL_STYLE = CodeStyleConfig(
        indentationType = IndentationType.SPACES,
        indentSize = 4,
        namingConvention = NamingConvention.CAMEL_CASE,
        bracketStyle = BracketStyle.END_OF_LINE,
        commentStyle = CommentStyle.BLOCK,
        fieldPrefix = "",
        useGetterSetterPrefix = false, // Kotlin uses properties
        packageStructure = PackageStructure.FEATURE_BASED
    )

    /**
     * IntelliJ IDEA default style configuration.
     */
    val INTELLIJ_IDEA_STYLE = CodeStyleConfig(
        indentationType = IndentationType.SPACES,
        indentSize = 4,
        namingConvention = NamingConvention.CAMEL_CASE,
        bracketStyle = BracketStyle.END_OF_LINE,
        commentStyle = CommentStyle.JAVADOC,
        fieldPrefix = "",
        useGetterSetterPrefix = true,
        packageStructure = PackageStructure.LAYERED
    )

    /**
     * Android style configuration based on AOSP.
     * @see <a href="https://source.android.com/setup/contribute/code-style">Android Code Style</a>
     */
    val ANDROID_STYLE = CodeStyleConfig(
        indentationType = IndentationType.SPACES,
        indentSize = 4,
        namingConvention = NamingConvention.CAMEL_CASE,
        bracketStyle = BracketStyle.END_OF_LINE,
        commentStyle = CommentStyle.JAVADOC,
        fieldPrefix = "m",
        useGetterSetterPrefix = true,
        packageStructure = PackageStructure.FEATURE_BASED
    )

    /**
     * Spring Framework style configuration.
     */
    val SPRING_STYLE = CodeStyleConfig(
        indentationType = IndentationType.TABS,
        indentSize = 4,
        namingConvention = NamingConvention.CAMEL_CASE,
        bracketStyle = BracketStyle.END_OF_LINE,
        commentStyle = CommentStyle.JAVADOC,
        fieldPrefix = "",
        useGetterSetterPrefix = true,
        packageStructure = PackageStructure.LAYERED
    )

    /**
     * Get a predefined style configuration by style standard.
     *
     * @param standard The style standard to get the configuration for
     * @return The code style configuration for the specified standard
     */
    fun getStyleConfig(standard: StyleStandard): CodeStyleConfig {
        return when (standard) {
            StyleStandard.GOOGLE -> GOOGLE_STYLE
            StyleStandard.SQUARE -> SQUARE_STYLE
            StyleStandard.KOTLIN_OFFICIAL -> KOTLIN_OFFICIAL_STYLE
            StyleStandard.INTELLIJ_IDEA -> INTELLIJ_IDEA_STYLE
            StyleStandard.ANDROID -> ANDROID_STYLE
            StyleStandard.SPRING -> SPRING_STYLE
            StyleStandard.CUSTOM -> INTELLIJ_IDEA_STYLE // Default to IntelliJ style for custom
        }
    }

    /**
     * Find the closest matching predefined style for a given code style configuration.
     *
     * @param config The code style configuration to match
     * @return The closest matching style standard
     */
    fun findClosestStyle(config: CodeStyleConfig): StyleStandard {
        // Calculate similarity scores for each predefined style
        val scores = mutableMapOf<StyleStandard, Int>()
        
        for (standard in StyleStandard.values()) {
            if (standard == StyleStandard.CUSTOM) continue
            
            val predefinedConfig = getStyleConfig(standard)
            var score = 0
            
            // Compare each property and increment score for matches
            if (config.indentationType == predefinedConfig.indentationType) score += 1
            if (config.indentSize == predefinedConfig.indentSize) score += 1
            if (config.namingConvention == predefinedConfig.namingConvention) score += 2
            if (config.bracketStyle == predefinedConfig.bracketStyle) score += 1
            if (config.commentStyle == predefinedConfig.commentStyle) score += 1
            if (config.fieldPrefix == predefinedConfig.fieldPrefix) score += 1
            if (config.useGetterSetterPrefix == predefinedConfig.useGetterSetterPrefix) score += 1
            if (config.packageStructure == predefinedConfig.packageStructure) score += 1
            
            scores[standard] = score
        }
        
        // Return the style with the highest score, or CUSTOM if no good match
        val bestMatch = scores.maxByOrNull { it.value }
        return if (bestMatch != null && bestMatch.value >= 5) bestMatch.key else StyleStandard.CUSTOM
    }
}
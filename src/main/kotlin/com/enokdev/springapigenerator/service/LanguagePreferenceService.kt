package com.enokdev.springapigenerator.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Service for managing language generation preferences in mixed Java/Kotlin projects.
 */
@State(
    name = "SpringApiGeneratorLanguageSettings",
    storages = [Storage("springApiGeneratorLanguage.xml")]
)
class LanguagePreferenceService : PersistentStateComponent<LanguagePreferenceService.State> {

    data class State(
        var forceLanguage: String? = null, // "java", "kotlin", or null for auto-detect
        var alwaysAsk: Boolean = true,     // Ask user in mixed projects
        var defaultForMixed: String = "auto" // "java", "kotlin", or "auto"
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): LanguagePreferenceService {
            return project.getService(LanguagePreferenceService::class.java)
        }
    }

    /**
     * Gets the preferred language for code generation.
     */
    fun getPreferredLanguage(project: Project): String? {
        val languageInfo = ProjectTypeDetectionService.getProjectLanguageInfo(project)

        return when {
            // If user has forced a specific language, use it
            myState.forceLanguage != null -> myState.forceLanguage

            // If project is purely one language, use that
            languageInfo.primaryLanguage == ProjectTypeDetectionService.ProjectLanguage.JAVA -> "java"
            languageInfo.primaryLanguage == ProjectTypeDetectionService.ProjectLanguage.KOTLIN -> "kotlin"

            // For mixed projects, use preference or auto-detect
            languageInfo.isMixed -> {
                when (myState.defaultForMixed) {
                    "java" -> "java"
                    "kotlin" -> "kotlin"
                    else -> null // auto-detect
                }
            }

            else -> null // auto-detect
        }
    }

    /**
     * Sets the forced language preference.
     */
    fun setForcedLanguage(language: String?) {
        myState.forceLanguage = language
    }

    /**
     * Sets whether to always ask user in mixed projects.
     */
    fun setAlwaysAsk(alwaysAsk: Boolean) {
        myState.alwaysAsk = alwaysAsk
    }

    /**
     * Sets the default language for mixed projects.
     */
    fun setDefaultForMixed(language: String) {
        myState.defaultForMixed = language
    }

    /**
     * Checks if user should be prompted for language choice.
     */
    fun shouldPromptForLanguage(project: Project): Boolean {
        val languageInfo = ProjectTypeDetectionService.getProjectLanguageInfo(project)
        return languageInfo.isMixed && myState.alwaysAsk && myState.forceLanguage == null
    }
}

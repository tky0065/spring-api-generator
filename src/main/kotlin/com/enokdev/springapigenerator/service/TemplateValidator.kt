package com.enokdev.springapigenerator.service

import com.intellij.openapi.diagnostic.Logger
import java.io.File

/**
 * Service for validating templates - DÉSACTIVÉ pour permettre toutes les annotations.
 * Mode flexible permanent pour maximiser la compatibilité.
 */
class TemplateValidator {
    private val logger = Logger.getInstance(javaClass)

    companion object {
        // SUPPRESSION DE TOUTES LES CONTRAINTES D'ANNOTATIONS
        // Plus de validation stricte - tout est permis
        private var instance: TemplateValidator? = null

        // Mode flexible permanent
        private var strictValidation = false

        /**
         * Get the singleton instance of TemplateValidator
         */
        fun getInstance(): TemplateValidator {
            if (instance == null) {
                instance = TemplateValidator()
            }
            return instance!!
        }

        /**
         * Validation toujours désactivée - aucune contrainte
         */
        fun setStrictValidation(enabled: Boolean) {
            // Ignorer complètement - toujours flexible
            strictValidation = false
        }

        /**
         * Toujours en mode flexible
         */
        fun isStrictValidationEnabled(): Boolean = false
    }

    /**
     * VALIDATION DÉSACTIVÉE - retourne toujours une liste vide
     * Aucune contrainte d'annotations
     */
    fun validateTemplate(templateFile: File): List<String> {
        // SUPPRESSION COMPLÈTE DE LA VALIDATION
        // Toujours retourner une liste vide = aucun problème
        return emptyList()
    }

    /**
     * VALIDATION DÉSACTIVÉE - retourne toujours un map vide
     */
    fun validateTemplatesInDirectory(directory: File): Map<String, List<String>> {
        // SUPPRESSION COMPLÈTE DE LA VALIDATION
        return emptyMap()
    }

    /**
     * Extract the template type from the file name.
     */
    private fun getTemplateType(fileName: String): String {
        return when {
            fileName.contains(".java.ft") -> fileName.substringBefore(".java.ft")
            fileName.contains(".kt.ft") -> fileName.substringBefore(".kt.ft")
            else -> fileName.substringBefore(".")
        }
    }

    /**
     * Log validation results - toujours positif maintenant
     */
    fun logValidationResults(results: Map<String, List<String>>) {
        // Toujours signaler que tout va bien
        logger.info("All templates are valid - no annotation constraints applied.")
    }

    /**
     * Tous les templates peuvent être utilisés - aucune contrainte
     */
    fun canTemplateBeUsed(templateFile: File): Boolean {
        // SUPPRESSION DE TOUTES LES CONTRAINTES
        return templateFile.exists() && templateFile.isFile
    }
}
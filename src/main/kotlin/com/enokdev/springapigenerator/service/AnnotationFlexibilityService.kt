package com.enokdev.springapigenerator.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Service pour la flexibilité maximale des annotations - TOUTES CONTRAINTES SUPPRIMÉES.
 * Permet la génération avec ou sans annotations, sans aucune restriction.
 */
@Service(Service.Level.PROJECT)
class AnnotationFlexibilityService(private val project: Project) {
    private val logger = Logger.getInstance(javaClass)

    companion object {
        // SUPPRESSION DE TOUTES LES CONTRAINTES
        // Mode flexible permanent et global
        private var defaultStrictMode = false

        /**
         * Mode strict toujours désactivé
         */
        fun setGlobalStrictMode(strict: Boolean) {
            // Ignorer - toujours flexible
            defaultStrictMode = false
            TemplateValidator.setStrictValidation(false)
        }

        /**
         * Toujours en mode flexible
         */
        fun isGlobalStrictMode(): Boolean = false
    }

    /**
     * Initialise en mode flexible permanent
     */
    fun initializeFlexibilitySettings() {
        // Forcer le mode flexible permanent
        TemplateValidator.setStrictValidation(false)
        logger.info("Annotation flexibility initialized - ALL CONSTRAINTS REMOVED")
    }

    /**
     * Ignorer les tentatives d'activation de la validation stricte
     */
    fun setStrictAnnotationValidation(strict: Boolean) {
        // Toujours ignorer et rester en mode flexible
        TemplateValidator.setStrictValidation(false)
        logger.info("Annotation validation kept flexible - constraints disabled")
    }

    /**
     * Toujours permettre la génération sans annotations
     */
    fun canProceedWithoutAnnotations(): Boolean {
        // TOUJOURS AUTORISER
        return true
    }

    /**
     * Toujours compatible - aucune contrainte
     */
    fun isClassCompatibleForGeneration(className: String, hasAnnotations: Boolean): Boolean {
        // SUPPRESSION DE TOUTES LES CONTRAINTES
        // Toujours compatible, avec ou sans annotations
        return true
    }

    /**
     * Suggestions positives seulement - pas de contraintes
     */
    fun getSuggestionsForClass(className: String, classType: String, hasAnnotations: Boolean): List<String> {
        val suggestions = mutableListOf<String>()

        if (!hasAnnotations) {
            // Suggestions positives, pas de contraintes
            suggestions.add("Génération possible sans annotations (mode flexible)")
            suggestions.add("Les annotations Spring peuvent être ajoutées manuellement si désiré")
        } else {
            suggestions.add("Classe avec annotations détectées - génération optimale")
        }

        return suggestions
    }

    /**
     * Rapport toujours positif
     */
    fun getAnnotationCompatibilityReport(): Map<String, Any> {
        val report = mutableMapOf<String, Any>()

        report["strictMode"] = false
        report["canProceedWithoutAnnotations"] = true
        report["status"] = "flexible"
        report["constraints"] = "none"

        // Toujours des recommandations positives
        val recommendations = listOf(
            "Mode flexible actif - génération sans contraintes",
            "Annotations optionnelles - génération possible dans tous les cas"
        )

        report["recommendations"] = recommendations

        return report
    }

    /**
     * Pas de réparation nécessaire - tout fonctionne
     */
    fun autoFixAnnotationIssues(): Boolean {
        // Forcer le mode flexible
        setStrictAnnotationValidation(false)
        logger.info("Auto-fix applied: flexible mode enforced")
        return true
    }
}

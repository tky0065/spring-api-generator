package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.util.BuildSystemHelper
import com.intellij.openapi.project.Project

/**
 * Service pour valider et gérer les dépendances requises selon les fonctionnalités sélectionnées.
 * Ce service vérifie si les dépendances nécessaires sont présentes et les ajoute automatiquement si nécessaire.
 */
class DependencyValidationService {

    companion object {
        /**
         * Valide et assure que toutes les dépendances requises sont présentes
         *
         * @param project Le projet IntelliJ
         * @param selectedFeatures Map des fonctionnalités sélectionnées
         * @return Liste des dépendances qui ont été ajoutées
         */
        fun validateAndEnsureDependencies(project: Project, selectedFeatures: Map<String, Boolean>): List<String> {
            val addedDependencies = mutableListOf<String>()
            val buildSystemType = BuildSystemHelper.detectBuildSystemType(project)

            // Vérifier les fonctionnalités nécessitant des dépendances spécifiques
            selectedFeatures.forEach { (feature, isEnabled) ->
                if (isEnabled) {
                    when (feature.lowercase()) {
                        "validation", "dto_validation", "entity_validation" -> {
                            if (!BuildSystemHelper.hasValidationDependency(project)) {
                                BuildSystemHelper.addValidationDependency(project, buildSystemType)
                                addedDependencies.add("spring-boot-starter-validation")
                            }
                        }
                        "swagger", "openapi", "api_documentation" -> {
                            if (!BuildSystemHelper.hasSwaggerDependency(project)) {
                                BuildSystemHelper.addSwaggerDependency(project, buildSystemType)
                                addedDependencies.add("springdoc-openapi-ui")
                            }
                        }
                        "security", "spring_security", "jwt" -> {
                            if (!BuildSystemHelper.hasSecurityDependency(project)) {
                                BuildSystemHelper.addSpringSecurityDependency(project, buildSystemType)
                                addedDependencies.add("spring-boot-starter-security")
                            }
                        }
                        "graphql", "spring_graphql" -> {
                            if (!BuildSystemHelper.hasGraphQLDependency(project)) {
                                BuildSystemHelper.addGraphQLDependency(project, buildSystemType)
                                addedDependencies.add("spring-boot-starter-graphql")
                            }
                        }
                        "mapstruct", "mapping", "object_mapping" -> {
                            if (!BuildSystemHelper.hasMapStructDependency(project)) {
                                BuildSystemHelper.addMapstructDependency(project, buildSystemType)
                                addedDependencies.add("mapstruct")
                            }
                        }
                    }
                }
            }

            return addedDependencies
        }

        /**
         * Vérifie si une fonctionnalité donnée a toutes ses dépendances requises
         *
         * @param project Le projet IntelliJ
         * @param feature La fonctionnalité à vérifier
         * @return true si toutes les dépendances sont présentes, false sinon
         */
        fun hasRequiredDependencies(project: Project, feature: String): Boolean {
            return when (feature.lowercase()) {
                "validation", "dto_validation", "entity_validation" ->
                    BuildSystemHelper.hasValidationDependency(project)
                "swagger", "openapi", "api_documentation" ->
                    BuildSystemHelper.hasSwaggerDependency(project)
                "security", "spring_security", "jwt" ->
                    BuildSystemHelper.hasSecurityDependency(project)
                "graphql", "spring_graphql" ->
                    BuildSystemHelper.hasGraphQLDependency(project)
                "mapstruct", "mapping", "object_mapping" ->
                    BuildSystemHelper.hasMapStructDependency(project)
                else -> true // Pour les fonctionnalités qui n'ont pas de dépendances spécifiques
            }
        }

        /**
         * Retourne la liste des dépendances manquantes pour les fonctionnalités sélectionnées
         *
         * @param project Le projet IntelliJ
         * @param selectedFeatures Map des fonctionnalités sélectionnées
         * @return Liste des noms des dépendances manquantes
         */
        fun getMissingDependencies(project: Project, selectedFeatures: Map<String, Boolean>): List<String> {
            val missingDependencies = mutableListOf<String>()

            selectedFeatures.forEach { (feature, isEnabled) ->
                if (isEnabled && !hasRequiredDependencies(project, feature)) {
                    when (feature.lowercase()) {
                        "validation", "dto_validation", "entity_validation" ->
                            missingDependencies.add("spring-boot-starter-validation")
                        "swagger", "openapi", "api_documentation" ->
                            missingDependencies.add("springdoc-openapi-ui")
                        "security", "spring_security", "jwt" ->
                            missingDependencies.add("spring-boot-starter-security")
                        "graphql", "spring_graphql" ->
                            missingDependencies.add("spring-boot-starter-graphql")
                        "mapstruct", "mapping", "object_mapping" ->
                            missingDependencies.add("mapstruct")
                    }
                }
            }

            return missingDependencies.distinct()
        }

        /**
         * Génère une map des fonctionnalités basée sur les types de contenu à générer
         *
         * @param generateController Génération de contrôleurs
         * @param generateService Génération de services
         * @param generateDto Génération de DTOs
         * @param generateMapper Génération de mappers
         * @param generateSwagger Génération de documentation Swagger
         * @param generateSecurity Génération de configuration de sécurité
         * @param generateGraphQL Génération GraphQL
         * @param enableValidation Activation de la validation
         * @return Map des fonctionnalités activées
         */
        fun createFeatureMap(
            generateController: Boolean = false,
            generateService: Boolean = false,
            generateDto: Boolean = false,
            generateMapper: Boolean = false,
            generateSwagger: Boolean = false,
            generateSecurity: Boolean = false,
            generateGraphQL: Boolean = false,
            enableValidation: Boolean = false
        ): Map<String, Boolean> {
            return mapOf(
                "validation" to (enableValidation || generateDto),
                "swagger" to (generateSwagger || generateController),
                "security" to generateSecurity,
                "graphql" to generateGraphQL,
                "mapstruct" to generateMapper
            )
        }
    }
}

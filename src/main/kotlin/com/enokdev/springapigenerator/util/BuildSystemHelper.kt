package com.enokdev.springapigenerator.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.nio.file.Paths

/**
 * Classe utilitaire pour gérer les opérations liées au système de build.
 * Cette classe fournit des fonctions pour détecter le type de système de build
 * et ajouter des dépendances aux fichiers de build.
 */
class BuildSystemHelper {
    companion object {
        /**
         * Détecte le type de système de build (Maven, Gradle, Gradle Kotlin) utilisé dans le projet
         *
         * @param project Le projet IntelliJ
         * @return Le type de système de build détecté, par défaut "Gradle Groovy"
         */
        fun detectBuildSystemType(project: Project): String {
            val basePath = project.basePath ?: return "Gradle Groovy"

            // Vérifier si c'est un projet Maven
            if (File(Paths.get(basePath, "pom.xml").toString()).exists()) {
                return "Maven"
            }

            // Vérifier si c'est un projet Gradle Kotlin
            if (File(Paths.get(basePath, "build.gradle.kts").toString()).exists()) {
                return "Gradle Kotlin"
            }

            // Par défaut, considérer comme Gradle Groovy
            return "Gradle Groovy"
        }

        /**
         * Adds MapStruct dependency to the build file
         */
        fun addMapstructDependency(project: Project, buildSystemType: String) {
            val basePath = project.basePath ?: return

            when (buildSystemType) {
                "Maven" -> {
                    val pomXml = File(Paths.get(basePath, "pom.xml").toString())
                    if (pomXml.exists()) {
                        val content = pomXml.readText()
                        // Simple approach to add dependencies, for a more robust solution a proper XML parser would be needed
                        val dependenciesTag = "<dependencies>"
                        val index = content.indexOf(dependenciesTag)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesTag.length,
                                """
                                
                                <!-- MapStruct for object mapping -->
                                <dependency>
                                    <groupId>org.mapstruct</groupId>
                                    <artifactId>mapstruct</artifactId>
                                    <version>1.6.3</version>
                                </dependency>
                                <dependency>
                                    <groupId>org.mapstruct</groupId>
                                    <artifactId>mapstruct-processor</artifactId>
                                    <version>1.6.3</version>
                                    <scope>provided</scope>
                                </dependency>
                                """
                            ).toString()
                            pomXml.writeText(updatedContent)
                        }
                    }
                }
                "Gradle Kotlin" -> {
                    val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
                    if (buildGradleKts.exists()) {
                        val content = buildGradleKts.readText()
                        val dependenciesBlock = "dependencies {"
                        val index = content.indexOf(dependenciesBlock)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesBlock.length,
                                """
                                
                                // MapStruct for object mapping
                                implementation("org.mapstruct:mapstruct:1.6.3")
                                annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
                                """
                            ).toString()
                            buildGradleKts.writeText(updatedContent)
                        }
                    }
                }
                else -> {
                    val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
                    if (buildGradle.exists()) {
                        val content = buildGradle.readText()
                        val dependenciesBlock = "dependencies {"
                        val index = content.indexOf(dependenciesBlock)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesBlock.length,
                                """
                                
                                // MapStruct for object mapping
                                implementation 'org.mapstruct:mapstruct:1.6.3'
                                annotationProcessor 'org.mapstruct:mapstruct-processor:1.6.3'
                                """
                            ).toString()
                            buildGradle.writeText(updatedContent)
                        }
                    }
                }
            }

            // Refresh files in IDE
            refreshProjectFiles()
        }

        /**
         * Adds Swagger dependency to the build file
         */
        fun addSwaggerDependency(project: Project, buildSystemType: String) {
            val basePath = project.basePath ?: return

            when (buildSystemType) {
                "Maven" -> {
                    val pomXml = File(Paths.get(basePath, "pom.xml").toString())
                    if (pomXml.exists()) {
                        val content = pomXml.readText()
                        // Simple approach to add dependencies, for a more robust solution a proper XML parser would be needed
                        val dependenciesTag = "<dependencies>"
                        val index = content.indexOf(dependenciesTag)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesTag.length,
                                """
                                
                                <!-- SpringDoc OpenAPI for API documentation -->
                                <dependency>
                                    <groupId>org.springdoc</groupId>
                                    <artifactId>springdoc-openapi-ui</artifactId>
                                    <version>2.8.0</version>
                                </dependency>
                                """
                            ).toString()
                            pomXml.writeText(updatedContent)
                        }
                    }
                }
                "Gradle Kotlin" -> {
                    val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
                    if (buildGradleKts.exists()) {
                        val content = buildGradleKts.readText()
                        val dependenciesBlock = "dependencies {"
                        val index = content.indexOf(dependenciesBlock)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesBlock.length,
                                """
                                
                                // SpringDoc OpenAPI for API documentation
                                implementation("org.springdoc:springdoc-openapi-ui:2.8.0")
                                """
                            ).toString()
                            buildGradleKts.writeText(updatedContent)
                        }
                    }
                }
                else -> {
                    val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
                    if (buildGradle.exists()) {
                        val content = buildGradle.readText()
                        val dependenciesBlock = "dependencies {"
                        val index = content.indexOf(dependenciesBlock)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesBlock.length,
                                """
                                
                                // SpringDoc OpenAPI for API documentation
                                implementation 'org.springdoc:springdoc-openapi-ui:2.8.0'
                                """
                            ).toString()
                            buildGradle.writeText(updatedContent)
                        }
                    }
                }
            }

            // Refresh files in IDE
            refreshProjectFiles()
        }

        /**
         * Adds Spring Security dependency to the build file
         */
        fun addSpringSecurityDependency(project: Project, buildSystemType: String) {
            val basePath = project.basePath ?: return

            when (buildSystemType) {
                "Maven" -> {
                    val pomXml = File(Paths.get(basePath, "pom.xml").toString())
                    if (pomXml.exists()) {
                        val content = pomXml.readText()
                        // Simple approach to add dependencies, for a more robust solution a proper XML parser would be needed
                        val dependenciesTag = "<dependencies>"
                        val index = content.indexOf(dependenciesTag)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesTag.length,
                                """
                                
                                <!-- Spring Security with JWT -->
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-starter-security</artifactId>
                                </dependency>
                                <dependency>
                                    <groupId>io.jsonwebtoken</groupId>
                                    <artifactId>jjwt-api</artifactId>
                                    <version>0.12.6</version>
                                </dependency>
                                <dependency>
                                    <groupId>io.jsonwebtoken</groupId>
                                    <artifactId>jjwt-impl</artifactId>
                                    <version>0.12.6</version>
                                    <scope>runtime</scope>
                                </dependency>
                                <dependency>
                                    <groupId>io.jsonwebtoken</groupId>
                                    <artifactId>jjwt-jackson</artifactId>
                                    <version>0.12.6</version>
                                    <scope>runtime</scope>
                                </dependency>
                                """
                            ).toString()
                            pomXml.writeText(updatedContent)
                        }
                    }
                }
                "Gradle Kotlin" -> {
                    val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
                    if (buildGradleKts.exists()) {
                        val content = buildGradleKts.readText()
                        val dependenciesBlock = "dependencies {"
                        val index = content.indexOf(dependenciesBlock)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesBlock.length,
                                """
                                
                                // Spring Security with JWT
                                implementation("org.springframework.boot:spring-boot-starter-security")
                                implementation("io.jsonwebtoken:jjwt-api:0.12.6")
                                runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
                                runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
                                """
                            ).toString()
                            buildGradleKts.writeText(updatedContent)
                        }
                    }
                }
                else -> {
                    val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
                    if (buildGradle.exists()) {
                        val content = buildGradle.readText()
                        val dependenciesBlock = "dependencies {"
                        val index = content.indexOf(dependenciesBlock)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesBlock.length,
                                """
                                
                                // Spring Security with JWT
                                implementation 'org.springframework.boot:spring-boot-starter-security'
                                implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
                                runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
                                runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
                                """
                            ).toString()
                            buildGradle.writeText(updatedContent)
                        }
                    }
                }
            }

            // Refresh files in IDE
            refreshProjectFiles()
        }

        /**
         * Adds GraphQL dependency to the build file
         */
        fun addGraphQLDependency(project: Project, buildSystemType: String) {
            val basePath = project.basePath ?: return

            when (buildSystemType) {
                "Maven" -> {
                    val pomXml = File(Paths.get(basePath, "pom.xml").toString())
                    if (pomXml.exists()) {
                        val content = pomXml.readText()
                        val dependenciesTag = "<dependencies>"
                        val index = content.indexOf(dependenciesTag)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesTag.length,
                                """
                                
                                <!-- Spring Boot GraphQL -->
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-starter-graphql</artifactId>
                                </dependency>
                                """
                            ).toString()
                            pomXml.writeText(updatedContent)
                        }
                    }
                }
                "Gradle Kotlin" -> {
                    val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
                    if (buildGradleKts.exists()) {
                        val content = buildGradleKts.readText()
                        val dependenciesBlock = "dependencies {"
                        val index = content.indexOf(dependenciesBlock)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesBlock.length,
                                """
                                
                                // Spring Boot GraphQL
                                implementation("org.springframework.boot:spring-boot-starter-graphql")
                                """
                            ).toString()
                            buildGradleKts.writeText(updatedContent)
                        }
                    }
                }
                else -> {
                    val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
                    if (buildGradle.exists()) {
                        val content = buildGradle.readText()
                        val dependenciesBlock = "dependencies {"
                        val index = content.indexOf(dependenciesBlock)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesBlock.length,
                                """
                                
                                // Spring Boot GraphQL
                                implementation 'org.springframework.boot:spring-boot-starter-graphql'
                                """
                            ).toString()
                            buildGradle.writeText(updatedContent)
                        }
                    }
                }
            }

            // Refresh files in IDE
            refreshProjectFiles()
        }

        /**
         * Adds Validation dependency to the build file
         */
        fun addValidationDependency(project: Project, buildSystemType: String) {
            val basePath = project.basePath ?: return

            when (buildSystemType) {
                "Maven" -> {
                    val pomXml = File(Paths.get(basePath, "pom.xml").toString())
                    if (pomXml.exists()) {
                        val content = pomXml.readText()
                        val dependenciesTag = "<dependencies>"
                        val index = content.indexOf(dependenciesTag)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesTag.length,
                                """
                                
                                <!-- Spring Boot Validation -->
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-starter-validation</artifactId>
                                </dependency>
                                """
                            ).toString()
                            pomXml.writeText(updatedContent)
                        }
                    }
                }
                "Gradle Kotlin" -> {
                    val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
                    if (buildGradleKts.exists()) {
                        val content = buildGradleKts.readText()
                        val dependenciesBlock = "dependencies {"
                        val index = content.indexOf(dependenciesBlock)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesBlock.length,
                                """
                                
                                // Spring Boot Validation
                                implementation("org.springframework.boot:spring-boot-starter-validation")
                                """
                            ).toString()
                            buildGradleKts.writeText(updatedContent)
                        }
                    }
                }
                else -> {
                    val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
                    if (buildGradle.exists()) {
                        val content = buildGradle.readText()
                        val dependenciesBlock = "dependencies {"
                        val index = content.indexOf(dependenciesBlock)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesBlock.length,
                                """
                                
                                // Spring Boot Validation
                                implementation 'org.springframework.boot:spring-boot-starter-validation'
                                """
                            ).toString()
                            buildGradle.writeText(updatedContent)
                        }
                    }
                }
            }

            // Refresh files in IDE
            refreshProjectFiles()
        }

        /**
         * Vérifie si une dépendance existe dans le fichier de build
         *
         * @param project Le projet IntelliJ
         * @param dependencyGroup Le groupe de la dépendance (ex: "org.springframework.boot")
         * @param dependencyArtifact L'artefact de la dépendance (ex: "spring-boot-starter-validation")
         * @return true si la dépendance existe, false sinon
         */
        fun hasDependency(project: Project, dependencyGroup: String, dependencyArtifact: String): Boolean {
            val basePath = project.basePath ?: return false
            val buildSystemType = detectBuildSystemType(project)

            when (buildSystemType) {
                "Maven" -> {
                    val pomXml = File(Paths.get(basePath, "pom.xml").toString())
                    if (pomXml.exists()) {
                        val content = pomXml.readText()
                        return content.contains("<groupId>$dependencyGroup</groupId>") &&
                               content.contains("<artifactId>$dependencyArtifact</artifactId>")
                    }
                }
                "Gradle Kotlin" -> {
                    val buildGradleKts = File(Paths.get(basePath, "build.gradle.kts").toString())
                    if (buildGradleKts.exists()) {
                        val content = buildGradleKts.readText()
                        return content.contains("\"$dependencyGroup:$dependencyArtifact") ||
                               content.contains("'$dependencyGroup:$dependencyArtifact")
                    }
                }
                else -> {
                    val buildGradle = File(Paths.get(basePath, "build.gradle").toString())
                    if (buildGradle.exists()) {
                        val content = buildGradle.readText()
                        return content.contains("'$dependencyGroup:$dependencyArtifact") ||
                               content.contains("\"$dependencyGroup:$dependencyArtifact")
                    }
                }
            }
            return false
        }

        /**
         * Vérifie et ajoute automatiquement les dépendances requises selon les fonctionnalités sélectionnées
         *
         * @param project Le projet IntelliJ
         * @param features Map des fonctionnalités activées
         */
        fun ensureRequiredDependencies(project: Project, features: Map<String, Boolean>) {
            val buildSystemType = detectBuildSystemType(project)

            // Vérifier et ajouter les dépendances de validation si nécessaire
            if (features["validation"] == true) {
                if (!hasValidationDependency(project)) {
                    addValidationDependency(project, buildSystemType)
                }
            }

            // Vérifier et ajouter les dépendances Swagger/OpenAPI si nécessaire
            if (features["swagger"] == true || features["openapi"] == true) {
                if (!hasSwaggerDependency(project)) {
                    addSwaggerDependency(project, buildSystemType)
                }
            }

            // Vérifier et ajouter les dépendances Security si nécessaire
            if (features["security"] == true) {
                if (!hasSecurityDependency(project)) {
                    addSpringSecurityDependency(project, buildSystemType)
                }
            }

            // Vérifier et ajouter les dépendances GraphQL si nécessaire
            if (features["graphql"] == true) {
                if (!hasGraphQLDependency(project)) {
                    addGraphQLDependency(project, buildSystemType)
                }
            }

            // Vérifier et ajouter les dépendances MapStruct si nécessaire
            if (features["mapstruct"] == true) {
                if (!hasMapStructDependency(project)) {
                    addMapstructDependency(project, buildSystemType)
                }
            }
        }

        /**
         * Vérifie si la dépendance de validation existe
         */
        fun hasValidationDependency(project: Project): Boolean {
            return hasDependency(project, "org.springframework.boot", "spring-boot-starter-validation")
        }

        /**
         * Vérifie si la dépendance Swagger/OpenAPI existe
         */
        fun hasSwaggerDependency(project: Project): Boolean {
            return hasDependency(project, "org.springdoc", "springdoc-openapi-ui") ||
                   hasDependency(project, "org.springdoc", "springdoc-openapi-starter-webmvc-ui")
        }

        /**
         * Vérifie si la dépendance Security existe
         */
        fun hasSecurityDependency(project: Project): Boolean {
            return hasDependency(project, "org.springframework.boot", "spring-boot-starter-security")
        }

        /**
         * Vérifie si la dépendance GraphQL existe
         */
        fun hasGraphQLDependency(project: Project): Boolean {
            return hasDependency(project, "org.springframework.boot", "spring-boot-starter-graphql")
        }

        /**
         * Vérifie si la dépendance MapStruct existe
         */
        fun hasMapStructDependency(project: Project): Boolean {
            return hasDependency(project, "org.mapstruct", "mapstruct")
        }

        /**
         * Refresh project files in IntelliJ IDEA
         */
        private fun refreshProjectFiles() {
            ApplicationManager.getApplication().invokeLater {
                WriteAction.run<RuntimeException> {
                    LocalFileSystem.getInstance().refresh(false)
                }
            }
        }
    }
}

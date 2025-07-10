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
                        // Simple approach to add dependencies, for a more robust solution a proper XML parser would be needed
                        val dependenciesTag = "<dependencies>"
                        val index = content.indexOf(dependenciesTag)
                        if (index != -1) {
                            val updatedContent = StringBuilder(content).insert(
                                index + dependenciesTag.length,
                                """
                                
                                <!-- Spring GraphQL -->
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-starter-graphql</artifactId>
                                </dependency>
                                <dependency>
                                    <groupId>org.springframework.graphql</groupId>
                                    <artifactId>spring-graphql-test</artifactId>
                                    <scope>test</scope>
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
                                
                                // Spring GraphQL
                                implementation("org.springframework.boot:spring-boot-starter-graphql")
                                testImplementation("org.springframework.graphql:spring-graphql-test")
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
                                
                                // Spring GraphQL
                                implementation 'org.springframework.boot:spring-boot-starter-graphql'
                                testImplementation 'org.springframework.graphql:spring-graphql-test'
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
         * Adds OpenAPI 3.0 dependency to the build file
         */
        fun addOpenApiDependency(project: Project, buildSystemType: String) {
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
                                
                                <!-- SpringDoc OpenAPI 3.0 for API documentation -->
                                <dependency>
                                    <groupId>org.springdoc</groupId>
                                    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                                    <version>2.8.9</version>
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
                                
                                // SpringDoc OpenAPI 3.0 for API documentation
                                implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
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
                                
                                // SpringDoc OpenAPI 3.0 for API documentation
                                implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9'
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
         * Rafraîchit les fichiers du projet dans l'IDE
         */
        private fun refreshProjectFiles() {
            ApplicationManager.getApplication().invokeLater {
                WriteAction.runAndWait<Throwable> {
                    LocalFileSystem.getInstance().refresh(false)
                }
            }
        }
    }
}

package com.enokdev.springapigenerator.service

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.PsiManager
import com.intellij.ide.highlighter.XmlFileType
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Service for detecting Spring Boot version and capabilities in the project.
 */
class SpringBootVersionDetectionService {

    data class SpringBootInfo(
        val version: String,
        val majorVersion: Int,
        val minorVersion: Int,
        val patchVersion: Int,
        val hasWebStarter: Boolean = false,
        val hasDataJpaStarter: Boolean = false,
        val hasSecurityStarter: Boolean = false,
        val hasTestStarter: Boolean = false,
        val hasValidationStarter: Boolean = false,
        val hasActuatorStarter: Boolean = false,
        val dependencies: Set<String> = emptySet()
    ) {
        fun isVersion3OrHigher(): Boolean = majorVersion >= 3
        fun isVersion2OrHigher(): Boolean = majorVersion >= 2
        fun hasFeature(feature: SpringBootFeature): Boolean {
            return when (feature) {
                SpringBootFeature.WEBFLUX -> dependencies.contains("spring-boot-starter-webflux")
                SpringBootFeature.REACTIVE_DATA -> dependencies.contains("spring-boot-starter-data-r2dbc")
                SpringBootFeature.NATIVE_IMAGE -> isVersion3OrHigher()
                SpringBootFeature.MICROMETER -> dependencies.contains("micrometer-core") || isVersion2OrHigher()
                SpringBootFeature.CONFIGURATION_PROPERTIES -> isVersion2OrHigher()
                SpringBootFeature.CONDITIONAL_BEANS -> true // Available in all supported versions
            }
        }
    }

    enum class SpringBootFeature {
        WEBFLUX,
        REACTIVE_DATA,
        NATIVE_IMAGE,
        MICROMETER,
        CONFIGURATION_PROPERTIES,
        CONDITIONAL_BEANS
    }

    /**
     * Detects Spring Boot version and capabilities from the project.
     */
    fun detectSpringBootInfo(project: Project): SpringBootInfo? {
        // Try different detection methods
        return detectFromGradleBuild(project)
            ?: detectFromMavenPom(project)
            ?: detectFromDependencies(project)
    }

    /**
     * Detects Spring Boot info from Gradle build files.
     */
    private fun detectFromGradleBuild(project: Project): SpringBootInfo? {
        val basePath = project.basePath ?: return null
        val buildGradleKts = File(basePath, "build.gradle.kts")
        val buildGradle = File(basePath, "build.gradle")

        val buildFile = when {
            buildGradleKts.exists() -> buildGradleKts
            buildGradle.exists() -> buildGradle
            else -> return null
        }

        try {
            val content = buildFile.readText()
            return parseGradleBuildContent(content)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Detects Spring Boot info from Maven POM files.
     */
    private fun detectFromMavenPom(project: Project): SpringBootInfo? {
        val basePath = project.basePath ?: return null
        val pomFile = File(basePath, "pom.xml")

        if (!pomFile.exists()) return null

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(pomFile)
            return parseMavenPomContent(document)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Detects Spring Boot info from project dependencies as fallback.
     */
    private fun detectFromDependencies(project: Project): SpringBootInfo? {
        // This would analyze the project's classpath and library dependencies
        // For now, return a default configuration
        return SpringBootInfo(
            version = "2.7.0",
            majorVersion = 2,
            minorVersion = 7,
            patchVersion = 0
        )
    }

    private fun parseGradleBuildContent(content: String): SpringBootInfo? {
        val lines = content.lines()
        var springBootVersion: String? = null
        val dependencies = mutableSetOf<String>()

        lines.forEach { line ->
            val trimmedLine = line.trim()

            // Look for Spring Boot plugin version
            if (trimmedLine.contains("org.springframework.boot") && trimmedLine.contains("version")) {
                val versionMatch = Regex("""version\s*[\"']([^\"']+)[\"']""").find(trimmedLine)
                if (versionMatch != null) {
                    springBootVersion = versionMatch.groupValues[1]
                }
            }

            // Look for Spring Boot BOM version
            if (trimmedLine.contains("spring-boot-dependencies") && trimmedLine.contains("version")) {
                val versionMatch = Regex("""[\"']([^\"']+)[\"']""").find(trimmedLine)
                if (versionMatch != null) {
                    springBootVersion = versionMatch.groupValues[1]
                }
            }

            // Collect dependencies
            if (trimmedLine.contains("implementation") || trimmedLine.contains("compile")) {
                extractDependencyName(trimmedLine)?.let { dependencies.add(it) }
            }
        }

        return springBootVersion?.let { version ->
            val versionParts = parseVersionString(version)
            SpringBootInfo(
                version = version,
                majorVersion = versionParts.first,
                minorVersion = versionParts.second,
                patchVersion = versionParts.third,
                hasWebStarter = dependencies.any { it.contains("spring-boot-starter-web") },
                hasDataJpaStarter = dependencies.any { it.contains("spring-boot-starter-data-jpa") },
                hasSecurityStarter = dependencies.any { it.contains("spring-boot-starter-security") },
                hasTestStarter = dependencies.any { it.contains("spring-boot-starter-test") },
                hasValidationStarter = dependencies.any { it.contains("spring-boot-starter-validation") },
                hasActuatorStarter = dependencies.any { it.contains("spring-boot-starter-actuator") },
                dependencies = dependencies
            )
        }
    }

    private fun parseMavenPomContent(document: Document): SpringBootInfo? {
        val root = document.documentElement
        var springBootVersion: String? = null
        val dependencies = mutableSetOf<String>()

        // Look for Spring Boot parent
        val parent = root.getElementsByTagName("parent").item(0) as? Element
        if (parent != null) {
            val groupId = parent.getElementsByTagName("groupId").item(0)?.textContent
            val version = parent.getElementsByTagName("version").item(0)?.textContent

            if (groupId == "org.springframework.boot" && version != null) {
                springBootVersion = version
            }
        }

        // Look for Spring Boot version in properties
        val properties = root.getElementsByTagName("properties").item(0) as? Element
        if (properties != null && springBootVersion == null) {
            val versionElement = properties.getElementsByTagName("spring-boot.version").item(0)
                ?: properties.getElementsByTagName("spring.boot.version").item(0)
            springBootVersion = versionElement?.textContent
        }

        // Collect dependencies
        val dependenciesElement = root.getElementsByTagName("dependencies").item(0) as? Element
        if (dependenciesElement != null) {
            val dependencyNodes = dependenciesElement.getElementsByTagName("dependency")
            for (i in 0 until dependencyNodes.length) {
                val dependency = dependencyNodes.item(i) as Element
                val groupId = dependency.getElementsByTagName("groupId").item(0)?.textContent ?: ""
                val artifactId = dependency.getElementsByTagName("artifactId").item(0)?.textContent ?: ""
                dependencies.add("$groupId:$artifactId")
            }
        }

        return springBootVersion?.let { version ->
            val versionParts = parseVersionString(version)
            SpringBootInfo(
                version = version,
                majorVersion = versionParts.first,
                minorVersion = versionParts.second,
                patchVersion = versionParts.third,
                hasWebStarter = dependencies.any { it.contains("spring-boot-starter-web") },
                hasDataJpaStarter = dependencies.any { it.contains("spring-boot-starter-data-jpa") },
                hasSecurityStarter = dependencies.any { it.contains("spring-boot-starter-security") },
                hasTestStarter = dependencies.any { it.contains("spring-boot-starter-test") },
                hasValidationStarter = dependencies.any { it.contains("spring-boot-starter-validation") },
                hasActuatorStarter = dependencies.any { it.contains("spring-boot-starter-actuator") },
                dependencies = dependencies
            )
        }
    }

    private fun extractDependencyName(line: String): String? {
        val dependencyPattern = Regex("""[\"']([^\"':]+:[^\"':]+)(?::[^\"']+)?[\"']""")
        val match = dependencyPattern.find(line)
        return match?.groupValues?.get(1)
    }

    private fun parseVersionString(version: String): Triple<Int, Int, Int> {
        val parts = version.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.split("-")?.get(0)?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }
}

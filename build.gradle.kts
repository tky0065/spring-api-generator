plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.enokdev"
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IC", "2025.1")                          // compile + runIde using 2025.1
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("com.intellij.java")              // require Java support at runtime
    }

    implementation("org.freemarker:freemarker:2.3.32")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

intellijPlatform {
    pluginConfiguration {
        // Patch plugin.xml for compatibility metadata
        ideaVersion {
            sinceBuild.set("242")                     // supports 2025.1+ (builds starting with 251)
            // untilBuild is unset → compatible with all future builds :contentReference[oaicite:1]{index=1}
            untilBuild.set(provider { null })
        }
        changeNotes.set("""
            <h3>1.1.0</h3>
            <ul>
                <li><b>Ajout</b>: Support de Spring Security - Génération d'endpoints sécurisés, classes d'authentification et configuration JWT</li>
                <li><b>Ajout</b>: Support de GraphQL - Génération de schémas et résolveurs GraphQL (types, inputs, queries, mutations)</li>
                <li><b>Amélioration</b>: Correction de bugs mineurs et optimisations de performance</li>
            </ul>
            <h3>1.0.0</h3>
            <ul>
                <li>Version initiale du plugin</li>
                <li>Génération de code Spring Boot (Controller, Service, Repository, DTO, etc.)</li>
                <li>Support de la génération depuis des entités existantes</li>
                <li>Support du reverse engineering depuis base de données</li>
            </ul>
        """.trimIndent())
    }

    // Configuration pour la publication du plugin
    publishing {
        // Utiliser le token depuis gradle.properties ou variable d'environnement
        token.set(System.getenv("INTELLIJ_PUBLISH_TOKEN") ?: findProperty("intellijPublishToken")?.toString() ?: "")

        // Publier dans le canal stable
        channels.set(listOf("stable"))

        // Version du plugin qui sera affichée sur le marketplace
        //version.set(project.version.toString())
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }

    // Tâche pour nettoyer le cache Gradle en cas de problème
    register<Delete>("cleanGradleCache") {
        delete(fileTree("${System.getProperty("user.home")}/.gradle/caches/"))
        group = "build"
        description = "Clean Gradle cache to resolve strange errors"
    }
}

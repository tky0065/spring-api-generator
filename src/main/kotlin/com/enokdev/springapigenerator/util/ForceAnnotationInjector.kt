package com.enokdev.springapigenerator.util

import com.intellij.openapi.diagnostic.Logger

/**
 * Injecteur d'annotations ultra-robuste qui force l'ajout d'annotations de manière simple et garantie.
 * Cette approche utilise des remplacements de texte directs plutôt que des regex complexes.
 */
object ForceAnnotationInjector {
    private val logger = Logger.getInstance(ForceAnnotationInjector::class.java)

    /**
     * Force l'injection d'annotations de manière brutale mais efficace
     */
    fun forceAnnotationsInAllFiles(code: String, fileName: String): String {
        var result = code

        try {
            when {
                fileName.contains("ServiceImpl") -> {
                    result = forceServiceImplAnnotations(result)
                }
                fileName.contains("Controller") -> {
                    result = forceControllerAnnotations(result)
                }
                fileName.contains("Repository") -> {
                    result = forceRepositoryAnnotations(result)
                }
                fileName.contains("DTO") -> {
                    result = forceDtoAnnotations(result, fileName)
                }
                fileName.contains("Mapper") -> {
                    result = forceMapperAnnotations(result)
                }
            }

            // Ajouter les imports nécessaires
            result = addAllNecessaryImports(result, fileName)

            // S'assurer du modificateur public pour Java
            if (fileName.endsWith(".java")) {
                result = ensurePublicModifier(result)
            }

        } catch (e: Exception) {
            logger.error("Error in ForceAnnotationInjector for $fileName: ${e.message}", e)
        }

        return result
    }

    /**
     * Force les annotations @Service et @Transactional pour ServiceImpl
     */
    private fun forceServiceImplAnnotations(code: String): String {
        var result = code

        // Détecter si c'est Kotlin ou Java
        val isKotlin = code.contains("fun ") || code.contains("class ") && !code.contains("public class")

        // Chercher "class" suivi de "ServiceImpl" (pour Kotlin et Java)
        val patterns = if (isKotlin) {
            listOf("class")  // Kotlin n'a pas besoin de "public"
        } else {
            listOf("public class", "class")
        }

        for (pattern in patterns) {
            if (result.contains("${pattern} ") && result.contains("ServiceImpl")) {
                val lines = result.split("\n").toMutableList()

                for (i in lines.indices) {
                    if (lines[i].contains("${pattern} ") && lines[i].contains("ServiceImpl")) {
                        // Vérifier si les annotations sont déjà présentes dans les lignes précédentes
                        val previousLines = lines.take(i).takeLast(5).joinToString("\n")

                        if (!previousLines.contains("@Service")) {
                            lines.add(i, "@Service")
                        }
                        if (!previousLines.contains("@Transactional")) {
                            lines.add(i, "@Transactional")
                        }
                        break
                    }
                }
                result = lines.joinToString("\n")
                break
            }
        }

        return result
    }

    /**
     * Force les annotations @RestController et @RequestMapping pour Controller
     */
    private fun forceControllerAnnotations(code: String): String {
        var result = code

        // Détecter si c'est Kotlin ou Java
        val isKotlin = code.contains("fun ") || code.contains("class ") && !code.contains("public class")

        val patterns = if (isKotlin) {
            listOf("class")
        } else {
            listOf("public class", "class")
        }

        for (pattern in patterns) {
            if (result.contains("${pattern} ") && result.contains("Controller")) {
                val lines = result.split("\n").toMutableList()

                for (i in lines.indices) {
                    if (lines[i].contains("${pattern} ") && lines[i].contains("Controller")) {
                        val previousLines = lines.take(i).takeLast(10).joinToString("\n")

                        if (!previousLines.contains("@RestController")) {
                            lines.add(i, "@RestController")
                        }
                        if (!previousLines.contains("@RequestMapping")) {
                            // CORRECTION : Extraction plus robuste du nom de l'entité
                            val classDeclaration = lines[i]
                            val className = if (isKotlin) {
                                // Pour Kotlin: "class TaskController" ou "class TaskController("
                                classDeclaration.substringAfter("class ").substringBefore("(").substringBefore(" ").trim()
                            } else {
                                // Pour Java: "public class TaskController"
                                classDeclaration.substringAfter("class ").substringBefore(" ").substringBefore("{").trim()
                            }

                            // CORRECTION CRITIQUE : Calcul sécurisé du nom de l'entité
                            var entityName = className.replace("Controller", "").lowercase()

                            // Éviter les valeurs problématiques comme @rest
                            if (entityName.contains("@") || entityName.isBlank() || entityName == "rest") {
                                // Fallback : utiliser le nom de classe sans "Controller"
                                entityName = className.replace("Controller", "").lowercase()
                                if (entityName.isBlank()) {
                                    entityName = "entity" // Fallback ultime
                                }
                            }

                            lines.add(i, "@RequestMapping(\"/api/$entityName\")")
                        }
                        break
                    }
                }
                result = lines.joinToString("\n")
                break
            }
        }

        return result
    }

    /**
     * Force l'annotation @Repository pour Repository
     */
    private fun forceRepositoryAnnotations(code: String): String {
        var result = code

        // Détecter si c'est Kotlin ou Java
        val isKotlin = code.contains("fun ") || code.contains("interface ") && !code.contains("public interface")

        val patterns = if (isKotlin) {
            listOf("interface")
        } else {
            listOf("public interface", "interface")
        }

        for (pattern in patterns) {
            if (result.contains("${pattern} ") && result.contains("Repository")) {
                val lines = result.split("\n").toMutableList()

                for (i in lines.indices) {
                    if (lines[i].contains("${pattern} ") && lines[i].contains("Repository")) {
                        val previousLines = lines.take(i).takeLast(5).joinToString("\n")

                        if (!previousLines.contains("@Repository")) {
                            lines.add(i, "@Repository")
                        }
                        break
                    }
                }
                result = lines.joinToString("\n")
                break
            }
        }

        return result
    }

    /**
     * Force les annotations pour DTO (Lombok ou data class)
     */
    private fun forceDtoAnnotations(code: String, fileName: String): String {
        var result = code

        if (fileName.endsWith(".kt")) {
            // Pour Kotlin, forcer data class
            if (result.contains("class ") && result.contains("DTO") && !result.contains("data class")) {
                // Remplacer "class" par "data class" pour les DTO Kotlin
                val lines = result.split("\n").toMutableList()

                for (i in lines.indices) {
                    if (lines[i].contains("class ") && lines[i].contains("DTO") && !lines[i].contains("data class")) {
                        lines[i] = lines[i].replace("class ", "data class ")
                        break
                    }
                }
                result = lines.joinToString("\n")
            }
        } else {
            // Pour Java, forcer les annotations Lombok
            val patterns = listOf("public class", "class")

            for (pattern in patterns) {
                if (result.contains("${pattern} ") && result.contains("DTO")) {
                    val lines = result.split("\n").toMutableList()

                    for (i in lines.indices) {
                        if (lines[i].contains("${pattern} ") && lines[i].contains("DTO")) {
                            val previousLines = lines.take(i).takeLast(10).joinToString("\n")

                            if (!previousLines.contains("@Data")) {
                                lines.add(i, "@Data")
                            }
                            if (!previousLines.contains("@NoArgsConstructor")) {
                                lines.add(i, "@NoArgsConstructor")
                            }
                            if (!previousLines.contains("@AllArgsConstructor")) {
                                lines.add(i, "@AllArgsConstructor")
                            }
                            break
                        }
                    }
                    result = lines.joinToString("\n")
                    break
                }
            }
        }

        return result
    }

    /**
     * Force l'annotation @Mapper pour Mapper
     */
    private fun forceMapperAnnotations(code: String): String {
        var result = code

        // Détecter si c'est Kotlin ou Java
        val isKotlin = code.contains("fun ") || code.contains("interface ") && !code.contains("public interface")

        val patterns = if (isKotlin) {
            listOf("interface")
        } else {
            listOf("public interface", "interface")
        }

        for (pattern in patterns) {
            if (result.contains("${pattern} ") && result.contains("Mapper")) {
                val lines = result.split("\n").toMutableList()

                for (i in lines.indices) {
                    if (lines[i].contains("${pattern} ") && lines[i].contains("Mapper")) {
                        val previousLines = lines.take(i).takeLast(5).joinToString("\n")

                        if (!previousLines.contains("@Mapper")) {
                            lines.add(i, "@Mapper(componentModel = \"spring\")")
                        }
                        break
                    }
                }
                result = lines.joinToString("\n")
                break
            }
        }

        return result
    }

    /**
     * Ajoute TOUS les imports nécessaires de manière brutale
     */
    private fun addAllNecessaryImports(code: String, fileName: String): String {
        var result = code

        // Créer la liste complète des imports nécessaires selon le type de fichier
        val importsToAdd = mutableListOf<String>()

        when {
            fileName.contains("ServiceImpl") -> {
                if (result.contains("@Service") && !result.contains("import org.springframework.stereotype.Service")) {
                    importsToAdd.add("import org.springframework.stereotype.Service;")
                }
                if (result.contains("@Transactional") && !result.contains("import org.springframework.transaction.annotation.Transactional")) {
                    importsToAdd.add("import org.springframework.transaction.annotation.Transactional;")
                }
            }
            fileName.contains("Controller") -> {
                if (result.contains("@RestController") && !result.contains("import org.springframework.web.bind.annotation.RestController")) {
                    importsToAdd.add("import org.springframework.web.bind.annotation.RestController;")
                }
                if (result.contains("@RequestMapping") && !result.contains("import org.springframework.web.bind.annotation.RequestMapping")) {
                    importsToAdd.add("import org.springframework.web.bind.annotation.RequestMapping;")
                }
            }
            fileName.contains("Repository") -> {
                if (result.contains("@Repository") && !result.contains("import org.springframework.stereotype.Repository")) {
                    importsToAdd.add("import org.springframework.stereotype.Repository;")
                }
            }
            fileName.contains("DTO") && fileName.endsWith(".java") -> {
                if (result.contains("@Data") && !result.contains("import lombok.Data")) {
                    importsToAdd.add("import lombok.Data;")
                }
                if (result.contains("@NoArgsConstructor") && !result.contains("import lombok.NoArgsConstructor")) {
                    importsToAdd.add("import lombok.NoArgsConstructor;")
                }
                if (result.contains("@AllArgsConstructor") && !result.contains("import lombok.AllArgsConstructor")) {
                    importsToAdd.add("import lombok.AllArgsConstructor;")
                }
            }
            fileName.contains("Mapper") -> {
                if (result.contains("@Mapper") && !result.contains("import org.mapstruct.Mapper")) {
                    importsToAdd.add("import org.mapstruct.Mapper;")
                }
            }
        }

        // Ajouter les imports en bloc après le package
        if (importsToAdd.isNotEmpty()) {
            val lines = result.split("\n").toMutableList()

            // Trouver la ligne package
            var packageLineIndex = -1
            for (i in lines.indices) {
                if (lines[i].startsWith("package ")) {
                    packageLineIndex = i
                    break
                }
            }

            if (packageLineIndex != -1) {
                // Insérer tous les imports après la ligne package
                var insertIndex = packageLineIndex + 1

                // Ajouter une ligne vide si nécessaire
                if (insertIndex < lines.size && lines[insertIndex].isNotBlank()) {
                    lines.add(insertIndex, "")
                    insertIndex++
                }

                // Ajouter tous les imports
                importsToAdd.reversed().forEach { import ->
                    lines.add(insertIndex, import)
                }

                result = lines.joinToString("\n")
            }
        }

        return result
    }

    /**
     * Force le modificateur public de manière simple
     */
    private fun ensurePublicModifier(code: String): String {
        var result = code

        // Remplacements simples et directs
        val replacements = mapOf(
            " class " to " public class ",
            " interface " to " public interface ",
            " enum " to " public enum "
        )

        replacements.forEach { (from, to) ->
            // Éviter les doublons
            if (!result.contains("public$from")) {
                result = result.replace(from, to)
            }
        }

        // Nettoyer les doublons
        result = result.replace("public public ", "public ")

        return result
    }
}

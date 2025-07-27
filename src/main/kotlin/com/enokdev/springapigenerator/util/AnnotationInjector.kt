package com.enokdev.springapigenerator.util

import com.intellij.openapi.diagnostic.Logger

/**
 * Utilitaire pour forcer l'injection d'annotations dans tous les fichiers générés.
 * Cette approche garantit que les annotations sont ajoutées même si les générateurs individuels échouent.
 */
object AnnotationInjector {
    private val logger = Logger.getInstance(AnnotationInjector::class.java)

    /**
     * Injecte toutes les annotations nécessaires dans le code généré selon le type de fichier
     */
    fun forceInjectAnnotations(code: String, fileName: String): String {
        var result = code

        try {
            when {
                fileName.contains("ServiceImpl") -> {
                    result = injectServiceImplAnnotations(result, fileName)
                }
                fileName.contains("Service") && !fileName.contains("Impl") -> {
                    result = injectServiceAnnotations(result, fileName)
                }
                fileName.contains("Controller") -> {
                    result = injectControllerAnnotations(result, fileName)
                }
                fileName.contains("Repository") -> {
                    result = injectRepositoryAnnotations(result, fileName)
                }
                fileName.contains("DTO") -> {
                    result = injectDtoAnnotations(result, fileName)
                }
                fileName.contains("Mapper") -> {
                    result = injectMapperAnnotations(result, fileName)
                }
            }

            // Forcer l'ajout du modificateur public pour Java
            if (fileName.endsWith(".java")) {
                result = ensurePublicModifier(result)
            }

        } catch (e: Exception) {
            logger.error("Error injecting annotations for $fileName: ${e.message}", e)
        }

        return result
    }

    /**
     * Injecte @Service et @Transactional pour ServiceImpl
     */
    private fun injectServiceImplAnnotations(code: String, fileName: String): String {
        val className = fileName.substringBefore(".").replace("ServiceImpl", "ServiceImpl")

        // Pattern pour trouver la déclaration de classe
        val classPattern = Regex("(public\\s+)?class\\s+\\w*ServiceImpl", RegexOption.MULTILINE)

        return classPattern.replace(code) { matchResult ->
            val beforeClass = code.substring(0, matchResult.range.first)
            val lastLines = beforeClass.split("\n").takeLast(10).joinToString("\n")

            val hasService = lastLines.contains("@Service")
            val hasTransactional = lastLines.contains("@Transactional")

            if (hasService && hasTransactional) {
                matchResult.value
            } else {
                val annotations = buildString {
                    if (!hasService) appendLine("@Service")
                    if (!hasTransactional) appendLine("@Transactional")
                }
                annotations + matchResult.value
            }
        }
    }

    /**
     * Injecte @Service pour les interfaces Service (optionnel)
     */
    private fun injectServiceAnnotations(code: String, fileName: String): String {
        // Les interfaces Service n'ont généralement pas besoin d'annotations
        // Mais on peut ajouter des annotations de documentation si nécessaire
        return code
    }

    /**
     * Injecte @RestController et @RequestMapping pour Controller
     */
    private fun injectControllerAnnotations(code: String, fileName: String): String {
        val entityName = fileName.substringBefore("Controller").lowercase()

        val classPattern = Regex("(public\\s+)?class\\s+\\w*Controller", RegexOption.MULTILINE)

        return classPattern.replace(code) { matchResult ->
            val beforeClass = code.substring(0, matchResult.range.first)
            val lastLines = beforeClass.split("\n").takeLast(15).joinToString("\n")

            val hasRestController = lastLines.contains("@RestController")
            val hasRequestMapping = lastLines.contains("@RequestMapping")

            if (hasRestController && hasRequestMapping) {
                matchResult.value
            } else {
                val annotations = buildString {
                    if (!hasRestController) appendLine("@RestController")
                    if (!hasRequestMapping) appendLine("@RequestMapping(\"/api/$entityName\")")
                }
                annotations + matchResult.value
            }
        }
    }

    /**
     * Injecte @Repository pour Repository
     */
    private fun injectRepositoryAnnotations(code: String, fileName: String): String {
        val interfacePattern = Regex("(public\\s+)?interface\\s+\\w*Repository", RegexOption.MULTILINE)

        return interfacePattern.replace(code) { matchResult ->
            val beforeInterface = code.substring(0, matchResult.range.first)
            val lastLines = beforeInterface.split("\n").takeLast(10).joinToString("\n")

            val hasRepository = lastLines.contains("@Repository")

            if (hasRepository) {
                matchResult.value
            } else {
                "@Repository\n" + matchResult.value
            }
        }
    }

    /**
     * Injecte les annotations Lombok pour DTO ou data class pour Kotlin
     */
    private fun injectDtoAnnotations(code: String, fileName: String): String {
        val isKotlin = fileName.endsWith(".kt")

        if (isKotlin) {
            // Pour Kotlin, s'assurer que c'est une data class
            val classPattern = Regex("class\\s+\\w*DTO", RegexOption.MULTILINE)
            return classPattern.replace(code) { matchResult ->
                if (matchResult.value.contains("data class")) {
                    matchResult.value
                } else {
                    matchResult.value.replace("class", "data class")
                }
            }
        } else {
            // Pour Java, ajouter les annotations Lombok
            val classPattern = Regex("(public\\s+)?class\\s+\\w*DTO", RegexOption.MULTILINE)

            return classPattern.replace(code) { matchResult ->
                val beforeClass = code.substring(0, matchResult.range.first)
                val lastLines = beforeClass.split("\n").takeLast(15).joinToString("\n")

                val hasData = lastLines.contains("@Data")
                val hasNoArgs = lastLines.contains("@NoArgsConstructor")
                val hasAllArgs = lastLines.contains("@AllArgsConstructor")

                if (hasData && hasNoArgs && hasAllArgs) {
                    matchResult.value
                } else {
                    val annotations = buildString {
                        if (!hasData) appendLine("@Data")
                        if (!hasNoArgs) appendLine("@NoArgsConstructor")
                        if (!hasAllArgs) appendLine("@AllArgsConstructor")
                    }
                    annotations + matchResult.value
                }
            }
        }
    }

    /**
     * Injecte @Mapper pour MapStruct
     */
    private fun injectMapperAnnotations(code: String, fileName: String): String {
        val interfacePattern = Regex("(public\\s+)?interface\\s+\\w*Mapper", RegexOption.MULTILINE)

        return interfacePattern.replace(code) { matchResult ->
            val beforeInterface = code.substring(0, matchResult.range.first)
            val lastLines = beforeInterface.split("\n").takeLast(10).joinToString("\n")

            val hasMapper = lastLines.contains("@Mapper")

            if (hasMapper) {
                matchResult.value
            } else {
                "@Mapper(componentModel = \"spring\")\n" + matchResult.value
            }
        }
    }

    /**
     * S'assure que les classes Java ont le modificateur public
     */
    private fun ensurePublicModifier(code: String): String {
        var result = code

        // Remplacements simples et sûrs
        result = result.replace(Regex("(?<!public )class "), "public class ")
        result = result.replace(Regex("(?<!public )interface "), "public interface ")
        result = result.replace(Regex("(?<!public )enum "), "public enum ")

        // Nettoyer les doublons
        result = result.replace("public public ", "public ")

        return result
    }

    /**
     * Ajoute les imports nécessaires pour les annotations
     */
    fun addMissingImports(code: String, fileName: String): String {
        val imports = mutableSetOf<String>()

        // Détecter les annotations utilisées et ajouter les imports correspondants
        when {
            fileName.contains("ServiceImpl") -> {
                if (code.contains("@Service") && !code.contains("import org.springframework.stereotype.Service")) {
                    imports.add("import org.springframework.stereotype.Service;")
                }
                if (code.contains("@Transactional") && !code.contains("import org.springframework.transaction.annotation.Transactional")) {
                    imports.add("import org.springframework.transaction.annotation.Transactional;")
                }
            }
            fileName.contains("Controller") -> {
                if (code.contains("@RestController") && !code.contains("import org.springframework.web.bind.annotation.RestController")) {
                    imports.add("import org.springframework.web.bind.annotation.RestController;")
                }
                if (code.contains("@RequestMapping") && !code.contains("import org.springframework.web.bind.annotation.RequestMapping")) {
                    imports.add("import org.springframework.web.bind.annotation.RequestMapping;")
                }
            }
            fileName.contains("Repository") -> {
                if (code.contains("@Repository") && !code.contains("import org.springframework.stereotype.Repository")) {
                    imports.add("import org.springframework.stereotype.Repository;")
                }
            }
            fileName.contains("DTO") && fileName.endsWith(".java") -> {
                if (code.contains("@Data") && !code.contains("import lombok.Data")) {
                    imports.add("import lombok.Data;")
                }
                if (code.contains("@NoArgsConstructor") && !code.contains("import lombok.NoArgsConstructor")) {
                    imports.add("import lombok.NoArgsConstructor;")
                }
                if (code.contains("@AllArgsConstructor") && !code.contains("import lombok.AllArgsConstructor")) {
                    imports.add("import lombok.AllArgsConstructor;")
                }
            }
            fileName.contains("Mapper") -> {
                if (code.contains("@Mapper") && !code.contains("import org.mapstruct.Mapper")) {
                    imports.add("import org.mapstruct.Mapper;")
                }
            }
        }

        if (imports.isNotEmpty()) {
            // Trouver où insérer les imports (après le package et avant la première classe)
            val packagePattern = Regex("package\\s+[\\w.]+;")
            val packageMatch = packagePattern.find(code)

            if (packageMatch != null) {
                val insertPosition = packageMatch.range.last + 1
                val beforeImports = code.substring(0, insertPosition)
                val afterImports = code.substring(insertPosition)

                return beforeImports + "\n\n" + imports.joinToString("\n") + afterImports
            }
        }

        return code
    }
}

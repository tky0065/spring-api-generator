package com.enokdev.springapigenerator.util

/**
 * Utilitaire pour s'assurer que toutes les classes Java générées ont le modificateur public.
 * Corrige le problème des classes générées sans le modificateur public en Java.
 */
class JavaClassVisibilityFixer {

    companion object {
        /**
         * Corrige le code Java généré pour s'assurer que toutes les classes ont le modificateur public.
         *
         * @param javaCode Le code Java à corriger
         * @return Le code Java corrigé avec les modificateurs public ajoutés
         */
        fun ensurePublicClasses(javaCode: String): String {
            var correctedCode = javaCode

            // Pattern pour détecter les classes sans modificateur public
            val classPatterns = listOf(
                // Classes avec annotations directement avant
                Regex("(@\\w+(?:\\([^)]*\\))?\\s*)+\\s*class\\s+(\\w+)", RegexOption.MULTILINE),
                // Classes simples
                Regex("^\\s*class\\s+(\\w+)", RegexOption.MULTILINE),
                // Interfaces sans public
                Regex("(@\\w+(?:\\([^)]*\\))?\\s*)+\\s*interface\\s+(\\w+)", RegexOption.MULTILINE),
                Regex("^\\s*interface\\s+(\\w+)", RegexOption.MULTILINE),
                // Enums sans public
                Regex("(@\\w+(?:\\([^)]*\\))?\\s*)+\\s*enum\\s+(\\w+)", RegexOption.MULTILINE),
                Regex("^\\s*enum\\s+(\\w+)", RegexOption.MULTILINE)
            )

            classPatterns.forEach { pattern ->
                correctedCode = pattern.replace(correctedCode) { matchResult ->
                    val fullMatch = matchResult.value

                    // Si la classe a déjà le modificateur public, ne pas la modifier
                    if (fullMatch.contains("public class") ||
                        fullMatch.contains("public interface") ||
                        fullMatch.contains("public enum")) {
                        fullMatch
                    } else {
                        // Ajouter le modificateur public
                        when {
                            fullMatch.contains("class") -> fullMatch.replace("class", "public class")
                            fullMatch.contains("interface") -> fullMatch.replace("interface", "public interface")
                            fullMatch.contains("enum") -> fullMatch.replace("enum", "public enum")
                            else -> fullMatch
                        }
                    }
                }
            }

            return correctedCode
        }

        /**
         * Vérifie si un fichier Java contient des classes sans le modificateur public.
         *
         * @param javaCode Le code Java à vérifier
         * @return true si des classes sans public sont trouvées, false sinon
         */
        fun hasNonPublicClasses(javaCode: String): Boolean {
            val nonPublicPatterns = listOf(
                Regex("^\\s*class\\s+\\w+", RegexOption.MULTILINE),
                Regex("^\\s*interface\\s+\\w+", RegexOption.MULTILINE),
                Regex("^\\s*enum\\s+\\w+", RegexOption.MULTILINE),
                Regex("@\\w+\\s*\\n\\s*class\\s+\\w+", RegexOption.MULTILINE),
                Regex("@\\w+\\s*\\n\\s*interface\\s+\\w+", RegexOption.MULTILINE)
            )

            return nonPublicPatterns.any { pattern ->
                val matches = pattern.findAll(javaCode)
                matches.any { match ->
                    !match.value.contains("public")
                }
            }
        }

        /**
         * Corrige spécifiquement les classes générées par les templates Spring API Generator.
         *
         * @param javaCode Le code Java à corriger
         * @param className Le nom de la classe attendue
         * @return Le code Java corrigé
         */
        fun fixSpringGeneratedClass(javaCode: String, className: String): String {
            var correctedCode = javaCode

            // Patterns spécifiques pour les classes Spring générées
            val springPatterns = mapOf(
                "DTO" to Regex("(@Data\\s*@NoArgsConstructor\\s*@AllArgsConstructor\\s*@Builder\\s*)class\\s+${className}", RegexOption.MULTILINE),
                "Controller" to Regex("(@RestController\\s*@RequestMapping[^\\n]*\\s*)class\\s+${className}", RegexOption.MULTILINE),
                "Service" to Regex("(@Service\\s*@Transactional\\s*)class\\s+${className}", RegexOption.MULTILINE),
                "Repository" to Regex("(@Repository\\s*)interface\\s+${className}", RegexOption.MULTILINE),
                "Config" to Regex("(@Configuration\\s*)class\\s+${className}", RegexOption.MULTILINE),
                "Component" to Regex("(@Component\\s*)class\\s+${className}", RegexOption.MULTILINE)
            )

            springPatterns.forEach { (type, pattern) ->
                correctedCode = pattern.replace(correctedCode) { matchResult ->
                    val annotations = matchResult.groupValues[1]
                    when (type) {
                        "Repository" -> "${annotations}public interface $className"
                        else -> "${annotations}public class $className"
                    }
                }
            }

            return correctedCode
        }
    }
}

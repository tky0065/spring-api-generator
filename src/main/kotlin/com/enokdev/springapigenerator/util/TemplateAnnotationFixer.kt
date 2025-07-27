package com.enokdev.springapigenerator.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Utilitaire pour forcer la régénération propre des templates sans contraintes d'annotations.
 * SUPPRIME TOUTES LES CONTRAINTES ET FORCE L'INCLUSION DES ANNOTATIONS.
 */
object TemplateAnnotationFixer {
    private val logger = Logger.getInstance(TemplateAnnotationFixer::class.java)

    /**
     * Force la régénération du template ServiceImpl.java.ft avec les annotations garanties
     */
    fun fixServiceImplTemplate(project: Project): Boolean {
        return try {
            val projectPath = project.basePath ?: return false
            val templatesDir = File(projectPath, "src/main/resources/templates")

            if (!templatesDir.exists()) {
                templatesDir.mkdirs()
            }

            val serviceImplTemplate = File(templatesDir, "ServiceImpl.java.ft")

            // Template ServiceImpl avec annotations FORCÉES
            val templateContent = """
package ${'$'}{packageName};

import ${'$'}{servicePackage}.${'$'}{serviceName};
import ${'$'}{dtoPackage}.${'$'}{dtoName};
import ${'$'}{domainPackage}.${'$'}{entityName};
import ${'$'}{repositoryPackage}.${'$'}{repositoryName};
import ${'$'}{mapperPackage}.${'$'}{mapperName};
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service Implementation for managing {@link ${'$'}{entityName}}.
 */
@Service
@Transactional
public class ${'$'}{serviceImplName} implements ${'$'}{serviceName} {

    private final Logger log = LoggerFactory.getLogger(${'$'}{serviceImplName}.class);

    private final ${'$'}{repositoryName} ${'$'}{repositoryVarName};
    private final ${'$'}{mapperName} ${'$'}{mapperVarName};

    public ${'$'}{serviceImplName}(${'$'}{repositoryName} ${'$'}{repositoryVarName}, ${'$'}{mapperName} ${'$'}{mapperVarName}) {
        this.${'$'}{repositoryVarName} = ${'$'}{repositoryVarName};
        this.${'$'}{mapperVarName} = ${'$'}{mapperVarName};
    }

    @Override
    public ${'$'}{dtoName} save(${'$'}{dtoName} ${'$'}{entityNameLower}DTO) {
        log.debug("Request to save ${'$'}{entityName} : {}", ${'$'}{entityNameLower}DTO);
        ${'$'}{entityName} ${'$'}{entityNameLower} = ${'$'}{mapperVarName}.toEntity(${'$'}{entityNameLower}DTO);
        ${'$'}{entityNameLower} = ${'$'}{repositoryVarName}.save(${'$'}{entityNameLower});
        return ${'$'}{mapperVarName}.toDto(${'$'}{entityNameLower});
    }

    @Override
    public ${'$'}{dtoName} update(${'$'}{dtoName} ${'$'}{entityNameLower}DTO) {
        log.debug("Request to update ${'$'}{entityName} : {}", ${'$'}{entityNameLower}DTO);
        ${'$'}{entityName} ${'$'}{entityNameLower} = ${'$'}{mapperVarName}.toEntity(${'$'}{entityNameLower}DTO);
        ${'$'}{entityNameLower} = ${'$'}{repositoryVarName}.save(${'$'}{entityNameLower});
        return ${'$'}{mapperVarName}.toDto(${'$'}{entityNameLower});
    }

    @Override
    public Optional<${'$'}{dtoName}> partialUpdate(${'$'}{dtoName} ${'$'}{entityNameLower}DTO) {
        log.debug("Request to partially update ${'$'}{entityName} : {}", ${'$'}{entityNameLower}DTO);

        return ${'$'}{repositoryVarName}
            .findById(${'$'}{entityNameLower}DTO.getId())
            .map(existing${'$'}{entityName} -> {
                ${'$'}{mapperVarName}.partialUpdate(existing${'$'}{entityName}, ${'$'}{entityNameLower}DTO);
                return existing${'$'}{entityName};
            })
            .map(${'$'}{repositoryVarName}::save)
            .map(${'$'}{mapperVarName}::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<${'$'}{dtoName}> findAll() {
        log.debug("Request to get all ${'$'}{entityName}s");
        return ${'$'}{repositoryVarName}.findAll().stream()
            .map(${'$'}{mapperVarName}::toDto)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<${'$'}{dtoName}> findAll(Pageable pageable) {
        log.debug("Request to get all ${'$'}{entityName}s with pagination");
        return ${'$'}{repositoryVarName}.findAll(pageable)
            .map(${'$'}{mapperVarName}::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<${'$'}{dtoName}> findOne(${'$'}{idType} id) {
        log.debug("Request to get ${'$'}{entityName} : {}", id);
        return ${'$'}{repositoryVarName}.findById(id)
            .map(${'$'}{mapperVarName}::toDto);
    }

    @Override
    public void delete(${'$'}{idType} id) {
        log.debug("Request to delete ${'$'}{entityName} : {}", id);
        ${'$'}{repositoryVarName}.deleteById(id);
    }

    @Override
    public void deleteByIds(List<${'$'}{idType}> ids) {
        log.debug("Request to delete ${'$'}{entityName}s by ids : {}", ids);
        ${'$'}{repositoryVarName}.deleteAllById(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        log.debug("Request to count ${'$'}{entityName}s");
        return ${'$'}{repositoryVarName}.count();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(${'$'}{idType} id) {
        log.debug("Request to check if ${'$'}{entityName} exists : {}", id);
        return ${'$'}{repositoryVarName}.existsById(id);
    }
}
""".trimIndent()

            // Écrire le template corrigé
            serviceImplTemplate.writeText(templateContent)

            logger.info("ServiceImpl template fixed with guaranteed annotations")
            true
        } catch (e: Exception) {
            logger.error("Failed to fix ServiceImpl template: ${e.message}", e)
            false
        }
    }

    /**
     * Nettoie tous les caches et fichiers temporaires qui pourraient interférer
     */
    fun clearAllCaches(project: Project): Boolean {
        return try {
            val projectPath = project.basePath ?: return false

            // Nettoyer les caches IntelliJ
            val ideaDir = File(projectPath, ".idea")
            if (ideaDir.exists()) {
                val cacheDirs = listOf("caches", "cache", "temp", "tmp")
                cacheDirs.forEach { cacheDir ->
                    val dir = File(ideaDir, cacheDir)
                    if (dir.exists()) {
                        dir.deleteRecursively()
                    }
                }
            }

            // Nettoyer les builds gradle
            val buildDir = File(projectPath, "build")
            if (buildDir.exists()) {
                val cacheSubDirs = listOf("tmp", "resources", "classes")
                cacheSubDirs.forEach { subDir ->
                    val dir = File(buildDir, subDir)
                    if (dir.exists()) {
                        dir.deleteRecursively()
                    }
                }
            }

            logger.info("All caches cleared successfully")
            true
        } catch (e: Exception) {
            logger.error("Failed to clear caches: ${e.message}", e)
            false
        }
    }

    /**
     * Applique toutes les corrections d'annotations d'un coup
     */
    fun applyAllAnnotationFixes(project: Project): Boolean {
        logger.info("Applying all annotation fixes...")

        var success = true

        // 1. Nettoyer les caches
        if (!clearAllCaches(project)) {
            logger.warn("Cache clearing failed")
            success = false
        }

        // 2. Fixer le template ServiceImpl
        if (!fixServiceImplTemplate(project)) {
            logger.warn("ServiceImpl template fix failed")
            success = false
        }

        // 3. Créer d'autres templates avec annotations si nécessaire
        success = success && fixControllerTemplate(project)
        success = success && fixRepositoryTemplate(project)

        if (success) {
            logger.info("All annotation fixes applied successfully!")
        } else {
            logger.warn("Some annotation fixes failed - check logs")
        }

        return success
    }

    private fun fixControllerTemplate(project: Project): Boolean {
        return try {
            val projectPath = project.basePath ?: return false
            val templatesDir = File(projectPath, "src/main/resources/templates")
            val controllerTemplate = File(templatesDir, "Controller.java.ft")

            // Vérifier si le template Controller existe et contient les annotations
            if (controllerTemplate.exists()) {
                val content = controllerTemplate.readText()
                if (!content.contains("@RestController") || !content.contains("@RequestMapping")) {
                    logger.warn("Controller template missing annotations - please check manually")
                }
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to check Controller template: ${e.message}", e)
            false
        }
    }

    private fun fixRepositoryTemplate(project: Project): Boolean {
        return try {
            val projectPath = project.basePath ?: return false
            val templatesDir = File(projectPath, "src/main/resources/templates")
            val repositoryTemplate = File(templatesDir, "Repository.java.ft")

            // Vérifier si le template Repository existe et contient les annotations
            if (repositoryTemplate.exists()) {
                val content = repositoryTemplate.readText()
                if (!content.contains("@Repository")) {
                    logger.warn("Repository template missing @Repository annotation - please check manually")
                }
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to check Repository template: ${e.message}", e)
            false
        }
    }
}

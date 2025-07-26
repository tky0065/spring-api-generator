package com.enokdev.springapigenerator.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import freemarker.template.Configuration
import freemarker.template.Template
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for caching FreeMarker templates and configurations to improve performance.
 * This service reduces template loading and processing overhead by:
 * 1. Caching FreeMarker configurations for each project
 * 2. Caching templates to avoid reloading them for each generation request
 * 3. Monitoring template files for changes to invalidate cache when needed
 */
@Service(Service.Level.PROJECT)
class TemplateCacheService(private val project: Project) {
    private val logger = Logger.getInstance(TemplateCacheService::class.java)
    
    // Cache for FreeMarker configurations
    private val configCache = ConcurrentHashMap<String, Configuration>()
    
    // Cache for templates
    private val templateCache = ConcurrentHashMap<String, CachedTemplate>()
    
    // Cache for template file timestamps to detect changes
    private val templateTimestamps = ConcurrentHashMap<String, Long>()
    
    /**
     * Get or create a FreeMarker configuration for the given key.
     * 
     * @param key The cache key for the configuration
     * @param configFactory A function to create the configuration if it's not in the cache
     * @return The cached or newly created configuration
     */
    fun getConfiguration(key: String, configFactory: () -> Configuration): Configuration {
        return configCache.computeIfAbsent(key) { 
            logger.info("Creating new FreeMarker configuration for key: $key")
            configFactory()
        }
    }
    
    /**
     * Get a template from the cache or load it if not cached.
     * If the template file has changed since it was last cached, it will be reloaded.
     * 
     * @param config The FreeMarker configuration to use for loading the template
     * @param templateName The name of the template to load
     * @return The cached or newly loaded template
     */
    fun getTemplate(config: Configuration, templateName: String): Template {
        val cacheKey = "${config.hashCode()}_$templateName"
        
        // Check if template is in cache and not modified
        val cachedTemplate = templateCache[cacheKey]
        if (cachedTemplate != null) {
            // Check if template file has changed
            val templateFile = getTemplateFile(config, templateName)
            val currentTimestamp = templateFile?.lastModified() ?: 0
            val cachedTimestamp = templateTimestamps[cacheKey] ?: 0
            
            if (templateFile == null || currentTimestamp <= cachedTimestamp) {
                // Template file hasn't changed, return cached template
                return cachedTemplate.template
            }
            
            // Template file has changed, update timestamp and reload
            logger.info("Template file changed, reloading: $templateName")
            templateTimestamps[cacheKey] = currentTimestamp
        }
        
        // Load template and cache it
        logger.info("Loading template: $templateName")
        val template = config.getTemplate(templateName)
        templateCache[cacheKey] = CachedTemplate(template, System.currentTimeMillis())
        
        // Store template file timestamp
        val templateFile = getTemplateFile(config, templateName)
        if (templateFile != null) {
            templateTimestamps[cacheKey] = templateFile.lastModified()
        }
        
        return template
    }
    
    /**
     * Clear all caches.
     */
    fun clearCache() {
        logger.info("Clearing template and configuration caches")
        configCache.clear()
        templateCache.clear()
        templateTimestamps.clear()
    }
    
    /**
     * Clear the template cache for a specific configuration.
     * 
     * @param config The configuration to clear the cache for
     */
    fun clearTemplateCache(config: Configuration) {
        val configKey = config.hashCode().toString()
        templateCache.keys.removeIf { it.startsWith(configKey) }
        templateTimestamps.keys.removeIf { it.startsWith(configKey) }
    }
    
    /**
     * Get the template file for a template name.
     * This is used to check if the template file has changed.
     * 
     * @param config The FreeMarker configuration
     * @param templateName The name of the template
     * @return The template file, or null if it couldn't be found
     */
    private fun getTemplateFile(config: Configuration, templateName: String): File? {
        // Try to find the template file in the template loader's directories
        val loader = config.templateLoader
        if (loader is freemarker.cache.FileTemplateLoader) {
            val baseDir = loader.baseDir
            val templateFile = File(baseDir, templateName)
            if (templateFile.exists()) {
                return templateFile
            }
        } else if (loader is freemarker.cache.MultiTemplateLoader) {
            for (i in 0 until loader.templateLoaderCount) {
                val innerLoader = loader.getTemplateLoader(i)
                if (innerLoader is freemarker.cache.FileTemplateLoader) {
                    val baseDir = innerLoader.baseDir
                    val templateFile = File(baseDir, templateName)
                    if (templateFile.exists()) {
                        return templateFile
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Data class for storing a cached template with its creation timestamp.
     */
    private data class CachedTemplate(
        val template: Template,
        val timestamp: Long
    )
    
    companion object {
        /**
         * Get the TemplateCacheService instance for a project.
         * 
         * @param project The project to get the service for
         * @return The TemplateCacheService instance
         */
        fun getInstance(project: Project): TemplateCacheService = project.service()
    }
}
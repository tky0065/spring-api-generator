package com.enokdev.springapigenerator.generator

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.*
import com.enokdev.springapigenerator.service.AdvancedJpaFeatureAnalyzer.*
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Paths

/**
 * Enhanced entity generator with advanced JPA features support.
 * Supports both Java and Kotlin code generation.
 */
class AdvancedJpaEntityGenerator(
    javaTemplateName: String = "AdvancedJpaEntity.java.ft",
    private val kotlinTemplateName: String = "AdvancedJpaEntity.kt.ft"
) : IncrementalCodeGenerator(javaTemplateName) {

    private val jpaAnalyzer = AdvancedJpaFeatureAnalyzer()

    /**
     * Generate entity code with language detection
     */
    fun generateEntity(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        styleAdapter: CodeStyleAdapter,
        project: Project,
        outputDir: File
    ): File {
        val isKotlinProject = detectKotlinProject(project)

        // Create a temporary generator with the appropriate template
        val generator = if (isKotlinProject) {
            AdvancedJpaEntityGenerator(kotlinTemplateName, kotlinTemplateName)
        } else {
            this
        }

        val generatedCode = generator.generate(project, entityMetadata, packageConfig)

        // Write to output file
        val fileName = "${entityMetadata.className}.${if (isKotlinProject) "kt" else "java"}"
        val outputFile = File(outputDir, fileName)
        outputFile.writeText(generatedCode)

        return outputFile
    }

    /**
     * Detect if the project uses Kotlin
     */
    private fun detectKotlinProject(project: Project): Boolean {
        // Check for Kotlin files in the project
        val projectPath = project.basePath ?: return false
        val kotlinFiles = File(projectPath).walkTopDown()
            .filter { it.extension == "kt" }
            .take(1)
        return kotlinFiles.any()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        styleAdapter: CodeStyleAdapter
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig, styleAdapter)

        // Analyze JPA features
        val jpaFeatures = analyzeJpaFeatures(entityMetadata)

        // Generate JPA-specific code
        val inheritanceCode = generateInheritanceCode(jpaFeatures, entityMetadata, styleAdapter)
        val embeddableCode = generateEmbeddableCode(jpaFeatures, styleAdapter)
        val lifecycleCode = generateLifecycleCode(jpaFeatures, styleAdapter)
        val auditingCode = generateAuditingCode(jpaFeatures, styleAdapter)
        val versioningCode = generateVersioningCode(jpaFeatures, styleAdapter)
        val softDeleteCode = generateSoftDeleteCode(jpaFeatures, styleAdapter)

        // Add JPA features to model
        model["jpaFeatures"] = createJpaFeaturesModel(jpaFeatures)
        model["inheritanceCode"] = inheritanceCode
        model["embeddableFieldsCode"] = embeddableCode
        model["lifecycleCallbacksCode"] = lifecycleCode
        model["auditingFieldsCode"] = auditingCode
        model["versioningCode"] = versioningCode
        model["softDeleteCode"] = softDeleteCode
        model["jpaImports"] = collectJpaImports(jpaFeatures)
        model["entityListenerClass"] = generateEntityListenerClass(jpaFeatures, entityMetadata, styleAdapter)

        return model
    }

    private fun analyzeJpaFeatures(entityMetadata: EntityMetadata): JpaFeatureConfiguration {
        // For demonstration, we'll create mock JPA features based on entity characteristics
        return createMockJpaFeatures(entityMetadata)
    }

    private fun createMockJpaFeatures(entityMetadata: EntityMetadata): JpaFeatureConfiguration {
        val hasAuditing = entityMetadata.className.contains("Auditable") ||
                         entityMetadata.fields.any { it.name.contains("created") || it.name.contains("updated") }

        val hasVersioning = entityMetadata.fields.any { it.name == "version" }

        val hasSoftDelete = entityMetadata.fields.any { it.name == "deleted" }

        val hasInheritance = entityMetadata.className.contains("Base") ||
                           entityMetadata.className.contains("Abstract")

        val embeddableFields = entityMetadata.fields.filter {
            it.type.contains("Address") || it.type.contains("Contact") || it.type.contains("Audit")
        }.map { field ->
            EmbeddableFieldInfo(
                fieldName = field.name,
                embeddableClass = field.type,
                attributeOverrides = mapOf(
                    "street" to "${field.name}_street",
                    "city" to "${field.name}_city",
                    "zipCode" to "${field.name}_zip_code"
                )
            )
        }

        val lifecycleCallbacks = mutableListOf<LifecycleCallback>()
        if (hasAuditing) {
            lifecycleCallbacks.add(LifecycleCallback(CallbackType.PRE_PERSIST, "prePersist"))
            lifecycleCallbacks.add(LifecycleCallback(CallbackType.PRE_UPDATE, "preUpdate"))
        }

        val inheritanceInfo = if (hasInheritance) {
            InheritanceInfo(
                strategy = ComplexRelationshipAnalyzer.InheritanceStrategy.SINGLE_TABLE,
                discriminatorColumn = "entity_type",
                discriminatorValue = entityMetadata.className.uppercase(),
                isMappedSuperclass = entityMetadata.className.contains("Base")
            )
        } else null

        return JpaFeatureConfiguration(
            inheritanceStrategy = inheritanceInfo,
            embeddableFields = embeddableFields,
            lifecycleCallbacks = lifecycleCallbacks,
            auditingEnabled = hasAuditing,
            versioningEnabled = hasVersioning,
            softDeleteEnabled = hasSoftDelete
        )
    }

    private fun createJpaFeaturesModel(jpaFeatures: JpaFeatureConfiguration): Map<String, Any> {
        return mapOf(
            "hasInheritance" to (jpaFeatures.inheritanceStrategy != null),
            "hasEmbeddableFields" to jpaFeatures.embeddableFields.isNotEmpty(),
            "hasLifecycleCallbacks" to jpaFeatures.lifecycleCallbacks.isNotEmpty(),
            "hasAuditing" to jpaFeatures.auditingEnabled,
            "hasVersioning" to jpaFeatures.versioningEnabled,
            "hasSoftDelete" to jpaFeatures.softDeleteEnabled,
            "hasCustomConverters" to jpaFeatures.customConverters.isNotEmpty(),
            "inheritanceStrategy" to (jpaFeatures.inheritanceStrategy?.strategy?.name ?: ""),
            "isMappedSuperclass" to (jpaFeatures.inheritanceStrategy?.isMappedSuperclass == true),
            "discriminatorColumn" to (jpaFeatures.inheritanceStrategy?.discriminatorColumn ?: ""),
            "discriminatorValue" to (jpaFeatures.inheritanceStrategy?.discriminatorValue ?: "")
        )
    }

    private fun generateInheritanceCode(
        jpaFeatures: JpaFeatureConfiguration,
        entityMetadata: EntityMetadata,
        styleAdapter: CodeStyleAdapter
    ): String {
        return jpaFeatures.inheritanceStrategy?.let { inheritanceInfo ->
            jpaAnalyzer.generateInheritanceCode(inheritanceInfo, entityMetadata, styleAdapter)
        } ?: ""
    }

    private fun generateEmbeddableCode(
        jpaFeatures: JpaFeatureConfiguration,
        styleAdapter: CodeStyleAdapter
    ): String {
        return jpaAnalyzer.generateEmbeddableFieldsCode(jpaFeatures.embeddableFields, styleAdapter)
    }

    private fun generateLifecycleCode(
        jpaFeatures: JpaFeatureConfiguration,
        styleAdapter: CodeStyleAdapter
    ): String {
        return jpaAnalyzer.generateLifecycleCallbacks(jpaFeatures.lifecycleCallbacks, styleAdapter)
    }

    private fun generateAuditingCode(
        jpaFeatures: JpaFeatureConfiguration,
        styleAdapter: CodeStyleAdapter
    ): String {
        return if (jpaFeatures.auditingEnabled) {
            jpaAnalyzer.generateAuditingSupport(styleAdapter)
        } else ""
    }

    private fun generateVersioningCode(
        jpaFeatures: JpaFeatureConfiguration,
        styleAdapter: CodeStyleAdapter
    ): String {
        return if (jpaFeatures.versioningEnabled) {
            jpaAnalyzer.generateVersioningSupport(styleAdapter)
        } else ""
    }

    private fun generateSoftDeleteCode(
        jpaFeatures: JpaFeatureConfiguration,
        styleAdapter: CodeStyleAdapter
    ): String {
        return if (jpaFeatures.softDeleteEnabled) {
            jpaAnalyzer.generateSoftDeleteSupport(styleAdapter)
        } else ""
    }

    private fun generateEntityListenerClass(
        jpaFeatures: JpaFeatureConfiguration,
        entityMetadata: EntityMetadata,
        styleAdapter: CodeStyleAdapter
    ): String {
        val entityListenerCallbacks = jpaFeatures.lifecycleCallbacks.filter { it.isEntityListener }
        return if (entityListenerCallbacks.isNotEmpty()) {
            jpaAnalyzer.generateEntityListener(entityListenerCallbacks, entityMetadata, styleAdapter)
        } else ""
    }

    private fun collectJpaImports(jpaFeatures: JpaFeatureConfiguration): Set<String> {
        val imports = mutableSetOf<String>()

        // Standard JPA imports
        imports.add("javax.persistence.*")

        // Inheritance imports
        if (jpaFeatures.inheritanceStrategy != null) {
            when (jpaFeatures.inheritanceStrategy.strategy) {
                ComplexRelationshipAnalyzer.InheritanceStrategy.SINGLE_TABLE -> {
                    imports.add("javax.persistence.InheritanceType")
                    imports.add("javax.persistence.DiscriminatorColumn")
                    imports.add("javax.persistence.DiscriminatorType")
                    imports.add("javax.persistence.DiscriminatorValue")
                }
                ComplexRelationshipAnalyzer.InheritanceStrategy.JOINED -> {
                    imports.add("javax.persistence.InheritanceType")
                }
                ComplexRelationshipAnalyzer.InheritanceStrategy.TABLE_PER_CLASS -> {
                    imports.add("javax.persistence.InheritanceType")
                }
            }

            if (jpaFeatures.inheritanceStrategy.isMappedSuperclass) {
                imports.add("javax.persistence.MappedSuperclass")
            }
        }

        // Embeddable imports
        if (jpaFeatures.embeddableFields.isNotEmpty()) {
            imports.add("javax.persistence.Embedded")
            imports.add("javax.persistence.AttributeOverride")
            imports.add("javax.persistence.AttributeOverrides")
        }

        // Lifecycle callback imports
        if (jpaFeatures.lifecycleCallbacks.isNotEmpty()) {
            jpaFeatures.lifecycleCallbacks.forEach { callback ->
                when (callback.type) {
                    CallbackType.PRE_PERSIST -> imports.add("javax.persistence.PrePersist")
                    CallbackType.POST_PERSIST -> imports.add("javax.persistence.PostPersist")
                    CallbackType.PRE_UPDATE -> imports.add("javax.persistence.PreUpdate")
                    CallbackType.POST_UPDATE -> imports.add("javax.persistence.PostUpdate")
                    CallbackType.PRE_REMOVE -> imports.add("javax.persistence.PreRemove")
                    CallbackType.POST_REMOVE -> imports.add("javax.persistence.PostRemove")
                    CallbackType.POST_LOAD -> imports.add("javax.persistence.PostLoad")
                }
            }
        }

        // Auditing imports
        if (jpaFeatures.auditingEnabled) {
            imports.add("org.springframework.data.annotation.CreatedDate")
            imports.add("org.springframework.data.annotation.LastModifiedDate")
            imports.add("org.springframework.data.annotation.CreatedBy")
            imports.add("org.springframework.data.annotation.LastModifiedBy")
            imports.add("java.time.LocalDateTime")
        }

        // Versioning imports
        if (jpaFeatures.versioningEnabled) {
            imports.add("javax.persistence.Version")
        }

        // Soft delete imports
        if (jpaFeatures.softDeleteEnabled) {
            imports.add("java.time.LocalDateTime")
        }

        // Custom converter imports
        if (jpaFeatures.customConverters.isNotEmpty()) {
            imports.add("javax.persistence.Convert")
        }

        return imports
    }

    /**
     * Get the target file path for the generated entity.
     */
    override fun getTargetFilePath(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String {
        val sourceRootDir = getSourceRootDirForProject(project)
        val packagePath = entityMetadata.packageName.replace(".", File.separator)
        val fileExtension = getFileExtensionForProject(project)
        return Paths.get(sourceRootDir, packagePath, "${entityMetadata.className}.$fileExtension").toString()
    }
}

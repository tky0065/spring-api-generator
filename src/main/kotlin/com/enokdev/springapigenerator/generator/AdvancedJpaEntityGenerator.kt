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
class AdvancedJpaEntityGenerator : IncrementalCodeGenerator() {

    private val jpaAnalyzer = AdvancedJpaFeatureAnalyzer()

    override fun getBaseTemplateName(): String {
        return "AdvancedJpaEntity.java.ft"
    }

    /**
     * Generate entity code with language detection and composite key support
     */
    fun generateEntity(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        styleAdapter: CodeStyleAdapter,
        project: Project,
        outputDir: File
    ): File {
        val generatedCode = generate(project, entityMetadata, packageConfig)

        // Write to output file
        val extension = getFileExtensionForProject(project)
        val fileName = "${entityMetadata.className}.$extension"
        val outputFile = File(outputDir, fileName)
        outputFile.writeText(generatedCode)

        // Generate composite key if needed
        if (needsCompositeKey(entityMetadata)) {
            generateCompositeKeyClass(entityMetadata, packageConfig, styleAdapter, project, outputDir)
        }

        // Generate custom validator classes for cross-field validations
        generateCustomValidators(entityMetadata, packageConfig, isKotlinProject(project), outputDir)

        return outputFile
    }

    /**
     * Determine if the entity needs a composite key.
     */
    private fun needsCompositeKey(entityMetadata: EntityMetadata): Boolean {
        // Check if entity has multiple ID fields or composite key indicators
        val idFields = entityMetadata.fields.filter {
            it.name.contains("id", ignoreCase = true) ||
            it.name == "key" ||
            it.columnName?.contains("_id") == true
        }

        // If more than one ID-like field, might need composite key
        if (idFields.size > 1) return true

        // Check for specific naming patterns that suggest composite keys
        val hasCompositePattern = entityMetadata.className.contains("Mapping") ||
                                  entityMetadata.className.contains("Association") ||
                                  entityMetadata.tableName?.contains("_") == true

        return hasCompositePattern
    }

    /**
     * Generate composite key class using CompositeKeyGenerator.
     */
    private fun generateCompositeKeyClass(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        styleAdapter: CodeStyleAdapter,
        project: Project,
        outputDir: File
    ) {
        val compositeKeyGenerator = CompositeKeyGenerator()

        try {
            val compositeKeyFile = compositeKeyGenerator.generateCompositeKey(
                entityMetadata,
                packageConfig,
                styleAdapter,
                project,
                outputDir
            )

            println("Generated composite key: ${compositeKeyFile.absolutePath}")
        } catch (e: Exception) {
            println("Warning: Failed to generate composite key for ${entityMetadata.className}: ${e.message}")
        }
    }

    /**
     * Generate custom validator classes for cross-field validations
     */
    private fun generateCustomValidators(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        isKotlinProject: Boolean,
        outputDir: File
    ) {
        val validationAnalyzer = EntityValidationAnalyzer()
        val crossFieldValidations = validationAnalyzer.generateCrossFieldValidations(entityMetadata)

        if (crossFieldValidations.isEmpty()) {
            return
        }

        // Create validator directory
        val basePackage = packageConfig["basePackage"] ?: entityMetadata.packageName
        val validatorPackage = "$basePackage.validator"
        val validatorDir = File(outputDir, "validator")
        validatorDir.mkdirs()

        // Generate validator classes
        for (validation in crossFieldValidations) {
            // Generate annotation interface
            val annotationCode = validationAnalyzer.generateCustomValidator(validation, basePackage)
            val annotationFileName = "${validation.type}.${if (isKotlinProject) "kt" else "java"}"
            val annotationFile = File(validatorDir, annotationFileName)
            annotationFile.writeText(annotationCode)

            // Generate validator implementation
            val validatorCode = generateValidatorImplementation(validation, validatorPackage, isKotlinProject)
            val validatorFileName = "${validation.type}Validator.${if (isKotlinProject) "kt" else "java"}"
            val validatorFile = File(validatorDir, validatorFileName)
            validatorFile.writeText(validatorCode)
        }
    }

    /**
     * Generate validator implementation for a cross-field validation
     */
    private fun generateValidatorImplementation(
        validation: CrossFieldValidation,
        validatorPackage: String,
        isKotlinProject: Boolean
    ): String {
        return when (validation.type) {
            "DateRange" -> generateDateRangeValidator(validation, validatorPackage, isKotlinProject)
            "ValueRange" -> generateValueRangeValidator(validation, validatorPackage, isKotlinProject)
            "FieldMatch" -> generateFieldMatchValidator(validation, validatorPackage, isKotlinProject)
            else -> ""
        }
    }

    /**
     * Generate date range validator implementation
     */
    private fun generateDateRangeValidator(
        validation: CrossFieldValidation,
        validatorPackage: String,
        isKotlinProject: Boolean
    ): String {
        return if (isKotlinProject) {
            """
            package $validatorPackage
            
            import javax.validation.ConstraintValidator
            import javax.validation.ConstraintValidatorContext
            import java.time.temporal.Temporal
            import java.util.Date
            import kotlin.reflect.KProperty1
            import kotlin.reflect.full.memberProperties
            
            /**
             * Validator implementation for the DateRange constraint.
             */
            class DateRangeValidator : ConstraintValidator<DateRange, Any> {
                private lateinit var startDateFieldName: String
                private lateinit var endDateFieldName: String
                
                override fun initialize(constraintAnnotation: DateRange) {
                    startDateFieldName = constraintAnnotation.startDate
                    endDateFieldName = constraintAnnotation.endDate
                }
                
                override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean {
                    if (value == null) {
                        return true
                    }
                    
                    try {
                        val startDateValue = getFieldValue(value, startDateFieldName)
                        val endDateValue = getFieldValue(value, endDateFieldName)
                        
                        if (startDateValue == null || endDateValue == null) {
                            return true
                        }
                        
                        return when {
                            startDateValue is Date && endDateValue is Date -> 
                                !startDateValue.after(endDateValue)
                            startDateValue is Temporal && endDateValue is Temporal -> 
                                !startDateValue.isAfter(endDateValue)
                            else -> true
                        }
                    } catch (e: Exception) {
                        return false
                    }
                }
                
                private fun getFieldValue(obj: Any, fieldName: String): Any? {
                    val property = obj::class.memberProperties.find { it.name == fieldName } as? KProperty1<Any, *>
                    return property?.get(obj)
                }
            }
            """.trimIndent()
        } else {
            """
            package $validatorPackage;
            
            import javax.validation.ConstraintValidator;
            import javax.validation.ConstraintValidatorContext;
            import java.lang.reflect.Field;
            import java.time.temporal.Temporal;
            import java.util.Date;
            
            /**
             * Validator implementation for the DateRange constraint.
             */
            public class DateRangeValidator implements ConstraintValidator<DateRange, Object> {
                private String startDateFieldName;
                private String endDateFieldName;
                
                @Override
                public void initialize(DateRange constraintAnnotation) {
                    startDateFieldName = constraintAnnotation.startDate();
                    endDateFieldName = constraintAnnotation.endDate();
                }
                
                @Override
                public boolean isValid(Object value, ConstraintValidatorContext context) {
                    if (value == null) {
                        return true;
                    }
                    
                    try {
                        Object startDateValue = getFieldValue(value, startDateFieldName);
                        Object endDateValue = getFieldValue(value, endDateFieldName);
                        
                        if (startDateValue == null || endDateValue == null) {
                            return true;
                        }
                        
                        if (startDateValue instanceof Date && endDateValue instanceof Date) {
                            return !((Date) startDateValue).after((Date) endDateValue);
                        } else if (startDateValue instanceof Temporal && endDateValue instanceof Temporal) {
                            return !((Temporal) startDateValue).isAfter((Temporal) endDateValue);
                        }
                        
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
                
                private Object getFieldValue(Object obj, String fieldName) throws Exception {
                    Field field = obj.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(obj);
                }
            }
            """.trimIndent()
        }
    }

    /**
     * Generate value range validator implementation
     */
    private fun generateValueRangeValidator(
        validation: CrossFieldValidation,
        validatorPackage: String,
        isKotlinProject: Boolean
    ): String {
        return if (isKotlinProject) {
            """
            package $validatorPackage
            
            import javax.validation.ConstraintValidator
            import javax.validation.ConstraintValidatorContext
            import kotlin.reflect.KProperty1
            import kotlin.reflect.full.memberProperties
            
            /**
             * Validator implementation for the ValueRange constraint.
             */
            class ValueRangeValidator : ConstraintValidator<ValueRange, Any> {
                private lateinit var minValueFieldName: String
                private lateinit var maxValueFieldName: String
                
                override fun initialize(constraintAnnotation: ValueRange) {
                    minValueFieldName = constraintAnnotation.minValue
                    maxValueFieldName = constraintAnnotation.maxValue
                }
                
                override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean {
                    if (value == null) {
                        return true
                    }
                    
                    try {
                        val minValue = getFieldValue(value, minValueFieldName)
                        val maxValue = getFieldValue(value, maxValueFieldName)
                        
                        if (minValue == null || maxValue == null) {
                            return true
                        }
                        
                        return when {
                            minValue is Number && maxValue is Number -> 
                                minValue.toDouble() <= maxValue.toDouble()
                            minValue is Comparable<*> && maxValue is Comparable<*> -> 
                                @Suppress("UNCHECKED_CAST")
                                (minValue as Comparable<Any>) <= (maxValue as Comparable<Any>)
                            else -> true
                        }
                    } catch (e: Exception) {
                        return false
                    }
                }
                
                private fun getFieldValue(obj: Any, fieldName: String): Any? {
                    val property = obj::class.memberProperties.find { it.name == fieldName } as? KProperty1<Any, *>
                    return property?.get(obj)
                }
            }
            """.trimIndent()
        } else {
            """
            package $validatorPackage;
            
            import javax.validation.ConstraintValidator;
            import javax.validation.ConstraintValidatorContext;
            import java.lang.reflect.Field;
            
            /**
             * Validator implementation for the ValueRange constraint.
             */
            public class ValueRangeValidator implements ConstraintValidator<ValueRange, Object> {
                private String minValueFieldName;
                private String maxValueFieldName;
                
                @Override
                public void initialize(ValueRange constraintAnnotation) {
                    minValueFieldName = constraintAnnotation.minValue();
                    maxValueFieldName = constraintAnnotation.maxValue();
                }
                
                @Override
                public boolean isValid(Object value, ConstraintValidatorContext context) {
                    if (value == null) {
                        return true;
                    }
                    
                    try {
                        Object minValue = getFieldValue(value, minValueFieldName);
                        Object maxValue = getFieldValue(value, maxValueFieldName);
                        
                        if (minValue == null || maxValue == null) {
                            return true;
                        }
                        
                        if (minValue instanceof Number && maxValue instanceof Number) {
                            return ((Number) minValue).doubleValue() <= ((Number) maxValue).doubleValue();
                        } else if (minValue instanceof Comparable && maxValue instanceof Comparable) {
                            @SuppressWarnings("unchecked")
                            Comparable<Object> comparableMin = (Comparable<Object>) minValue;
                            return comparableMin.compareTo(maxValue) <= 0;
                        }
                        
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
                
                private Object getFieldValue(Object obj, String fieldName) throws Exception {
                    Field field = obj.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(obj);
                }
            }
            """.trimIndent()
        }
    }

    /**
     * Generate field match validator implementation
     */
    private fun generateFieldMatchValidator(
        validation: CrossFieldValidation,
        validatorPackage: String,
        isKotlinProject: Boolean
    ): String {
        return if (isKotlinProject) {
            """
            package $validatorPackage
            
            import javax.validation.ConstraintValidator
            import javax.validation.ConstraintValidatorContext
            import kotlin.reflect.KProperty1
            import kotlin.reflect.full.memberProperties
            
            /**
             * Validator implementation for the FieldMatch constraint.
             */
            class FieldMatchValidator : ConstraintValidator<FieldMatch, Any> {
                private lateinit var fieldName: String
                private lateinit var fieldMatchName: String
                
                override fun initialize(constraintAnnotation: FieldMatch) {
                    fieldName = constraintAnnotation.field
                    fieldMatchName = constraintAnnotation.fieldMatch
                }
                
                override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean {
                    if (value == null) {
                        return true
                    }
                    
                    try {
                        val fieldValue = getFieldValue(value, fieldName)
                        val fieldMatchValue = getFieldValue(value, fieldMatchName)
                        
                        return fieldValue == fieldMatchValue
                    } catch (e: Exception) {
                        return false
                    }
                }
                
                private fun getFieldValue(obj: Any, fieldName: String): Any? {
                    val property = obj::class.memberProperties.find { it.name == fieldName } as? KProperty1<Any, *>
                    return property?.get(obj)
                }
            }
            """.trimIndent()
        } else {
            """
            package $validatorPackage;
            
            import javax.validation.ConstraintValidator;
            import javax.validation.ConstraintValidatorContext;
            import java.lang.reflect.Field;
            import java.util.Objects;
            
            /**
             * Validator implementation for the FieldMatch constraint.
             */
            public class FieldMatchValidator implements ConstraintValidator<FieldMatch, Object> {
                private String fieldName;
                private String fieldMatchName;
                
                @Override
                public void initialize(FieldMatch constraintAnnotation) {
                    fieldName = constraintAnnotation.field();
                    fieldMatchName = constraintAnnotation.fieldMatch();
                }
                
                @Override
                public boolean isValid(Object value, ConstraintValidatorContext context) {
                    if (value == null) {
                        return true;
                    }
                    
                    try {
                        Object fieldValue = getFieldValue(value, fieldName);
                        Object fieldMatchValue = getFieldValue(value, fieldMatchName);
                        
                        return Objects.equals(fieldValue, fieldMatchValue);
                    } catch (Exception e) {
                        return false;
                    }
                }
                
                private Object getFieldValue(Object obj, String fieldName) throws Exception {
                    Field field = obj.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(obj);
                }
            }
            """.trimIndent()
        }
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

        // Analyze validation requirements
        val validationAnalyzer = EntityValidationAnalyzer()
        val fieldValidations = validationAnalyzer.analyzeEntity(entityMetadata)
        val crossFieldValidations = validationAnalyzer.generateCrossFieldValidations(entityMetadata)

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

        // Add validation features to model
        model["fieldValidations"] = fieldValidations
        model["crossFieldValidations"] = crossFieldValidations
        model["hasValidation"] = fieldValidations.isNotEmpty() || crossFieldValidations.isNotEmpty()
        model["validationImports"] = collectValidationImports(fieldValidations, crossFieldValidations)

        return model
    }
    
    /**
     * Collects imports needed for validation annotations.
     */
    private fun collectValidationImports(
        fieldValidations: Map<String, List<ValidationAnnotation>>,
        crossFieldValidations: List<CrossFieldValidation>
    ): Set<String> {
        val imports = mutableSetOf<String>()
        
        // Basic validation imports
        imports.add("javax.validation.constraints.*")
        
        // Add imports for specific validation types
        val allAnnotations = fieldValidations.values.flatten().map { it.type }
        
        if (allAnnotations.any { it.contains("Email") }) {
            imports.add("javax.validation.constraints.Email")
        }
        
        if (allAnnotations.any { it.contains("Pattern") }) {
            imports.add("javax.validation.constraints.Pattern")
        }
        
        if (allAnnotations.any { it.contains("Future") || it.contains("Past") }) {
            imports.add("javax.validation.constraints.Future")
            imports.add("javax.validation.constraints.Past")
        }
        
        if (allAnnotations.any { it.contains("Min") || it.contains("Max") }) {
            imports.add("javax.validation.constraints.Min")
            imports.add("javax.validation.constraints.Max")
        }
        
        if (allAnnotations.any { it.contains("DecimalMin") || it.contains("DecimalMax") }) {
            imports.add("javax.validation.constraints.DecimalMin")
            imports.add("javax.validation.constraints.DecimalMax")
        }
        
        if (allAnnotations.any { it.contains("Positive") || it.contains("Negative") }) {
            imports.add("javax.validation.constraints.Positive")
            imports.add("javax.validation.constraints.Negative")
        }
        
        // Add imports for cross-field validations
        if (crossFieldValidations.isNotEmpty()) {
            imports.add("javax.validation.Valid")
        }
        
        return imports
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
        
        // Create mock attribute converters based on field types
        val customConverters = mutableListOf<AttributeConverter>()
        
        // Look for fields that might use converters
        entityMetadata.fields.forEach { field ->
            when {
                // Enum types often use converters
                field.type.contains("Enum") || field.name.endsWith("Type") || field.name.endsWith("Status") -> {
                    customConverters.add(
                        AttributeConverter(
                            fieldName = field.name,
                            converterClass = "${field.type}Converter",
                            databaseType = "String",
                            entityType = field.type
                        )
                    )
                }
                
                // JSON data often uses converters
                field.type.contains("Map") || field.type.contains("List") || field.name.contains("json") || field.name.contains("data") -> {
                    customConverters.add(
                        AttributeConverter(
                            fieldName = field.name,
                            converterClass = "JsonConverter",
                            databaseType = "String",
                            entityType = field.type
                        )
                    )
                }
                
                // Boolean fields might use Y/N converters
                field.type == "Boolean" && (field.name.contains("flag") || field.name.contains("indicator")) -> {
                    customConverters.add(
                        AttributeConverter(
                            fieldName = field.name,
                            converterClass = "BooleanToYNConverter",
                            databaseType = "String",
                            entityType = "Boolean"
                        )
                    )
                }
                
                // Date fields might use custom formats
                field.type.contains("Date") || field.type.contains("Time") -> {
                    customConverters.add(
                        AttributeConverter(
                            fieldName = field.name,
                            converterClass = "CustomDateConverter",
                            databaseType = "String",
                            entityType = field.type
                        )
                    )
                }
            }
        }

        return JpaFeatureConfiguration(
            inheritanceStrategy = inheritanceInfo,
            embeddableFields = embeddableFields,
            lifecycleCallbacks = lifecycleCallbacks,
            auditingEnabled = hasAuditing,
            versioningEnabled = hasVersioning,
            softDeleteEnabled = hasSoftDelete,
            customConverters = customConverters
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

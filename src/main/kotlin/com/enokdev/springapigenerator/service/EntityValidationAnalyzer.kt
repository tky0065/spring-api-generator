package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.model.EntityField
import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.RelationType

/**
 * Analyzes entity fields to determine appropriate validation annotations.
 * This class provides comprehensive validation analysis for entity fields
 * based on field types, names, and other characteristics.
 */
class EntityValidationAnalyzer {

    /**
     * Analyzes an entity field and returns a list of validation annotations.
     *
     * @param field The entity field to analyze
     * @return A list of validation annotations for the field
     */
    fun analyzeField(field: EntityField): List<ValidationAnnotation> {
        val annotations = mutableListOf<ValidationAnnotation>()

        // Add @NotNull for non-nullable fields
        if (!field.nullable) {
            annotations.add(ValidationAnnotation("NotNull", "message = \"${field.name} cannot be null\""))
        }

        // Add type-specific validations
        when (field.simpleTypeName) {
            "String" -> {
                // Add @NotBlank for non-nullable String fields
                if (!field.nullable) {
                    annotations.add(ValidationAnnotation("NotBlank", "message = \"${field.name} cannot be blank\""))
                }

                // Add @Size for String fields
                val maxSize = determineMaxSize(field)
                annotations.add(ValidationAnnotation("Size", "max = $maxSize, message = \"${field.name} must be less than $maxSize characters\""))

                // Add @Email for email fields
                if (field.name.contains("email", ignoreCase = true)) {
                    annotations.add(ValidationAnnotation("Email", "message = \"${field.name} must be a valid email address\""))
                }

                // Add @Pattern for specific fields
                when {
                    field.name.contains("phone", ignoreCase = true) -> {
                        annotations.add(ValidationAnnotation("Pattern", "regexp = \"^\\\\+?[0-9\\\\s-]{10,15}$\", message = \"${field.name} must be a valid phone number\""))
                    }
                    field.name.contains("zipcode", ignoreCase = true) || field.name.contains("postalcode", ignoreCase = true) -> {
                        annotations.add(ValidationAnnotation("Pattern", "regexp = \"^[0-9]{5}(-[0-9]{4})?\$\", message = \"${field.name} must be a valid zip code\""))
                    }
                    field.name.contains("url", ignoreCase = true) || field.name.contains("website", ignoreCase = true) -> {
                        annotations.add(ValidationAnnotation("URL", "message = \"${field.name} must be a valid URL\""))
                    }
                }
            }
            "Integer", "int", "Long", "long", "Short", "short" -> {
                // Add range validations for numeric fields
                when {
                    field.name.contains("age", ignoreCase = true) -> {
                        annotations.add(ValidationAnnotation("Min", "value = 0, message = \"${field.name} must be positive\""))
                        annotations.add(ValidationAnnotation("Max", "value = 150, message = \"${field.name} must be less than 150\""))
                    }
                    field.name.contains("year", ignoreCase = true) -> {
                        annotations.add(ValidationAnnotation("Min", "value = 1900, message = \"${field.name} must be after 1900\""))
                    }
                    field.name.contains("count", ignoreCase = true) || 
                    field.name.contains("quantity", ignoreCase = true) || 
                    field.name.contains("number", ignoreCase = true) -> {
                        annotations.add(ValidationAnnotation("Min", "value = 0, message = \"${field.name} must be positive\""))
                    }
                    field.name.contains("price", ignoreCase = true) || 
                    field.name.contains("amount", ignoreCase = true) || 
                    field.name.contains("cost", ignoreCase = true) -> {
                        annotations.add(ValidationAnnotation("Min", "value = 0, message = \"${field.name} must be positive\""))
                    }
                }
            }
            "Double", "double", "Float", "float", "BigDecimal" -> {
                // Add range validations for decimal fields
                when {
                    field.name.contains("price", ignoreCase = true) || 
                    field.name.contains("amount", ignoreCase = true) || 
                    field.name.contains("cost", ignoreCase = true) -> {
                        annotations.add(ValidationAnnotation("DecimalMin", "value = \"0.0\", message = \"${field.name} must be positive\""))
                    }
                    field.name.contains("rate", ignoreCase = true) || 
                    field.name.contains("percentage", ignoreCase = true) -> {
                        annotations.add(ValidationAnnotation("DecimalMin", "value = \"0.0\", message = \"${field.name} must be positive\""))
                        annotations.add(ValidationAnnotation("DecimalMax", "value = \"100.0\", message = \"${field.name} must be less than 100\""))
                    }
                }
            }
            "Date", "LocalDate", "LocalDateTime", "ZonedDateTime", "Calendar" -> {
                // Add date validations
                when {
                    field.name.contains("birthdate", ignoreCase = true) || 
                    field.name.contains("birthDay", ignoreCase = true) -> {
                        annotations.add(ValidationAnnotation("Past", "message = \"${field.name} must be in the past\""))
                    }
                    field.name.contains("expiryDate", ignoreCase = true) || 
                    field.name.contains("dueDate", ignoreCase = true) || 
                    field.name.contains("deadline", ignoreCase = true) -> {
                        annotations.add(ValidationAnnotation("Future", "message = \"${field.name} must be in the future\""))
                    }
                }
            }
            "List", "Set", "Collection", "Map" -> {
                // Add collection validations
                if (field.isCollection) {
                    annotations.add(ValidationAnnotation("Size", "max = 1000, message = \"${field.name} must have less than 1000 items\""))
                }
            }
            "Boolean", "boolean" -> {
                // No specific validations for boolean fields
            }
        }

        return annotations
    }

    /**
     * Analyzes an entity and returns a map of field names to validation annotations.
     *
     * @param entityMetadata The entity metadata to analyze
     * @return A map of field names to lists of validation annotations
     */
    fun analyzeEntity(entityMetadata: EntityMetadata): Map<String, List<ValidationAnnotation>> {
        val validationMap = mutableMapOf<String, List<ValidationAnnotation>>()

        // Analyze each field
        for (field in entityMetadata.fields) {
            if (field.relationType == RelationType.NONE) {
                validationMap[field.name] = analyzeField(field)
            }
        }

        return validationMap
    }

    /**
     * Determines the maximum size for a string field based on its name and characteristics.
     *
     * @param field The entity field to analyze
     * @return The maximum size for the field
     */
    private fun determineMaxSize(field: EntityField): Int {
        return when {
            field.name.contains("description", ignoreCase = true) -> 2000
            field.name.contains("content", ignoreCase = true) -> 4000
            field.name.contains("text", ignoreCase = true) -> 1000
            field.name.contains("name", ignoreCase = true) -> 100
            field.name.contains("title", ignoreCase = true) -> 200
            field.name.contains("email", ignoreCase = true) -> 100
            field.name.contains("password", ignoreCase = true) -> 100
            field.name.contains("phone", ignoreCase = true) -> 20
            field.name.contains("address", ignoreCase = true) -> 200
            field.name.contains("url", ignoreCase = true) -> 255
            field.name.contains("code", ignoreCase = true) -> 50
            else -> 255 // Default max size
        }
    }

    /**
     * Generates cross-field validation annotations for an entity.
     *
     * @param entityMetadata The entity metadata to analyze
     * @return A list of cross-field validation annotations
     */
    fun generateCrossFieldValidations(entityMetadata: EntityMetadata): List<CrossFieldValidation> {
        val validations = mutableListOf<CrossFieldValidation>()

        // Find fields that might need cross-field validation
        val dateFields = entityMetadata.fields.filter { 
            it.simpleTypeName == "Date" || 
            it.simpleTypeName == "LocalDate" || 
            it.simpleTypeName == "LocalDateTime" || 
            it.simpleTypeName == "ZonedDateTime" 
        }

        // Check for start/end date pairs
        val startDateFields = dateFields.filter { it.name.contains("start", ignoreCase = true) }
        val endDateFields = dateFields.filter { it.name.contains("end", ignoreCase = true) }

        if (startDateFields.isNotEmpty() && endDateFields.isNotEmpty()) {
            for (startField in startDateFields) {
                val baseName = startField.name.replace("start", "", ignoreCase = true)
                val matchingEndFields = endDateFields.filter { it.name.contains(baseName, ignoreCase = true) }
                
                if (matchingEndFields.isNotEmpty()) {
                    val endField = matchingEndFields.first()
                    validations.add(
                        CrossFieldValidation(
                            "DateRange",
                            "startDate = \"${startField.name}\", endDate = \"${endField.name}\", message = \"${endField.name} must be after ${startField.name}\""
                        )
                    )
                }
            }
        }

        // Check for min/max value pairs
        val numericFields = entityMetadata.fields.filter { 
            it.simpleTypeName == "Integer" || 
            it.simpleTypeName == "int" || 
            it.simpleTypeName == "Long" || 
            it.simpleTypeName == "long" || 
            it.simpleTypeName == "Double" || 
            it.simpleTypeName == "double" || 
            it.simpleTypeName == "Float" || 
            it.simpleTypeName == "float" || 
            it.simpleTypeName == "BigDecimal" 
        }

        val minFields = numericFields.filter { it.name.contains("min", ignoreCase = true) }
        val maxFields = numericFields.filter { it.name.contains("max", ignoreCase = true) }

        if (minFields.isNotEmpty() && maxFields.isNotEmpty()) {
            for (minField in minFields) {
                val baseName = minField.name.replace("min", "", ignoreCase = true)
                val matchingMaxFields = maxFields.filter { it.name.contains(baseName, ignoreCase = true) }
                
                if (matchingMaxFields.isNotEmpty()) {
                    val maxField = matchingMaxFields.first()
                    validations.add(
                        CrossFieldValidation(
                            "ValueRange",
                            "minValue = \"${minField.name}\", maxValue = \"${maxField.name}\", message = \"${maxField.name} must be greater than ${minField.name}\""
                        )
                    )
                }
            }
        }

        // Check for password confirmation
        val passwordField = entityMetadata.fields.find { it.name.contains("password", ignoreCase = true) }
        val confirmPasswordField = entityMetadata.fields.find { 
            it.name.contains("confirmPassword", ignoreCase = true) || 
            it.name.contains("passwordConfirm", ignoreCase = true) 
        }

        if (passwordField != null && confirmPasswordField != null) {
            validations.add(
                CrossFieldValidation(
                    "FieldMatch",
                    "field = \"${passwordField.name}\", fieldMatch = \"${confirmPasswordField.name}\", message = \"Passwords do not match\""
                )
            )
        }

        return validations
    }

    /**
     * Generates a custom validator class for a cross-field validation.
     *
     * @param validation The cross-field validation
     * @param packageName The package name for the validator
     * @return The custom validator class code
     */
    fun generateCustomValidator(validation: CrossFieldValidation, packageName: String): String {
        return when (validation.type) {
            "DateRange" -> generateDateRangeValidator(validation, packageName)
            "ValueRange" -> generateValueRangeValidator(validation, packageName)
            "FieldMatch" -> generateFieldMatchValidator(validation, packageName)
            else -> ""
        }
    }

    /**
     * Generates a date range validator class.
     *
     * @param validation The cross-field validation
     * @param packageName The package name for the validator
     * @return The date range validator class code
     */
    private fun generateDateRangeValidator(validation: CrossFieldValidation, packageName: String): String {
        return """
            package $packageName.validator;
            
            import javax.validation.Constraint;
            import javax.validation.Payload;
            import java.lang.annotation.Documented;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            
            /**
             * Validates that the end date is after the start date.
             */
            @Documented
            @Constraint(validatedBy = DateRangeValidator.class)
            @Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
            @Retention(RetentionPolicy.RUNTIME)
            public @interface DateRange {
                String message() default "End date must be after start date";
                Class<?>[] groups() default {};
                Class<? extends Payload>[] payload() default {};
                
                String startDate();
                String endDate();
            }
        """.trimIndent()
    }

    /**
     * Generates a value range validator class.
     *
     * @param validation The cross-field validation
     * @param packageName The package name for the validator
     * @return The value range validator class code
     */
    private fun generateValueRangeValidator(validation: CrossFieldValidation, packageName: String): String {
        return """
            package $packageName.validator;
            
            import javax.validation.Constraint;
            import javax.validation.Payload;
            import java.lang.annotation.Documented;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            
            /**
             * Validates that the max value is greater than the min value.
             */
            @Documented
            @Constraint(validatedBy = ValueRangeValidator.class)
            @Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
            @Retention(RetentionPolicy.RUNTIME)
            public @interface ValueRange {
                String message() default "Max value must be greater than min value";
                Class<?>[] groups() default {};
                Class<? extends Payload>[] payload() default {};
                
                String minValue();
                String maxValue();
            }
        """.trimIndent()
    }

    /**
     * Generates a field match validator class.
     *
     * @param validation The cross-field validation
     * @param packageName The package name for the validator
     * @return The field match validator class code
     */
    private fun generateFieldMatchValidator(validation: CrossFieldValidation, packageName: String): String {
        return """
            package $packageName.validator;
            
            import javax.validation.Constraint;
            import javax.validation.Payload;
            import java.lang.annotation.Documented;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            
            /**
             * Validates that two fields have the same value.
             */
            @Documented
            @Constraint(validatedBy = FieldMatchValidator.class)
            @Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
            @Retention(RetentionPolicy.RUNTIME)
            public @interface FieldMatch {
                String message() default "Fields do not match";
                Class<?>[] groups() default {};
                Class<? extends Payload>[] payload() default {};
                
                String field();
                String fieldMatch();
            }
        """.trimIndent()
    }
}

/**
 * Represents a validation annotation for a field.
 *
 * @property type The type of validation annotation (e.g., NotNull, Size, etc.)
 * @property attributes The attributes for the annotation (e.g., min = 1, max = 100, etc.)
 */
data class ValidationAnnotation(
    val type: String,
    val attributes: String = ""
) {
    /**
     * Returns the annotation as a string.
     *
     * @return The annotation as a string
     */
    fun toAnnotationString(): String {
        return if (attributes.isBlank()) {
            "@$type"
        } else {
            "@$type($attributes)"
        }
    }
}

/**
 * Represents a cross-field validation annotation.
 *
 * @property type The type of cross-field validation (e.g., DateRange, ValueRange, etc.)
 * @property attributes The attributes for the annotation
 */
data class CrossFieldValidation(
    val type: String,
    val attributes: String
) {
    /**
     * Returns the annotation as a string.
     *
     * @return The annotation as a string
     */
    fun toAnnotationString(): String {
        return "@$type($attributes)"
    }
}
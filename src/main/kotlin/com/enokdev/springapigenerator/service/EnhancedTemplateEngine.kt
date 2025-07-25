package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.SpringBootVersionDetectionService.*
import com.intellij.openapi.project.Project
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModelException

/**
 * Enhanced template engine with conditional sections and sophisticated helpers.
 */
class EnhancedTemplateEngine(private val project: Project) {

    private val springBootInfo: SpringBootInfo? by lazy {
        SpringBootVersionDetectionService().detectSpringBootInfo(project)
    }

    /**
     * Creates enhanced data model with conditional helpers and fragments.
     */
    fun createEnhancedDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>,
        styleAdapter: CodeStyleAdapter
    ): MutableMap<String, Any> {
        val model = mutableMapOf<String, Any>()

        // Add basic entity metadata
        model["entity"] = entityMetadata
        model["className"] = entityMetadata.className
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["fields"] = entityMetadata.fields
        model["packages"] = packageConfig

        // Add Spring Boot version info
        springBootInfo?.let { info ->
            model["springBoot"] = mapOf(
                "version" to info.version,
                "majorVersion" to info.majorVersion,
                "minorVersion" to info.minorVersion,
                "isVersion3OrHigher" to info.isVersion3OrHigher(),
                "isVersion2OrHigher" to info.isVersion2OrHigher(),
                "hasWebStarter" to info.hasWebStarter,
                "hasDataJpaStarter" to info.hasDataJpaStarter,
                "hasSecurityStarter" to info.hasSecurityStarter,
                "hasValidationStarter" to info.hasValidationStarter,
                "hasActuatorStarter" to info.hasActuatorStarter,
                "dependencies" to info.dependencies
            )
        }

        // Add template helpers
        model["helpers"] = createTemplateHelpers(entityMetadata, styleAdapter)

        // Add fragments
        model["fragments"] = createTemplateFragments(entityMetadata, styleAdapter)

        // Add conditional sections
        model["conditionals"] = createConditionalHelpers()

        return model
    }

    /**
     * Creates sophisticated template helpers for common patterns.
     */
    private fun createTemplateHelpers(entityMetadata: EntityMetadata, styleAdapter: CodeStyleAdapter): Map<String, Any> {
        return mapOf(
            "capitalize" to CapitalizeHelper(),
            "uncapitalize" to UncapitalizeHelper(),
            "camelToSnake" to CamelToSnakeHelper(),
            "snakeToCamel" to SnakeToCamelHelper(),
            "pluralize" to PluralizeHelper(),
            "singularize" to SingularizeHelper(),
            "indent" to IndentHelper(styleAdapter),
            "annotation" to AnnotationHelper(springBootInfo),
            "import" to ImportHelper(springBootInfo),
            "validation" to ValidationHelper(springBootInfo),
            "jpa" to JpaHelper(springBootInfo),
            "security" to SecurityHelper(springBootInfo),
            "test" to TestHelper(springBootInfo),
            "field" to FieldHelper(entityMetadata, styleAdapter),
            "method" to MethodHelper(entityMetadata, styleAdapter)
        )
    }

    /**
     * Creates reusable template fragments for common code blocks.
     */
    private fun createTemplateFragments(entityMetadata: EntityMetadata, styleAdapter: CodeStyleAdapter): Map<String, Any> {
        return mapOf(
            "entityAnnotations" to generateEntityAnnotationsFragment(entityMetadata),
            "fieldAnnotations" to FieldAnnotationsFragment(springBootInfo),
            "repositoryMethods" to generateRepositoryMethodsFragment(entityMetadata),
            "serviceMethods" to generateServiceMethodsFragment(entityMetadata),
            "controllerMethods" to generateControllerMethodsFragment(entityMetadata),
            "validationAnnotations" to generateValidationAnnotationsFragment(entityMetadata),
            "securityAnnotations" to generateSecurityAnnotationsFragment(),
            "testAnnotations" to generateTestAnnotationsFragment(),
            "auditingFields" to generateAuditingFieldsFragment(),
            "commonImports" to generateCommonImportsFragment()
        )
    }

    /**
     * Creates conditional helpers based on Spring Boot features.
     */
    private fun createConditionalHelpers(): Map<String, Any> {
        return mapOf(
            "ifVersion3OrHigher" to ConditionalHelper { springBootInfo?.isVersion3OrHigher() == true },
            "ifVersion2OrHigher" to ConditionalHelper { springBootInfo?.isVersion2OrHigher() == true },
            "ifWebStarter" to ConditionalHelper { springBootInfo?.hasWebStarter == true },
            "ifDataJpaStarter" to ConditionalHelper { springBootInfo?.hasDataJpaStarter == true },
            "ifSecurityStarter" to ConditionalHelper { springBootInfo?.hasSecurityStarter == true },
            "ifValidationStarter" to ConditionalHelper { springBootInfo?.hasValidationStarter == true },
            "ifActuatorStarter" to ConditionalHelper { springBootInfo?.hasActuatorStarter == true },
            "ifFeature" to FeatureConditionalHelper(springBootInfo)
        )
    }

    // Template helper implementations

    class CapitalizeHelper : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("capitalize helper requires one argument")
            val value = arguments[0].toString()
            return value.replaceFirstChar { it.uppercase() }
        }
    }

    class UncapitalizeHelper : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("uncapitalize helper requires one argument")
            val value = arguments[0].toString()
            return value.replaceFirstChar { it.lowercase() }
        }
    }

    class CamelToSnakeHelper : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("camelToSnake helper requires one argument")
            val value = arguments[0].toString()
            return value.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
        }
    }

    class SnakeToCamelHelper : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("snakeToCamel helper requires one argument")
            val value = arguments[0].toString()
            return value.split("_").joinToString("") { part ->
                if (part.isNotEmpty()) part.replaceFirstChar { it.uppercase() } else part
            }.replaceFirstChar { it.lowercase() }
        }
    }

    class PluralizeHelper : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("pluralize helper requires one argument")
            val value = arguments[0].toString()
            return when {
                value.endsWith("y") -> value.dropLast(1) + "ies"
                value.endsWith("s") || value.endsWith("sh") || value.endsWith("ch") -> value + "es"
                else -> value + "s"
            }
        }
    }

    class SingularizeHelper : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("singularize helper requires one argument")
            val value = arguments[0].toString()
            return when {
                value.endsWith("ies") -> value.dropLast(3) + "y"
                value.endsWith("es") -> value.dropLast(2)
                value.endsWith("s") && !value.endsWith("ss") -> value.dropLast(1)
                else -> value
            }
        }
    }

    class IndentHelper(private val styleAdapter: CodeStyleAdapter) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            val level = if (!arguments.isNullOrEmpty()) arguments[0].toString().toIntOrNull() ?: 1 else 1
            return styleAdapter.getIndentation(level)
        }
    }

    class AnnotationHelper(private val springBootInfo: SpringBootInfo?) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("annotation helper requires at least one argument")
            val annotationType = arguments[0].toString()
            val attributes = if (arguments.size > 1) arguments[1].toString() else ""

            return when (annotationType) {
                "Entity" -> "@Entity" + if (attributes.isNotEmpty()) "($attributes)" else ""
                "Table" -> "@Table" + if (attributes.isNotEmpty()) "($attributes)" else ""
                "Id" -> "@Id"
                "GeneratedValue" -> "@GeneratedValue" + if (attributes.isNotEmpty()) "($attributes)" else "(strategy = GenerationType.IDENTITY)"
                "Column" -> "@Column" + if (attributes.isNotEmpty()) "($attributes)" else ""
                "NotNull" -> if (springBootInfo?.hasValidationStarter == true) "@NotNull" else ""
                "NotBlank" -> if (springBootInfo?.hasValidationStarter == true) "@NotBlank" else ""
                "Size" -> if (springBootInfo?.hasValidationStarter == true) "@Size" + if (attributes.isNotEmpty()) "($attributes)" else "" else ""
                else -> "@$annotationType" + if (attributes.isNotEmpty()) "($attributes)" else ""
            }
        }
    }

    class ImportHelper(private val springBootInfo: SpringBootInfo?) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("import helper requires one argument")
            val importType = arguments[0].toString()

            return when (importType) {
                "jpa" -> "import javax.persistence.*;"
                "validation" -> if (springBootInfo?.hasValidationStarter == true) "import javax.validation.constraints.*;" else ""
                "spring-web" -> if (springBootInfo?.hasWebStarter == true) "import org.springframework.web.bind.annotation.*;" else ""
                "spring-data" -> if (springBootInfo?.hasDataJpaStarter == true) "import org.springframework.data.jpa.repository.*;" else ""
                "spring-security" -> if (springBootInfo?.hasSecurityStarter == true) "import org.springframework.security.access.prepost.*;" else ""
                else -> "import $importType;"
            }
        }
    }

    class ValidationHelper(private val springBootInfo: SpringBootInfo?) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (springBootInfo?.hasValidationStarter != true) return ""
            if (arguments.isNullOrEmpty()) throw TemplateModelException("validation helper requires one argument")

            val validationType = arguments[0].toString()
            val attributes = if (arguments.size > 1) arguments[1].toString() else ""

            return when (validationType) {
                "notNull" -> "@NotNull"
                "notBlank" -> "@NotBlank"
                "size" -> "@Size($attributes)"
                "min" -> "@Min($attributes)"
                "max" -> "@Max($attributes)"
                "email" -> "@Email"
                "pattern" -> "@Pattern(regexp = \"$attributes\")"
                else -> "@$validationType"
            }
        }
    }

    class JpaHelper(private val springBootInfo: SpringBootInfo?) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("jpa helper requires one argument")
            val jpaType = arguments[0].toString()
            val attributes = if (arguments.size > 1) arguments[1].toString() else ""

            return when (jpaType) {
                "entity" -> "@Entity"
                "table" -> "@Table" + if (attributes.isNotEmpty()) "(name = \"$attributes\")" else ""
                "id" -> "@Id"
                "generatedValue" -> "@GeneratedValue(strategy = GenerationType.IDENTITY)"
                "column" -> "@Column" + if (attributes.isNotEmpty()) "($attributes)" else ""
                "oneToMany" -> "@OneToMany" + if (attributes.isNotEmpty()) "($attributes)" else ""
                "manyToOne" -> "@ManyToOne" + if (attributes.isNotEmpty()) "($attributes)" else ""
                "manyToMany" -> "@ManyToMany" + if (attributes.isNotEmpty()) "($attributes)" else ""
                "joinColumn" -> "@JoinColumn" + if (attributes.isNotEmpty()) "(name = \"$attributes\")" else ""
                else -> "@$jpaType"
            }
        }
    }

    class SecurityHelper(private val springBootInfo: SpringBootInfo?) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (springBootInfo?.hasSecurityStarter != true) return ""
            if (arguments.isNullOrEmpty()) throw TemplateModelException("security helper requires one argument")

            val securityType = arguments[0].toString()
            val attributes = if (arguments.size > 1) arguments[1].toString() else ""

            return when (securityType) {
                "preAuthorize" -> "@PreAuthorize(\"$attributes\")"
                "postAuthorize" -> "@PostAuthorize(\"$attributes\")"
                "secured" -> "@Secured(\"$attributes\")"
                "rolesAllowed" -> "@RolesAllowed(\"$attributes\")"
                else -> "@$securityType"
            }
        }
    }

    class TestHelper(private val springBootInfo: SpringBootInfo?) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (springBootInfo?.hasTestStarter != true) return ""
            if (arguments.isNullOrEmpty()) throw TemplateModelException("test helper requires one argument")

            val testType = arguments[0].toString()

            return when (testType) {
                "springBootTest" -> "@SpringBootTest"
                "webMvcTest" -> "@WebMvcTest"
                "dataJpaTest" -> "@DataJpaTest"
                "test" -> "@Test"
                "beforeEach" -> "@BeforeEach"
                "afterEach" -> "@AfterEach"
                else -> "@$testType"
            }
        }
    }

    class FieldHelper(private val entityMetadata: EntityMetadata, private val styleAdapter: CodeStyleAdapter) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("field helper requires one argument")
            val operation = arguments[0].toString()
            val fieldName = if (arguments.size > 1) arguments[1].toString() else ""

            return when (operation) {
                "adaptName" -> styleAdapter.adaptFieldName(fieldName)
                "getterName" -> {
                    val field = entityMetadata.fields.find { it.name == fieldName }
                    styleAdapter.formatGetterName(fieldName, field?.type == "boolean")
                }
                "setterName" -> styleAdapter.formatSetterName(fieldName)
                else -> ""
            }
        }
    }

    class MethodHelper(private val entityMetadata: EntityMetadata, private val styleAdapter: CodeStyleAdapter) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("method helper requires one argument")
            val operation = arguments[0].toString()
            val methodName = if (arguments.size > 1) arguments[1].toString() else ""

            return when (operation) {
                "adaptName" -> styleAdapter.adaptMethodName(methodName)
                "declaration" -> styleAdapter.formatMethodDeclaration(methodName)
                else -> ""
            }
        }
    }

    class ConditionalHelper(private val condition: () -> Boolean) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            return condition()
        }
    }

    class FeatureConditionalHelper(private val springBootInfo: SpringBootInfo?) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("ifFeature helper requires one argument")
            val featureName = arguments[0].toString()

            val feature = try {
                SpringBootFeature.valueOf(featureName.uppercase())
            } catch (_: IllegalArgumentException) {
                return false
            }

            return springBootInfo?.hasFeature(feature) == true
        }
    }

    // Fragment implementations

    private fun generateEntityAnnotationsFragment(entityMetadata: EntityMetadata): String {
        return "@Entity\n@Table(name = \"${entityMetadata.tableName}\")"
    }

    class FieldAnnotationsFragment(private val springBootInfo: SpringBootInfo?) : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            if (arguments.isNullOrEmpty()) throw TemplateModelException("fieldAnnotations fragment requires field argument")
            // This would analyze the field and return appropriate annotations
            return ""
        }
    }

    private fun generateRepositoryMethodsFragment(entityMetadata: EntityMetadata): String {
        return """
            List<${entityMetadata.className}> findByNameContaining(String name);
            Optional<${entityMetadata.className}> findByEmail(String email);
            void deleteByName(String name);
        """.trimIndent()
    }

    private fun generateServiceMethodsFragment(entityMetadata: EntityMetadata): String {
        return """
            public List<${entityMetadata.className}> findAll() {
                return repository.findAll();
            }
            
            public Optional<${entityMetadata.className}> findById(Long id) {
                return repository.findById(id);
            }
            
            public ${entityMetadata.className} save(${entityMetadata.className} entity) {
                return repository.save(entity);
            }
            
            public void deleteById(Long id) {
                repository.deleteById(id);
            }
        """.trimIndent()
    }

    private fun generateControllerMethodsFragment(entityMetadata: EntityMetadata): String {
        return """
            @GetMapping
            public ResponseEntity<List<${entityMetadata.className}>> getAll() {
                return ResponseEntity.ok(service.findAll());
            }
            
            @GetMapping("/{id}")
            public ResponseEntity<${entityMetadata.className}> getById(@PathVariable Long id) {
                return service.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
            }
            
            @PostMapping
            public ResponseEntity<${entityMetadata.className}> create(@RequestBody ${entityMetadata.className} entity) {
                return ResponseEntity.ok(service.save(entity));
            }
            
            @PutMapping("/{id}")
            public ResponseEntity<${entityMetadata.className}> update(@PathVariable Long id, @RequestBody ${entityMetadata.className} entity) {
                return service.findById(id)
                    .map(existing -> ResponseEntity.ok(service.save(entity)))
                    .orElse(ResponseEntity.notFound().build());
            }
            
            @DeleteMapping("/{id}")
            public ResponseEntity<Void> delete(@PathVariable Long id) {
                service.deleteById(id);
                return ResponseEntity.noContent().build();
            }
        """.trimIndent()
    }

    private fun generateValidationAnnotationsFragment(entityMetadata: EntityMetadata): String {
        return """
            @NotNull(message = "Field cannot be null")
            @NotBlank(message = "Field cannot be blank")
            @Size(min = 1, max = 255, message = "Field must be between 1 and 255 characters")
        """.trimIndent()
    }

    private fun generateSecurityAnnotationsFragment(): String {
        return """
            @PreAuthorize("hasRole('ADMIN')")
            @PreAuthorize("hasRole('USER')")
            @PostAuthorize("returnObject.owner == authentication.name")
        """.trimIndent()
    }

    private fun generateTestAnnotationsFragment(): String {
        return """
            @SpringBootTest
            @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
            @Transactional
            @Rollback
        """.trimIndent()
    }

    private fun generateAuditingFieldsFragment(): String {
        return """
            @CreatedDate
            private LocalDateTime createdAt;
            
            @LastModifiedDate
            private LocalDateTime updatedAt;
            
            @CreatedBy
            private String createdBy;
            
            @LastModifiedBy
            private String lastModifiedBy;
        """.trimIndent()
    }

    private fun generateCommonImportsFragment(): String {
        return """
            import java.time.LocalDateTime;
            import java.util.List;
            import java.util.Optional;
            import javax.persistence.*;
            import javax.validation.constraints.*;
            import org.springframework.data.annotation.CreatedDate;
            import org.springframework.data.annotation.LastModifiedDate;
        """.trimIndent()
    }
}

package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for unit tests.
 */
class TestGenerator : AbstractTemplateCodeGenerator("Test.java.ft") {

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val sourceRoot = getSourceRootDir(project)
        // Tests go to src/test/java
        val testRoot = sourceRoot.replace("main", "test")
        val servicePackage = packageConfig["servicePackage"] ?: entityMetadata.servicePackage
        val serviceDir = servicePackage.replace(".", "/")
        val fileName = "${entityMetadata.serviceName}Test.java"
        return Paths.get(testRoot, serviceDir, fileName).toString()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // Add test-specific model data
        val testFields = generateTestFields(entityMetadata)
        val testSetup = generateTestSetup(entityMetadata)
        val testMethods = generateTestMethods(entityMetadata)
        val additionalImports = generateAdditionalImports(entityMetadata)

        model["testFields"] = testFields
        model["testSetup"] = testSetup
        model["testMethods"] = testMethods
        model["additionalImports"] = additionalImports

        return model
    }

    /**
     * Generate field declarations for the test class.
     */
    private fun generateTestFields(entityMetadata: EntityMetadata): String {
        val entityName = entityMetadata.className
        val entityNameLower = entityMetadata.entityNameLower

        return """
            @Mock
            private ${entityName}Repository ${entityNameLower}Repository;

            @Mock
            private ${entityName}Mapper ${entityNameLower}Mapper;

            @InjectMocks
            private ${entityName}ServiceImpl ${entityNameLower}Service;

            private ${entityName} ${entityNameLower};
            private ${entityName}DTO ${entityNameLower}DTO;
            private List<${entityName}> ${entityNameLower}List;
        """.trimIndent()
    }

    /**
     * Generate test setup method.
     */
    private fun generateTestSetup(entityMetadata: EntityMetadata): String {
        val entityName = entityMetadata.className
        val entityNameLower = entityMetadata.entityNameLower
        val idType = extractSimpleTypeName(entityMetadata.idType)

        return """
            @BeforeEach
            void setUp() {
                // Create test entity
                ${entityNameLower} = new ${entityName}();
                ${entityNameLower}.setId(($idType) 1L);
                
                // Create fields
                ${generateEntityFieldSetters(entityMetadata)}
                
                // Create test DTO
                ${entityNameLower}DTO = new ${entityName}DTO();
                ${entityNameLower}DTO.setId(($idType) 1L);
                ${generateDtoFieldSetters(entityMetadata)}
                
                // Create list of entities
                ${entityNameLower}List = Arrays.asList(${entityNameLower});
            }
        """.trimIndent()
    }

    /**
     * Generate setters for entity fields in test setup.
     */
    private fun generateEntityFieldSetters(entityMetadata: EntityMetadata): String {
        val sb = StringBuilder()
        val entityNameLower = entityMetadata.entityNameLower

        entityMetadata.fields.forEach { field ->
            if (field.name != "id" && !field.isCollection) {
                val fieldValue = getTestValueForType(field.simpleTypeName)
                sb.appendLine("${entityNameLower}.set${field.name.replaceFirstChar { it.uppercase() }}($fieldValue);")
            }
        }

        return sb.toString()
    }

    /**
     * Generate setters for DTO fields in test setup.
     */
    private fun generateDtoFieldSetters(entityMetadata: EntityMetadata): String {
        val sb = StringBuilder()
        val entityNameLower = entityMetadata.entityNameLower

        entityMetadata.fields.forEach { field ->
            if (field.name != "id" && !field.isCollection) {
                val fieldValue = getTestValueForType(field.simpleTypeName)
                sb.appendLine("${entityNameLower}DTO.set${field.name.replaceFirstChar { it.uppercase() }}($fieldValue);")
            }
        }

        return sb.toString()
    }

    /**
     * Generate test methods for CRUD operations.
     */
    private fun generateTestMethods(entityMetadata: EntityMetadata): String {
        val entityName = entityMetadata.className
        val entityNameLower = entityMetadata.entityNameLower
        val idType = extractSimpleTypeName(entityMetadata.idType)

        return """
            @Test
            void testFindAll() {
                // Arrange
                when(${entityNameLower}Repository.findAll()).thenReturn(${entityNameLower}List);
                when(${entityNameLower}Mapper.toDto(any(${entityName}.class))).thenReturn(${entityNameLower}DTO);
                
                // Act
                List<${entityName}DTO> result = ${entityNameLower}Service.findAll();
                
                // Assert
                assertThat(result).isNotNull();
                assertThat(result.size()).isEqualTo(1);
                verify(${entityNameLower}Repository).findAll();
            }
            
            @Test
            void testFindOne() {
                // Arrange
                when(${entityNameLower}Repository.findById(any($idType.class))).thenReturn(Optional.of(${entityNameLower}));
                when(${entityNameLower}Mapper.toDto(any(${entityName}.class))).thenReturn(${entityNameLower}DTO);
                
                // Act
                ${entityName}DTO result = ${entityNameLower}Service.findOne(($idType) 1L);
                
                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(${entityNameLower}DTO.getId());
                verify(${entityNameLower}Repository).findById(any($idType.class));
            }
            
            @Test
            void testSave() {
                // Arrange
                when(${entityNameLower}Mapper.toEntity(any(${entityName}DTO.class))).thenReturn(${entityNameLower});
                when(${entityNameLower}Repository.save(any(${entityName}.class))).thenReturn(${entityNameLower});
                when(${entityNameLower}Mapper.toDto(any(${entityName}.class))).thenReturn(${entityNameLower}DTO);
                
                // Act
                ${entityName}DTO result = ${entityNameLower}Service.save(${entityNameLower}DTO);
                
                // Assert
                assertThat(result).isNotNull();
                verify(${entityNameLower}Repository).save(any(${entityName}.class));
                verify(${entityNameLower}Mapper).toDto(any(${entityName}.class));
            }
            
            @Test
            void testUpdate() {
                // Arrange
                when(${entityNameLower}Mapper.toEntity(any(${entityName}DTO.class))).thenReturn(${entityNameLower});
                when(${entityNameLower}Repository.save(any(${entityName}.class))).thenReturn(${entityNameLower});
                when(${entityNameLower}Mapper.toDto(any(${entityName}.class))).thenReturn(${entityNameLower}DTO);
                
                // Act
                ${entityName}DTO result = ${entityNameLower}Service.update(${entityNameLower}DTO);
                
                // Assert
                assertThat(result).isNotNull();
                verify(${entityNameLower}Repository).save(any(${entityName}.class));
                verify(${entityNameLower}Mapper).toDto(any(${entityName}.class));
            }
            
            @Test
            void testDelete() {
                // Arrange
                doNothing().when(${entityNameLower}Repository).deleteById(any($idType.class));
                
                // Act
                ${entityNameLower}Service.delete(($idType) 1L);
                
                // Assert
                verify(${entityNameLower}Repository).deleteById(any($idType.class));
            }
        """.trimIndent()
    }

    /**
     * Generate additional imports needed for tests.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata): String {
        val imports = mutableSetOf<String>()

        // Add imports for the entity, dto, repository, mapper, service
        imports.add("${entityMetadata.domainPackage}.${entityMetadata.className}") // Le domainPackage contient maintenant le chemin vers entity
        imports.add("${entityMetadata.dtoPackage}.${entityMetadata.dtoName}")
        imports.add("${entityMetadata.repositoryPackage}.${entityMetadata.repositoryName}")
        imports.add("${entityMetadata.mapperPackage}.${entityMetadata.mapperName}")
        imports.add("${entityMetadata.servicePackage}.${entityMetadata.serviceName}")
        imports.add("${entityMetadata.servicePackage}.impl.${entityMetadata.serviceImplName}")

        // Add common imports for testing
        imports.add("org.junit.jupiter.api.BeforeEach")
        imports.add("org.junit.jupiter.api.Test")
        imports.add("org.junit.jupiter.api.extension.ExtendWith")
        imports.add("org.mockito.InjectMocks")
        imports.add("org.mockito.Mock")
        imports.add("org.mockito.junit.jupiter.MockitoExtension")

        imports.add("java.util.Arrays")
        imports.add("java.util.List")
        imports.add("java.util.Optional")

        imports.add("static org.assertj.core.api.Assertions.assertThat")
        imports.add("static org.mockito.ArgumentMatchers.any")
        imports.add("static org.mockito.Mockito.*")

        return imports.joinToString("\n") { "import $it;" }
    }

    /**
     * Get sample test values for field types.
     */
    private fun getTestValueForType(typeName: String): String {
        return when (typeName) {
            "String" -> "\"test\""
            "Integer", "int" -> "123"
            "Long", "long" -> "123L"
            "Double", "double" -> "123.45"
            "Float", "float" -> "123.45f"
            "Boolean", "boolean" -> "true"
            "LocalDate" -> "java.time.LocalDate.now()"
            "LocalDateTime" -> "java.time.LocalDateTime.now()"
            "ZonedDateTime" -> "java.time.ZonedDateTime.now()"
            "BigDecimal" -> "new java.math.BigDecimal(\"123.45\")"
            "UUID" -> "java.util.UUID.randomUUID()"
            else -> "null"
        }
    }
}

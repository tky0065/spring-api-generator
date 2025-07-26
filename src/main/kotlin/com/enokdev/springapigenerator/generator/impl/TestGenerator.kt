package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.generator.AbstractTemplateCodeGenerator
import com.enokdev.springapigenerator.model.EntityMetadata
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Generator for unit tests.
 */
class TestGenerator : AbstractTemplateCodeGenerator() {

    override fun getBaseTemplateName(): String {
        return "Test.java.ft"
    }

    override fun getTargetFilePath(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        val testRoot = getTestRootDirForProject(project)
        val servicePackage = packageConfig["servicePackage"] ?: entityMetadata.servicePackage
        val serviceDir = servicePackage.replace(".", "/")
        val extension = getFileExtensionForProject(project)
        val fileName = "${entityMetadata.serviceName}Test.$extension"
        return Paths.get(testRoot, serviceDir, fileName).toString()
    }

    override fun createDataModel(
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)

        // ========== VARIABLES DE BASE POUR TOUS LES TEMPLATES ==========
        model["testClassName"] = "${entityMetadata.serviceName}Test"
        model["serviceName"] = entityMetadata.serviceName
        model["serviceImplName"] = entityMetadata.serviceImplName
        model["className"] = entityMetadata.className
        model["entityName"] = entityMetadata.className
        model["entityNameLower"] = entityMetadata.entityNameLower
        model["dtoName"] = entityMetadata.dtoName
        model["repositoryName"] = entityMetadata.repositoryName
        model["mapperName"] = entityMetadata.mapperName
        model["packageName"] = packageConfig["servicePackage"] ?: entityMetadata.servicePackage

        // ========== VARIABLES POUR LES NOMS DE VARIABLES ==========
        model["entityVarName"] = entityMetadata.entityNameLower
        model["serviceVarName"] = "${entityMetadata.entityNameLower}Service"
        model["repositoryVarName"] = "${entityMetadata.entityNameLower}Repository"
        model["mapperVarName"] = "${entityMetadata.entityNameLower}Mapper"
        model["dtoVarName"] = "${entityMetadata.entityNameLower}DTO"

        // ========== VARIABLES POUR LES API PATHS ==========
        model["entityApiPath"] = entityMetadata.entityNameLower.lowercase()

        // Add test-specific model data
        val additionalImports = generateAdditionalImports(entityMetadata)
        val testMethods = generateTestMethods(entityMetadata)
        val mockSetup = generateMockSetup(entityMetadata)
        val customMethods = generateCustomMethods(entityMetadata)

        model["additionalImports"] = additionalImports
        model["imports"] = additionalImports
        model["testMethods"] = testMethods
        model["mockSetup"] = mockSetup
        model["customMethods"] = customMethods

        return model
    }

    /**
     * Generate additional imports needed for the test class.
     */
    private fun generateAdditionalImports(entityMetadata: EntityMetadata): String {
        val imports = mutableSetOf<String>()

        // Add imports for the classes being tested
        imports.add("${entityMetadata.servicePackage}.impl.${entityMetadata.serviceImplName}")
        imports.add("${entityMetadata.repositoryPackage}.${entityMetadata.repositoryName}")
        imports.add("${entityMetadata.mapperPackage}.${entityMetadata.mapperName}")
        imports.add("${entityMetadata.domainPackage}.${entityMetadata.className}")
        imports.add("${entityMetadata.dtoPackage}.${entityMetadata.dtoName}")

        // Add JUnit 5 imports
        imports.add("org.junit.jupiter.api.Test")
        imports.add("org.junit.jupiter.api.BeforeEach")
        imports.add("org.junit.jupiter.api.DisplayName")
        imports.add("org.junit.jupiter.api.extension.ExtendWith")

        // Add Mockito imports
        imports.add("org.mockito.InjectMocks")
        imports.add("org.mockito.Mock")
        imports.add("org.mockito.junit.jupiter.MockitoExtension")
        imports.add("org.mockito.Mockito.*")

        // Add Spring test imports
        imports.add("org.springframework.test.context.junit.jupiter.SpringJUnitConfig")

        // Add assertion imports
        imports.add("org.assertj.core.api.Assertions.*")

        // Add common Java imports
        imports.add("java.util.*")

        return imports.joinToString("\n") { "import $it;" }
    }

    /**
     * Generate mock setup code for the test class.
     */
    private fun generateMockSetup(entityMetadata: EntityMetadata): String {
        val setup = StringBuilder()

        setup.append("""
            @Mock
            private ${entityMetadata.repositoryName} ${entityMetadata.entityNameLower}Repository;
            
            @Mock
            private ${entityMetadata.mapperName} ${entityMetadata.entityNameLower}Mapper;
            
            @InjectMocks
            private ${entityMetadata.serviceImplName} ${entityMetadata.entityNameLower}Service;
            
            private ${entityMetadata.className} ${entityMetadata.entityNameLower};
            private ${entityMetadata.dtoName} ${entityMetadata.entityNameLower}DTO;
            
            @BeforeEach
            void setUp() {
                ${entityMetadata.entityNameLower} = create${entityMetadata.className}();
                ${entityMetadata.entityNameLower}DTO = create${entityMetadata.className}DTO();
            }
        """.trimIndent())

        return setup.toString()
    }

    /**
     * Generate test methods for the service.
     */
    private fun generateTestMethods(entityMetadata: EntityMetadata): String {
        val methods = StringBuilder()
        val entityName = entityMetadata.className
        val entityNameLower = entityMetadata.entityNameLower
        val dtoName = entityMetadata.dtoName
        val idType = entityMetadata.idType.substringAfterLast(".")

        // Test findAll method
        methods.append("""
            @Test
            @DisplayName("Should find all ${entityNameLower}s")
            void shouldFindAll${entityName}s() {
                // Given
                List<${entityName}> ${entityNameLower}s = Arrays.asList(${entityNameLower});
                List<${dtoName}> ${entityNameLower}DTOs = Arrays.asList(${entityNameLower}DTO);
                
                when(${entityNameLower}Repository.findAll()).thenReturn(${entityNameLower}s);
                when(${entityNameLower}Mapper.toDto(${entityNameLower})).thenReturn(${entityNameLower}DTO);
                
                // When
                List<${dtoName}> result = ${entityNameLower}Service.findAll();
                
                // Then
                assertThat(result).isNotEmpty();
                assertThat(result).hasSize(1);
                assertThat(result.get(0)).isEqualTo(${entityNameLower}DTO);
                
                verify(${entityNameLower}Repository).findAll();
                verify(${entityNameLower}Mapper).toDto(${entityNameLower});
            }
            
        """.trimIndent())

        // Test findById method
        methods.append("""
            @Test
            @DisplayName("Should find ${entityNameLower} by id")
            void shouldFind${entityName}ById() {
                // Given
                ${idType} id = 1L;
                when(${entityNameLower}Repository.findById(id)).thenReturn(Optional.of(${entityNameLower}));
                when(${entityNameLower}Mapper.toDto(${entityNameLower})).thenReturn(${entityNameLower}DTO);
                
                // When
                Optional<${dtoName}> result = ${entityNameLower}Service.findById(id);
                
                // Then
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo(${entityNameLower}DTO);
                
                verify(${entityNameLower}Repository).findById(id);
                verify(${entityNameLower}Mapper).toDto(${entityNameLower});
            }
            
        """.trimIndent())

        // Test save method
        methods.append("""
            @Test
            @DisplayName("Should save ${entityNameLower}")
            void shouldSave${entityName}() {
                // Given
                when(${entityNameLower}Mapper.toEntity(${entityNameLower}DTO)).thenReturn(${entityNameLower});
                when(${entityNameLower}Repository.save(${entityNameLower})).thenReturn(${entityNameLower});
                when(${entityNameLower}Mapper.toDto(${entityNameLower})).thenReturn(${entityNameLower}DTO);
                
                // When
                ${dtoName} result = ${entityNameLower}Service.save(${entityNameLower}DTO);
                
                // Then
                assertThat(result).isEqualTo(${entityNameLower}DTO);
                
                verify(${entityNameLower}Mapper).toEntity(${entityNameLower}DTO);
                verify(${entityNameLower}Repository).save(${entityNameLower});
                verify(${entityNameLower}Mapper).toDto(${entityNameLower});
            }
            
        """.trimIndent())

        // Test delete method
        methods.append("""
            @Test
            @DisplayName("Should delete ${entityNameLower}")
            void shouldDelete${entityName}() {
                // Given
                ${idType} id = 1L;
                
                // When
                ${entityNameLower}Service.deleteById(id);
                
                // Then
                verify(${entityNameLower}Repository).deleteById(id);
            }
            
        """.trimIndent())

        // Helper methods for creating test objects
        methods.append("""
            private ${entityName} create${entityName}() {
                ${entityName} entity = new ${entityName}();
                // Set test data for entity fields
        """.trimIndent())

        // Generate field setters for the entity
        entityMetadata.fields.forEach { field ->
            if (!field.isCollection && field.name != "id") {
                when (field.simpleTypeName) {
                    "String" -> methods.append("\n        entity.set${field.name.replaceFirstChar { it.uppercase() }}(\"test${field.name}\");")
                    "Integer", "int" -> methods.append("\n        entity.set${field.name.replaceFirstChar { it.uppercase() }}(1);")
                    "Long", "long" -> methods.append("\n        entity.set${field.name.replaceFirstChar { it.uppercase() }}(1L);")
                    "Boolean", "boolean" -> methods.append("\n        entity.set${field.name.replaceFirstChar { it.uppercase() }}(true);")
                    "Double", "double" -> methods.append("\n        entity.set${field.name.replaceFirstChar { it.uppercase() }}(1.0);")
                    "Float", "float" -> methods.append("\n        entity.set${field.name.replaceFirstChar { it.uppercase() }}(1.0f);")
                    else -> methods.append("\n        // entity.set${field.name.replaceFirstChar { it.uppercase() }}(/* set appropriate test value */);")
                }
            }
        }

        methods.append("""
                return entity;
            }
            
            private ${dtoName} create${entityName}DTO() {
                ${dtoName} dto = new ${dtoName}();
                // Set test data for DTO fields
        """.trimIndent())

        // Generate field setters for the DTO
        entityMetadata.fields.forEach { field ->
            if (!field.isCollection && field.name != "id") {
                when (field.simpleTypeName) {
                    "String" -> methods.append("\n        dto.set${field.name.replaceFirstChar { it.uppercase() }}(\"test${field.name}\");")
                    "Integer", "int" -> methods.append("\n        dto.set${field.name.replaceFirstChar { it.uppercase() }}(1);")
                    "Long", "long" -> methods.append("\n        dto.set${field.name.replaceFirstChar { it.uppercase() }}(1L);")
                    "Boolean", "boolean" -> methods.append("\n        dto.set${field.name.replaceFirstChar { it.uppercase() }}(true);")
                    "Double", "double" -> methods.append("\n        dto.set${field.name.replaceFirstChar { it.uppercase() }}(1.0);")
                    "Float", "float" -> methods.append("\n        dto.set${field.name.replaceFirstChar { it.uppercase() }}(1.0f);")
                    else -> methods.append("\n        // dto.set${field.name.replaceFirstChar { it.uppercase() }}(/* set appropriate test value */);")
                }
            }
        }

        methods.append("""
                return dto;
            }
        """.trimIndent())

        return methods.toString()
    }

    /**
     * Generate custom methods for the test.
     */
    private fun generateCustomMethods(entityMetadata: EntityMetadata): String {
        // Return empty string for now, can be expanded later
        return ""
    }
}

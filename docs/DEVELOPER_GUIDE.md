# Developer Documentation

This document provides technical details about the Spring Boot Code Generator plugin architecture, internal APIs, and extension points. It's intended for developers who want to understand the plugin internals or contribute to the project.

## Architecture Overview

The Spring Boot Code Generator plugin follows a modular architecture with several key components:

```
com.enokdev.springapigenerator/
├── action/         # IntelliJ action handlers
├── generator/      # Code generation components
│   └── impl/       # Specific generator implementations
├── model/          # Data model classes
│   └── db/         # Database schema model classes
├── service/        # Core services for entity analysis
└── ui/             # User interface components
```

### Core Components

1. **Entity Detection and Analysis**
   - `EntityDetectionService`: Detects JPA entities in the project
   - `EntityAnalyzer`: Analyzes entity structure, fields, and relationships

2. **Code Generation**
   - `AbstractTemplateCodeGenerator`: Base class for all template-based generators
   - Specific generators for Controller, Service, Repository, etc.

3. **Database Reverse Engineering**
   - `DatabaseConnectionService`: Manages database connections
   - `SchemaExtractor`: Extracts database schema information
   - `EntityFromSchemaGenerator`: Converts database tables to JPA entities

4. **User Interface**
   - `GenerateSpringCodeAction`: Entry point from the IDE UI
   - `GeneratorConfigDialog`: Configuration dialog for code generation options

## Data Flow

1. **User Initiates Generation**
   - User right-clicks on an entity file and selects "Generate Spring REST Code"
   - `GenerateSpringCodeAction` processes this request

2. **Entity Analysis**
   - `EntityDetectionService` verifies the selected class is a valid JPA entity
   - `EntityAnalyzer` extracts metadata about the entity
   - Results are stored in an `EntityMetadata` object

3. **User Configuration**
   - `GeneratorConfigDialog` displays options to the user
   - User selects desired components and packages
   - Configuration is passed to generators

4. **Code Generation**
   - Each selected generator produces code from templates
   - Templates are processed with FreeMarker
   - Generated code is written to appropriate locations in the project

## Internal APIs

### Entity Detection API

```kotlin
class EntityDetectionService(private val project: Project) {
    // Check if a class is a JPA entity
    fun isJpaEntity(psiClass: PsiClass): Boolean
    
    // Check if a class extends AbstractEntity
    fun extendsAbstractEntity(psiClass: PsiClass): Boolean
    
    // Find all JPA entities in the project
    fun findAllJpaEntities(): List<PsiClass>
}
```

### Entity Analysis API

```kotlin
class EntityAnalyzer {
    // Analyze entity class and extract metadata
    fun analyzeEntity(psiClass: PsiClass): EntityMetadata
}
```

### Code Generation API

```kotlin
interface CodeGenerator {
    // Generate code based on entity metadata
    fun generate(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String
    
    // Get the target file path for the generated code
    fun getTargetFilePath(project: Project, entityMetadata: EntityMetadata, packageConfig: Map<String, String>): String
}
```

### Database Connection API

```kotlin
class DatabaseConnectionService(private val project: Project) {
    // Create database connection
    fun createConnection(type: DatabaseType, host: String, port: Int, database: String, 
                         username: String, password: String): Connection?
    
    // Test database connection
    fun testConnection(type: DatabaseType, host: String, port: Int, database: String, 
                      username: String, password: String): Boolean
}
```

## Template System

The plugin uses FreeMarker templates (`.ft` files) located in `/src/main/resources/templates/`. Each component type has its own template:

- `Controller.java.ft`
- `Service.java.ft`
- `ServiceImpl.java.ft`
- `Repository.java.ft`
- `DTO.java.ft`
- `Mapper.java.ft`
- `Test.java.ft`
- `AbstractEntity.java.ft`
- `SwaggerConfig.java.ft`
- `GlobalExceptionHandler.java.ft`
- `RelationshipController.java.ft`

### Template Variables

Templates have access to these common variables:

| Variable | Description |
|----------|-------------|
| `className` | Entity class name |
| `entityName` | Entity class name (alias) |
| `entityNameLower` | Lowercase version of entity name |
| `packageName` | Base package for generated code |
| `fields` | List of entity fields |
| `idType` | Type of the entity ID field |
| `tableName` | Database table name |

Component-specific templates have additional variables.

## Extending the Plugin

### Adding a New Generator

To create a new code generator:

1. Create a class extending `AbstractTemplateCodeGenerator`
2. Override `getTargetFilePath()` to specify output location
3. Override `createDataModel()` to provide template data
4. Create a template file in `/src/main/resources/templates/`
5. Register your generator in the main workflow

Example:

```kotlin
class MyCustomGenerator : AbstractTemplateCodeGenerator("MyCustom.java.ft") {
    override fun getTargetFilePath(project: Project, entityMetadata: EntityMetadata, 
                                 packageConfig: Map<String, String>): String {
        val sourceRoot = getSourceRootDir(project)
        val packageDir = entityMetadata.customPackage.replace(".", "/")
        val fileName = "${entityMetadata.className}Custom.java"
        return Paths.get(sourceRoot, packageDir, fileName).toString()
    }
    
    override fun createDataModel(entityMetadata: EntityMetadata, 
                               packageConfig: Map<String, String>): MutableMap<String, Any> {
        val model = super.createDataModel(entityMetadata, packageConfig)
        model["customProperty"] = "customValue"
        return model
    }
}
```

### Customizing Templates

To customize the templates:

1. Create a copy of the original template
2. Modify it according to your needs
3. Place it in the configured templates directory
4. Update the template path in your generator

## Testing

The plugin includes several types of tests:

1. **Unit Tests** - Test individual components in isolation
2. **Integration Tests** - Test the interaction between components
3. **Plugin Tests** - Test the plugin in a real IDE environment

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test category
./gradlew test --tests "com.enokdev.springapigenerator.generator.*"
```

## Building and Debugging

### Building the Plugin

```bash
# Build plugin
./gradlew buildPlugin

# Run IDE with plugin installed
./gradlew runIde
```

### Debugging the Plugin

1. Run the plugin in debug mode: `./gradlew runIde --debug-jvm`
2. Connect your IDE debugger to the running process
3. Set breakpoints in your plugin code

## Common Issues and Solutions

### PSI Element Access

When working with IntelliJ's PSI (Program Structure Interface):

- Always check for `null` values
- Use read actions for PSI element access:
  ```kotlin
  ApplicationManager.getApplication().runReadAction {
      // PSI access code
  }
  ```
- Be aware of the PSI element's validity before using it

### Template Processing

- Handle exceptions from template processing
- Ensure all variables used in templates are provided
- Use FreeMarker's built-in directives for conditional logic

## Contribution Guidelines

### Code Style

The project follows the Kotlin coding conventions. Configure your IDE to use:

- 4 spaces for indentation
- Line length limit of 120 characters
- Kotlin code style from `.editorconfig`

### Pull Request Process

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run existing tests
6. Submit a pull request with a clear description

### Commit Message Format

Use conventional commit messages:

```
type(scope): subject

body

footer
```

Types include: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

## License and Legal

This project is licensed under the MIT License. Contributions must adhere to this license.

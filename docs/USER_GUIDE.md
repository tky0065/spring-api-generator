# User Guide

This guide provides detailed instructions on how to use the Spring Boot Code Generator plugin to accelerate your Spring Boot development workflow.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Generating Code from Entities](#generating-code-from-entities)
3. [Reverse Engineering from Database](#reverse-engineering-from-database)
4. [Customizing Generated Code](#customizing-generated-code)
5. [Advanced Features](#advanced-features)
6. [Best Practices](#best-practices)
7. [Troubleshooting](#troubleshooting)

## Getting Started

### Prerequisites

Before using the plugin, ensure that:
- You have a Spring Boot project open in IntelliJ IDEA
- Your project includes JPA dependencies
- You have at least one JPA entity class defined or access to a database

### Basic Workflow

The plugin offers two primary workflows:
1. **Forward Engineering**: Generate Spring Boot components from existing JPA entities
2. **Reverse Engineering**: Create JPA entities from database schema, then generate components

## Generating Code from Entities

### Step 1: Prepare Your Entity

Ensure your entity is properly annotated with JPA annotations:

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String username;
    private String email;
    
    // Getters and setters
}
```

### Step 2: Invoke the Generator

1. Open the entity file in the editor
2. Right-click anywhere in the editor
3. Select "Generate Spring REST Code" from the context menu

### Step 3: Configure Generation Options

In the dialog that appears:

1. **Select Components**: Check the components you want to generate:
   - Controller
   - Service
   - Repository
   - DTO
   - Mapper
   - Tests

2. **Configure Packages**: Set package names for each component type:
   - Base Package: `com.example.myapp`
   - Controller Package: `com.example.myapp.controller`
   - Service Package: `com.example.myapp.service`
   - Repository Package: `com.example.myapp.repository`
   - DTO Package: `com.example.myapp.dto`
   - Mapper Package: `com.example.myapp.mapper`

3. Click "Generate" to create the selected components

### Step 4: Review Generated Code

The plugin will generate the following files (if selected):

- `UserController.java`: REST controller with CRUD endpoints
- `UserService.java`: Service interface
- `UserServiceImpl.java`: Service implementation
- `UserRepository.java`: Spring Data JPA repository
- `UserDTO.java`: Data Transfer Object
- `UserMapper.java`: MapStruct mapper interface
- `UserServiceTest.java`: Unit tests for the service

## Reverse Engineering from Database

### Step 1: Connect to Database

1. Right-click on your project in the Project panel
2. Select "Spring Boot Code Generator" → "Generate from Database"
3. Enter database connection details:
   - Database Type: MySQL, PostgreSQL, or H2
   - Host: Database server hostname
   - Port: Database server port
   - Database Name: Name of the database
   - Username: Database username
   - Password: Database password
4. Click "Test Connection" to verify connectivity

### Step 2: Select Tables

1. After successful connection, you'll see a list of tables from the database
2. Select the tables you want to convert to JPA entities
3. Configure entity generation options:
   - Entity Package: Package where entities will be created
   - Naming Strategy: Table-to-class naming convention
   - Relationship Detection: Auto-detect relations between tables

### Step 3: Generate Entities

1. Click "Generate Entities" to create JPA entity classes
2. Review the generated entity classes
3. If needed, proceed with generating Spring components from these entities

## Customizing Generated Code

### Template Customization

The plugin uses templates that can be customized:

1. Go to Settings → Tools → Spring Boot Code Generator → Templates
2. Select the template you want to customize
3. Modify the template content
4. Click "Apply" to save changes

### Custom Annotations

You can configure the plugin to add custom annotations:

1. Go to Settings → Tools → Spring Boot Code Generator → Annotations
2. Add custom annotations for each component type
3. Click "Apply" to save changes

## Advanced Features

### Relationship Management

For entities with relationships, the plugin generates:

- Special endpoints for managing relationships
- DTO structures that handle related entities
- Services methods for relationship operations

Example for a `User` with many `Orders`:

```java
// In UserController
@PostMapping("/{id}/orders/{orderId}")
public ResponseEntity<Void> addOrderToUser(@PathVariable Long id, @PathVariable Long orderId) {
    userService.addOrder(id, orderId);
    return ResponseEntity.ok().build();
}
```

### Swagger Documentation

The plugin generates Swagger/OpenAPI documentation for your API:

1. A `SwaggerConfig.java` class with API information
2. Swagger annotations on controller methods
3. Documentation for request/response models

### Global Exception Handling

The generated code includes a global exception handler with:

- HTTP status code mapping for different exception types
- Consistent error response format
- Validation error handling

## Best Practices

For optimal results with the plugin:

1. **Entity Design**: Design your entities carefully before generation
2. **Package Structure**: Use a consistent package structure
3. **Template Customization**: Customize templates to match your team's coding standards
4. **Selective Generation**: Only generate components you need
5. **Code Review**: Always review generated code before committing

## Troubleshooting

### Common Issues

**Q: The plugin doesn't detect my entity**
A: Ensure your class has both `@Entity` and `@Id` annotations

**Q: Generated code has compilation errors**
A: Check if all required dependencies are in your project (Spring Web, Spring Data JPA, Lombok, MapStruct)

**Q: My database tables aren't showing up**
A: Verify connection details and ensure your user has permissions to access the schema

**Q: The plugin is slow with large databases**
A: Select only the tables you need rather than the entire database

### Getting Help

If you encounter issues not covered here:

1. Check the project's [GitHub Issues](https://github.com/enokdev/spring-api-generator/issues)
2. Submit a detailed bug report with steps to reproduce
3. Contact support at support@enokdev.com

# Spring Boot Code Generator - Publication Assets

## Marketplace Description

```html
<h1>Spring Boot Code Generator</h1>

<p>Generate complete Spring Boot REST APIs from JPA entities or database schemas with a single click.</p>

<h2>Why Use This Plugin?</h2>

<p>Building Spring Boot applications often involves writing repetitive boilerplate code across multiple layers. This plugin automates the creation of complete API structures following best practices, allowing you to focus on business logic instead of CRUD operations.</p>

<h2>Key Features</h2>

<h3>🚀 Complete API Generation</h3>
<p>Generate all components of a Spring Boot application layer stack:</p>
<ul>
  <li>REST controllers with CRUD operations</li>
  <li>Service interfaces and implementations</li>
  <li>Spring Data JPA repositories</li>
  <li>Data Transfer Objects with validation</li>
  <li>MapStruct mappers for entity-DTO conversion</li>
  <li>JUnit 5 tests with Mockito for all components</li>
</ul>

<h3>🔄 Database Reverse Engineering</h3>
<p>Connect to existing databases and generate:</p>
<ul>
  <li>JPA entity classes from table structures</li>
  <li>Annotations for columns, relationships, and constraints</li>
  <li>Complete API stack from the extracted entities</li>
</ul>

<h3>🔗 Relationship Management</h3>
<p>Special handling for JPA relationships:</p>
<ul>
  <li>Dedicated endpoints for managing entity relationships</li>
  <li>Support for OneToMany, ManyToOne, OneToOne, and ManyToMany</li>
  <li>Proper DTO structures for related entities</li>
</ul>

<h3>📝 API Documentation</h3>
<ul>
  <li>Auto-generated Swagger/OpenAPI configuration</li>
  <li>API documentation annotations on endpoints</li>
  <li>Comprehensive API metadata and descriptions</li>
</ul>

<h3>❌ Error Handling</h3>
<ul>
  <li>Global exception handler with proper HTTP status codes</li>
  <li>Validation error handling with detailed responses</li>
  <li>Resource not found handling</li>
</ul>

<h2>Quick Start</h2>

<h3>From JPA Entities:</h3>
<ol>
  <li>Right-click on a JPA entity class</li>
  <li>Select "Generate Spring REST Code"</li>
  <li>Choose the components to generate</li>
  <li>Configure package names</li>
  <li>Click "Generate"</li>
</ol>

<h3>From Database:</h3>
<ol>
  <li>Right-click on your project</li>
  <li>Select "Spring Boot Code Generator" → "Generate from Database"</li>
  <li>Enter database connection details</li>
  <li>Select tables to include</li>
  <li>Click "Generate"</li>
</ol>

<h2>Compatibility</h2>

<ul>
  <li>IntelliJ IDEA 2023.1 or newer (Community or Ultimate)</li>
  <li>Spring Boot 2.x and 3.x projects</li>
  <li>Java 8, 11, 17, or 21</li>
  <li>Support for MySQL, PostgreSQL, and H2 databases</li>
</ul>

<h2>Benefits</h2>

<ul>
  <li><b>Productivity:</b> Reduce boilerplate code by up to 80%</li>
  <li><b>Consistency:</b> Enforce standardized patterns across your application</li>
  <li><b>Quality:</b> Generate well-tested code that follows best practices</li>
  <li><b>Focus:</b> Concentrate on business logic rather than repetitive CRUD operations</li>
</ul>

<h2>Documentation and Support</h2>

<p>For documentation, examples, and support, visit our <a href="https://github.com/enokdev/spring-api-generator">GitHub repository</a>.</p>
```

## Screenshots for Marketplace

The following screenshots should be prepared for the JetBrains Marketplace:

### 1. Main Plugin Action
Screenshot showing the plugin action in the context menu when right-clicking on a JPA entity.

### 2. Configuration Dialog
Screenshot of the configuration dialog where users can select components to generate and configure package names.

### 3. Generated Code Structure
Screenshot showing the structure of generated code in the Project view.

### 4. Database Connection Dialog
Screenshot of the database connection dialog for reverse engineering.

### 5. Example API Usage
Screenshot showing the generated API endpoints being tested in a tool like Postman.

## Plugin Logo

The plugin logo has been created as an SVG file at `src/main/resources/META-INF/pluginIcon.svg`. It uses Spring green and blue colors with code and API symbols.

## Marketplace Keywords

Primary keywords:
- spring boot
- code generation
- jpa
- rest api
- spring mvc
- database
- dto
- swagger
- mapstruct

## JetBrains Marketplace Category

Primary category: Code Generation

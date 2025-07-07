# Screenshots Plan for JetBrains Marketplace

This document outlines the screenshots we need to create for the JetBrains Marketplace listing of our Spring API Generator plugin.

## Current Screenshots
We already have two screenshots in the `docs/images` folder:
- `generate-from-entity.png`: Shows the process of generating Spring Boot code from a JPA entity
- `generate-from-db.png`: Shows the database connection and entity generation interface

## Additional Screenshots Needed

### 1. Configuration Dialog (Priority: High)
- **Content**: Show the `GeneratorConfigDialog` with all options expanded
- **Value**: Demonstrates the flexibility of the plugin and customization options
- **Caption**: "Customize the components you want to generate and their target packages"

### 2. Generated Code Example (Priority: High)
- **Content**: Split-screen showing an entity on one side and generated controller/service on the other
- **Value**: Shows the quality and structure of generated code
- **Caption**: "From a simple entity to a complete REST API with a single click"

### 3. Advanced Configuration Options (Priority: Medium)
- **Content**: Show the advanced settings tab with relationship handling options
- **Value**: Highlights advanced features for developers with complex domain models
- **Caption**: "Fine-tune how relationships are handled in your generated API"

### 4. Test Generation (Priority: Medium)
- **Content**: Show the generated test classes with high test coverage
- **Value**: Emphasizes the quality assurance aspect of the plugin
- **Caption**: "Automatically generated JUnit 5 tests with Mockito for all components"

### 5. Swagger Integration (Priority: Medium)
- **Content**: Show the generated Swagger UI with the API endpoints
- **Value**: Demonstrates the documentation capabilities
- **Caption**: "Automatically documented APIs with Swagger/OpenAPI"

### 6. Project Structure Before/After (Priority: High)
- **Content**: Side-by-side comparison of project structure before and after generation
- **Value**: Visual impact of how the plugin organizes and creates files
- **Caption**: "Transform your project with a complete layered architecture in seconds"

## Screenshot Requirements
- **Resolution**: At least 1280x800 pixels
- **Format**: PNG with transparent background where appropriate
- **Theme**: Use IntelliJ light theme for better visibility
- **Content**: Ensure no sensitive information is visible
- **Text**: Make sure text is readable at the final display size

## Process for Creating Screenshots
1. Set up a sample Spring Boot project with 2-3 JPA entities
2. Configure IntelliJ to use the light theme
3. Install the latest version of our plugin
4. Capture each scenario listed above
5. Edit images to highlight key areas if necessary
6. Create a new folder at `docs/images/marketplace` if it doesn't exist
7. Save screenshots in the `docs/images/marketplace` folder with descriptive filenames:
   - `config-dialog.png` - For the configuration dialog
   - `generated-code-example.png` - For the code generation example
   - `advanced-config.png` - For advanced configuration options
   - `test-generation.png` - For test generation example
   - `swagger-integration.png` - For Swagger UI example
   - `project-structure-comparison.png` - For the before/after comparison

## Final Checklist
- [ ] All screenshots are high resolution
- [ ] Text is readable in all images
- [ ] No sensitive/proprietary information is visible
- [ ] All major features are represented
- [ ] Screenshots show a consistent visual style
- [ ] Filenames are descriptive and follow convention

# Guide des Conventions de Nommage - Spring API Generator

## 📋 Vue d'ensemble

Ce document décrit les conventions de nommage standardisées adoptées dans le Spring API Generator après la correction systématique des bugs. Ces conventions garantissent la cohérence et la prévisibilité du code généré.

## 🏗️ Structure des Packages

### **Structure Standard**
```
com.example.project
├── entity/          # Entités JPA
├── dto/             # Data Transfer Objects
├── repository/      # Repositories Spring Data
├── service/         # Interfaces de service
│   └── impl/        # Implémentations de service
├── controller/      # Controllers REST
├── mapper/          # Mappers (MapStruct)
└── config/          # Classes de configuration
```

### **Règles de Package**
- **Base Package** : `com.{company}.{project}`
- **Entity Package** : `{basePackage}.entity`
- **DTO Package** : `{basePackage}.dto`
- **Repository Package** : `{basePackage}.repository`
- **Service Package** : `{basePackage}.service`
- **Service Impl Package** : `{basePackage}.service.impl`
- **Controller Package** : `{basePackage}.controller`
- **Mapper Package** : `{basePackage}.mapper`

## 🎯 Conventions de Nommage des Classes

### **Entités JPA**
- **Format** : `{EntityName}`
- **Exemple** : `User`, `Product`, `OrderItem`
- **Package** : `{basePackage}.entity`

### **DTOs (Data Transfer Objects)**
- **Format** : `{EntityName}DTO`
- **Exemple** : `UserDTO`, `ProductDTO`, `OrderItemDTO`
- **Package** : `{basePackage}.dto`

### **Repositories**
- **Format** : `{EntityName}Repository`
- **Exemple** : `UserRepository`, `ProductRepository`
- **Package** : `{basePackage}.repository`
- **Extension** : `extends JpaRepository<{EntityName}, {IdType}>`

### **Services**
- **Interface Format** : `{EntityName}Service`
- **Implementation Format** : `{EntityName}ServiceImpl`
- **Exemple** : `UserService`, `UserServiceImpl`
- **Packages** : 
  - Interface : `{basePackage}.service`
  - Implementation : `{basePackage}.service.impl`

### **Controllers**
- **REST Format** : `{EntityName}Controller`
- **GraphQL Format** : `{EntityName}GraphQLController`
- **Exemple** : `UserController`, `UserGraphQLController`
- **Package** : `{basePackage}.controller`

### **Mappers**
- **Format** : `{EntityName}Mapper`
- **Exemple** : `UserMapper`, `ProductMapper`
- **Package** : `{basePackage}.mapper`
- **Annotation** : `@Mapper(componentModel = "spring")`

## 🔤 Conventions de Nommage des Variables

### **Variables d'Instance**
- **Entity Variable** : `{entityNameLower}` (ex: `user`, `product`)
- **DTO Variable** : `{entityNameLower}DTO` (ex: `userDTO`, `productDTO`)
- **Service Variable** : `{entityNameLower}Service` (ex: `userService`)
- **Repository Variable** : `{entityNameLower}Repository` (ex: `userRepository`)
- **Mapper Variable** : `{entityNameLower}Mapper` (ex: `userMapper`)

### **Paramètres de Méthode**
- **Entity Parameter** : `{entityNameLower}` 
- **DTO Parameter** : `{entityNameLower}DTO`
- **ID Parameter** : `id` (toujours Long)

## 🛣️ Conventions d'API REST

### **Base Path**
- **Format** : `/api/{entityNameLower}s`
- **Exemple** : `/api/users`, `/api/products`
- **Règle Pluriel** : 
  - Standard : `{entityName}s`
  - Terminaison en 'y' : `{entityName[0..-2]}ies` (ex: `Category` → `categories`)

### **Endpoints Standards**
```
GET    /api/{entities}           # Liste avec pagination
GET    /api/{entities}/{id}      # Récupérer par ID
POST   /api/{entities}           # Créer
PUT    /api/{entities}/{id}      # Mettre à jour
PATCH  /api/{entities}/{id}      # Mise à jour partielle
DELETE /api/{entities}/{id}      # Supprimer
```

## 📊 Conventions GraphQL

### **Types**
- **Entity Type** : `{EntityName}`
- **Input Create** : `Create{EntityName}Input`
- **Input Update** : `Update{EntityName}Input`
- **Page Type** : `{EntityName}Page`

### **Queries**
- **Get One** : `{entityNameLower}(id: ID!): {EntityName}`
- **Get Many** : `{entityNameLower}s(...): {EntityName}Page!`
- **Search** : `search{EntityName}s(...): {EntityName}Page!`
- **Count** : `count{EntityName}s: Int!`

### **Mutations**
- **Create** : `create{EntityName}(input: Create{EntityName}Input!): {EntityName}!`
- **Update** : `update{EntityName}(id: ID!, input: Update{EntityName}Input!): {EntityName}!`
- **Delete** : `delete{EntityName}(id: ID!): Boolean!`

## 🔧 Conventions Techniques

### **Imports Standards**
- **Validation** : `jakarta.validation.constraints.*`
- **JPA** : `jakarta.persistence.*`
- **Swagger** : `io.swagger.v3.oas.annotations.*`

### **Annotations Standards**

#### **Java**
```java
// DTO
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

// Service Implementation
@Service
@Transactional

// Repository
@Repository

// Controller
@RestController
@RequestMapping("/api/{entities}")
@Tag(name = "{EntityName}s", description = "The {EntityName} API")
```

#### **Kotlin**
```kotlin
// DTO
data class {EntityName}DTO(...)

// Service Implementation
@Service
@Transactional
class {EntityName}ServiceImpl(...)

// Repository
@Repository
interface {EntityName}Repository : JpaRepository<{EntityName}, Long>

// Controller
@RestController
@RequestMapping("/api/{entities}")
@Tag(name = "{EntityName}s", description = "The {EntityName} API")
class {EntityName}Controller(...)
```

## 🎨 Conventions de Style

### **Java**
- **Classes** : PascalCase (`UserService`)
- **Méthodes** : camelCase (`findByEmail`)
- **Variables** : camelCase (`userRepository`)
- **Constantes** : UPPER_SNAKE_CASE (`ENTITY_NAME`)

### **Kotlin**
- **Classes** : PascalCase (`UserService`)
- **Fonctions** : camelCase (`findByEmail`)
- **Propriétés** : camelCase (`userRepository`)
- **Constantes** : UPPER_SNAKE_CASE (`ENTITY_NAME`)

## 📝 Conventions de Documentation

### **Commentaires JavaDoc/KDoc**
```java
/**
 * {Description courte de la méthode}.
 *
 * @param {param} {description du paramètre}
 * @return {description du retour}
 */
```

### **Comments GraphQL**
```graphql
# {Description du type/champ}
type {EntityName} {
    # {Description du champ}
    {fieldName}: {FieldType}
}
```

## ✅ Validation des Conventions

### **Checklist de Validation**
- [ ] Tous les packages suivent la structure standardisée
- [ ] Aucun doublon de package (ex: `.dto.dto`)
- [ ] Tous les imports utilisent Jakarta au lieu de javax
- [ ] Swagger v3 utilisé partout
- [ ] Noms de classes cohérents
- [ ] Variables nommées selon les conventions
- [ ] API REST suit les patterns RESTful
- [ ] GraphQL suit les conventions modernes

### **Outils de Validation**
- Tests automatisés de génération de code
- Vérification de compilation
- Analyse statique des imports
- Validation de la structure des packages

---

**Note** : Ces conventions sont appliquées automatiquement par le Spring API Generator. Pour toute modification, référez-vous au code source des templates dans `src/main/resources/templates/`.

# Guide des Options de Génération Conditionnelle

## 📋 Vue d'ensemble

Le Spring API Generator offre une génération conditionnelle flexible permettant aux utilisateurs de sélectionner précisément quelles fonctionnalités inclure dans leur projet. Cette approche évite la génération de code inutile et permet une personnalisation fine selon les besoins du projet.

## ⚙️ Options de Composants de Base

### **Composants Principaux**
Ces composants forment le cœur de l'architecture Spring Boot et sont généralement tous sélectionnés :

| Option | Description | Template | Dépendances |
|--------|-------------|----------|-------------|
| **Controller** | Contrôleurs REST avec endpoints CRUD | `Controller.java.ft` / `Controller.kt.ft` | Spring Web |
| **Service** | Interfaces et implémentations de services | `Service.java.ft` / `ServiceImpl.java.ft` | Spring Core |
| **Repository** | Repositories Spring Data JPA | `Repository.java.ft` / `Repository.kt.ft` | Spring Data JPA |
| **DTO** | Data Transfer Objects avec validation | `DTO.java.ft` / `DTO.kt.ft` | Jakarta Validation |
| **Mapper** | Mappers MapStruct pour conversions | `Mapper.java.ft` / `Mapper.kt.ft` | MapStruct |
| **Tests** | Tests unitaires JUnit 5 | `Test.java.ft` / `Test.kt.ft` | JUnit 5, Mockito |

### **Configuration dans l'Interface**
```
☑️ Controller
☑️ Service  
☑️ DTO
☑️ Repository
☑️ Mapper
☑️ Tests
```

## 🔧 Options de Fonctionnalités Avancées

### **Documentation API**

#### **Option : Use Swagger/OpenAPI**
- **Description** : Génère la documentation API automatique
- **Templates affectés** : Tous les Controllers
- **Variable FreeMarker** : `enableSwagger`
- **Annotations ajoutées** :
  ```java
  @Tag(name = "Users", description = "The User API")
  @Operation(summary = "Create user")
  @ApiResponse(responseCode = "201", description = "User created")
  ```

#### **Option : Use OpenAPI 3.0 Documentation**
- **Description** : Configuration OpenAPI 3.0 complète
- **Template généré** : `OpenApiConfig.java.ft`
- **Variable FreeMarker** : `enableOpenApi`
- **Condition** : `<#if enableOpenApi?? && enableOpenApi>`

### **Sécurité**

#### **Option : Add Spring Security**
- **Description** : Intégration complète Spring Security avec JWT
- **Templates générés** :
  - `SpringSecurityConfig.java.ft`
  - `JwtUtil.java.ft`
  - `AuthController.java.ft`
- **Variable FreeMarker** : `enableSecurity`
- **Condition** : `<#if enableSecurity?? && enableSecurity>`
- **Fonctionnalités** :
  - Authentification JWT
  - Endpoints sécurisés
  - Gestion des rôles
  - Configuration CORS

### **GraphQL**

#### **Option : Add GraphQL Support**
- **Description** : API GraphQL complète avec schémas et resolvers
- **Templates générés** :
  - `GraphQLSchema.graphqls.ft`
  - `GraphQLController.java.ft`
  - `GraphQLConfig.java.ft`
- **Variable FreeMarker** : `enableGraphQL`
- **Condition** : `<#if enableGraphQL?? && enableGraphQL>`
- **Fonctionnalités** :
  - Schémas GraphQL typés
  - Queries et Mutations
  - Pagination intégrée
  - Scalars personnalisés (Date, DateTime)

### **Repository Avancé**

#### **Option : Generate Custom Query Methods**
- **Description** : Méthodes de recherche personnalisées dans les repositories
- **Template affecté** : `Repository.java.ft` / `Repository.kt.ft`
- **Variable FreeMarker** : `enableCustomQueryMethods`
- **Méthodes générées** :
  ```java
  List<User> findByName(String name);
  List<User> findByNameContaining(String name);
  Page<User> searchName(@Param("name") String name, Pageable pageable);
  ```

## 🎯 Combinaisons Recommandées

### **Configuration Minimale**
```
✅ Controller
✅ Service
✅ DTO
✅ Repository
❌ Mapper
❌ Tests
❌ Swagger
❌ Security
❌ GraphQL
❌ Custom Query Methods
```
**Usage** : Prototypage rapide, MVP

### **Configuration Standard**
```
✅ Controller
✅ Service
✅ DTO
✅ Repository
✅ Mapper
✅ Tests
✅ Swagger
❌ Security
❌ GraphQL
✅ Custom Query Methods
```
**Usage** : Développement d'API REST standard

### **Configuration Complète**
```
✅ Controller
✅ Service
✅ DTO
✅ Repository
✅ Mapper
✅ Tests
✅ Swagger
✅ Security
✅ GraphQL
✅ Custom Query Methods
```
**Usage** : Applications d'entreprise, APIs publiques

### **Configuration API Publique**
```
✅ Controller
✅ Service
✅ DTO
✅ Repository
✅ Mapper
✅ Tests
✅ OpenAPI 3.0
✅ Security
❌ GraphQL
✅ Custom Query Methods
```
**Usage** : APIs publiques avec documentation complète

## 🔍 Variables FreeMarker Utilisées

### **Variables de Fonctionnalités**
```freemarker
${enableSwagger}          // Active les annotations Swagger
${enableOpenApi}          // Génère OpenApiConfig
${enableSecurity}         // Génère SpringSecurityConfig
${enableGraphQL}          // Génère GraphQLConfig et schemas
${enableCustomQueryMethods} // Ajoute méthodes custom aux repositories
${enableMapstruct}        // Utilise MapStruct pour les mappers
${enableTests}            // Génère les classes de test
```

### **Variables de Configuration**
```freemarker
${apiTitle}               // Titre de l'API
${apiDescription}         // Description de l'API
${apiVersion}             // Version de l'API
${apiContact}             // Contact API
${apiLicense}             // Licence API
```

## 🛠️ Personnalisation des Templates

### **Ajouter une Nouvelle Option**

1. **Ajouter la checkbox dans l'UI** :
```kotlin
private val enableMyFeatureCheckbox = JBCheckBox("Enable My Feature", false)
```

2. **Ajouter la méthode de récupération** :
```kotlin
fun shouldEnableMyFeature(): Boolean {
    return enableMyFeatureCheckbox.isSelected
}
```

3. **Utiliser dans le template** :
```freemarker
<#if enableMyFeature?? && enableMyFeature>
// Code spécifique à la fonctionnalité
</#if>
```

### **Conditions Complexes**
```freemarker
<#if enableSecurity?? && enableSecurity && enableSwagger?? && enableSwagger>
// Code qui nécessite à la fois Security ET Swagger
</#if>
```

## 📊 Impact sur les Dépendances

### **Dépendances Automatiques**
Le plugin gère automatiquement l'ajout des dépendances selon les options sélectionnées :

| Option | Maven | Gradle |
|--------|-------|--------|
| **Swagger** | `springdoc-openapi-starter-webmvc-ui:2.8.9` | `implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9'` |
| **MapStruct** | `mapstruct:1.6.3` + `mapstruct-processor` | `implementation 'org.mapstruct:mapstruct:1.6.3'` |
| **Security** | `spring-boot-starter-security` + JWT | `implementation 'org.springframework.boot:spring-boot-starter-security'` |
| **GraphQL** | `spring-boot-starter-graphql` | `implementation 'org.springframework.boot:spring-boot-starter-graphql'` |

### **Détection Automatique**
Le plugin détecte automatiquement les dépendances existantes et adapte l'interface :
- ✅ **Détecté** : Option grisée avec message "Already detected"
- ❌ **Non détecté** : Option disponible avec suggestion d'ajout

## 🧪 Tests de Validation

### **Tests Automatiques**
Le système inclut des tests pour valider chaque combinaison :

```kotlin
@Test
fun testBasicComponentsOnly() // Test composants de base uniquement
@Test  
fun testWithSwaggerEnabled() // Test avec Swagger activé
@Test
fun testAllFeaturesEnabled() // Test toutes fonctionnalités
@Test
fun testMinimalConfiguration() // Test configuration minimale
```

### **Validation Runtime**
- **Compilation** : Vérification que le code généré compile
- **Imports** : Validation des imports selon les options
- **Annotations** : Présence des annotations appropriées
- **Structure** : Cohérence de l'architecture générée

## 🎯 Bonnes Pratiques

### **Recommandations**
1. **Commencer minimal** : Sélectionner d'abord les composants de base
2. **Ajouter progressivement** : Intégrer les fonctionnalités avancées selon les besoins
3. **Tester les combinaisons** : Valider que le code généré compile et fonctionne
4. **Documenter les choix** : Noter les options sélectionnées pour l'équipe

### **Éviter**
- Activer toutes les options sans réflexion
- Mélanger des technologies incompatibles
- Générer du code non utilisé
- Ignorer les dépendances suggérées

---

Cette documentation assure que les utilisateurs comprennent parfaitement les options disponibles et peuvent faire des choix éclairés selon leurs besoins de projet.

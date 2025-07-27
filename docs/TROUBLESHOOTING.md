# Guide de Dépannage - Spring API Generator

## 🚨 Problèmes Courants et Solutions

Ce guide présente les problèmes les plus fréquents rencontrés avec le Spring API Generator et leurs solutions, basé sur les corrections apportées lors de la phase de débogage.

## 📦 Problèmes de Packages

### **❌ Problème : Packages dupliqués**
```
com.enokdev.demospringapigen.mapper.mapper.UserMapper
com.enokdev.demospringapigen.dto.dto.UserDTO
```

**🔧 Solution :**
- Les packages sont maintenant générés avec `packageName.replace('.layer', '.targetLayer')`
- Vérifiez que la configuration des packages dans l'interface utilisateur est correcte
- Les doublons ont été éliminés dans tous les générateurs

### **❌ Problème : Structure de packages incohérente**
```
entity/User.java dans repository package
dto/UserDTO.java dans service package
```

**🔧 Solution :**
- Utilisez la structure standardisée : `{basePackage}.{layer}`
- Vérifiez la configuration dans l'onglet "Packages" du dialogue de génération
- Chaque composant a maintenant son package dédié

## 🔗 Problèmes d'Imports

### **❌ Problème : Imports javax au lieu de jakarta**
```java
import javax.validation.constraints.NotNull;
import javax.persistence.Entity;
```

**🔧 Solution :**
- Tous les templates utilisent maintenant `jakarta.validation.constraints.*`
- Imports JPA : `jakarta.persistence.*`
- Ces corrections sont automatiques dans la nouvelle version

### **❌ Problème : Imports Swagger v2 obsolètes**
```java
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
```

**🔧 Solution :**
- Utilisez Swagger v3 : `io.swagger.v3.oas.annotations.*`
- Templates mis à jour avec les nouvelles annotations
- Meilleure intégration avec Spring Boot 3.x

### **❌ Problème : Imports incorrects entre layers**
```java
import com.example.repository.entity.User; // ❌ Incorrect
```

**🔧 Solution :**
```java
import com.example.entity.User; // ✅ Correct
```
- Les générateurs utilisent maintenant la structure de packages correcte
- Imports automatiquement corrigés

## 🏗️ Problèmes de Templates

### **❌ Problème : Erreurs FreeMarker dans DTO**
```
${fields} non défini
Erreur de parsing template
```

**🔧 Solution :**
- Variables FreeMarker normalisées : `${dtoFields}`, `${importStatements}`
- Templates Java et Kotlin corrigés
- Validation des variables avant génération

### **❌ Problème : Syntaxe Kotlin incorrecte**
```kotlin
// ❌ Incorrect
class UserDTO {
    var id: Long? = null
    var name: String? = null
}
```

**🔧 Solution :**
```kotlin
// ✅ Correct
data class UserDTO(
    val id: Long?,
    val name: String,
    val email: String? = null
)
```
- Templates Kotlin corrigés pour utiliser `data class`
- Propriétés `val` avec types nullables appropriés

## 🔧 Problèmes de Génération

### **❌ Problème : ServiceImpl manquants**
```
UserService généré mais pas UserServiceImpl
```

**🔧 Solution :**
- ServiceImpl maintenant généré automatiquement
- Templates Java et Kotlin disponibles
- Injection de dépendances correcte

### **❌ Problème : Classes sans visibilité public en Java**
```java
class UserController { // ❌ Package-private
```

**🔧 Solution :**
```java
public class UserController { // ✅ Public
```
- Tous les templates Java incluent maintenant `public`
- Classes, interfaces et méthodes avec visibilité appropriée

### **❌ Problème : Custom Query Methods incontrôlables**
```java
// Toujours générées même si non désirées
List<User> findByNameContaining(String name);
```

**🔧 Solution :**
- Nouvelle checkbox "Generate Custom Query Methods" dans l'interface
- Contrôle granulaire de la génération
- Option dans l'onglet "Components"

## 🌐 Problèmes GraphQL

### **❌ Problème : Schéma GraphQL basique**
```graphql
type User {
    id: ID
    name: String
}
```

**🔧 Solution :**
```graphql
type User {
    id: ID!
    name: String!
    email: String
}

type UserPage {
    content: [User!]!
    totalElements: Int!
    totalPages: Int!
}
```
- Schéma GraphQL enrichi avec pagination
- Types Input distincts pour Create/Update
- Scalars personnalisés (Date, DateTime, BigDecimal)

## 🧪 Problèmes de Tests

### **❌ Problème : Pas de validation du code généré**
```
Code généré non testé
Erreurs de compilation non détectées
```

**🔧 Solution :**
- Tests automatisés créés dans `/src/test/`
- Validation de compilation
- Tests de cohérence entre composants
- Vérification des imports et packages

## ⚙️ Problèmes de Configuration

### **❌ Problème : Fonctionnalités générées même si non sélectionnées**
```java
// SwaggerConfig généré même sans Swagger
@Configuration
public class SwaggerConfig {
```

**🔧 Solution :**
- Templates conditionnels avec FreeMarker
- Vérification des options utilisateur
- Génération basée sur les sélections

### **❌ Problème : Dépendances manquantes**
```xml
<!-- MapStruct manquant malgré génération de Mapper -->
```

**🔧 Solution :**
- Détection automatique des dépendances existantes
- Suggestions d'ajout de dépendances
- Support Maven et Gradle

## 🔍 Diagnostic Rapide

### **Vérifications de Base**
1. **Packages** : Vérifiez la structure dans l'onglet "Packages"
2. **Imports** : Recherchez `javax` et remplacez par `jakarta`
3. **Templates** : Vérifiez la version des templates dans `/resources/templates/`
4. **Compilation** : Lancez les tests dans `/src/test/`

### **Commandes de Diagnostic**
```bash
# Vérifier la structure des packages
find . -name "*.java" -exec grep -l "javax.validation" {} \;

# Vérifier les imports Swagger
find . -name "*.java" -exec grep -l "io.swagger.annotations" {} \;

# Lancer les tests de validation
./gradlew test
```

## 🛠️ Solutions par Type de Projet

### **Projet Java + Maven**
```xml
<!-- Ajoutez dans pom.xml -->
<dependencies>
    <dependency>
        <groupId>jakarta.validation</groupId>
        <artifactId>jakarta.validation-api</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.8.9</version>
    </dependency>
</dependencies>
```

### **Projet Kotlin + Gradle**
```kotlin
dependencies {
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
}
```

## 📞 Support et Assistance

### **Avant de Signaler un Bug**
1. Vérifiez ce guide de dépannage
2. Consultez les conventions de nommage
3. Lancez les tests de validation
4. Vérifiez la version du plugin

### **Informations à Fournir**
- Version du Spring API Generator
- Type de projet (Java/Kotlin, Maven/Gradle)
- Configuration des packages utilisée
- Message d'erreur complet
- Code généré problématique

### **Logs Utiles**
- IntelliJ IDEA : `Help > Show Log in Explorer`
- Plugin logs : Recherchez "spring-api-generator" dans les logs
- Erreurs de compilation : Output de build

---

**📋 Checklist de Dépannage Rapide**
- [ ] Structure de packages correcte ?
- [ ] Imports Jakarta au lieu de javax ?
- [ ] Swagger v3 au lieu de v2 ?
- [ ] Classes publiques en Java ?
- [ ] Data classes en Kotlin ?
- [ ] ServiceImpl générés ?
- [ ] Options utilisateur respectées ?
- [ ] Tests de validation OK ?

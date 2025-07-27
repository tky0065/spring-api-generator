# Exemples de Code Généré - Spring API Generator

Ce document présente des exemples du code généré par le Spring API Generator après les corrections de bugs. Ces exemples illustrent les meilleures pratiques et la structure attendue.

## 📋 Entité Exemple

### **User.java (Entité JPA)**
```java
package com.example.demo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @Size(min = 2, max = 50)
    @Column(name = "name", nullable = false)
    private String name;
    
    @NotNull
    @Email
    @Column(name = "email", unique = true, nullable = false)
    private String email;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // Constructors, getters, setters...
}
```

## 🔄 DTO (Data Transfer Object)

### **UserDTO.java**
```java
package com.example.demo.dto;

import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * A DTO for the {@link com.example.demo.entity.User} entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull
    private String name;

    @NotNull
    @Email
    private String email;

    private LocalDateTime createdAt;

    // Lombok génère automatiquement les getters, setters, equals, hashCode et toString
}
```

### **UserDTO.kt (Kotlin)**
```kotlin
package com.example.demo.dto

import jakarta.validation.constraints.*
import java.time.LocalDateTime

/**
 * A DTO for the {@link com.example.demo.entity.User} entity.
 */
data class UserDTO(
    val id: Long?,
    @NotNull val name: String,
    @NotNull @Email val email: String,
    val createdAt: LocalDateTime? = null
) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
```

## 🗄️ Repository

### **UserRepository.java**
```java
package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Find by exact name match
    List<User> findByName(String name);

    // Find by name containing the given string
    List<User> findByNameContaining(String name);

    // Find by name ignoring case
    List<User> findByNameIgnoreCase(String name);

    // Custom query to find by name with pagination
    @Query("SELECT u FROM User u WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<User> searchName(@Param("name") String name, Pageable pageable);

    // Find by exact email match
    Optional<User> findByEmail(String email);

    // Example of a method with pagination
    Page<User> findAll(Pageable pageable);
}
```

## 🔧 Service

### **UserService.java**
```java
package com.example.demo.service;

import com.example.demo.dto.UserDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

/**
 * Service Interface for managing User.
 */
public interface UserService {

    /**
     * Save a user.
     */
    UserDTO save(UserDTO userDTO);

    /**
     * Update a user.
     */
    UserDTO update(UserDTO userDTO);

    /**
     * Partially update a user.
     */
    Optional<UserDTO> partialUpdate(UserDTO userDTO);

    /**
     * Get all the users.
     */
    List<UserDTO> findAll();

    /**
     * Get all the users with pagination.
     */
    Page<UserDTO> findAll(Pageable pageable);

    /**
     * Get the "id" user.
     */
    Optional<UserDTO> findOne(Long id);

    /**
     * Delete the "id" user.
     */
    void delete(Long id);

    /**
     * Count total number of users.
     */
    long count();

    /**
     * Check if a user exists by id.
     */
    boolean existsById(Long id);

    /**
     * Find a user by email.
     */
    Optional<UserDTO> findByEmail(String email);
}
```

### **UserServiceImpl.java**
```java
package com.example.demo.service.impl;

import com.example.demo.entity.User;
import com.example.demo.dto.UserDTO;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import com.example.demo.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service Implementation for managing User.
 */
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public UserDTO save(UserDTO userDTO) {
        log.debug("Request to save User : {}", userDTO);
        User user = userMapper.toEntity(userDTO);
        user = userRepository.save(user);
        return userMapper.toDto(user);
    }

    @Override
    public UserDTO update(UserDTO userDTO) {
        log.debug("Request to update User : {}", userDTO);
        User user = userMapper.toEntity(userDTO);
        user = userRepository.save(user);
        return userMapper.toDto(user);
    }

    @Override
    public Optional<UserDTO> partialUpdate(UserDTO userDTO) {
        log.debug("Request to partially update User : {}", userDTO);

        return userRepository
            .findById(userDTO.getId())
            .map(existingUser -> {
                userMapper.partialUpdate(existingUser, userDTO);
                return existingUser;
            })
            .map(userRepository::save)
            .map(userMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> findAll() {
        log.debug("Request to get all Users");
        return userRepository.findAll().stream()
            .map(userMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserDTO> findAll(Pageable pageable) {
        log.debug("Request to get all Users with pagination");
        return userRepository.findAll(pageable)
            .map(userMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDTO> findOne(Long id) {
        log.debug("Request to get User : {}", id);
        return userRepository.findById(id)
            .map(userMapper::toDto);
    }

    @Override
    public void delete(Long id) {
        log.debug("Request to delete User : {}", id);
        userRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        log.debug("Request to count Users");
        return userRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        log.debug("Request to check if User exists : {}", id);
        return userRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDTO> findByEmail(String email) {
        log.debug("Request to find User by email : {}", email);
        return userRepository.findByEmail(email)
            .map(userMapper::toDto);
    }
}
```

## 🌐 Controller REST

### **UserController.java**
```java
package com.example.demo.controller;

import com.example.demo.dto.UserDTO;
import com.example.demo.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * REST Controller for managing User entities.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "The User API")
public class UserController {

    private final Logger log = LoggerFactory.getLogger(UserController.class);
    private static final String ENTITY_NAME = "user";
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /api/users : Create a new user.
     */
    @PostMapping
    @Operation(summary = "Create a new user", description = "Create a new user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody UserDTO userDTO) throws URISyntaxException {
        log.debug("REST request to save User : {}", userDTO);
        if (userDTO.getId() != null) {
            return ResponseEntity.badRequest().build();
        }
        UserDTO result = userService.save(userDTO);
        return ResponseEntity
            .created(new URI("/api/users/" + result.getId()))
            .body(result);
    }

    /**
     * PUT /api/users/:id : Updates an existing user.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a user", description = "Update an existing user")
    public ResponseEntity<UserDTO> updateUser(
        @Parameter(description = "ID of the user to update") @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody UserDTO userDTO
    ) {
        log.debug("REST request to update User : {}, {}", id, userDTO);
        if (userDTO.getId() == null || !Objects.equals(id, userDTO.getId())) {
            return ResponseEntity.badRequest().build();
        }

        if (!userService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        UserDTO result = userService.update(userDTO);
        return ResponseEntity.ok().body(result);
    }

    /**
     * GET /api/users : get all the users.
     */
    @GetMapping
    @Operation(summary = "Get all users", description = "Get all users with pagination")
    @ApiResponse(responseCode = "200", description = "Successful operation")
    public ResponseEntity<List<UserDTO>> getAllUsers(@Parameter(hidden = true) Pageable pageable) {
        log.debug("REST request to get a page of Users");
        Page<UserDTO> page = userService.findAll(pageable);
        return ResponseEntity.ok().body(page.getContent());
    }

    /**
     * GET /api/users/:id : get the "id" user.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a user by ID", description = "Get a specific user by its ID")
    public ResponseEntity<UserDTO> getUser(@Parameter(description = "ID of the user to retrieve") @PathVariable Long id) {
        log.debug("REST request to get User : {}", id);
        Optional<UserDTO> userDTO = userService.findOne(id);
        return userDTO
            .map(response -> ResponseEntity.ok().body(response))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/users/:id : delete the "id" user.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user", description = "Delete a user by its ID")
    public ResponseEntity<Void> deleteUser(@Parameter(description = "ID of the user to delete") @PathVariable Long id) {
        log.debug("REST request to delete User : {}", id);
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

## 🔗 Mapper

### **UserMapper.java**
```java
package com.example.demo.mapper;

import com.example.demo.entity.User;
import com.example.demo.dto.UserDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity User and its DTO UserDTO.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDTO toDto(User user);

    User toEntity(UserDTO userDTO);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void partialUpdate(@MappingTarget User entity, UserDTO dto);
}
```

## 🌐 GraphQL

### **schema.graphqls**
```graphql
# GraphQL Schema for User
# Generated by Spring API Generator Plugin

# Custom scalars for better type safety
scalar Date
scalar DateTime
scalar BigDecimal
scalar UUID

# User type definition
type User {
    id: ID!
    name: String!
    email: String!
    createdAt: DateTime
}

# Input type for creating a new User
input CreateUserInput {
    name: String!
    email: String!
}

# Input type for updating an existing User
input UpdateUserInput {
    name: String
    email: String
}

# Root Query type
type Query {
    # Get a single User by ID
    user(id: ID!): User
    
    # Get all Users with pagination
    users(
        page: Int = 0
        size: Int = 20
        sort: String = "id"
        direction: SortDirection = ASC
    ): UserPage!
    
    # Search Users by criteria
    searchUsers(
        query: String
        page: Int = 0
        size: Int = 20
    ): UserPage!
    
    # Count total Users
    countUsers: Int!
}

# Root Mutation type
type Mutation {
    # Create a new User
    createUser(input: CreateUserInput!): User!
    
    # Update an existing User
    updateUser(id: ID!, input: UpdateUserInput!): User!
    
    # Delete a User
    deleteUser(id: ID!): Boolean!
}

# Pagination wrapper for User
type UserPage {
    content: [User!]!
    totalElements: Int!
    totalPages: Int!
    size: Int!
    number: Int!
    first: Boolean!
    last: Boolean!
}

# Sort direction enum
enum SortDirection {
    ASC
    DESC
}
```

### **UserGraphQLController.java**
```java
package com.example.demo.controller;

import com.example.demo.dto.UserDTO;
import com.example.demo.service.UserService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

/**
 * GraphQL Controller for User entity.
 */
@Controller
public class UserGraphQLController {

    private final UserService userService;

    public UserGraphQLController(UserService userService) {
        this.userService = userService;
    }

    @QueryMapping
    public UserDTO user(@Argument Long id) {
        return userService.findOne(id).orElse(null);
    }

    @QueryMapping
    public UserPage users(
            @Argument int page,
            @Argument int size,
            @Argument String sort,
            @Argument String direction) {
        
        Sort.Direction sortDirection = "DESC".equalsIgnoreCase(direction) 
            ? Sort.Direction.DESC 
            : Sort.Direction.ASC;
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        Page<UserDTO> pageResult = userService.findAll(pageRequest);
        
        return UserPage.builder()
            .content(pageResult.getContent())
            .totalElements((int) pageResult.getTotalElements())
            .totalPages(pageResult.getTotalPages())
            .size(pageResult.getSize())
            .number(pageResult.getNumber())
            .first(pageResult.isFirst())
            .last(pageResult.isLast())
            .build();
    }

    @MutationMapping
    public UserDTO createUser(@Argument CreateUserInput input) {
        UserDTO dto = mapCreateInputToDTO(input);
        return userService.save(dto);
    }

    // Helper classes and methods...
}
```

## 📝 Points Clés

### **✅ Bonnes Pratiques Appliquées**
- **Packages cohérents** : Structure standardisée
- **Imports Jakarta** : Migration javax → jakarta
- **Swagger v3** : Annotations modernes
- **Data classes Kotlin** : Syntaxe appropriée
- **ServiceImpl complets** : Implémentations robustes
- **Validation** : Annotations appropriées
- **Logging** : SLF4J avec messages contextuels
- **GraphQL moderne** : Types et pagination

### **🔧 Fonctionnalités Générées**
- CRUD complet REST et GraphQL
- Pagination et tri
- Validation des données
- Mapping automatique
- Gestion des erreurs
- Documentation API intégrée
- Tests de validation inclus

---

Ces exemples représentent la qualité et la structure du code généré après les corrections de bugs. Chaque composant respecte les conventions modernes de Spring Boot et les meilleures pratiques de développement.

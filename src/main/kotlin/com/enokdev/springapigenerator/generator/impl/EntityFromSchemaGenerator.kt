package com.enokdev.springapigenerator.generator.impl

import com.enokdev.springapigenerator.model.db.Column
import com.enokdev.springapigenerator.model.db.ForeignKey
import com.enokdev.springapigenerator.model.db.Table
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Generator for JPA entities from database schema.
 */
class EntityFromSchemaGenerator(private val project: Project) {

    /**
     * Generate JPA entity classes from database tables.
     *
     * @param tables List of database tables
     * @param basePackage Base package for generated entities
     * @return Map of generated file paths to their contents
     */
    fun generateEntities(tables: List<Table>, basePackage: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        tables.forEach { table ->
            val entityCode = generateEntityCode(table, basePackage)
            val filePath = getEntityFilePath(table.entityName, basePackage)
            result[filePath] = entityCode
        }

        return result
    }

    /**
     * Get the file path for an entity class.
     *
     * @param entityName Name of the entity
     * @param basePackage Base package for the entity
     * @return Full file path for the entity
     */
    private fun getEntityFilePath(entityName: String, basePackage: String): String {
        val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")
        val isKotlin = isKotlinProject(project)
        val extension = if (isKotlin) "kt" else "java"
        val srcPath = if (isKotlin) "src/main/kotlin" else "src/main/java"
        val packagePath = basePackage.replace(".", "/")

        return "$basePath/$srcPath/$packagePath/entity/$entityName.$extension"
    }

    /**
     * Generate entity class code.
     *
     * @param table Database table
     * @param basePackage Base package for generated entity
     * @return Entity class code
     */
    private fun generateEntityCode(table: Table, basePackage: String): String {
        val entityName = table.entityName
        val tableName = table.name
        val isKotlin = isKotlinProject(project)

        val code = StringBuilder()

        // Package declaration
        if (isKotlin) {
            code.append("package $basePackage.entity\n\n")

            // Imports for Kotlin
            code.append("import jakarta.persistence.*\n")
            code.append("import jakarta.validation.constraints.*\n")
            code.append("import java.time.LocalDateTime\n")
            code.append("import java.util.*\n\n")

            // Entity annotation and class declaration
            code.append("@Entity\n")
            code.append("@Table(name = \"$tableName\")\n")
            code.append("data class $entityName(\n")

            // Generate fields for Kotlin
            val fields = generateKotlinFields(table)
            code.append(fields)

            code.append(")")
        } else {
            code.append("package $basePackage.entity;\n\n")

            // Imports for Java
            code.append("import jakarta.persistence.*;\n")
            code.append("import jakarta.validation.constraints.*;\n")
            code.append("import java.time.LocalDateTime;\n")
            code.append("import java.util.*;\n\n")

            // Entity annotation and class declaration
            code.append("@Entity\n")
            code.append("@Table(name = \"$tableName\")\n")
            code.append("public class $entityName {\n\n")

            // Generate fields for Java
            val fields = generateJavaFields(table)
            code.append(fields)

            // Generate getters and setters for Java
            val gettersSetters = generateJavaGettersSetters(table)
            code.append(gettersSetters)

            code.append("}")
        }

        return code.toString()
    }

    /**
     * Generate Kotlin fields from table columns.
     */
    private fun generateKotlinFields(table: Table): String {
        val fields = StringBuilder()

        table.columns.forEachIndexed { index, column ->
            // Check if this column is a primary key
            val isPrimaryKey = table.primaryKeyColumns.contains(column.name)

            // Add primary key annotation if it's an ID column
            if (isPrimaryKey) {
                fields.append("    @Id\n")
                if (column.autoIncrement) {
                    fields.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n")
                }
            }

            // Add column annotation
            fields.append("    @Column(name = \"${column.name}\"")
            if (!column.nullable) {
                fields.append(", nullable = false")
            }
            if (column.size > 0) {
                fields.append(", length = ${column.size}")
            }
            fields.append(")\n")

            // Add validation annotations
            if (!column.nullable && column.sqlTypeName != "Boolean") {
                fields.append("    @NotNull\n")
            }

            // Field declaration
            val kotlinType = mapDbTypeToKotlinType(column.sqlTypeName, column.nullable)
            val defaultValue = if (column.nullable) " = null" else ""
            fields.append("    val ${toCamelCase(column.name)}: $kotlinType$defaultValue")

            if (index < table.columns.size - 1) {
                fields.append(",")
            }
            fields.append("\n")
            if (index < table.columns.size - 1) {
                fields.append("\n")
            }
        }

        return fields.toString()
    }

    /**
     * Generate Java fields from table columns.
     */
    private fun generateJavaFields(table: Table): String {
        val fields = StringBuilder()

        table.columns.forEach { column ->
            // Check if this column is a primary key
            val isPrimaryKey = table.primaryKeyColumns.contains(column.name)

            // Add primary key annotation if it's an ID column
            if (isPrimaryKey) {
                fields.append("    @Id\n")
                if (column.autoIncrement) {
                    fields.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n")
                }
            }

            // Add column annotation
            fields.append("    @Column(name = \"${column.name}\"")
            if (!column.nullable) {
                fields.append(", nullable = false")
            }
            if (column.size > 0) {
                fields.append(", length = ${column.size}")
            }
            fields.append(")\n")

            // Add validation annotations
            if (!column.nullable && column.sqlTypeName != "Boolean") {
                fields.append("    @NotNull\n")
            }

            // Field declaration
            val javaType = mapDbTypeToJavaType(column.sqlTypeName, column.nullable)
            fields.append("    private $javaType ${toCamelCase(column.name)};\n\n")
        }

        return fields.toString()
    }

    /**
     * Generate Java getters and setters.
     */
    private fun generateJavaGettersSetters(table: Table): String {
        val methods = StringBuilder()

        table.columns.forEach { column ->
            val fieldName = toCamelCase(column.name)
            val capitalizedFieldName = fieldName.replaceFirstChar { it.uppercase() }
            val javaType = mapDbTypeToJavaType(column.sqlTypeName, column.nullable)

            // Getter
            methods.append("    public $javaType get$capitalizedFieldName() {\n")
            methods.append("        return $fieldName;\n")
            methods.append("    }\n\n")

            // Setter
            methods.append("    public void set$capitalizedFieldName($javaType $fieldName) {\n")
            methods.append("        this.$fieldName = $fieldName;\n")
            methods.append("    }\n\n")
        }

        return methods.toString()
    }

    /**
     * Map database types to Kotlin types.
     */
    private fun mapDbTypeToKotlinType(dbType: String, nullable: Boolean): String {
        val baseType = when (dbType.uppercase()) {
            "VARCHAR", "CHAR", "TEXT", "LONGTEXT", "MEDIUMTEXT" -> "String"
            "INT", "INTEGER" -> "Int"
            "BIGINT" -> "Long"
            "DECIMAL", "NUMERIC", "DOUBLE" -> "Double"
            "FLOAT", "REAL" -> "Float"
            "BOOLEAN", "BOOL", "BIT" -> "Boolean"
            "DATE" -> "java.time.LocalDate"
            "DATETIME", "TIMESTAMP" -> "java.time.LocalDateTime"
            "TIME" -> "java.time.LocalTime"
            "BLOB", "LONGBLOB" -> "ByteArray"
            "UUID" -> "java.util.UUID"
            else -> "String"
        }

        return if (nullable && baseType != "String") "$baseType?" else baseType
    }

    /**
     * Map database types to Java types.
     */
    private fun mapDbTypeToJavaType(dbType: String, nullable: Boolean): String {
        return when (dbType.uppercase()) {
            "VARCHAR", "CHAR", "TEXT", "LONGTEXT", "MEDIUMTEXT" -> "String"
            "INT", "INTEGER" -> if (nullable) "Integer" else "int"
            "BIGINT" -> if (nullable) "Long" else "long"
            "DECIMAL", "NUMERIC", "DOUBLE" -> if (nullable) "Double" else "double"
            "FLOAT", "REAL" -> if (nullable) "Float" else "float"
            "BOOLEAN", "BOOL", "BIT" -> if (nullable) "Boolean" else "boolean"
            "DATE" -> "java.time.LocalDate"
            "DATETIME", "TIMESTAMP" -> "java.time.LocalDateTime"
            "TIME" -> "java.time.LocalTime"
            "BLOB", "LONGBLOB" -> "byte[]"
            "UUID" -> "java.util.UUID"
            else -> "String"
        }
    }

    /**
     * Convert snake_case to camelCase.
     */
    private fun toCamelCase(snakeCase: String): String {
        return snakeCase.split("_").mapIndexed { index, part ->
            if (index == 0) part.lowercase() else part.lowercase().replaceFirstChar { it.uppercase() }
        }.joinToString("")
    }

    /**
     * Check if the project is a Kotlin project.
     */
    private fun isKotlinProject(project: Project): Boolean {
        // Simple check: look for Kotlin files in the project
        val basePath = project.basePath ?: return false
        val srcPath = Paths.get(basePath, "src", "main", "kotlin")
        return Files.exists(srcPath)
    }

    /**
     * Write generated entities to files.
     *
     * @param generatedEntities Map of file paths to entity code
     * @return List of created file paths
     */
    fun writeEntitiesToFiles(generatedEntities: Map<String, String>): List<String> {
        val createdFiles = mutableListOf<String>()

        generatedEntities.forEach { (filePath, content) ->
            val path = Path.of(filePath)
            Files.createDirectories(path.parent)
            Files.writeString(
                path,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            createdFiles.add(filePath)
        }

        return createdFiles
    }
}

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
            val filePath = getEntityFilePath(project, table.entityName, basePackage)
            result[filePath] = entityCode
        }

        return result
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

        val code = StringBuilder()

        // Package declaration
        code.append("package $basePackage.domain;\n\n")

        // Imports
        val imports = generateImports(table)
        code.append(imports).append("\n")

        // Class javadoc
        code.append("/**\n")
        code.append(" * Entity for the ${table.name} table.\n")
        if (table.comments.isNotEmpty()) {
            code.append(" * ${table.comments}\n")
        }
        code.append(" */\n")

        // Class annotations
        code.append("@Entity\n")
        code.append("@Table(name = \"$tableName\")\n")
        code.append("@Getter\n")
        code.append("@Setter\n")
        code.append("@NoArgsConstructor\n")
        code.append("@AllArgsConstructor\n")
        code.append("public class $entityName extends AbstractEntity<${getPrimaryKeyType(table)}> {\n\n")

        // Fields
        code.append(generateFields(table))

        // ID getter and setter implementation
        val pkColumn = table.getPrimaryKeyColumn()
        if (pkColumn != null) {
            code.append("\n    @Override\n")
            code.append("    public ${pkColumn.javaType} getId() {\n")
            code.append("        return ${pkColumn.fieldName};\n")
            code.append("    }\n\n")

            code.append("    @Override\n")
            code.append("    public void setId(${pkColumn.javaType} id) {\n")
            code.append("        this.${pkColumn.fieldName} = id;\n")
            code.append("    }\n")
        }

        // Close class
        code.append("}\n")

        return code.toString()
    }

    /**
     * Generate entity imports.
     *
     * @param table Database table
     * @return Import statements
     */
    private fun generateImports(table: Table): String {
        val imports = mutableSetOf(
            "javax.persistence.*",
            "lombok.Getter",
            "lombok.Setter",
            "lombok.NoArgsConstructor",
            "lombok.AllArgsConstructor"
        )

        // Add imports for column types
        table.columns.forEach { column ->
            when {
                column.javaType.startsWith("java.") -> imports.add(column.javaType)
            }
        }

        // Sort and join imports
        return imports.sorted().joinToString("\n") { "import $it;" }
    }

    /**
     * Generate entity fields.
     *
     * @param table Database table
     * @return Field declarations
     */
    private fun generateFields(table: Table): String {
        val fields = StringBuilder()

        // Process primary key field
        val pkColumn = table.getPrimaryKeyColumn()
        if (pkColumn != null) {
            fields.append(generateFieldCode(pkColumn, true))
        }

        // Process regular fields
        val regularColumns = table.getNonPrimaryKeyColumns()
        regularColumns.forEach { column ->
            // Check if it's a foreign key
            val foreignKey = table.foreignKeys.find { it.columnName == column.name }
            if (foreignKey != null) {
                fields.append(generateRelationshipField(column, foreignKey))
            } else {
                fields.append(generateFieldCode(column, false))
            }
        }

        return fields.toString()
    }

    /**
     * Generate code for a regular field.
     *
     * @param column Database column
     * @param isPrimaryKey Whether this is a primary key field
     * @return Field declaration
     */
    private fun generateFieldCode(column: Column, isPrimaryKey: Boolean): String {
        val field = StringBuilder()

        // Field comment
        if (column.comments.isNotEmpty()) {
            field.append("    /**\n")
            field.append("     * ${column.comments}\n")
            field.append("     */\n")
        }

        // Field annotations
        if (isPrimaryKey) {
            field.append("    @Id\n")
            if (column.autoIncrement) {
                field.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n")
            }
        }

        field.append("    @Column(name = \"${column.name}\"")
        if (!column.nullable) field.append(", nullable = false")
        if (column.size > 0 && column.javaType == "String") field.append(", length = ${column.size}")
        field.append(")\n")

        // Field declaration
        field.append("    private ${column.javaType} ${column.fieldName};\n\n")

        return field.toString()
    }

    /**
     * Generate code for a relationship field.
     *
     * @param column Foreign key column
     * @param foreignKey Foreign key constraint
     * @return Field declaration
     */
    private fun generateRelationshipField(column: Column, foreignKey: ForeignKey): String {
        val field = StringBuilder()
        val relationType = foreignKey.determineRelationType()
        val fieldName = foreignKey.getRelationshipFieldName()

        // Field comment
        field.append("    /**\n")
        field.append("     * Relationship to ${foreignKey.referenceTable}\n")
        field.append("     */\n")

        // Field annotations
        if (relationType == "ManyToOne") {
            field.append("    @ManyToOne\n")
            field.append("    @JoinColumn(name = \"${column.name}\", referencedColumnName = \"${foreignKey.referenceColumn}\")\n")
        } else if (relationType == "OneToOne") {
            field.append("    @OneToOne\n")
            field.append("    @JoinColumn(name = \"${column.name}\", referencedColumnName = \"${foreignKey.referenceColumn}\")\n")
        }

        // Field declaration
        val targetEntityName = convertToEntityName(foreignKey.referenceTable)
        field.append("    private $targetEntityName $fieldName;\n\n")

        return field.toString()
    }

    /**
     * Get entity file path.
     *
     * @param project IntelliJ project
     * @param entityName Entity class name
     * @param basePackage Base package for entity
     * @return File path
     */
    private fun getEntityFilePath(project: Project, entityName: String, basePackage: String): String {
        val domainPackage = "$basePackage.domain"
        val packagePath = domainPackage.replace(".", "/")

        val projectPath = project.basePath ?: throw RuntimeException("Project path not found")
        val sourcePath = "src/main/java"

        return "$projectPath/$sourcePath/$packagePath/$entityName.java"
    }

    /**
     * Get primary key type.
     *
     * @param table Database table
     * @return Java type for primary key
     */
    private fun getPrimaryKeyType(table: Table): String {
        val pkColumn = table.getPrimaryKeyColumn()
        return pkColumn?.javaType ?: "Long" // Default to Long if no PK found
    }

    /**
     * Convert table name to entity name.
     *
     * @param tableName Table name
     * @return Entity class name
     */
    private fun convertToEntityName(tableName: String): String {
        val parts = tableName.split("_")
        val baseName = parts.joinToString("") {
            it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
        }

        // Simple singularization
        return if (baseName.endsWith("s") && !baseName.endsWith("ss")) {
            baseName.substring(0, baseName.length - 1)
        } else {
            baseName
        }
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

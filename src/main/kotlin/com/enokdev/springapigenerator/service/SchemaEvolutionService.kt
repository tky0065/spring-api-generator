package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.model.EntityField
import com.enokdev.springapigenerator.model.RelationType
import com.intellij.openapi.project.Project
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Service for generating database schema evolution scripts.
 */
class SchemaEvolutionService {

    data class MigrationScript(
        val version: String,
        val description: String,
        val type: MigrationType,
        val content: String,
        val checksum: String? = null,
        val executionTime: LocalDateTime = LocalDateTime.now()
    )

    enum class MigrationType {
        FLYWAY, LIQUIBASE
    }

    data class SchemaChange(
        val changeType: ChangeType,
        val tableName: String,
        val columnName: String? = null,
        val oldValue: String? = null,
        val newValue: String? = null,
        val entityClass: String,
        val description: String
    )

    enum class ChangeType {
        CREATE_TABLE, DROP_TABLE, ADD_COLUMN, DROP_COLUMN,
        MODIFY_COLUMN, RENAME_COLUMN, RENAME_TABLE,
        ADD_INDEX, DROP_INDEX, ADD_CONSTRAINT, DROP_CONSTRAINT
    }

    /**
     * Generates Flyway migration script for entity creation.
     */
    fun generateFlywayMigration(
        entityMetadata: EntityMetadata,
        version: String = generateVersionNumber()
    ): MigrationScript {
        val description = "Create ${entityMetadata.tableName} table"
        val content = generateFlywayCreateTableScript(entityMetadata)

        return MigrationScript(
            version = version,
            description = description,
            type = MigrationType.FLYWAY,
            content = content
        )
    }

    /**
     * Generates Liquibase changeset for entity creation.
     */
    fun generateLiquibaseMigration(
        entityMetadata: EntityMetadata,
        changeSetId: String = generateChangeSetId()
    ): MigrationScript {
        val description = "Create ${entityMetadata.tableName} table"
        val content = generateLiquibaseCreateTableScript(entityMetadata, changeSetId)

        return MigrationScript(
            version = changeSetId,
            description = description,
            type = MigrationType.LIQUIBASE,
            content = content
        )
    }

    /**
     * Generates incremental migration based on entity changes.
     */
    fun generateIncrementalMigration(
        oldEntity: EntityMetadata,
        newEntity: EntityMetadata,
        migrationType: MigrationType = MigrationType.FLYWAY
    ): MigrationScript? {
        val changes = detectSchemaChanges(oldEntity, newEntity)

        if (changes.isEmpty()) return null

        val version = generateVersionNumber()
        val description = "Update ${newEntity.tableName} table - ${changes.size} changes"

        val content = when (migrationType) {
            MigrationType.FLYWAY -> generateFlywayIncrementalScript(changes, newEntity)
            MigrationType.LIQUIBASE -> generateLiquibaseIncrementalScript(changes, newEntity)
        }

        return MigrationScript(
            version = version,
            description = description,
            type = migrationType,
            content = content
        )
    }

    /**
     * Detects schema changes between two entity versions.
     */
    fun detectSchemaChanges(oldEntity: EntityMetadata, newEntity: EntityMetadata): List<SchemaChange> {
        val changes = mutableListOf<SchemaChange>()

        // Check table name changes
        if (oldEntity.tableName != newEntity.tableName) {
            changes.add(SchemaChange(
                changeType = ChangeType.RENAME_TABLE,
                tableName = oldEntity.tableName,
                oldValue = oldEntity.tableName,
                newValue = newEntity.tableName,
                entityClass = newEntity.qualifiedClassName,
                description = "Rename table from ${oldEntity.tableName} to ${newEntity.tableName}"
            ))
        }

        // Check for new fields (add columns)
        val oldFieldNames = oldEntity.fields.map { it.name }.toSet()
        val newFields = newEntity.fields.filter { it.name !in oldFieldNames }

        newFields.forEach { field ->
            changes.add(SchemaChange(
                changeType = ChangeType.ADD_COLUMN,
                tableName = newEntity.tableName,
                columnName = field.columnName ?: field.name,
                newValue = mapFieldTypeToSqlType(field),
                entityClass = newEntity.qualifiedClassName,
                description = "Add column ${field.name} (${field.type})"
            ))
        }

        // Check for removed fields (drop columns)
        val newFieldNames = newEntity.fields.map { it.name }.toSet()
        val removedFields = oldEntity.fields.filter { it.name !in newFieldNames }

        removedFields.forEach { field ->
            changes.add(SchemaChange(
                changeType = ChangeType.DROP_COLUMN,
                tableName = newEntity.tableName,
                columnName = field.columnName ?: field.name,
                oldValue = mapFieldTypeToSqlType(field),
                entityClass = newEntity.qualifiedClassName,
                description = "Drop column ${field.name}"
            ))
        }

        // Check for modified fields
        val commonFields = oldEntity.fields.filter { oldField ->
            newEntity.fields.any { newField -> newField.name == oldField.name }
        }

        commonFields.forEach { oldField ->
            val newField = newEntity.fields.first { it.name == oldField.name }
            if (hasFieldChanged(oldField, newField)) {
                changes.add(SchemaChange(
                    changeType = ChangeType.MODIFY_COLUMN,
                    tableName = newEntity.tableName,
                    columnName = newField.columnName ?: newField.name,
                    oldValue = mapFieldTypeToSqlType(oldField),
                    newValue = mapFieldTypeToSqlType(newField),
                    entityClass = newEntity.qualifiedClassName,
                    description = "Modify column ${newField.name}: ${oldField.type} -> ${newField.type}"
                ))
            }
        }

        return changes
    }

    private fun generateFlywayCreateTableScript(entityMetadata: EntityMetadata): String {
        val script = StringBuilder()

        script.append("-- Create table for ${entityMetadata.className}\n")
        script.append("CREATE TABLE ${entityMetadata.tableName} (\n")

        // Add ID column
        script.append("    id ${mapIdTypeToSql(entityMetadata.idType)} PRIMARY KEY")
        if (entityMetadata.idType.contains("IDENTITY") || entityMetadata.idType.contains("SERIAL")) {
            script.append(" AUTO_INCREMENT")
        }
        script.append(",\n")

        // Add regular columns
        entityMetadata.fields.forEach { field ->
            if (field.relationType == RelationType.NONE) {
                val columnName = field.columnName ?: field.name
                val sqlType = mapFieldTypeToSqlType(field)
                val nullable = if (field.nullable) "" else " NOT NULL"

                script.append("    $columnName $sqlType$nullable,\n")
            }
        }

        // Add audit columns if needed
        if (hasAuditFields(entityMetadata)) {
            script.append("    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n")
            script.append("    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n")
            script.append("    created_by VARCHAR(255),\n")
            script.append("    last_modified_by VARCHAR(255),\n")
        }

        // Add version column if needed
        if (hasVersionField(entityMetadata)) {
            script.append("    version BIGINT DEFAULT 0,\n")
        }

        // Add soft delete columns if needed
        if (hasSoftDeleteFields(entityMetadata)) {
            script.append("    deleted BOOLEAN DEFAULT FALSE,\n")
            script.append("    deleted_at TIMESTAMP NULL,\n")
        }

        // Remove trailing comma and close
        val content = script.toString().trimEnd().removeSuffix(",")
        script.clear()
        script.append(content)
        script.append("\n);\n\n")

        // Add indexes
        script.append(generateIndexes(entityMetadata))

        return script.toString()
    }

    private fun generateLiquibaseCreateTableScript(entityMetadata: EntityMetadata, changeSetId: String): String {
        val script = StringBuilder()

        script.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        script.append("<databaseChangeLog\n")
        script.append("    xmlns=\"http://www.liquibase.org/xml/ns/dbchangelog\"\n")
        script.append("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        script.append("    xsi:schemaLocation=\"http://www.liquibase.org/xml/ns/dbchangelog\n")
        script.append("                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.0.xsd\">\n\n")

        script.append("    <changeSet id=\"$changeSetId\" author=\"spring-api-generator\">\n")
        script.append("        <createTable tableName=\"${entityMetadata.tableName}\">\n")

        // Add ID column
        script.append("            <column name=\"id\" type=\"${mapIdTypeToSql(entityMetadata.idType)}\">\n")
        script.append("                <constraints primaryKey=\"true\" nullable=\"false\"/>\n")
        script.append("            </column>\n")

        // Add regular columns
        entityMetadata.fields.forEach { field ->
            if (field.relationType == RelationType.NONE) {
                val columnName = field.columnName ?: field.name
                val sqlType = mapFieldTypeToSqlType(field)

                script.append("            <column name=\"$columnName\" type=\"$sqlType\">\n")
                if (!field.nullable) {
                    script.append("                <constraints nullable=\"false\"/>\n")
                }
                script.append("            </column>\n")
            }
        }

        // Add audit columns if needed
        if (hasAuditFields(entityMetadata)) {
            script.append("            <column name=\"created_at\" type=\"TIMESTAMP\" defaultValueComputed=\"CURRENT_TIMESTAMP\">\n")
            script.append("                <constraints nullable=\"false\"/>\n")
            script.append("            </column>\n")
            script.append("            <column name=\"updated_at\" type=\"TIMESTAMP\" defaultValueComputed=\"CURRENT_TIMESTAMP\"/>\n")
            script.append("            <column name=\"created_by\" type=\"VARCHAR(255)\"/>\n")
            script.append("            <column name=\"last_modified_by\" type=\"VARCHAR(255)\"/>\n")
        }

        // Add version column if needed
        if (hasVersionField(entityMetadata)) {
            script.append("            <column name=\"version\" type=\"BIGINT\" defaultValue=\"0\"/>\n")
        }

        // Add soft delete columns if needed
        if (hasSoftDeleteFields(entityMetadata)) {
            script.append("            <column name=\"deleted\" type=\"BOOLEAN\" defaultValueBoolean=\"false\"/>\n")
            script.append("            <column name=\"deleted_at\" type=\"TIMESTAMP\"/>\n")
        }

        script.append("        </createTable>\n")
        script.append("    </changeSet>\n\n")
        script.append("</databaseChangeLog>\n")

        return script.toString()
    }

    private fun generateFlywayIncrementalScript(changes: List<SchemaChange>, entityMetadata: EntityMetadata): String {
        val script = StringBuilder()

        script.append("-- Incremental migration for ${entityMetadata.className}\n\n")

        changes.forEach { change ->
            when (change.changeType) {
                ChangeType.ADD_COLUMN -> {
                    script.append("-- ${change.description}\n")
                    script.append("ALTER TABLE ${change.tableName} ADD COLUMN ${change.columnName} ${change.newValue};\n\n")
                }
                ChangeType.DROP_COLUMN -> {
                    script.append("-- ${change.description}\n")
                    script.append("ALTER TABLE ${change.tableName} DROP COLUMN ${change.columnName};\n\n")
                }
                ChangeType.MODIFY_COLUMN -> {
                    script.append("-- ${change.description}\n")
                    script.append("ALTER TABLE ${change.tableName} MODIFY COLUMN ${change.columnName} ${change.newValue};\n\n")
                }
                ChangeType.RENAME_TABLE -> {
                    script.append("-- ${change.description}\n")
                    script.append("RENAME TABLE ${change.oldValue} TO ${change.newValue};\n\n")
                }
                else -> {
                    script.append("-- TODO: Implement ${change.changeType} for ${change.description}\n\n")
                }
            }
        }

        return script.toString()
    }

    private fun generateLiquibaseIncrementalScript(changes: List<SchemaChange>, entityMetadata: EntityMetadata): String {
        val script = StringBuilder()
        val changeSetId = generateChangeSetId()

        script.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        script.append("<databaseChangeLog\n")
        script.append("    xmlns=\"http://www.liquibase.org/xml/ns/dbchangelog\"\n")
        script.append("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        script.append("    xsi:schemaLocation=\"http://www.liquibase.org/xml/ns/dbchangelog\n")
        script.append("                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.0.xsd\">\n\n")

        script.append("    <changeSet id=\"$changeSetId\" author=\"spring-api-generator\">\n")

        changes.forEach { change ->
            when (change.changeType) {
                ChangeType.ADD_COLUMN -> {
                    script.append("        <addColumn tableName=\"${change.tableName}\">\n")
                    script.append("            <column name=\"${change.columnName}\" type=\"${change.newValue}\"/>\n")
                    script.append("        </addColumn>\n")
                }
                ChangeType.DROP_COLUMN -> {
                    script.append("        <dropColumn tableName=\"${change.tableName}\" columnName=\"${change.columnName}\"/>\n")
                }
                ChangeType.MODIFY_COLUMN -> {
                    script.append("        <modifyDataType tableName=\"${change.tableName}\" columnName=\"${change.columnName}\" newDataType=\"${change.newValue}\"/>\n")
                }
                ChangeType.RENAME_TABLE -> {
                    script.append("        <renameTable oldTableName=\"${change.oldValue}\" newTableName=\"${change.newValue}\"/>\n")
                }
                else -> {
                    script.append("        <!-- TODO: Implement ${change.changeType} -->\n")
                }
            }
        }

        script.append("    </changeSet>\n\n")
        script.append("</databaseChangeLog>\n")

        return script.toString()
    }

    private fun generateIndexes(entityMetadata: EntityMetadata): String {
        val script = StringBuilder()

        // Add indexes for foreign key fields
        entityMetadata.fields.forEach { field ->
            if (field.relationType == RelationType.MANY_TO_ONE) {
                val indexName = "idx_${entityMetadata.tableName}_${field.name}"
                val columnName = field.columnName ?: "${field.name}_id"
                script.append("CREATE INDEX $indexName ON ${entityMetadata.tableName} ($columnName);\n")
            }
        }

        // Add common indexes
        if (hasAuditFields(entityMetadata)) {
            script.append("CREATE INDEX idx_${entityMetadata.tableName}_created_at ON ${entityMetadata.tableName} (created_at);\n")
        }

        if (hasSoftDeleteFields(entityMetadata)) {
            script.append("CREATE INDEX idx_${entityMetadata.tableName}_deleted ON ${entityMetadata.tableName} (deleted);\n")
        }

        return script.toString()
    }

    // Helper methods

    private fun generateVersionNumber(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        return "V${LocalDateTime.now().format(formatter)}"
    }

    private fun generateChangeSetId(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        return LocalDateTime.now().format(formatter)
    }

    private fun mapIdTypeToSql(idType: String): String {
        return when {
            idType.contains("UUID") -> "UUID"
            idType.contains("String") -> "VARCHAR(255)"
            idType.contains("Integer") -> "INTEGER"
            else -> "BIGINT"
        }
    }

    private fun mapFieldTypeToSqlType(field: EntityField): String {
        return when (field.type) {
            "String" -> "VARCHAR(255)"
            "Integer", "int" -> "INTEGER"
            "Long", "long" -> "BIGINT"
            "Double", "double" -> "DOUBLE"
            "Float", "float" -> "FLOAT"
            "Boolean", "boolean" -> "BOOLEAN"
            "LocalDateTime" -> "TIMESTAMP"
            "LocalDate" -> "DATE"
            "LocalTime" -> "TIME"
            "BigDecimal" -> "DECIMAL(19,2)"
            "UUID" -> "UUID"
            else -> "VARCHAR(255)" // Default for unknown types
        }
    }

    private fun hasFieldChanged(oldField: EntityField, newField: EntityField): Boolean {
        return oldField.type != newField.type ||
               oldField.nullable != newField.nullable ||
               oldField.columnName != newField.columnName
    }

    private fun hasAuditFields(entityMetadata: EntityMetadata): Boolean {
        return entityMetadata.fields.any {
            it.name.contains("created") || it.name.contains("updated") || it.name.contains("modified")
        }
    }

    private fun hasVersionField(entityMetadata: EntityMetadata): Boolean {
        return entityMetadata.fields.any { it.name == "version" }
    }

    private fun hasSoftDeleteFields(entityMetadata: EntityMetadata): Boolean {
        return entityMetadata.fields.any { it.name == "deleted" || it.name == "deletedAt" }
    }
}

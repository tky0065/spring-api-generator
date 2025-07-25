package com.enokdev.springapigenerator.generator

import com.enokdev.springapigenerator.model.EntityMetadata
import com.enokdev.springapigenerator.service.*
import com.enokdev.springapigenerator.service.SchemaEvolutionService.*
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Paths

/**
 * Generator for database migration scripts.
 */
class SchemaMigrationGenerator(
    private val migrationType: MigrationType = MigrationType.FLYWAY
) {

    private val schemaEvolutionService = SchemaEvolutionService()

    /**
     * Generates migration script for a new entity.
     */
    fun generateEntityMigration(
        project: Project,
        entityMetadata: EntityMetadata,
        packageConfig: Map<String, String>
    ): String {
        return when (migrationType) {
            MigrationType.FLYWAY -> {
                val migration = schemaEvolutionService.generateFlywayMigration(entityMetadata)
                saveMigrationFile(project, migration, entityMetadata)
                migration.content
            }
            MigrationType.LIQUIBASE -> {
                val migration = schemaEvolutionService.generateLiquibaseMigration(entityMetadata)
                saveMigrationFile(project, migration, entityMetadata)
                migration.content
            }
        }
    }

    /**
     * Generates incremental migration when entity changes.
     */
    fun generateIncrementalMigration(
        project: Project,
        oldEntity: EntityMetadata,
        newEntity: EntityMetadata
    ): String? {
        val migration = schemaEvolutionService.generateIncrementalMigration(oldEntity, newEntity, migrationType)

        return if (migration != null) {
            saveMigrationFile(project, migration, newEntity)
            migration.content
        } else {
            null // No changes detected
        }
    }

    /**
     * Saves migration file to the appropriate directory.
     */
    private fun saveMigrationFile(
        project: Project,
        migration: MigrationScript,
        entityMetadata: EntityMetadata
    ) {
        val migrationDir = getMigrationDirectory(project)
        val fileName = generateMigrationFileName(migration, entityMetadata)
        val filePath = Paths.get(migrationDir, fileName)

        // Ensure directory exists
        File(migrationDir).mkdirs()

        // Write migration content
        File(filePath.toString()).writeText(migration.content)
    }

    /**
     * Gets the migration directory based on the migration type.
     */
    private fun getMigrationDirectory(project: Project): String {
        val basePath = project.basePath ?: throw RuntimeException("Project base path not found")

        return when (migrationType) {
            MigrationType.FLYWAY -> Paths.get(basePath, "src", "main", "resources", "db", "migration").toString()
            MigrationType.LIQUIBASE -> Paths.get(basePath, "src", "main", "resources", "db", "changelog").toString()
        }
    }

    /**
     * Generates migration file name based on type and content.
     */
    private fun generateMigrationFileName(migration: MigrationScript, entityMetadata: EntityMetadata): String {
        return when (migrationType) {
            MigrationType.FLYWAY -> {
                "${migration.version}__${migration.description.replace(" ", "_").lowercase()}.sql"
            }
            MigrationType.LIQUIBASE -> {
                "${migration.version}_${entityMetadata.tableName}_migration.xml"
            }
        }
    }

    /**
     * Detects the preferred migration type from project configuration.
     */
    companion object {
        fun detectMigrationType(project: Project): MigrationType {
            val basePath = project.basePath ?: return MigrationType.FLYWAY

            // Check for existing migration directories
            val flywayDir = File(Paths.get(basePath, "src", "main", "resources", "db", "migration").toString())
            val liquibaseDir = File(Paths.get(basePath, "src", "main", "resources", "db", "changelog").toString())

            return when {
                liquibaseDir.exists() -> MigrationType.LIQUIBASE
                flywayDir.exists() -> MigrationType.FLYWAY
                else -> {
                    // Check build files for dependencies
                    val buildGradle = File(basePath, "build.gradle")
                    val buildGradleKts = File(basePath, "build.gradle.kts")
                    val pomXml = File(basePath, "pom.xml")

                    val buildContent = when {
                        buildGradleKts.exists() -> buildGradleKts.readText()
                        buildGradle.exists() -> buildGradle.readText()
                        pomXml.exists() -> pomXml.readText()
                        else -> ""
                    }

                    when {
                        buildContent.contains("liquibase") -> MigrationType.LIQUIBASE
                        buildContent.contains("flyway") -> MigrationType.FLYWAY
                        else -> MigrationType.FLYWAY // Default
                    }
                }
            }
        }
    }
}

package com.enokdev.springapigenerator.service

import com.enokdev.springapigenerator.model.db.Column
import com.enokdev.springapigenerator.model.db.ForeignKey
import com.enokdev.springapigenerator.model.db.Table
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Types

/**
 * Service to extract database schema information.
 */
class SchemaExtractor {

    /**
     * Extract all tables from a database connection.
     *
     * @param connection Active database connection
     * @param catalog Catalog name (can be null)
     * @param schemaPattern Schema pattern (can be null)
     * @param tableNamePattern Table name pattern (can be null)
     * @return List of tables with their columns and relations
     */
    fun extractTables(
        connection: Connection,
        catalog: String? = null,
        schemaPattern: String? = null,
        tableNamePattern: String? = "%"
    ): List<Table> {
        val tables = mutableListOf<Table>()
        val metadata = connection.metaData

        // Get all tables
        val tableTypes = arrayOf("TABLE")
        metadata.getTables(catalog, schemaPattern, tableNamePattern, tableTypes).use { tablesResultSet ->
            while (tablesResultSet.next()) {
                val tableName = tablesResultSet.getString("TABLE_NAME")
                val remarks = tablesResultSet.getString("REMARKS") ?: ""

                val columns = extractColumns(metadata, catalog, schemaPattern, tableName)
                val primaryKeys = extractPrimaryKeys(metadata, catalog, schemaPattern, tableName)

                tables.add(
                    Table(
                        name = tableName,
                        comments = remarks,
                        columns = columns,
                        primaryKeyColumns = primaryKeys
                    )
                )
            }
        }

        // Extract foreign keys for all tables
        tables.forEach { table ->
            table.foreignKeys = extractForeignKeys(metadata, catalog, schemaPattern, table.name)
        }

        return tables
    }

    /**
     * Extract columns for a specific table.
     *
     * @param metadata Database metadata
     * @param catalog Catalog name
     * @param schemaPattern Schema pattern
     * @param tableName Table name
     * @return List of columns
     */
    private fun extractColumns(
        metadata: DatabaseMetaData,
        catalog: String?,
        schemaPattern: String?,
        tableName: String
    ): List<Column> {
        val columns = mutableListOf<Column>()

        metadata.getColumns(catalog, schemaPattern, tableName, null).use { columnsResultSet ->
            while (columnsResultSet.next()) {
                val columnName = columnsResultSet.getString("COLUMN_NAME")
                val dataType = columnsResultSet.getInt("DATA_TYPE")
                val typeName = columnsResultSet.getString("TYPE_NAME")
                val columnSize = columnsResultSet.getInt("COLUMN_SIZE")
                val decimalDigits = columnsResultSet.getInt("DECIMAL_DIGITS")
                val nullable = columnsResultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable
                val remarks = columnsResultSet.getString("REMARKS") ?: ""
                val defaultValue = columnsResultSet.getString("COLUMN_DEF")
                val autoIncrement = columnsResultSet.getString("IS_AUTOINCREMENT")?.equals("YES", ignoreCase = true) ?: false

                columns.add(
                    Column(
                        name = columnName,
                        sqlType = dataType,
                        sqlTypeName = typeName,
                        size = columnSize,
                        decimalDigits = decimalDigits,
                        nullable = nullable,
                        comments = remarks,
                        defaultValue = defaultValue,
                        autoIncrement = autoIncrement,
                        javaType = mapSqlTypeToJavaType(dataType, typeName)
                    )
                )
            }
        }

        return columns
    }

    /**
     * Extract primary keys for a specific table.
     *
     * @param metadata Database metadata
     * @param catalog Catalog name
     * @param schemaPattern Schema pattern
     * @param tableName Table name
     * @return List of primary key column names
     */
    private fun extractPrimaryKeys(
        metadata: DatabaseMetaData,
        catalog: String?,
        schemaPattern: String?,
        tableName: String
    ): List<String> {
        val primaryKeys = mutableListOf<String>()

        metadata.getPrimaryKeys(catalog, schemaPattern, tableName).use { primaryKeysResultSet ->
            while (primaryKeysResultSet.next()) {
                val columnName = primaryKeysResultSet.getString("COLUMN_NAME")
                primaryKeys.add(columnName)
            }
        }

        return primaryKeys
    }

    /**
     * Extract foreign keys for a specific table.
     *
     * @param metadata Database metadata
     * @param catalog Catalog name
     * @param schemaPattern Schema pattern
     * @param tableName Table name
     * @return List of foreign keys
     */
    private fun extractForeignKeys(
        metadata: DatabaseMetaData,
        catalog: String?,
        schemaPattern: String?,
        tableName: String
    ): List<ForeignKey> {
        val foreignKeys = mutableListOf<ForeignKey>()

        metadata.getImportedKeys(catalog, schemaPattern, tableName).use { foreignKeysResultSet ->
            while (foreignKeysResultSet.next()) {
                val fkName = foreignKeysResultSet.getString("FK_NAME")
                val fkColumnName = foreignKeysResultSet.getString("FKCOLUMN_NAME")
                val pkTableName = foreignKeysResultSet.getString("PKTABLE_NAME")
                val pkColumnName = foreignKeysResultSet.getString("PKCOLUMN_NAME")
                val updateRule = foreignKeysResultSet.getInt("UPDATE_RULE")
                val deleteRule = foreignKeysResultSet.getInt("DELETE_RULE")

                foreignKeys.add(
                    ForeignKey(
                        name = fkName,
                        columnName = fkColumnName,
                        referenceTable = pkTableName,
                        referenceColumn = pkColumnName,
                        updateRule = updateRule,
                        deleteRule = deleteRule
                    )
                )
            }
        }

        return foreignKeys
    }

    /**
     * Map SQL type to Java type.
     *
     * @param sqlType SQL type code
     * @param typeName SQL type name
     * @return Corresponding Java type name
     */
    private fun mapSqlTypeToJavaType(sqlType: Int, typeName: String): String {
        return when (sqlType) {
            Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR -> "String"
            Types.BIT, Types.BOOLEAN -> "Boolean"
            Types.TINYINT, Types.SMALLINT -> "Short"
            Types.INTEGER -> "Integer"
            Types.BIGINT -> "Long"
            Types.FLOAT, Types.REAL -> "Float"
            Types.DOUBLE -> "Double"
            Types.DECIMAL, Types.NUMERIC -> "java.math.BigDecimal"
            Types.DATE -> "java.time.LocalDate"
            Types.TIME -> "java.time.LocalTime"
            Types.TIMESTAMP -> "java.time.LocalDateTime"
            Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> "byte[]"
            Types.BLOB -> "java.sql.Blob"
            Types.CLOB -> "java.sql.Clob"
            Types.ARRAY -> "java.sql.Array"
            Types.OTHER -> {
                // Handle special types based on type name
                when {
                    typeName.equals("uuid", ignoreCase = true) -> "java.util.UUID"
                    typeName.equals("json", ignoreCase = true) || typeName.equals("jsonb", ignoreCase = true) -> "String"
                    else -> "Object"
                }
            }
            else -> "Object"
        }
    }
}

package com.enokdev.springapigenerator.service

import com.intellij.openapi.project.Project
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties

/**
 * Service for database connection management.
 * Supports different database types: MySQL, PostgreSQL, H2.
 */
class DatabaseConnectionService(private val project: Project) {

    /**
     * Database connection types supported by the service.
     */
    enum class DatabaseType {
        MYSQL,
        POSTGRESQL,
        H2
    }

    /**
     * Create a database connection.
     *
     * @param type Database type (MySQL, PostgreSQL, H2)
     * @param host Database host
     * @param port Database port
     * @param database Database name
     * @param username Database username
     * @param password Database password
     * @return Connection object or null if connection failed
     */
    fun createConnection(
        type: DatabaseType,
        host: String,
        port: Int,
        database: String,
        username: String,
        password: String
    ): Connection? {
        val url = buildConnectionUrl(type, host, port, database)
        val properties = Properties()
        properties.setProperty("user", username)
        properties.setProperty("password", password)

        try {
            // Load database driver
            when (type) {
                DatabaseType.MYSQL -> Class.forName("com.mysql.cj.jdbc.Driver")
                DatabaseType.POSTGRESQL -> Class.forName("org.postgresql.Driver")
                DatabaseType.H2 -> Class.forName("org.h2.Driver")
            }

            // Create connection
            return DriverManager.getConnection(url, properties)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Database driver not found: ${e.message}")
        } catch (e: SQLException) {
            throw RuntimeException("Failed to connect to database: ${e.message}")
        }
    }

    /**
     * Test database connection.
     *
     * @param type Database type
     * @param host Database host
     * @param port Database port
     * @param database Database name
     * @param username Database username
     * @param password Database password
     * @return true if connection successful, false otherwise
     */
    fun testConnection(
        type: DatabaseType,
        host: String,
        port: Int,
        database: String,
        username: String,
        password: String
    ): Boolean {
        var connection: Connection? = null

        try {
            connection = createConnection(type, host, port, database, username, password)
            return connection != null
        } catch (e: Exception) {
            return false
        } finally {
            try {
                connection?.close()
            } catch (e: SQLException) {
                // Ignore
            }
        }
    }

    /**
     * Build database connection URL.
     *
     * @param type Database type
     * @param host Database host
     * @param port Database port
     * @param database Database name
     * @return Connection URL string
     */
    private fun buildConnectionUrl(type: DatabaseType, host: String, port: Int, database: String): String {
        return when (type) {
            DatabaseType.MYSQL -> "jdbc:mysql://$host:$port/$database?serverTimezone=UTC&useSSL=false"
            DatabaseType.POSTGRESQL -> "jdbc:postgresql://$host:$port/$database"
            DatabaseType.H2 -> "jdbc:h2:$host:$port/$database"
        }
    }
}

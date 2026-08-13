package com.sprawlauditor.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Read connection details from
 * db.properties (on the classpath, i.e. src/main/resources) and
 * hand back a live JDBC Connection.
 * No queries live here — that's the DAO layer's job. Keeping this
 * class narrow means swapping databases later (MySQL -> Postgres,
 * local -> cloud) only ever touches this one file and db.properties.
 */
public class DatabaseManager {

    private static final String PROPERTIES_FILE = "db.properties";

    /**
     * Opens and returns a new JDBC connection using the values in
     * db.properties. Caller is responsible for closing it (use
     * try-with-resources).
     */
    public static Connection getConnection() throws SQLException, IOException {
        Properties props = loadProperties();

        String url = props.getProperty("db.url");
        String username = props.getProperty("db.username");
        String password = props.getProperty("db.password");

        if (url == null || username == null || password == null) {
            throw new IllegalStateException(
                    "db.properties is missing one of: db.url, db.username, db.password"
            );
        }

        return DriverManager.getConnection(url, username, password);
    }

    private static Properties loadProperties() throws IOException {
        Properties props = new Properties();
        // Read from the classpath (src/main/resources), not a hardcoded
        // filesystem path — this is what makes it work the same way
        // whether run from IntelliJ, `mvn exec:java`, or a packaged jar.
        try (InputStream input = DatabaseManager.class.getClassLoader()
                .getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                throw new IOException(
                        PROPERTIES_FILE + " not found on the classpath — " +
                                "make sure it's in src/main/resources"
                );
            }
            props.load(input);
        }
        return props;
    }
}

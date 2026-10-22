package org.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConnection {

    private static final Properties PROPERTIES = new Properties();

    static {
        try (InputStream input =
                     DatabaseConnection.class
                             .getClassLoader()
                             .getResourceAsStream("application.properties")) {

            if (input == null) {
                throw new IllegalStateException(
                        "application.properties not found");
            }

            PROPERTIES.load(input);

        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DatabaseConnection() {
    }


    public static Connection getConnection() throws SQLException {
        String url = PROPERTIES.getProperty("db.url");
        String username = PROPERTIES.getProperty("db.username");
        String password = PROPERTIES.getProperty("db.password", "");

        System.setProperty(
                "javax.net.ssl.trustStore",
                PROPERTIES.getProperty("db.ssl.truststore")
        );

        System.setProperty(
                "javax.net.ssl.trustStorePassword",
                PROPERTIES.getProperty("db.ssl.truststore.password")
        );

        System.setProperty(
                "javax.net.ssl.trustStoreType",
                "PKCS12"
        );

        return DriverManager.getConnection(url, username, password);
    }

}
package org.example;

import org.example.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;

public class Main {

    public static void main(String[] args) {
        try (Connection connection =
                     DatabaseConnection.getConnection()) {

            System.out.println("Connected to MySQL successfully!");

        } catch (SQLException e) {
            System.err.println("MySQL connection failed.");
            e.printStackTrace();
        }
    }
}
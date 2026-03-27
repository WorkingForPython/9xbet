package com.auction.app;

import java.sql.*;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:auction_data.db";

    public static void initializeDB() {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {

            // Create tables
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, username TEXT UNIQUE, password TEXT, role TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS items (id INTEGER PRIMARY KEY, name TEXT, current_bid REAL, highest_bidder TEXT, seller_id INTEGER)");

            // Pre-seed users for testing (Ignore errors if they already exist)
            try {
                stmt.execute("INSERT INTO users (username, password, role) VALUES ('bidder', 'pass', 'Bidder')");
                stmt.execute("INSERT INTO users (username, password, role) VALUES ('seller', 'pass', 'Seller')");
                stmt.execute("INSERT INTO users (username, password, role) VALUES ('admin', 'admin', 'Admin')");
            } catch (Exception ignore) {} // Users already exist

            System.out.println("Database and tables initialized successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}
package com.auction.app;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class AuctionServer {
    private static final int PORT = 8080;

    public static void main(String[] args) {
        DatabaseManager.initializeDB();

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
            server.createContext("/", new AuctionHandler());
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            System.out.println("HTTP Web Server started on port " + PORT + ". Ready for ngrok traffic!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class AuctionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
                String action = request.get("action").getAsString();
                JsonObject response = new JsonObject();

                try (Connection conn = DatabaseManager.getConnection()) {
                    if (action.equals("LOGIN")) {
                        PreparedStatement ps = conn.prepareStatement("SELECT id, role FROM users WHERE username = ? AND password = ?");
                        ps.setString(1, request.get("username").getAsString());
                        ps.setString(2, request.get("password").getAsString());
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            // Update last_active on login
                            PreparedStatement actPs = conn.prepareStatement("UPDATE users SET last_active = datetime('now') WHERE id = ?");
                            actPs.setInt(1, rs.getInt("id"));
                            actPs.executeUpdate();

                            response.addProperty("status", "SUCCESS");
                            response.addProperty("role", rs.getString("role"));
                            response.addProperty("user_id", rs.getInt("id"));
                            response.addProperty("username", request.get("username").getAsString());
                        } else {
                            response.addProperty("status", "ERROR");
                            response.addProperty("message", "Invalid credentials.");
                        }
                    }
                    else if (action.equals("REGISTER")) {
                        String newUsername = request.get("username").getAsString();
                        String newPassword = request.get("password").getAsString();
                        try {
                            PreparedStatement ps = conn.prepareStatement("INSERT INTO users (username, password, role) VALUES (?, ?, 'User')");
                            ps.setString(1, newUsername);
                            ps.setString(2, newPassword);
                            ps.executeUpdate();
                            response.addProperty("status", "SUCCESS");
                            response.addProperty("message", "Account created successfully!");
                        } catch (SQLException e) {
                            if (e.getMessage().contains("UNIQUE constraint failed")) {
                                response.addProperty("status", "ERROR");
                                response.addProperty("message", "Username already exists. Pick another one.");
                            } else {
                                response.addProperty("status", "ERROR");
                                response.addProperty("message", "Database error: " + e.getMessage());
                            }
                        }
                    }
                    else if (action.equals("LIST_ITEM")) {
                        // Added description field to the INSERT statement
                        PreparedStatement ps = conn.prepareStatement("INSERT INTO items (name, description, current_bid, highest_bidder, seller_id, end_time, status) VALUES (?, ?, ?, 'None', ?, datetime('now', '+3 minutes'), 'ACTIVE')");
                        ps.setString(1, request.get("name").getAsString());
                        ps.setString(2, request.has("description") ? request.get("description").getAsString() : "No description.");
                        ps.setDouble(3, request.get("price").getAsDouble());
                        ps.setInt(4, request.get("seller_id").getAsInt());
                        ps.executeUpdate();
                        response.addProperty("status", "SUCCESS");
                    }
                    else if (action.equals("GET_ITEMS")) {
                        // 1. Update the asking user's last_active timestamp
                        if (request.has("user_id")) {
                            PreparedStatement actPs = conn.prepareStatement("UPDATE users SET last_active = datetime('now') WHERE id = ?");
                            actPs.setInt(1, request.get("user_id").getAsInt());
                            actPs.executeUpdate();
                        }

                        // 2. Count users active in the last 5 seconds
                        Statement countStmt = conn.createStatement();
                        ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) AS online_count FROM users WHERE last_active >= datetime('now', '-5 seconds')");
                        int onlineUsers = countRs.next() ? countRs.getInt("online_count") : 1;
                        response.addProperty("online_users", onlineUsers);

                        // 3. Mark expired items as SOLD
                        Statement expireStmt = conn.createStatement();
                        expireStmt.executeUpdate("UPDATE items SET status = 'SOLD' WHERE end_time <= datetime('now') AND status = 'ACTIVE'");

                        // 4. Fetch the items (now including description)
                        Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT *, CAST(strftime('%s', end_time) - strftime('%s', 'now') AS INTEGER) AS time_left FROM items WHERE status = 'ACTIVE'");
                        JsonArray items = new JsonArray();
                        while (rs.next()) {
                            JsonObject item = new JsonObject();
                            item.addProperty("id", rs.getInt("id"));
                            item.addProperty("name", rs.getString("name"));
                            item.addProperty("description", rs.getString("description"));
                            item.addProperty("current_bid", rs.getDouble("current_bid"));
                            item.addProperty("highest_bidder", rs.getString("highest_bidder"));
                            item.addProperty("time_left", rs.getInt("time_left"));
                            items.add(item);
                        }
                        response.addProperty("status", "SUCCESS");
                        response.add("items", items);
                    }
                    else if (action.equals("BID")) {
                        int itemId = request.get("item_id").getAsInt();
                        double bidAmount = request.get("bid_amount").getAsDouble();
                        String bidderName = request.get("bidder_name").getAsString();

                        PreparedStatement checkPs = conn.prepareStatement(
                                "SELECT i.current_bid, u.username AS seller_name " +
                                        "FROM items i JOIN users u ON i.seller_id = u.id " +
                                        "WHERE i.id = ? AND i.status = 'ACTIVE'"
                        );
                        checkPs.setInt(1, itemId);
                        ResultSet rs = checkPs.executeQuery();

                        if (rs.next()) {
                            String sellerName = rs.getString("seller_name");

                            if (bidderName.equals(sellerName)) {
                                response.addProperty("status", "ERROR");
                                response.addProperty("message", "You cannot bid on your own item!");
                            } else if (bidAmount > rs.getDouble("current_bid")) {
                                PreparedStatement updatePs = conn.prepareStatement("UPDATE items SET current_bid = ?, highest_bidder = ? WHERE id = ?");
                                updatePs.setDouble(1, bidAmount);
                                updatePs.setString(2, bidderName);
                                updatePs.setInt(3, itemId);
                                updatePs.executeUpdate();
                                response.addProperty("status", "SUCCESS");
                            } else {
                                response.addProperty("status", "ERROR");
                                response.addProperty("message", "Bid too low!");
                            }
                        } else {
                            response.addProperty("status", "ERROR");
                            response.addProperty("message", "Item not found or already sold!");
                        }
                    }
                    else if (action.equals("GET_HISTORY")) {
                        String username = request.get("username").getAsString();

                        Statement pubStmt = conn.createStatement();
                        ResultSet pubRs = pubStmt.executeQuery("SELECT name, current_bid, highest_bidder FROM items WHERE status = 'SOLD' ORDER BY id DESC LIMIT 20");
                        JsonArray publicHistory = new JsonArray();
                        while (pubRs.next()) {
                            JsonObject item = new JsonObject();
                            item.addProperty("name", pubRs.getString("name"));
                            item.addProperty("winning_bid", pubRs.getDouble("current_bid"));
                            item.addProperty("winner", pubRs.getString("highest_bidder"));
                            publicHistory.add(item);
                        }

                        PreparedStatement privStmt = conn.prepareStatement("SELECT name, current_bid FROM items WHERE status = 'SOLD' AND highest_bidder = ? ORDER BY id DESC");
                        privStmt.setString(1, username);
                        ResultSet privRs = privStmt.executeQuery();
                        JsonArray privateHistory = new JsonArray();
                        while (privRs.next()) {
                            JsonObject item = new JsonObject();
                            item.addProperty("name", privRs.getString("name"));
                            item.addProperty("winning_bid", privRs.getDouble("current_bid"));
                            privateHistory.add(item);
                        }

                        response.addProperty("status", "SUCCESS");
                        response.add("public_history", publicHistory);
                        response.add("private_history", privateHistory);
                    }
                    else if (action.equals("DELETE_ITEM")) {
                        PreparedStatement ps = conn.prepareStatement("DELETE FROM items WHERE id = ?");
                        ps.setInt(1, request.get("item_id").getAsInt());
                        ps.executeUpdate();
                        response.addProperty("status", "SUCCESS");
                    }
                    else {
                        response.addProperty("status", "ERROR");
                        response.addProperty("message", "Unknown action requested: " + action);
                    }
                } catch (SQLException e) {
                    response.addProperty("status", "ERROR");
                    response.addProperty("message", "Database error: " + e.getMessage());
                }

                String responseBody = response.toString();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}
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
            // Create a web server listening on port 8080
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
            // We only accept POST requests containing JSON
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
                            response.addProperty("status", "SUCCESS");
                            response.addProperty("role", rs.getString("role"));
                            response.addProperty("user_id", rs.getInt("id"));
                            response.addProperty("username", request.get("username").getAsString());
                        } else {
                            response.addProperty("status", "ERROR");
                            response.addProperty("message", "Invalid credentials.");
                        }
                    }
                    else if (action.equals("LIST_ITEM")) {
                        PreparedStatement ps = conn.prepareStatement("INSERT INTO items (name, current_bid, highest_bidder, seller_id) VALUES (?, ?, 'None', ?)");
                        ps.setString(1, request.get("name").getAsString());
                        ps.setDouble(2, request.get("price").getAsDouble());
                        ps.setInt(3, request.get("seller_id").getAsInt());
                        ps.executeUpdate();
                        response.addProperty("status", "SUCCESS");
                    }
                    else if (action.equals("GET_ITEMS")) {
                        Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT * FROM items");
                        JsonArray items = new JsonArray();
                        while (rs.next()) {
                            JsonObject item = new JsonObject();
                            item.addProperty("id", rs.getInt("id"));
                            item.addProperty("name", rs.getString("name"));
                            item.addProperty("current_bid", rs.getDouble("current_bid"));
                            item.addProperty("highest_bidder", rs.getString("highest_bidder"));
                            items.add(item);
                        }
                        response.addProperty("status", "SUCCESS");
                        response.add("items", items);
                    }
                    else if (action.equals("BID")) {
                        int itemId = request.get("item_id").getAsInt();
                        double bidAmount = request.get("bid_amount").getAsDouble();
                        String bidderName = request.get("bidder_name").getAsString();

                        PreparedStatement checkPs = conn.prepareStatement("SELECT current_bid FROM items WHERE id = ?");
                        checkPs.setInt(1, itemId);
                        ResultSet rs = checkPs.executeQuery();

                        if (rs.next() && bidAmount > rs.getDouble("current_bid")) {
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
                    }
                    else if (action.equals("DELETE_ITEM")) {
                        PreparedStatement ps = conn.prepareStatement("DELETE FROM items WHERE id = ?");
                        ps.setInt(1, request.get("item_id").getAsInt());
                        ps.executeUpdate();
                        response.addProperty("status", "SUCCESS");
                    }
                } catch (SQLException e) {
                    response.addProperty("status", "ERROR");
                    response.addProperty("message", "Database error: " + e.getMessage());
                }

                // Send the HTTP response back to the client
                String responseBody = response.toString();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // Reject anything that isn't a POST request
            }
        }
    }
}
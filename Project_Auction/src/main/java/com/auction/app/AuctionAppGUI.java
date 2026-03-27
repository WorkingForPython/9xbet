package com.auction.app;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class AuctionAppGUI extends Application {

    private NetworkClient networkClient;
    private int currentUserId;
    private String currentUserName;

    @Override
    public void start(Stage primaryStage) {
        networkClient = new NetworkClient();
        try {
            networkClient.connect("https://valeria-witless-stellularly.ngrok-free.dev", 8080); // Change to ngrok URL if needed
        } catch (Exception e) {
            System.err.println("Could not connect to server.");
            return;
        }

        buildLoginScreen(primaryStage);
    }

    private void buildLoginScreen(Stage stage) {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("root");

        VBox panel = new VBox(15);
        panel.setAlignment(Pos.CENTER);
        panel.getStyleClass().add("panel-bg");
        panel.setMaxWidth(350);

        Label title = new Label("Secure Login");
        title.getStyleClass().add("title-text");

        TextField userField = new TextField();
        userField.setPromptText("Username (try: bidder, seller, admin)");
        userField.setStyle("-fx-font-size: 14px; -fx-padding: 10px;");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Password (try: pass, pass, admin)");
        passField.setStyle("-fx-font-size: 14px; -fx-padding: 10px;");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #f38ba8;"); // Aesthetic red

        Button loginBtn = createAnimatedButton("Login");
        loginBtn.setOnAction(e -> {
            try {
                JsonObject req = new JsonObject();
                req.addProperty("action", "LOGIN");
                req.addProperty("username", userField.getText());
                req.addProperty("password", passField.getText());

                JsonObject res = networkClient.sendRequest(req);
                if (res.get("status").getAsString().equals("SUCCESS")) {
                    currentUserId = res.get("user_id").getAsInt();
                    currentUserName = res.get("username").getAsString();
                    String role = res.get("role").getAsString();
                    openDashboard(role, stage);
                } else {
                    errorLabel.setText("Login Failed. Try again.");
                }
            } catch (Exception ex) {
                errorLabel.setText("Server communication error.");
            }
        });

        panel.getChildren().addAll(title, userField, passField, loginBtn, errorLabel);
        root.getChildren().add(panel);

        Scene scene = new Scene(root, 600, 500);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setTitle("Auction Platform - Login");
        stage.setScene(scene);
        stage.show();
    }

    private void openDashboard(String role, Stage stage) {
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        layout.getStyleClass().add("root");

        Label title = new Label(role + " Dashboard - Welcome, " + currentUserName);
        title.getStyleClass().add("title-text");
        layout.getChildren().add(title);

        if (role.equals("Bidder")) buildBidderUI(layout);
        if (role.equals("Seller")) buildSellerUI(layout);
        if (role.equals("Admin")) buildAdminUI(layout);

        Scene scene = new Scene(layout, 700, 600);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setScene(scene);
    }

    // --- BIDDER INTERFACE ---
    private void buildBidderUI(VBox layout) {
        ListView<String> itemList = new ListView<>();
        itemList.setPrefHeight(300);
        itemList.setMaxWidth(500);

        HBox bidBox = new HBox(10);
        bidBox.setAlignment(Pos.CENTER);
        TextField bidAmount = new TextField();
        bidAmount.setPromptText("Enter Bid Amount");
        Button bidBtn = createAnimatedButton("Place Bid");

        Button refreshBtn = createAnimatedButton("Refresh Items");

        Runnable fetchItems = () -> {
            try {
                JsonObject req = new JsonObject();
                req.addProperty("action", "GET_ITEMS");
                JsonObject res = networkClient.sendRequest(req);
                itemList.getItems().clear();
                JsonArray items = res.getAsJsonArray("items");
                for (JsonElement e : items) {
                    JsonObject item = e.getAsJsonObject();
                    itemList.getItems().add(String.format("ID: %d | %s | Current Bid: $%.2f | Highest: %s",
                            item.get("id").getAsInt(), item.get("name").getAsString(),
                            item.get("current_bid").getAsDouble(), item.get("highest_bidder").getAsString()));
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        };

        bidBtn.setOnAction(e -> {
            String selected = itemList.getSelectionModel().getSelectedItem();
            if (selected != null && !bidAmount.getText().isEmpty()) {
                int itemId = Integer.parseInt(selected.split(" ")[1]); // Hacky but works for this scope
                try {
                    JsonObject req = new JsonObject();
                    req.addProperty("action", "BID");
                    req.addProperty("item_id", itemId);
                    req.addProperty("bid_amount", Double.parseDouble(bidAmount.getText()));
                    req.addProperty("bidder_name", currentUserName);
                    networkClient.sendRequest(req);
                    fetchItems.run();
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        refreshBtn.setOnAction(e -> fetchItems.run());
        fetchItems.run();

        bidBox.getChildren().addAll(bidAmount, bidBtn);
        layout.getChildren().addAll(itemList, bidBox, refreshBtn);
    }

    // --- SELLER INTERFACE ---
    private void buildSellerUI(VBox layout) {
        VBox form = new VBox(15);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(400);

        TextField nameField = new TextField();
        nameField.setPromptText("Item Name");
        TextField priceField = new TextField();
        priceField.setPromptText("Starting Price (e.g., 50.00)");

        Button listBtn = createAnimatedButton("List Item");
        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #a6e3a1;"); // Aesthetic green

        listBtn.setOnAction(e -> {
            try {
                JsonObject req = new JsonObject();
                req.addProperty("action", "LIST_ITEM");
                req.addProperty("name", nameField.getText());
                req.addProperty("price", Double.parseDouble(priceField.getText()));
                req.addProperty("seller_id", currentUserId);

                JsonObject res = networkClient.sendRequest(req);
                if (res.get("status").getAsString().equals("SUCCESS")) {
                    statusLabel.setText("Item listed successfully!");
                    nameField.clear(); priceField.clear();
                }
            } catch (Exception ex) {
                statusLabel.setText("Error listing item.");
            }
        });

        form.getChildren().addAll(nameField, priceField, listBtn, statusLabel);
        layout.getChildren().add(form);
    }

    // --- ADMIN INTERFACE ---
    private void buildAdminUI(VBox layout) {
        ListView<String> itemList = new ListView<>();
        itemList.setPrefHeight(300);
        itemList.setMaxWidth(500);

        Button deleteBtn = createAnimatedButton("Delete Selected Item");
        Button refreshBtn = createAnimatedButton("Refresh Items");

        Runnable fetchItems = () -> {
            try {
                JsonObject req = new JsonObject();
                req.addProperty("action", "GET_ITEMS");
                JsonObject res = networkClient.sendRequest(req);
                itemList.getItems().clear();
                JsonArray items = res.getAsJsonArray("items");
                for (JsonElement e : items) {
                    JsonObject item = e.getAsJsonObject();
                    itemList.getItems().add(String.format("ID: %d | %s | Current Bid: $%.2f",
                            item.get("id").getAsInt(), item.get("name").getAsString(), item.get("current_bid").getAsDouble()));
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        };

        deleteBtn.setOnAction(e -> {
            String selected = itemList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                int itemId = Integer.parseInt(selected.split(" ")[1]);
                try {
                    JsonObject req = new JsonObject();
                    req.addProperty("action", "DELETE_ITEM");
                    req.addProperty("item_id", itemId);
                    networkClient.sendRequest(req);
                    fetchItems.run();
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        refreshBtn.setOnAction(e -> fetchItems.run());
        fetchItems.run();

        layout.getChildren().addAll(itemList, deleteBtn, refreshBtn);
    }

    private Button createAnimatedButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("modern-button");
        btn.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), btn);
            st.setToX(0.92); st.setToY(0.92); st.play();
        });
        btn.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), btn);
            st.setToX(1.0); st.setToY(1.0); st.play();
        });
        return btn;
    }

    @Override
    public void stop() throws Exception {
        if (networkClient != null) networkClient.disconnect();
    }

    public static void main(String[] args) { launch(args); }
}
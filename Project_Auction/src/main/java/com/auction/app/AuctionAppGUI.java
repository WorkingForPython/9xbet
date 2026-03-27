package com.auction.app;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.concurrent.CompletableFuture;

public class AuctionAppGUI extends Application {

    private NetworkClient networkClient;
    private int currentUserId;
    private String currentUserName;

    private FlowPane itemGrid;
    private StackPane rootPane;
    private VBox modalOverlay;
    private Timeline autoUpdater;

    // NEW: Live Online Users Label
    private Label onlineUsersLabel;

    @Override
    public void start(Stage primaryStage) {
        networkClient = new NetworkClient();
        try {
            networkClient.connect("https://valeria-witless-stellularly.ngrok-free.dev", 8080);
        } catch (Exception e) {
            System.err.println("Could not connect to server.");
            return;
        }
        buildAuthScreen(primaryStage);
    }

    private void buildAuthScreen(Stage stage) {
        HBox splitScreen = new HBox();
        splitScreen.setPrefSize(900, 600);

        VBox rightSide = new VBox(10);
        rightSide.setAlignment(Pos.CENTER);
        rightSide.setPrefWidth(450);
        rightSide.setStyle("-fx-background-color: #2e3440;");
        Label brandTitle = new Label("MutualArt Auctions");
        brandTitle.setStyle("-fx-text-fill: white; -fx-font-size: 32px; -fx-font-weight: bold;");
        Label brandSubtitle = new Label("Discover and Bid on Masterpieces");
        brandSubtitle.setStyle("-fx-text-fill: #d8dee9; -fx-font-size: 16px;");
        rightSide.getChildren().addAll(brandTitle, brandSubtitle);

        VBox leftSide = new VBox(20);
        leftSide.setAlignment(Pos.CENTER);
        leftSide.setPrefWidth(450);
        leftSide.setStyle("-fx-background-color: #eceff4; -fx-padding: 40px;");

        Label formTitle = new Label("Welcome Back");
        formTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2e3440;");
        TextField userField = new TextField(); userField.setPromptText("Username");
        PasswordField passField = new PasswordField(); passField.setPromptText("Password");
        Label errorLabel = new Label(); errorLabel.setStyle("-fx-text-fill: #bf616a; -fx-font-weight: bold;");
        Button actionBtn = createAnimatedButton("Login", "#5e81ac");
        Hyperlink toggleLink = new Hyperlink("Don't have an account? Register here.");

        final boolean[] isLoginMode = {true};

        toggleLink.setOnAction(e -> {
            isLoginMode[0] = !isLoginMode[0];
            formTitle.setText(isLoginMode[0] ? "Welcome Back" : "Create an Account");
            actionBtn.setText(isLoginMode[0] ? "Login" : "Register");
            toggleLink.setText(isLoginMode[0] ? "Don't have an account? Register here." : "Already have an account? Login here.");
            errorLabel.setText("");
        });

        actionBtn.setOnAction(e -> {
            if (userField.getText().isEmpty() || passField.getText().isEmpty()) return;
            actionBtn.setDisable(true); errorLabel.setText("Connecting...");
            String actionType = isLoginMode[0] ? "LOGIN" : "REGISTER";

            CompletableFuture.supplyAsync(() -> {
                try {
                    JsonObject req = new JsonObject();
                    req.addProperty("action", actionType);
                    req.addProperty("username", userField.getText());
                    req.addProperty("password", passField.getText());
                    return networkClient.sendRequest(req);
                } catch (Exception ex) { return null; }
            }).thenAccept(res -> Platform.runLater(() -> {
                actionBtn.setDisable(false);
                if (res != null && res.has("status") && res.get("status").getAsString().equals("SUCCESS")) {
                    if (isLoginMode[0]) {
                        currentUserId = res.get("user_id").getAsInt();
                        currentUserName = res.get("username").getAsString();
                        openDashboard(stage);
                    } else {
                        errorLabel.setStyle("-fx-text-fill: #a3be8c;");
                        errorLabel.setText("Account created! Please login.");
                        toggleLink.fire();
                    }
                } else {
                    errorLabel.setStyle("-fx-text-fill: #bf616a;");
                    String errorMsg = (res != null && res.has("message")) ? res.get("message").getAsString() : "Server error.";
                    errorLabel.setText(errorMsg);
                }
            }));
        });

        leftSide.getChildren().addAll(formTitle, userField, passField, actionBtn, errorLabel, toggleLink);
        splitScreen.getChildren().addAll(leftSide, rightSide);
        stage.setTitle("MutualArt - Authentication");
        stage.setScene(new Scene(splitScreen, 900, 600));
        stage.show();
    }

    private void openDashboard(Stage stage) {
        rootPane = new StackPane();
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: #f4f4f9;");

        HBox navBar = new HBox(20);
        navBar.setAlignment(Pos.CENTER_LEFT);
        navBar.setPadding(new Insets(15, 30, 15, 30));
        navBar.setStyle("-fx-background-color: #2e3440;");

        Label logo = new Label("MutualArt");
        logo.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        // NEW: Online Users Label
        onlineUsersLabel = new Label("🟢 Online: 1");
        onlineUsersLabel.setStyle("-fx-text-fill: #a3be8c; -fx-font-weight: bold; -fx-font-size: 14px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label("Hello, " + currentUserName);
        userLabel.setStyle("-fx-text-fill: #d8dee9; -fx-font-size: 14px;");

        Button sellBtn = createAnimatedButton("+ List New Item", "#a3be8c");
        sellBtn.setOnAction(e -> showSellModal());

        Button historyBtn = createAnimatedButton("History", "#81a1c1");
        historyBtn.setOnAction(e -> showHistoryModal());

        navBar.getChildren().addAll(logo, onlineUsersLabel, spacer, userLabel, sellBtn, historyBtn);
        mainLayout.setTop(navBar);

        itemGrid = new FlowPane(20, 20);
        itemGrid.setPadding(new Insets(30));
        itemGrid.setAlignment(Pos.TOP_CENTER);

        ScrollPane scrollPane = new ScrollPane(itemGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-control-inner-background: #f4f4f9;");
        mainLayout.setCenter(scrollPane);

        modalOverlay = new VBox();
        modalOverlay.setAlignment(Pos.CENTER);
        modalOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7);");
        modalOverlay.setVisible(false);

        rootPane.getChildren().addAll(mainLayout, modalOverlay);

        startBackgroundFetcher();

        Scene scene = new Scene(rootPane, 1000, 700);
        stage.setTitle("MutualArt - Dashboard");
        stage.setScene(scene);
        stage.centerOnScreen();
    }

    private void startBackgroundFetcher() {
        Runnable fetchItems = () -> {
            CompletableFuture.supplyAsync(() -> {
                try {
                    JsonObject req = new JsonObject();
                    req.addProperty("action", "GET_ITEMS");
                    req.addProperty("user_id", currentUserId); // Tell server we are active!
                    return networkClient.sendRequest(req);
                } catch (Exception ex) { return null; }
            }).thenAccept(res -> Platform.runLater(() -> {
                if (res != null && res.has("items")) {
                    // Update Online Users Count
                    if (res.has("online_users")) {
                        onlineUsersLabel.setText("🟢 Online: " + res.get("online_users").getAsInt());
                    }

                    itemGrid.getChildren().clear();
                    JsonArray items = res.getAsJsonArray("items");
                    for (JsonElement e : items) {
                        itemGrid.getChildren().add(createItemCard(e.getAsJsonObject()));
                    }
                }
            }));
        };

        // Running every 1 second now!
        autoUpdater = new Timeline(new KeyFrame(Duration.seconds(1), e -> fetchItems.run()));
        autoUpdater.setCycleCount(Timeline.INDEFINITE);
        autoUpdater.play();
        fetchItems.run();
    }

    private VBox createItemCard(JsonObject item) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        card.setPrefSize(220, 310);

        Region artPlaceholder = new Region();
        artPlaceholder.setPrefSize(190, 150);
        int colorSeed = item.get("id").getAsInt() * 45;
        artPlaceholder.setStyle("-fx-background-color: linear-gradient(to bottom right, hsb("+colorSeed+", 60%, 80%), hsb("+(colorSeed+40)+", 60%, 60%)); -fx-background-radius: 5;");

        Label name = new Label(item.get("name").getAsString());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        Label bid = new Label(String.format("Current Bid: $%.2f", item.get("current_bid").getAsDouble()));
        bid.setStyle("-fx-text-fill: #5e81ac; -fx-font-weight: bold;");

        Label topBidder = new Label("Highest: " + item.get("highest_bidder").getAsString());
        topBidder.setStyle("-fx-text-fill: #88c0d0; -fx-font-size: 12px;");

        int timeLeft = item.has("time_left") ? item.get("time_left").getAsInt() : 0;
        Label timeLabel = new Label(timeLeft > 0 ? String.format("Ends in: %02d:%02d", timeLeft / 60, timeLeft % 60) : "Ending soon...");
        timeLabel.setStyle("-fx-text-fill: #bf616a; -fx-font-weight: bold; -fx-font-size: 14px;");

        card.getChildren().addAll(artPlaceholder, name, bid, topBidder, timeLabel);

        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #fafafa; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 15, 0, 0, 8); -fx-cursor: hand;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);"));
        card.setOnMouseClicked(e -> showZoomedItemModal(item));

        return card;
    }

    private void showZoomedItemModal(JsonObject item) {
        VBox modal = new VBox(15);
        modal.setAlignment(Pos.CENTER);
        modal.setMaxSize(500, 650);
        modal.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-padding: 30;");

        Label title = new Label(item.get("name").getAsString());
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");

        Region bigArt = new Region();
        bigArt.setPrefSize(400, 250);
        int colorSeed = item.get("id").getAsInt() * 45;
        bigArt.setStyle("-fx-background-color: linear-gradient(to bottom right, hsb("+colorSeed+", 60%, 80%), hsb("+(colorSeed+40)+", 60%, 60%)); -fx-background-radius: 10;");

        // NEW: Show Description in Modal
        String descText = item.has("description") ? item.get("description").getAsString() : "No description available.";
        Label description = new Label(descText);
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 14px; -fx-text-fill: #4c566a; -fx-padding: 10; -fx-background-color: #f4f4f9; -fx-background-radius: 5;");
        description.setPrefWidth(400);

        int timeLeft = item.has("time_left") ? item.get("time_left").getAsInt() : 0;
        Label timeLabel = new Label(timeLeft > 0 ? String.format("Time Remaining: %02d:%02d", timeLeft / 60, timeLeft % 60) : "Auction Ending...");
        timeLabel.setStyle("-fx-text-fill: #bf616a; -fx-font-weight: bold; -fx-font-size: 16px;");

        Label currentBid = new Label(String.format("Current Bid: $%.2f (By: %s)", item.get("current_bid").getAsDouble(), item.get("highest_bidder").getAsString()));
        currentBid.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        HBox bidBox = new HBox(10);
        bidBox.setAlignment(Pos.CENTER);
        TextField bidAmount = new TextField();
        bidAmount.setPromptText("Enter higher amount");
        Button submitBidBtn = createAnimatedButton("Place Bid", "#5e81ac");

        Label statusLabel = new Label();

        submitBidBtn.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(bidAmount.getText());
                CompletableFuture.supplyAsync(() -> {
                    try {
                        JsonObject req = new JsonObject();
                        req.addProperty("action", "BID");
                        req.addProperty("item_id", item.get("id").getAsInt());
                        req.addProperty("bid_amount", amount);
                        req.addProperty("bidder_name", currentUserName);
                        return networkClient.sendRequest(req);
                    } catch (Exception ex) { return null; }
                }).thenAccept(res -> Platform.runLater(() -> {
                    if (res != null && res.has("status") && res.get("status").getAsString().equals("SUCCESS")) {
                        statusLabel.setText("Bid placed! Wait for refresh.");
                        statusLabel.setStyle("-fx-text-fill: #a3be8c;");
                        bidAmount.clear();
                    } else {
                        String msg = (res != null && res.has("message")) ? res.get("message").getAsString() : "Bid failed.";
                        statusLabel.setText(msg);
                        statusLabel.setStyle("-fx-text-fill: #bf616a;");
                    }
                }));
            } catch (NumberFormatException ex) {
                statusLabel.setText("Invalid amount!");
                statusLabel.setStyle("-fx-text-fill: #bf616a;");
            }
        });

        bidBox.getChildren().addAll(bidAmount, submitBidBtn);

        Button closeBtn = createAnimatedButton("Close", "#bf616a");
        closeBtn.setOnAction(e -> modalOverlay.setVisible(false));

        modal.getChildren().addAll(title, bigArt, description, timeLabel, currentBid, bidBox, statusLabel, closeBtn);

        modalOverlay.getChildren().clear();
        modalOverlay.getChildren().add(modal);
        modalOverlay.setVisible(true);
    }

    private void showSellModal() {
        VBox modal = new VBox(15);
        modal.setAlignment(Pos.CENTER);
        modal.setMaxSize(400, 420);
        modal.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-padding: 30;");

        Label title = new Label("List a New Artwork");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        TextField nameField = new TextField(); nameField.setPromptText("Artwork Title");

        // NEW: Description Text Area
        TextArea descField = new TextArea();
        descField.setPromptText("Enter a captivating description...");
        descField.setPrefRowCount(3);
        descField.setWrapText(true);

        TextField priceField = new TextField(); priceField.setPromptText("Starting Bid (e.g. 100.00)");

        Label statusLabel = new Label();

        HBox btnBox = new HBox(15);
        btnBox.setAlignment(Pos.CENTER);
        Button submitBtn = createAnimatedButton("List Item", "#a3be8c");
        Button cancelBtn = createAnimatedButton("Cancel", "#bf616a");

        submitBtn.setOnAction(e -> {
            submitBtn.setDisable(true);
            CompletableFuture.supplyAsync(() -> {
                try {
                    JsonObject req = new JsonObject();
                    req.addProperty("action", "LIST_ITEM");
                    req.addProperty("name", nameField.getText());
                    req.addProperty("description", descField.getText()); // Send Description!
                    req.addProperty("price", Double.parseDouble(priceField.getText()));
                    req.addProperty("seller_id", currentUserId);
                    return networkClient.sendRequest(req);
                } catch (Exception ex) { return null; }
            }).thenAccept(res -> Platform.runLater(() -> {
                modalOverlay.setVisible(false);
            }));
        });

        cancelBtn.setOnAction(e -> modalOverlay.setVisible(false));
        btnBox.getChildren().addAll(submitBtn, cancelBtn);

        modal.getChildren().addAll(title, nameField, descField, priceField, statusLabel, btnBox);
        modalOverlay.getChildren().clear();
        modalOverlay.getChildren().add(modal);
        modalOverlay.setVisible(true);
    }

    private void showHistoryModal() {
        VBox modal = new VBox(15);
        modal.setAlignment(Pos.CENTER);
        modal.setMaxSize(600, 500);
        modal.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-padding: 30;");

        Label title = new Label("Auction History");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        HBox columns = new HBox(20);
        columns.setAlignment(Pos.CENTER);

        VBox pubCol = new VBox(10);
        Label pubTitle = new Label("Public Sales");
        pubTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        ListView<String> pubList = new ListView<>();
        pubList.setPrefSize(250, 300);
        pubCol.getChildren().addAll(pubTitle, pubList);

        VBox privCol = new VBox(10);
        Label privTitle = new Label("My Wins");
        privTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        ListView<String> privList = new ListView<>();
        privList.setPrefSize(250, 300);
        privCol.getChildren().addAll(privTitle, privList);

        columns.getChildren().addAll(pubCol, privCol);

        Button closeBtn = createAnimatedButton("Close", "#bf616a");
        closeBtn.setOnAction(e -> modalOverlay.setVisible(false));

        modal.getChildren().addAll(title, columns, closeBtn);
        modalOverlay.getChildren().clear();
        modalOverlay.getChildren().add(modal);
        modalOverlay.setVisible(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject req = new JsonObject();
                req.addProperty("action", "GET_HISTORY");
                req.addProperty("username", currentUserName);
                return networkClient.sendRequest(req);
            } catch (Exception ex) { return null; }
        }).thenAccept(res -> Platform.runLater(() -> {
            if (res != null && res.has("status") && res.get("status").getAsString().equals("SUCCESS")) {
                JsonArray pubArr = res.getAsJsonArray("public_history");
                for (JsonElement el : pubArr) {
                    JsonObject obj = el.getAsJsonObject();
                    pubList.getItems().add(String.format("%s - $%.2f (%s)", obj.get("name").getAsString(), obj.get("winning_bid").getAsDouble(), obj.get("winner").getAsString()));
                }
                if (pubList.getItems().isEmpty()) pubList.getItems().add("No items sold yet.");

                JsonArray privArr = res.getAsJsonArray("private_history");
                for (JsonElement el : privArr) {
                    JsonObject obj = el.getAsJsonObject();
                    privList.getItems().add(String.format("%s - $%.2f", obj.get("name").getAsString(), obj.get("winning_bid").getAsDouble()));
                }
                if (privList.getItems().isEmpty()) privList.getItems().add("You haven't won anything yet.");
            }
        }));
    }

    private Button createAnimatedButton(String text, String hexColor) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + hexColor + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15;");
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
        if (autoUpdater != null) autoUpdater.stop();
        if (networkClient != null) networkClient.disconnect();
    }

    public static void main(String[] args) { launch(args); }
}
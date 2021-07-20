package org.example.controller;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.model.command.Command;
import org.example.model.command.CommandType;
import org.example.model.parameter.ParameterType;
import org.example.model.user.User;
import org.example.netty.NettyNetwork;

import java.io.IOException;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ClientMainController {

    @FXML
    private Label emailLabel;
    @FXML
    private HBox pathHBox;
    @FXML
    private Button connectButton;
    @FXML
    private Button uploadButton;
    @FXML
    private TilePane filesTilePane;
    private NettyNetwork network;

    @FXML
    private void connectionButtonHandling() {
        if (network == null || !network.isConnected()) {
            connectionProcess();
        } else if (network.isConnected()) {
            emailLabel.setText("");
            filesTilePane.getChildren().clear();
            filesTilePane.setAlignment(Pos.CENTER);
            filesTilePane.getChildren().add(new Label("Disconnected..."));
            connectButton.setText("Connect");
            network.close();
        }
    }

    private void connectionProcess() {
        try {
            showConnectionWindow();
            network = new NettyNetwork(command -> {
                log.info("Command received: {}", command);
                switch (command.getCommandType()) {
                    case AUTH_OK:
                        Platform.runLater(() -> {
                            connectButton.setText("Disconnect");
                            emailLabel.setText(Config.getUser().getEmail());
                            filesTilePane.setAlignment(Pos.TOP_LEFT);
                            filesTilePane.getChildren().clear();
                        });
                        break;
                    case AUTH_NO:
                        Platform.runLater(() -> showErrorWindow("Bad credentials"));
                        network.close();
                        break;
                    case CONTENT_RESPONSE:
                        refreshClientContent(command);
                        break;
                }
            }, Config.getHost(), Config.getPort());
            authRequest();
        } catch (Exception e) {
            log.error("ConnectionProcess exception: {}", e.getMessage());
            showErrorWindow("Connection error: " + e.getMessage());
        }
    }

    private void upload() {
        if (network.isConnected()) {
//            uploadProcess();
        }
    }

    private void showConnectionWindow() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("connection.fxml"));
        Parent parent = loader.load();
        Stage stage = new Stage();
        stage.setTitle("Connection");
        stage.setScene(new Scene(parent));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        stage.showAndWait();
    }

    private void showErrorWindow(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void contentRequest(String currentDir) {
        try {
            Map<ParameterType, String> parameters = new HashMap<>();
            parameters.put(ParameterType.EMAIL, Config.getUser().getEmail());
            parameters.put(ParameterType.CURRENT_DIR, currentDir);
            network.writeMessage(new Command(CommandType.CONTENT_REQUEST, parameters));
        } catch (ConnectException e) {
            log.error("Connection exception: {}", e.getMessage());
        }
    }

    private void authRequest() {
        try {
            Map<ParameterType, User> parameters = new HashMap<>();
            parameters.put(ParameterType.USER, Config.getUser());
            network.writeMessage(new Command(CommandType.AUTH_REQUEST, parameters));
        } catch (ConnectException e) {
            log.error("Connection exception: {}", e.getMessage());
        }
    }

    private void refreshClientContent(Command command) {
        Platform.runLater(() -> {
            filesTilePane.getChildren().clear();
            pathHBox.getChildren().clear();
        });
        List<String> fullPath = (List<String>) command.getParameters().get(ParameterType.CURRENT_DIR);
        List<String> directories = (List<String>) command.getParameters().get(ParameterType.DIRECTORIES);
        List<String> files = (List<String>) command.getParameters().get(ParameterType.FILES);

        directories.forEach(p -> addElementToPane(p, true));
        files.forEach(f -> addElementToPane(f, false));
        fullPath.forEach(d -> addDirToHBox(d));
    }

    private void addDirToHBox(String dir) {
        Label label = new Label(dir);
        label.setPadding(new Insets(2, 3, 2, 3));
        label.setStyle("-fx-background-color: transparent;");
        label.setOnMouseEntered(l -> label.setStyle("-fx-background-color: -fx-shadow-highlight-color, -fx-outer-border, -fx-inner-border, -fx-body-color;"));
        label.setOnMouseExited(l -> label.setStyle("-fx-background-color: transparent;"));
        label.setOnMouseClicked(l -> contentRequest(getFullPath(label)));
        Platform.runLater(() -> pathHBox.getChildren().add(label));
    }

    private String getFullPath(Label label) {
        if (label.getText().equals("root")) {
            return "root";
        }
        ObservableList<Node> list = pathHBox.getChildren();
        if (list.size() > 1) {
            StringBuilder sb = new StringBuilder("/");
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i) == label) {
                    sb.append(((Label) list.get(i)).getText());
                    return sb.toString();
                }
                sb.append(((Label) list.get(i)).getText()).append("/");
            }
            return sb.toString();
        }
        return "/";
    }

    private void addElementToPane(String name, Boolean isDirectory) {
        Label label = new Label(name);
        label.setMinSize(60, 80);
        label.setMaxSize(60, 80);
        label.setContentDisplay(ContentDisplay.TOP);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setAlignment(Pos.TOP_CENTER);
        label.setFont(new Font(10));
        label.setWrapText(true);
        label.setStyle("-fx-background-color: transparent;");
        label.setOnMouseEntered(l -> label.setStyle("-fx-background-color: -fx-shadow-highlight-color, -fx-outer-border, -fx-inner-border, -fx-body-color;"));
        label.setOnMouseExited(l -> label.setStyle("-fx-background-color: transparent;"));
        Image image = null;
        if (isDirectory) {
            image = new Image("dir.png");
            label.setOnMouseClicked(l -> contentRequest(getFullPath(label) + label.getText()));
        } else {
            image = new Image("file.png");
        }
        label.setGraphic(new ImageView(image));
        Platform.runLater(() -> filesTilePane.getChildren().add(label));
    }

    public void closeConnection() {
        if (network != null) {
            network.close();
            log.info("Network closed");
        }
    }

}

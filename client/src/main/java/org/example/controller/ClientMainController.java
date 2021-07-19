package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
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
import javafx.stage.Window;
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
    private void connectionButtonHandling() throws IOException {
        if (network == null || !network.isConnected()) {
            connectionProcess();
        } else if (network.isConnected()) {
//            disconnectionProcess();
        }
    }

    private void connectionProcess() {
        try {
            showConnectionWindow();

            network = new NettyNetwork(command -> {
                log.info("Incoming command: {}", command);
                switch (command.getCommandType()) {
                    case AUTH_OK:
                        contentRequest("root");
                        Platform.runLater(() -> {
                            connectButton.setText("Disconnect");
                            emailLabel.setText(Config.getUser().getEmail());
                        });
                        break;
                    case AUTH_NO:
                        showErrorWindow("Bad credentials");
                        break;
                    case CONTENT_RESPONSE:
                        refreshClientContent(command);
                        break;
                }
            }, Config.getHost(), Config.getPort());

            while (!network.isConnected()) {
                Thread.sleep(10);
            }

            authRequest();

        } catch (Exception e) {
            e.printStackTrace();
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

    private void contentRequest(String currentDir) throws ConnectException {
        Map<ParameterType, String> parameters = new HashMap<>();
        parameters.put(ParameterType.EMAIL, Config.getUser().getEmail());
        parameters.put(ParameterType.CURRENT_DIR, currentDir);
        network.writeMessage(new Command(CommandType.CONTENT_REQUEST, parameters));
    }

    private void authRequest() throws ConnectException {
        Map<ParameterType, User> parameters = new HashMap<>();
        parameters.put(ParameterType.USER, Config.getUser());
        network.writeMessage(new Command(CommandType.AUTH_REQUEST, parameters));
    }

    private void refreshClientContent(Command command) {
        List<String> currentDir = (List<String>) command.getParameters().get(ParameterType.CURRENT_DIR);
        List<String> directories = (List<String>) command.getParameters().get(ParameterType.DIRECTORIES);
        List<String> files = (List<String>) command.getParameters().get(ParameterType.FILES);

        directories.forEach(p -> addPathToPane(p, true));
        files.forEach(f -> addPathToPane(f, false));

    }

    private void addPathToPane(String path, Boolean isDirectory) {
        Label label = new Label(path);
        label.setMinSize(60, 90);
        label.setMaxSize(60, 90);
        label.setContentDisplay(ContentDisplay.TOP);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setAlignment(Pos.TOP_CENTER);
        label.setFont(new Font(10));
        label.setWrapText(true);
        Image image = null;
        if (isDirectory) {
            image = new Image("dir.png");
        } else {
            image = new Image("file.png");
        }
        label.setGraphic(new ImageView(image));
        Platform.runLater(() -> filesTilePane.getChildren().add(label));
    }

    public void closeConnection() {
        if (network != null) {
            network.close();
        }
    }

}

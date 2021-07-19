package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.model.user.User;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.ResourceBundle;

public class ConnectionWindowController implements Initializable {

    @FXML
    private TextField hostTextField;
    @FXML
    private TextField portTextField;
    @FXML
    private TextField emailTextField;
    @FXML
    private PasswordField passwordTextField;
    @FXML
    private CheckBox checkBox;
    @FXML
    private Button connectButton;

    @FXML
    private void connect() {
        try {
            User user = User.builder()
                    .email(emailTextField.getText())
                    .password(passwordTextField.getText())
                    .build();

            Config.setUser(user);
            Config.setHost(hostTextField.getText());
            Config.setPort(portTextField.getText());

            saveProperties();

            Stage stage = (Stage) connectButton.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            showErrorWindow(e.getMessage());
        }
    }

    private void loadProperties() {
        Properties properties = new Properties();
        try {
            if (Files.notExists(Paths.get("properties"))) {
                Files.createFile(Paths.get("properties"));
            }
            properties.load(Files.newBufferedReader(Paths.get("properties")));
            hostTextField.setText(properties.getProperty("host", ""));
            portTextField.setText(properties.getProperty("port", ""));
            emailTextField.setText(properties.getProperty("email", ""));
            passwordTextField.setText(properties.getProperty("password", ""));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveProperties() {
        if (checkBox.isSelected()) {
            Properties properties = new Properties();
            properties.setProperty("host", hostTextField.getText());
            properties.setProperty("port", portTextField.getText());
            properties.setProperty("email", emailTextField.getText());
            properties.setProperty("password", passwordTextField.getText());
            try {
                properties.store(Files.newOutputStream(Paths.get("properties")), "");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                Files.deleteIfExists(Paths.get("properties"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadProperties();
    }

    public void showErrorWindow(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

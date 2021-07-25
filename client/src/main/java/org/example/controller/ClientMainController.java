package org.example.controller;

import com.sun.javafx.scene.control.skin.ContextMenuContent;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.model.command.Command;
import org.example.model.command.CommandType;
import org.example.model.command.ContentActionType;
import org.example.model.command.ParameterType;
import org.example.model.dto.FileDTO;
import org.example.netty.NettyNetwork;

import java.io.*;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    @FXML
    private AnchorPane mainPane;
    private NettyNetwork network;
    private boolean isManualDisconnect;

    @FXML
    private void connectionButtonHandling() {
        if (network == null || !network.isConnected()) {
            connectionProcess();
            isManualDisconnect = false;
        } else if (network.isConnected()) {
            disconnectionProcess();
            isManualDisconnect = true;
        }
    }

    @FXML
    private void upload() {
        if (network.isConnected()) {
            uploadProcess();
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
                            uploadButton.setDisable(false);
                        });
                        break;
                    case AUTH_NO:
                        Platform.runLater(() -> {
                            showAlertWindow("Bad credentials", Alert.AlertType.ERROR);
                        });
                        network.close();
                        break;
                    case CONTENT_RESPONSE:
                        refreshClientContent(command);
                        break;
                    case FILE_UPLOAD_OK:
                    case DELETE_OK:
                    case RENAME_OK:
                        Platform.runLater(() -> showAlertWindow("Successful", Alert.AlertType.INFORMATION));
                        break;
                    case ERROR:
                        Platform.runLater(() -> showAlertWindow((String) command.getParameter(ParameterType.MESSAGE), Alert.AlertType.ERROR));
                        break;
                    case FILE_DOWNLOAD:
                        Platform.runLater(() -> downloadFileSave(command));
                        break;
                }
            }, Config.getHost(), Config.getPort());
            authRequest();
        } catch (Exception e) {
            log.error("ConnectionProcess exception: {}", e.getMessage());
        }
        runConnectionInspector();
    }

    private void downloadFileSave(Command command) {
        FileDTO dto = (FileDTO) command.getParameter(ParameterType.FILE_DTO);
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        fileChooser.setTitle("Save file");
        fileChooser.setInitialFileName(dto.getName());
        File file = fileChooser.showSaveDialog(mainPane.getScene().getWindow());
        if (file != null) {
            try (FileOutputStream os = new FileOutputStream(file)) {
                os.write(dto.getContent());
            } catch (IOException e) {
                log.error("File save exception: {}", e.getMessage());
            }
            if (file.length() != dto.getSize() || !dto.getMd5().equals(getFileChecksum(file))) {
                showAlertWindow("File is corrupted", Alert.AlertType.ERROR);
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (IOException e) {
                    log.error("Corrupted file delete exception: {}", e.getMessage());
                }
            }
            showAlertWindow("File saved succsessful", Alert.AlertType.INFORMATION);
        }
    }

    private void disconnectionProcess() {
        emailLabel.setText("");
        filesTilePane.getChildren().clear();
        filesTilePane.setAlignment(Pos.CENTER);
        filesTilePane.getChildren().add(new Label("Disconnected..."));
        connectButton.setText("Connect");
        pathHBox.getChildren().clear();
        uploadButton.setDisable(true);
        network.close();
    }

    private void uploadProcess() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        fileChooser.setTitle("Choose upload file");
        File file = fileChooser.showOpenDialog(mainPane.getScene().getWindow());
        if (file != null) {
            try (FileInputStream is = new FileInputStream(file)) {
                byte[] buffer = new byte[is.available()];
                int size = is.read(buffer);
                String md5 = getFileChecksum(file);

                network.writeMessage(new Command(CommandType.FILE_UPLOAD)
                        .setParameter(ParameterType.FILE_DTO,
                                FileDTO.builder()
                                        .owner(Config.getUser())
                                        .name(file.getName())
                                        .path(getFullPath(new Label()))
                                        .content(buffer)
                                        .size((long) size)
                                        .md5(md5)
                                        .build())
                );
            } catch (Exception e) {
                log.error("UploadProcess exception: {}", e.getMessage());
            }
        }
    }

    private String getFileChecksum(File file) {
        String md5 = "";
        try (InputStream is = Files.newInputStream(Paths.get(file.toURI()))) {
            md5 = DigestUtils.md5Hex(is);
        } catch (Exception e) {
            log.error("File checkSum exception: {}", e.getMessage());
        }
        return md5;
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

    private void showAlertWindow(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void contentRequest(String currentDir, ContentActionType actionType) {
        Command command = new Command(CommandType.CONTENT_REQUEST)
                .setParameter(ParameterType.CONTENT_ACTION, actionType)
                .setParameter(ParameterType.USER, Config.getUser())
                .setParameter(ParameterType.CURRENT, currentDir);
        switch (actionType) {
            case DELETE:
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Delete");
                alert.setHeaderText("Are you sure you want to delete?");
                Optional<ButtonType> result = alert.showAndWait();
                if (!result.isPresent() || result.get() != ButtonType.OK) {
                    return;
                }
                break;
            case RENAME:
                String oldName = currentDir.substring(currentDir.lastIndexOf("/") + 1);
                TextInputDialog dialog = new TextInputDialog(oldName);
                dialog.setTitle("Rename");
                dialog.setHeaderText("Enter new name");
                Optional<String> newName = dialog.showAndWait();
                if (newName.isPresent() && isValidName(newName.get()) && !oldName.equals(newName.get())) {
                    command.setParameter(ParameterType.NEW_NAME, newName.get());
                } else {
                    return;
                }
                break;
            case DOWNLOAD:
                break;
        }
        try {
            network.writeMessage(command);
        } catch (Exception e) {
            log.error("Content request exception: {}", e.getMessage());
            showAlertWindow(e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private boolean isValidName(String s) {
        if (s.trim().isEmpty()) {
            return false;
        }
        for (char ch : Config.getForbidden()) {
            if (s.contains("" + ch)) {
                showAlertWindow("Forbidden symbol: " + ch, Alert.AlertType.ERROR);
                return false;
            }
        }
        return true;
    }

    private void authRequest() {
        try {
            network.writeMessage(new Command(CommandType.AUTH_REQUEST).setParameter(ParameterType.USER, Config.getUser()));
        } catch (ConnectException e) {
            log.error("Connection exception: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshClientContent(Command command) {
        Platform.runLater(() -> {
            filesTilePane.getChildren().clear();
            pathHBox.getChildren().clear();
        });
        List<String> fullPath = (List<String>) command.getParameter(ParameterType.CURRENT);
        List<String> directories = (List<String>) command.getParameter(ParameterType.DIRECTORIES);
        List<String> files = (List<String>) command.getParameter(ParameterType.FILES);

        directories.forEach(p -> addElementToPane(p, true));
        files.forEach(f -> addElementToPane(f, false));
        fullPath.forEach(this::addDirToHBox);
    }

    private void addDirToHBox(String dir) {
        Label label = new Label(dir);
        label.setPadding(new Insets(2, 3, 2, 3));
        label.setStyle("-fx-background-color: transparent;");
        label.setOnMouseEntered(event -> label.setStyle("-fx-background-color: -fx-shadow-highlight-color, -fx-outer-border, -fx-inner-border, -fx-body-color;"));
        label.setOnMouseExited(event -> label.setStyle("-fx-background-color: transparent;"));
        label.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                ClientMainController.this.contentRequest(getFullPath(label), ContentActionType.OPEN);
            }
        });
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
        label.setPickOnBounds(true);
        label.setMinSize(60, 100);
        label.setMaxSize(60, 100);
        label.setContentDisplay(ContentDisplay.TOP);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setAlignment(Pos.TOP_CENTER);
        label.setFont(new Font(10));
        Image image;
        if (isDirectory) {
            image = new Image("dir.png");
            label.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    contentRequest(getFullPath(label) + label.getText(), ContentActionType.OPEN);
                }
                event.consume();
            });
            addLabelContextMenu(label, ContentActionType.OPEN, ContentActionType.RENAME, ContentActionType.DELETE);
        } else {
            image = new Image("file.png");
            addLabelContextMenu(label, ContentActionType.DOWNLOAD, ContentActionType.RENAME, ContentActionType.DELETE);
        }
        label.setGraphic(new ImageView(image));
        Platform.runLater(() -> filesTilePane.getChildren().add(label));
    }

    public void closeConnection() {
        isManualDisconnect = true;
        if (network != null) {
            network.close();
            log.info("Network closed");
        }
    }

    private void runConnectionInspector() {
        Thread inspector = new Thread(() -> {
            while (network != null && network.isConnected()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (!isManualDisconnect) {
                Platform.runLater(() -> {
                    showAlertWindow("Connection lost", Alert.AlertType.ERROR);
                    disconnectionProcess();
                });
            }
        });
        inspector.setDaemon(true);
        inspector.start();
    }

    private void addLabelContextMenu(Label label, ContentActionType... actionTypes) {
        ContextMenu menu = new ContextMenu();
        menu.addEventFilter(MouseEvent.ANY, event -> {
            if (event.isPrimaryButtonDown()) {
                String s = ((ContextMenuContent.MenuItemContainer) event.getTarget()).getItem().getId();
                ContentActionType type = ContentActionType.valueOf(s.toUpperCase(Locale.ROOT));
                Platform.runLater(() -> contentRequest(getFullPath(label) + label.getText(), type));
                event.consume();
            }
        });
        for (ContentActionType actionType : actionTypes) {
            MenuItem item = new MenuItem(actionType.getValue());
            item.setId(actionType.getValue());
            menu.getItems().add(item);
        }
        label.setOnContextMenuRequested(event -> menu.show(filesTilePane, event.getScreenX() - 5, event.getScreenY() - 5));

        label.setOnMouseEntered(event -> {
            label.setStyle("-fx-background-color: -fx-shadow-highlight-color," +
                    "-fx-outer-border, -fx-inner-border, -fx-body-color;");
            label.setWrapText(true);
        });
        label.setOnMouseExited(event -> {
            if (menu.isShowing()) {
                menu.hide();
            }
            label.setStyle("-fx-background-color: transparent;");
            label.setWrapText(false);
        });
    }
}

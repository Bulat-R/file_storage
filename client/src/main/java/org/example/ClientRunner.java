package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.controller.ClientMainController;

@Slf4j
public class ClientRunner extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("main.fxml"));
        Parent parent = loader.load();
        primaryStage.setTitle("My file storage");
        primaryStage.setScene(new Scene(parent));
        primaryStage.setOnCloseRequest(event -> {
            ((ClientMainController) loader.getController()).closeConnection();
            primaryStage.close();
            log.info("Client closed");
        });
        primaryStage.getIcons().add(new Image("client2.png"));
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        primaryStage.show();
    }
}

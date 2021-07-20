package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.controller.ClientMainController;

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
        });
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        primaryStage.show();
    }
}

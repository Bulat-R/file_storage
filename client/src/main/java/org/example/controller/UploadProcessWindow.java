package org.example.controller;

import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class UploadProcessWindow extends Stage {

    public UploadProcessWindow(Task<?> task) {
        ProgressBar progressBar = new ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.getChildren().add(progressBar);
        AnchorPane.setLeftAnchor(progressBar, 0.0);
        AnchorPane.setBottomAnchor(progressBar, 0.0);
        AnchorPane.setRightAnchor(progressBar, 0.0);
        AnchorPane.setTopAnchor(progressBar, 0.0);
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, event -> close());
        Scene scene = new Scene(anchorPane, 200, 20);
        initStyle(StageStyle.UNDECORATED);
        setTitle("Upload progress");
        setScene(scene);
        initModality(Modality.APPLICATION_MODAL);
        new Thread(task).start();
        showAndWait();
    }
}

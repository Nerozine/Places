package com.networkcourse;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.SneakyThrows;

import java.net.URL;

@Getter
public class Main extends Application {
    private static final RequestManager manager = new RequestManager();

    public static RequestManager getRequestManager() {
        return manager;
    }

    @Override
    @SneakyThrows
    public void start(Stage primaryStage) {
        FXMLLoader loader = new FXMLLoader();
        URL xmlUrl = getClass().getResource("/mainScene.fxml");
        loader.setLocation(xmlUrl);
        Parent root = loader.load();

        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) {
        Application.launch(args);
    }
}
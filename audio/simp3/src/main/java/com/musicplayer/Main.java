package com.musicplayer;

import java.io.IOException;

import com.musicplayer.ui.controllers.MainController;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Main entry point for the SiMP3 music player application.
 */
public class Main extends Application {

    private MainController mainController;

    /**
     * The main entry point for all JavaFX applications.
     *
     * @param primaryStage The primary stage for this application, onto which
     *                     the application scene can be set.
     * @throws IOException If the FXML file cannot be loaded.
     */
    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        mainController = loader.getController();
        
        Scene scene = new Scene(root, 1200, 800);

        primaryStage.setTitle("SiMP3 - Simple Music Player");
        
        // Set app icon
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/icons/app.png")));
        
        primaryStage.setScene(scene);
        
        // Set up window state monitoring for audio visualizer
        mainController.setupWindowStateMonitoring(primaryStage);
        
        // Handle application close event to cleanup resources
        primaryStage.setOnCloseRequest(event -> {
            if (mainController != null) {
                mainController.cleanup();
            }
        });
        
        primaryStage.show();
    }

    /**
     * The main method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}

package edu.bauet.java.cse.duckrun;

import edu.bauet.java.cse.duckrun.scenes.StoryScene;
import edu.bauet.java.cse.duckrun.utils.AssetLoader;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("Duck Dash");
        primaryStage.setResizable(false);

        // Preload all game assets
        AssetLoader.preloadAssets();

        // Start with the Story Scene
        StoryScene storyScene = new StoryScene();
        Scene scene = storyScene.createScene(stage);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void switchScene(Scene scene) {
        primaryStage.setScene(scene);
    }

    public static Stage getStage() {
        return primaryStage;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
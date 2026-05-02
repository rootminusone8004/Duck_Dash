package edu.bauet.java.cse.duckrun.scenes;

import edu.bauet.java.cse.duckrun.MainApp;
import edu.bauet.java.cse.duckrun.utils.AssetLoader;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

public class StoryScene {

    private volatile boolean hasNavigated = false;
    private static final int MAX_RETRIES = 3;

    public Scene createScene(Stage stage) {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: black;");

        MediaPlayer[] mpHolder = {null};

        Scene scene = new Scene(root, MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE) {
                navigateToMenu(stage, mpHolder[0]);
            }
        });

        addSkipLabel(root);
        attemptVideoLoad(root, stage, mpHolder, 1);

        return scene;
    }

    private void attemptVideoLoad(StackPane root, Stage stage, MediaPlayer[] mpHolder, int attempt) {
        System.out.println("Video load attempt " + attempt + "/" + MAX_RETRIES);

        root.getChildren().removeIf(n -> n instanceof MediaView);

        // Get the pre-written temp file URI — fully on disk, no JAR streaming
        String videoUri = AssetLoader.loadVideoUri("/Story/opening2.mp4");
        if (videoUri == null) {
            showImageFallback(root, stage);
            return;
        }

        Media video;
        try {
            video = new Media(videoUri);
        } catch (Exception e) {
            System.out.println("Media creation failed: " + e.getMessage());
            retryOrFallback(root, stage, mpHolder, attempt);
            return;
        }

        MediaPlayer player = new MediaPlayer(video);
        mpHolder[0] = player;

        player.setRate(1.0);

        MediaView mv = new MediaView(player);
        root.getChildren().add(0, mv);

        boolean[] everPlayed = {false};
        PauseTransition[] stallTimer = {null};

        player.setOnReady(() -> {
            mv.setFitWidth(MainApp.WINDOW_WIDTH);
            mv.setFitHeight(MainApp.WINDOW_HEIGHT);
            mv.setPreserveRatio(true);
            mv.setSmooth(true);

            player.setMute(true);

            PauseTransition startDelay = new PauseTransition(Duration.millis(300));
            startDelay.setOnFinished(e -> {
                player.play();
                // Unmute after another 200ms — by then video decoder is running
                PauseTransition unmuteDelay = new PauseTransition(Duration.millis(200));
                unmuteDelay.setOnFinished(u -> player.setMute(false));
                unmuteDelay.play();
            });
            startDelay.play();
        });

        player.setOnPlaying(() -> everPlayed[0] = true);
        player.setOnEndOfMedia(() -> navigateToMenu(stage, player));

        player.setOnError(() -> {
            System.out.println("Attempt " + attempt + " error: " + player.getError());
            player.dispose();
            mpHolder[0] = null;
            retryOrFallback(root, stage, mpHolder, attempt);
        });

        player.statusProperty().addListener((obs, oldStatus, newStatus) -> {
            if (newStatus == MediaPlayer.Status.STALLED) {
                PauseTransition timer = new PauseTransition(Duration.seconds(8));
                timer.setOnFinished(e -> {
                    if (player.getStatus() == MediaPlayer.Status.STALLED) {
                        player.dispose();
                        mpHolder[0] = null;
                        Platform.runLater(() -> retryOrFallback(root, stage, mpHolder, attempt));
                    }
                });
                stallTimer[0] = timer;
                timer.play();
            } else if (newStatus == MediaPlayer.Status.PLAYING) {
                if (stallTimer[0] != null) {
                    stallTimer[0].stop();
                    stallTimer[0] = null;
                }
            }
        });

        // Watchdog — if video hasn't started playing within 5 seconds, retry
        PauseTransition readyWatchdog = new PauseTransition(Duration.seconds(5));
        readyWatchdog.setOnFinished(e -> {
            if (!everPlayed[0] && player.getStatus() != MediaPlayer.Status.DISPOSED) {
                System.out.println("Ready watchdog fired — video never played.");
                player.dispose();
                mpHolder[0] = null;
                Platform.runLater(() -> retryOrFallback(root, stage, mpHolder, attempt));
            }
        });
        readyWatchdog.play();
    }

    private void retryOrFallback(StackPane root, Stage stage, MediaPlayer[] mpHolder, int attempt) {
        if (attempt < MAX_RETRIES) {
            System.out.println("Retrying in 500ms...");
            PauseTransition retry = new PauseTransition(Duration.millis(500));
            retry.setOnFinished(e -> Platform.runLater(() ->
                    attemptVideoLoad(root, stage, mpHolder, attempt + 1)));
            retry.play();
        } else {
            System.out.println("All retries exhausted — showing image fallback.");
            Platform.runLater(() -> showImageFallback(root, stage));
        }
    }

    private void addSkipLabel(StackPane root) {
        Label skipLabel = new Label("Press SPACE to Skip");
        Font pixelFont = Font.loadFont(
                getClass().getResourceAsStream("/fonts/PressStart2P-Regular.ttf"), 12);
        if (pixelFont != null) skipLabel.setFont(pixelFont);
        skipLabel.setTextFill(Color.WHITE);
        skipLabel.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-padding:6 12 6 12; -fx-background-radius:5;");
        skipLabel.setVisible(false);

        PauseTransition labelDelay = new PauseTransition(Duration.seconds(3));
        labelDelay.setOnFinished(e -> skipLabel.setVisible(true));
        labelDelay.play();

        root.getChildren().add(skipLabel);
        StackPane.setAlignment(skipLabel, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(skipLabel, new Insets(0, 20, 20, 0));
    }

    private void showImageFallback(StackPane root, Stage stage) {
        root.getChildren().removeIf(n -> n instanceof MediaView);
        Image img = AssetLoader.getImage("/Story/startstory.png");
        ImageView iv = new ImageView(img);
        iv.setFitWidth(MainApp.WINDOW_WIDTH);
        iv.setFitHeight(MainApp.WINDOW_HEIGHT);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        root.getChildren().add(0, iv);
    }

    private void navigateToMenu(Stage stage, MediaPlayer mp) {
        if (hasNavigated) return;
        hasNavigated = true;

        Platform.runLater(() -> {
            if (mp != null) {
                mp.stop();
                mp.dispose();
            }
            MenuScene menuScene = new MenuScene(stage);
            MainApp.switchScene(menuScene.createScene());
        });
    }
}
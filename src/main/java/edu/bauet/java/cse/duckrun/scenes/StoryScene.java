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

        Media video;
        try {
            video = AssetLoader.loadFreshVideo("/Story/opening.mp4");
            if (video == null) {
                showImageFallback(root, stage);
                return;
            }
        } catch (Exception e) {
            showImageFallback(root, stage);
            return;
        }

        MediaPlayer player = new MediaPlayer(video);
        mpHolder[0] = player;

        // KEY FIX 1: Never let GStreamer skip frames to catch up.
        // When decoding falls behind, this keeps playback at real speed
        // rather than racing ahead to sync with the clock.
        player.setRate(1.0);

        // KEY FIX 2: Mute audio-video sync corrections that cause speed-up.
        // JavaFX's default AV sync drops video frames to stay locked to audio —
        // disabling this keeps frames in order at the cost of slight drift,
        // which is far less noticeable than sudden speed bursts.
        System.setProperty("com.sun.media.jfxmediaimpl.platform.gstreamer.GSTPlatform.DISABLE_AV_SYNC", "true");

        MediaView mv = new MediaView(player);
        root.getChildren().add(0, mv);

        boolean[] everPlayed = {false};
        PauseTransition[] stallTimer = {null};

        player.setOnReady(() -> {
            mv.setFitWidth(MainApp.WINDOW_WIDTH);
            mv.setFitHeight(MainApp.WINDOW_HEIGHT);
            mv.setPreserveRatio(true);
            mv.setSmooth(true);

            // KEY FIX 3: Small delay before play so the JavaFX scene graph
            // has fully rendered the MediaView surface before the first frame.
            // Without this, early frames are dropped while the surface initializes,
            // making the video appear to start fast.
            PauseTransition startDelay = new PauseTransition(Duration.millis(200));
            startDelay.setOnFinished(e -> player.play());
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

        PauseTransition readyWatchdog = new PauseTransition(Duration.seconds(5));
        readyWatchdog.setOnFinished(e -> {
            if (!everPlayed[0] && player.getStatus() != MediaPlayer.Status.DISPOSED) {
                player.dispose();
                mpHolder[0] = null;
                Platform.runLater(() -> retryOrFallback(root, stage, mpHolder, attempt));
            }
        });
        readyWatchdog.play();
    }

    private void retryOrFallback(StackPane root, Stage stage, MediaPlayer[] mpHolder, int attempt) {
        if (attempt < MAX_RETRIES) {
            PauseTransition retry = new PauseTransition(Duration.millis(500));
            retry.setOnFinished(e -> Platform.runLater(() ->
                    attemptVideoLoad(root, stage, mpHolder, attempt + 1)));
            retry.play();
        } else {
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
package edu.bauet.java.cse.duckrun.scenes;

import edu.bauet.java.cse.duckrun.MainApp;
import edu.bauet.java.cse.duckrun.utils.AssetLoader;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Plays the ending cutscene video, then hands off to CreditsScene.
 * SPACE skips straight to CreditsScene.
 */
public class EndingScene {

    private volatile boolean hasNavigated = false;
    private static final int MAX_RETRIES = 3;
    private MediaPlayer videoPlayer = null;

    public Scene createScene() {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: black;");

        Scene scene = new Scene(root, MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE) {
                navigateToCredits(); // SPACE skips video → goes to credits
            }
        });

        attemptVideoLoad(root, 1);
        return scene;
    }

    private void attemptVideoLoad(StackPane root, int attempt) {
        System.out.println("Ending video load attempt " + attempt + "/" + MAX_RETRIES);
        root.getChildren().removeIf(n -> n instanceof MediaView);

        String videoUri = AssetLoader.loadVideoUri("/Story/ending.mp4");
        if (videoUri == null) {
            System.out.println("Ending video URI null — going to credits.");
            navigateToCredits();
            return;
        }

        Media videoMedia;
        try {
            videoMedia = new Media(videoUri);
        } catch (Exception e) {
            System.out.println("Ending media creation failed: " + e.getMessage());
            retryOrFallback(root, attempt);
            return;
        }

        videoPlayer = new MediaPlayer(videoMedia);
        videoPlayer.setRate(1.0);

        MediaView mv = new MediaView(videoPlayer);
        root.getChildren().add(0, mv);

        boolean[] everPlayed = {false};
        PauseTransition[] stallTimer = {null};

        videoPlayer.setOnReady(() -> {
            mv.setFitWidth(MainApp.WINDOW_WIDTH);
            mv.setFitHeight(MainApp.WINDOW_HEIGHT);
            mv.setPreserveRatio(true);
            mv.setSmooth(true);

            videoPlayer.setMute(true);
            PauseTransition startDelay = new PauseTransition(Duration.millis(300));
            startDelay.setOnFinished(e -> {
                videoPlayer.play();
                PauseTransition unmuteDelay = new PauseTransition(Duration.millis(200));
                unmuteDelay.setOnFinished(u -> videoPlayer.setMute(false));
                unmuteDelay.play();
            });
            startDelay.play();
        });

        videoPlayer.setOnPlaying(() -> everPlayed[0] = true);

        // ── Video ends → go to credits ─────────────────────────────────────
        videoPlayer.setOnEndOfMedia(this::navigateToCredits);

        videoPlayer.setOnError(() -> {
            System.out.println("Ending video error: " + videoPlayer.getError());
            disposeAll();
            retryOrFallback(root, attempt);
        });

        videoPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
            if (newStatus == MediaPlayer.Status.STALLED) {
                PauseTransition timer = new PauseTransition(Duration.seconds(8));
                timer.setOnFinished(e -> {
                    if (videoPlayer != null &&
                            videoPlayer.getStatus() == MediaPlayer.Status.STALLED) {
                        disposeAll();
                        Platform.runLater(() -> retryOrFallback(root, attempt));
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
            if (!everPlayed[0] &&
                    videoPlayer != null &&
                    videoPlayer.getStatus() != MediaPlayer.Status.DISPOSED) {
                System.out.println("Ending watchdog fired — skipping to credits.");
                disposeAll();
                Platform.runLater(() -> retryOrFallback(root, attempt));
            }
        });
        readyWatchdog.play();
    }

    private void disposeAll() {
        if (videoPlayer != null) {
            videoPlayer.stop();
            videoPlayer.dispose();
            videoPlayer = null;
        }
    }

    private void retryOrFallback(StackPane root, int attempt) {
        if (attempt < MAX_RETRIES) {
            System.out.println("Retrying ending video in 500ms...");
            PauseTransition retry = new PauseTransition(Duration.millis(500));
            retry.setOnFinished(e -> Platform.runLater(() ->
                    attemptVideoLoad(root, attempt + 1)));
            retry.play();
        } else {
            System.out.println("All retries exhausted — going to credits.");
            Platform.runLater(this::navigateToCredits);
        }
    }

    private void navigateToCredits() {
        if (hasNavigated) return;
        hasNavigated = true;

        Platform.runLater(() -> {
            disposeAll();
            CreditsScene credits = new CreditsScene();
            MainApp.switchScene(credits.createScene());
        });
    }
}
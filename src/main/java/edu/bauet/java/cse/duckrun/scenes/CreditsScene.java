package edu.bauet.java.cse.duckrun.scenes;

import edu.bauet.java.cse.duckrun.MainApp;
import edu.bauet.java.cse.duckrun.utils.MusicManager;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.transform.Scale;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.nio.file.Paths;

/**
 * CreditsScene — Ending credits for Duck Dash.
 *
 * Flow:
 *   1. Black screen fades in
 *   2. "Thank You for Playing" fades in and holds
 *   3. Full credits scroll upward (including title image at top)
 *   4. After scroll completes → fade to black → return to MenuScene
 *   SPACE at any time skips directly to MenuScene.
 *
 * All visual styling is controlled by:
 *   src/main/resources/styles/credits_scene.css
 *
 * Can also be launched standalone from the Settings menu via:
 *   new CreditsScene().createScene()
 */
public class CreditsScene {

    // ── Timing constants ──────────────────────────────────────────────────────
    private static final double SCROLL_SPEED_PX_PER_SEC = 60.0;
    private static final long   THANKYOU_HOLD_MS        = 2_200;
    private static final double THANKYOU_FADE_IN_SEC    = 1.8;
    private static final double SCREEN_FADE_IN_SEC      = 0.6;
    private static final double FINAL_FADE_OUT_SEC      = 1.2;

    // Title image — absolute path on disk
    private static final String TITLE_IMAGE_PATH =
            "D:/JavaProject/2D_Duck_in_Bauet/src/main/resources/images/ui/menu/title2.png";

    // ── Title image position controls ─────────────────────────────────────────
    // Tweak these to reposition the title image without touching anything else.
    private static final double TITLE_IMAGE_WIDTH          = 480; // rendered width in px
    private static final double TITLE_IMAGE_TOP_PADDING    = 60;  // space above the image
    private static final double TITLE_IMAGE_BOTTOM_PADDING = 50;  // space below the image

    // Credits music resource path
    private static final String CREDITS_MUSIC = "/audio/music/credits.mp3";

    // CSS resource path
    private static final String CREDITS_CSS = "/styles/credits_scene.css";

    // ── State ─────────────────────────────────────────────────────────────────
    private volatile boolean hasNavigated = false;
    private MediaPlayer      musicPlayer  = null;
    private AnimationTimer   scrollTimer  = null;

    // ── Public entry point ────────────────────────────────────────────────────

    public Scene createScene() {

        // Root: StackPane for overlays (thank you, black fade)
        StackPane root = new StackPane();
        root.getStyleClass().add("credits-root");
        root.setPrefSize(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        root.setMinSize(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        root.setMaxSize(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);

        // Scroll container: plain Pane — does NOT constrain children to its own
        // size, so the VBox can grow to its full natural content height.
        // StackPane would squash the VBox to 720px, hiding everything past the
        // first screenfull and breaking translateY-based scrolling.
        Pane scrollPane = new Pane();
        scrollPane.setPrefSize(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        scrollPane.setClip(new Rectangle(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT));

        StackPane thankYouOverlay = buildThankYouOverlay();
        VBox creditsColumn = buildCreditsColumn();

        scrollPane.getChildren().add(creditsColumn);
        root.getChildren().addAll(scrollPane, thankYouOverlay);

        // ── Scale-to-fit: content stays at 1280x720, window can be any size ─────
        // Group is used as wrapper because it sizes itself to the transformed
        // bounds of its child — unlike StackPane which ignores transforms for layout.
        Scale creditsScale = new Scale(1, 1, 0, 0);
        root.getTransforms().add(creditsScale);
        javafx.scene.Group scaleWrapper = new javafx.scene.Group(root);

        Scene scene = new Scene(scaleWrapper);
        scene.widthProperty().addListener((obs, o, n) -> {
            double s = Math.min(n.doubleValue() / MainApp.WINDOW_WIDTH, scene.getHeight() / MainApp.WINDOW_HEIGHT);
            creditsScale.setX(s); creditsScale.setY(s);
        });
        scene.heightProperty().addListener((obs, o, n) -> {
            double s = Math.min(scene.getWidth() / MainApp.WINDOW_WIDTH, n.doubleValue() / MainApp.WINDOW_HEIGHT);
            creditsScale.setX(s); creditsScale.setY(s);
        });
        scene.getStylesheets().add(getClass().getResource(CREDITS_CSS).toExternalForm());

        // SPACE skips to menu at any time
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE) skipToMenu();
        });

        // Black overlay fades out on entry, then sequence begins
        Rectangle blackOverlay = new Rectangle(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT, Color.BLACK);
        root.getChildren().add(blackOverlay);

        FadeTransition screenFadeIn = new FadeTransition(Duration.seconds(SCREEN_FADE_IN_SEC), blackOverlay);
        screenFadeIn.setFromValue(1.0);
        screenFadeIn.setToValue(0.0);
        screenFadeIn.setOnFinished(evt -> {
            root.getChildren().remove(blackOverlay);
            showThankYouThenScroll(thankYouOverlay, creditsColumn, root);
        });
        screenFadeIn.play();

        startMusic();
        return scene;
    }

    // ── "Thank You for Playing" overlay ──────────────────────────────────────

    private StackPane buildThankYouOverlay() {
        Label label = new Label("Thank You for Playing");
        label.getStyleClass().add("credits-thankyou-label");
        label.setOpacity(0.0);

        StackPane overlay = new StackPane(label);
        overlay.getStyleClass().add("credits-thankyou-overlay");
        overlay.setPrefSize(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        overlay.setMouseTransparent(true);
        return overlay;
    }

    private void showThankYouThenScroll(StackPane overlay, VBox creditsColumn, StackPane root) {
        Label label = (Label) overlay.getChildren().get(0);

        FadeTransition fadeIn = new FadeTransition(Duration.seconds(THANKYOU_FADE_IN_SEC), label);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        PauseTransition hold = new PauseTransition(Duration.millis(THANKYOU_HOLD_MS));

        FadeTransition fadeOut = new FadeTransition(Duration.seconds(1.0), overlay);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            root.getChildren().remove(overlay);
            startScrolling(creditsColumn, root);
        });

        new SequentialTransition(fadeIn, hold, fadeOut).play();
    }

    // ── Credits column ────────────────────────────────────────────────────────

    private VBox buildCreditsColumn() {
        VBox col = new VBox();
        col.getStyleClass().add("credits-column");
        col.setAlignment(Pos.TOP_CENTER);
        col.setSpacing(0);

        // Bind width so content centers correctly inside the Pane.
        // translateY starts at WINDOW_HEIGHT so the column begins fully
        // off-screen below, then scrolls up into view.
        col.setPrefWidth(MainApp.WINDOW_WIDTH);
        col.setTranslateY(MainApp.WINDOW_HEIGHT);

        // ── Title image ───────────────────────────────────────────────────
        // To reposition: adjust TITLE_IMAGE_WIDTH, TITLE_IMAGE_TOP_PADDING,
        // and TITLE_IMAGE_BOTTOM_PADDING at the top of this class.
        ImageView titleImg = loadTitleImage();
        if (titleImg != null) {
            StackPane imgWrapper = new StackPane(titleImg);
            imgWrapper.setPadding(new Insets(
                    TITLE_IMAGE_TOP_PADDING, 0, TITLE_IMAGE_BOTTOM_PADDING, 0));
            col.getChildren().add(imgWrapper);
        }

        addSpacer(col, 30);

        // ── A Game by ─────────────────────────────────────────────────────
        col.getChildren().add(makeLabel("A Game by",        "credits-small-gray"));
        addSpacer(col, 14);
        col.getChildren().add(makeLabel("Team Duck Dash", "credits-team-name"));

        addSpacer(col, 100);
        addDivider(col);
        addSpacer(col, 80);

        // ── Individual roles — header first, name(s) below ────────────────

        addRoleEntry(col, "Story & Concept",
                "Tawfik Rahman Shabab");
        addSpacer(col, 80);

        addRoleEntry(col, "Project Management",
                "Muhammad Rasek Biswas");
        addSpacer(col, 80);

        addRoleEntry(col, "UI & Scene Design",
                "Abdullah Hil Kafi");
        addSpacer(col, 80);

        addRoleEntry(col, "Game Logic",
                "Tawfik Rahman Shabab",
                "Muhammad Rasek Biswas");
        addSpacer(col, 80);

        addRoleEntry(col, "Entity & Level Design",
                "Abdullah Hil Kafi");
        addSpacer(col, 80);

        addRoleEntry(col, "Sound & Music",
                "Abdullah Hil Kafi",
                "Tawfik Rahman Shabab");
        addSpacer(col, 20);
        col.getChildren().add(makeLabel(
                "Music Generated with Assistance of Suno AI", "credits-small-italic"));

        addSpacer(col, 100);
        addDivider(col);
        addSpacer(col, 80);

        // ── Built With ────────────────────────────────────────────────────
        col.getChildren().add(makeLabel("Built With", "credits-section-header"));
        addSpacer(col, 50);

        addToolGroup(col, "Language & Runtime",
                "Java 21   ·   JDK 17+");
        addSpacer(col, 50);
        addToolGroup(col, "Framework & UI",
                "JavaFX 21",
                "JavaFX AnimationTimer",
                "JavaFX Media API",
                "JavaFX FXML",
                "CSS (JavaFX stylesheets)");
        addSpacer(col, 50);
        addToolGroup(col, "Build & Dependency Management",
                "Apache Maven 3.8+",
                "javafx-maven-plugin 0.0.8");
        addSpacer(col, 50);
        addToolGroup(col, "Version Control",
                "Git   ·   GitHub");
        addSpacer(col, 50);
        addToolGroup(col, "Development Environment",
                "IntelliJ IDEA",
                "VS Code");
        addSpacer(col, 50);
        addToolGroup(col, "Design Tool",
                "Figma",
                "ibisPaint");
        addSpacer(col, 50);
        addToolGroup(col, "Video & Music Tool",
                "CapCut",
                "BeepBox");
        addSpacer(col, 50);
        addToolGroup(col, "Documentation & Flowcharts",
                "Markdown",
                "Mermaid.js",
                "Shell scripting (Bash)",
                "rawcdn.githack.com");
        addSpacer(col, 50);
        // Ai Assistance
        addToolGroup(col, "Development Assistance",
                "Anthropic Claude AI",
                "Google Gemini",
                "& OpenAI ChatGPT");

        addSpacer(col, 100);
        addDivider(col);
        addSpacer(col, 80);

        // ── Special Thanks ────────────────────────────────────────────────
        col.getChildren().add(makeLabel("Special Thanks", "credits-section-header"));
        addSpacer(col, 50);

        // Mentor 1
        col.getChildren().add(makeLabel("Md. Atikur Rahman", "credits-name"));
        col.getChildren().add(makeLabel("Lecturer, Dept. of CSE, BAUET", "credits-small-gray"));
        addSpacer(col, 30);

        // Mentor 2
        col.getChildren().add(makeLabel("Redoanul Haque", "credits-name"));
        col.getChildren().add(makeLabel("Lecturer, Dept. of CSE, BAUET", "credits-small-gray"));
        addSpacer(col, 30);

        // Playtesters
        col.getChildren().add(makeLabel("& Our Playtesters", "credits-name"));

        addSpacer(col, 100);
        addDivider(col);
        addSpacer(col, 100);

        // Closing card
        col.getChildren().add(makeLabel("Duck Dash",                               "credits-closing-title"));
        addSpacer(col, 14);
        col.getChildren().add(makeLabel("GPL-v3-or-Later License   ·   2026",                             "credits-tiny-gray"));
        addSpacer(col, 50);
        col.getChildren().add(makeLabel(
                "Made with late nights and bad ideas — but we shipped it.", "credits-small-italic"));

        // Extra padding so last line fully scrolls off before scene ends
        addSpacer(col, 100);

        return col;
    }

    // ── Scroll animation ──────────────────────────────────────────────────────

    private void startScrolling(VBox creditsColumn, StackPane root) {
        long[] lastTime = {-1};

        scrollTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastTime[0] < 0) { lastTime[0] = now; return; }

                double delta = (now - lastTime[0]) / 1_000_000_000.0;
                lastTime[0] = now;

                double newY = creditsColumn.getTranslateY() - SCROLL_SPEED_PX_PER_SEC * delta;
                creditsColumn.setTranslateY(newY);

                double colHeight = creditsColumn.getBoundsInLocal().getHeight();
                if (newY + colHeight < 0) {
                    stop();
                    fadeOutAndGoToMenu(root);
                }
            }
        };
        scrollTimer.start();
    }

    // ── End-of-credits fade ───────────────────────────────────────────────────

    private void fadeOutAndGoToMenu(StackPane root) {
        // 1. Prepare the visual black overlay
        Rectangle blackOverlay = new Rectangle(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT, Color.BLACK);
        blackOverlay.setOpacity(0.0);
        root.getChildren().add(blackOverlay);

        // 2. Visual Fade Transition (Matched to 3 seconds)
        FadeTransition visualFade = new FadeTransition(Duration.seconds(3.0), blackOverlay);
        visualFade.setFromValue(0.0);
        visualFade.setToValue(1.0);

        // 3. Music Volume Fade (3 seconds)
        if (musicPlayer != null) {
            Timeline musicFade = new Timeline(
                    new KeyFrame(Duration.seconds(3.0),
                            new KeyValue(musicPlayer.volumeProperty(), 0))
            );
            musicFade.play();
        }

        // 4. Navigate when both fades are complete
        visualFade.setOnFinished(e -> navigateToMenu());
        visualFade.play();
    }

    // ── Music ─────────────────────────────────────────────────────────────────
    private void startMusic() {
        MediaPlayer existing = MusicManager.getInstance().getBgPlayer();
        if (existing != null) {
            existing.stop();
            existing.dispose();
            MusicManager.getInstance().setBgPlayer(null);
        }

        if (!MusicManager.getInstance().isMusicEnabled()) return;
        try {
            URL url = getClass().getResource(CREDITS_MUSIC);
            if (url == null) {
                System.err.println("CreditsScene: music not found at " + CREDITS_MUSIC);
                return;
            }
            Media media = new Media(url.toExternalForm());
            musicPlayer = new MediaPlayer(media);
            musicPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            musicPlayer.setVolume(0.7);
            musicPlayer.play();
            MusicManager.getInstance().setBgPlayer(musicPlayer);
        } catch (Exception e) {
            System.err.println("CreditsScene: could not start music — " + e.getMessage());
        }
    }

    private void stopMusic() {
        if (musicPlayer != null) {
            musicPlayer.stop();
            musicPlayer.dispose();
            musicPlayer = null;
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void skipToMenu() {
        if (scrollTimer != null) scrollTimer.stop();
        navigateToMenu();
    }

    private void navigateToMenu() {
        if (hasNavigated) return;
        hasNavigated = true;
        Platform.runLater(() -> {
            stopMusic();
            Stage stage = MainApp.getPrimaryStage();
            MenuScene menuScene = new MenuScene(stage);
            MainApp.switchScene(menuScene.createScene());
        });
    }

    // ── Title image loader ────────────────────────────────────────────────────

    private ImageView loadTitleImage() {
        try {
            Image img = new Image(
                    Paths.get(TITLE_IMAGE_PATH).toUri().toString(),
                    TITLE_IMAGE_WIDTH, 0, true, true
            );
            if (img.isError()) {
                System.err.println("CreditsScene: title image error — " + img.getException());
                return null;
            }
            return new ImageView(img);
        } catch (Exception e) {
            System.err.println("CreditsScene: could not load title image — " + e.getMessage());
            return null;
        }
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    /** Creates a Label and assigns a single CSS style class. */
    private Label makeLabel(String text, String styleClass) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add(styleClass);
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.setAlignment(Pos.CENTER);
        lbl.setWrapText(true);
        return lbl;
    }

    /** Role/section header on top, one or more names below — classic film format. */
    private void addRoleEntry(VBox col, String role, String... names) {
        col.getChildren().add(makeLabel(role, "credits-small-gray"));
        for (String name : names) {
            addSpacer(col, 10);
            col.getChildren().add(makeLabel(name, "credits-name"));
        }
    }

    /** Tool category heading + one or more value lines. */
    private void addToolGroup(VBox col, String category, String... values) {
        col.getChildren().add(makeLabel(category, "credits-small-gray"));
        addSpacer(col, 4);
        for (String v : values) {
            col.getChildren().add(makeLabel(v, "credits-name"));
        }
    }

    private void addDivider(VBox col) {
        Line line = new Line(0, 0, 300, 0);
        line.setStroke(Color.web("#333333"));
        line.setStrokeWidth(1.0);
        col.getChildren().add(new StackPane(line));
    }

    private void addSpacer(VBox col, double height) {
        Region spacer = new Region();
        spacer.setPrefHeight(height);
        col.getChildren().add(spacer);
    }
}

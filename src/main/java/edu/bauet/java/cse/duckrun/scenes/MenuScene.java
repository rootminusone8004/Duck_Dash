package edu.bauet.java.cse.duckrun.scenes;

import edu.bauet.java.cse.duckrun.MainApp;
import edu.bauet.java.cse.duckrun.levels.Level1;
import edu.bauet.java.cse.duckrun.ui.LevelMenu;
import edu.bauet.java.cse.duckrun.ui.HighScoreMenu;
import edu.bauet.java.cse.duckrun.ui.SettingsMenu;
import edu.bauet.java.cse.duckrun.ui.EndlessLevelMenu;
import edu.bauet.java.cse.duckrun.ui.MenuBackground;          // ← NEW
import edu.bauet.java.cse.duckrun.utils.AssetLoader;
import edu.bauet.java.cse.duckrun.utils.MusicManager;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.scene.shape.Rectangle;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class MenuScene {

    private Stage stage;
    private StackPane root;
    private MenuBackground animatedBg;          // ← NEW (replaces plain ImageView)
    private VBox menuBox;
    private Rectangle overlay;
    private SettingsMenu settingsMenu;
    private LevelMenu levelMenu;
    private HighScoreMenu highScoreMenu;
    private EndlessLevelMenu endlessLevelMenu;

    public MenuScene(Stage stage) {
        this.stage = stage;
    }

    public Scene createScene() {
        Font pixelFont = Font.loadFont(getClass().getResourceAsStream("/fonts/PressStart2P-Regular.ttf"), 14);

        root = new StackPane();

        // ── Animated background (sky + moving clouds + building) ──────────────
        animatedBg = new MenuBackground();

        // ── Dim overlay (shown when a sub-menu is open) ───────────────────────
        overlay = new Rectangle(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        overlay.setStyle("-fx-fill: rgba(0, 0, 0, 0.5);");
        overlay.setVisible(false);

        // ── Game title image ──────────────────────────────────────────────────
        Image gameTitle = new Image(getClass().getResourceAsStream("/images/ui/menu/title2.png"));
        ImageView titleView = new ImageView(gameTitle);
        titleView.setFitWidth(472);
        titleView.setPreserveRatio(true);
        VBox.setMargin(titleView, new javafx.geometry.Insets(-175, 0, 0, -105));

        // ── Menu buttons ──────────────────────────────────────────────────────
        Button btnNewGame  = createMenuButton("New Game");
        Button btnLevels   = createMenuButton("Levels");
        Button btnEndless  = createMenuButton("Endless");
        Button btnScore    = createMenuButton("High Score");
        Button btnSettings = createMenuButton("Settings");
        Button btnExit     = createMenuButton("Exit");

        // ── Sub-menus ─────────────────────────────────────────────────────────
        endlessLevelMenu = new EndlessLevelMenu(this::closeMenu);
        levelMenu        = new LevelMenu(this::closeMenu, () -> showMenu(endlessLevelMenu));
        highScoreMenu    = new HighScoreMenu(this::closeMenu);
        settingsMenu     = new SettingsMenu(this::closeMenu, highScoreMenu);

        // ── Button actions ────────────────────────────────────────────────────
        btnNewGame.setOnAction(e -> {
            animatedBg.stop();                              // stop animation when leaving
            Level1 level1 = new Level1(MainApp.WINDOW_HEIGHT - 130);
            GameScene gameScene = new GameScene(level1);
            MainApp.switchScene(gameScene.getScene());
        });

        btnLevels.setOnAction(e   -> showMenu(levelMenu));
        btnEndless.setOnAction(e  -> showMenu(endlessLevelMenu));
        btnScore.setOnAction(e    -> showMenu(highScoreMenu));
        btnSettings.setOnAction(e -> showMenu(settingsMenu));
        btnExit.setOnAction(e     -> { animatedBg.stop(); stage.close(); });

        // ── Layout ────────────────────────────────────────────────────────────
        menuBox = new VBox(10);
        menuBox.getChildren().addAll(titleView, btnNewGame, btnLevels, btnEndless,
                btnScore, btnSettings, btnExit);
        menuBox.setAlignment(Pos.CENTER_LEFT);
        menuBox.setStyle("-fx-padding: 80 0 0 160;");

        root.getChildren().addAll(
                animatedBg,                  // layer 0: bg + clouds + building
                overlay,                     // layer 1: dim overlay
                menuBox,                     // layer 2: buttons
                levelMenu,
                highScoreMenu,
                settingsMenu,
                endlessLevelMenu
        );

        Scene scene = new Scene(root, MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles/main_menu.css").toExternalForm());

        animatedBg.start();              // ← begin cloud animation
        startMenuMusic();
        return scene;
    }

    // ── Sub-menu show / hide ──────────────────────────────────────────────────

    private void showMenu(StackPane menuToShow) {
        levelMenu.setVisible(false);
        highScoreMenu.setVisible(false);
        settingsMenu.setVisible(false);
        endlessLevelMenu.setVisible(false);
        menuToShow.setVisible(true);
        overlay.setVisible(true);
        menuBox.setDisable(true);
        menuBox.setVisible(false);
        // Blur only the animated background pane
        animatedBg.setEffect(new GaussianBlur(10));
    }

    private void closeMenu() {
        levelMenu.setVisible(false);
        highScoreMenu.setVisible(false);
        settingsMenu.setVisible(false);
        endlessLevelMenu.setVisible(false);
        overlay.setVisible(false);
        menuBox.setDisable(false);
        menuBox.setVisible(true);
        animatedBg.setEffect(null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Button createMenuButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("menu-button");
        return btn;
    }

    private void startMenuMusic() {
        javafx.scene.media.Media intro = AssetLoader.loadMusic("/audio/music/Square Cartridge1.mp3");
        javafx.scene.media.Media loop  = AssetLoader.loadMusic("/audio/music/Square Cartridge2.wav");
        if (intro == null || loop == null) return;

        MusicManager mm = MusicManager.getInstance();
        if (mm.getBgPlayer() != null) mm.getBgPlayer().stop();

        // Build the looping player first so it's ready when intro ends
        MediaPlayer loopPlayer = new MediaPlayer(loop);
        loopPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        loopPlayer.setVolume(0.6);

        // Intro plays once, then hands off to the loop player
        MediaPlayer introPlayer = new MediaPlayer(intro);
        introPlayer.setCycleCount(1);
        introPlayer.setVolume(0.6);
        introPlayer.setOnEndOfMedia(() -> {
            mm.setBgPlayer(loopPlayer);
            if (mm.isMusicEnabled()) loopPlayer.play();
        });

        // Register intro as the current player so mute toggle works immediately
        mm.setBgPlayer(introPlayer);
        if (mm.isMusicEnabled()) introPlayer.play();
    }
}
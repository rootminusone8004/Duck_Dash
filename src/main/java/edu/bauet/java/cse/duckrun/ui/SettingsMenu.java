package edu.bauet.java.cse.duckrun.ui;

import edu.bauet.java.cse.duckrun.MainApp;
import edu.bauet.java.cse.duckrun.scenes.CreditsScene;
import edu.bauet.java.cse.duckrun.utils.HighScoreManager;
import edu.bauet.java.cse.duckrun.utils.MusicManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class SettingsMenu extends StackPane {

    private Button musicToggle;
    private boolean isMusicOn = true;
    private Runnable onClose;
    private HighScoreMenu highScoreMenu;

    public SettingsMenu(Runnable onCloseAction) {
        this(onCloseAction, null);
    }

    public SettingsMenu(Runnable onCloseAction, HighScoreMenu highScoreMenu) {
        this.onClose = onCloseAction;
        this.highScoreMenu = highScoreMenu;
        initialize();
    }

    private void initialize() {
        this.setVisible(false);
        this.getStyleClass().add("settings-box");
        this.setPrefSize(925, 546);
        this.setMaxSize(925, 546);

        this.getStylesheets().add(getClass().getResource("/styles/settings_style.css").toExternalForm());
        this.getStylesheets().add(getClass().getResource("/styles/main_menu.css").toExternalForm());

        // Frame Image
        Image frameImg = new Image(getClass().getResourceAsStream("/images/pause_menu/settings_menu_frame.png"));
        ImageView frameView = new ImageView(frameImg);

        // Content Layout
        VBox contentLayout = new VBox(20);
        contentLayout.setAlignment(Pos.TOP_CENTER);

        // Title
        Label titleLabel = new Label("SETTINGS");
        titleLabel.getStyleClass().add("settings-title");
        VBox.setMargin(titleLabel, new Insets(60, 0, 0, 0));

        // Music Control
        HBox musicBox = new HBox(30);
        musicBox.setAlignment(Pos.CENTER);

        Label musicLabel = new Label("Music :");
        musicLabel.getStyleClass().add("settings-label");

        musicToggle = new Button("ON");
        musicToggle.getStyleClass().addAll("music-toggle", "music-on");
        musicToggle.setOnAction(e -> toggleMusic());

        musicBox.getChildren().addAll(musicLabel, musicToggle);
        VBox.setMargin(musicBox, new Insets(80, 0, 0, 0));

        // Reset High Score
        Button resetHsButton = new Button("Reset High Score");
        resetHsButton.getStyleClass().add("menu-button");
        resetHsButton.setStyle("-fx-pref-width: 360px; -fx-min-width: 360px;");
        resetHsButton.setOnAction(e -> {
            HighScoreManager.resetAll();
            if (highScoreMenu != null) highScoreMenu.refresh();
        });
        VBox.setMargin(resetHsButton, new Insets(20, 0, 0, 0));

        // Credits button — same style as Reset High Score
        Button creditsButton = new Button("Credits");
        creditsButton.getStyleClass().add("menu-button");
        creditsButton.setStyle("-fx-pref-width: 360px; -fx-min-width: 360px;");
        creditsButton.setOnAction(e -> {
            onClose.run(); // close the settings overlay first
            CreditsScene credits = new CreditsScene();
            MainApp.switchScene(credits.createScene());

        });
        VBox.setMargin(creditsButton, new Insets(0, 0, 0, 0));

        contentLayout.getChildren().addAll(titleLabel, musicBox, resetHsButton, creditsButton);

        // Close Button
        Button closeButton = new Button("X");
        closeButton.getStyleClass().add("settings-close");
        closeButton.setOnAction(e -> onClose.run());

        StackPane.setAlignment(closeButton, Pos.TOP_RIGHT);
        StackPane.setMargin(closeButton, new Insets(45, 65, 0, 0));

        this.getChildren().addAll(frameView, contentLayout, closeButton);
    }

    private void toggleMusic() {
        isMusicOn = !isMusicOn;
        musicToggle.getStyleClass().removeAll("music-on", "music-off");
        if (isMusicOn) {
            musicToggle.getStyleClass().add("music-on");
            musicToggle.setText("ON");
        } else {
            musicToggle.getStyleClass().add("music-off");
            musicToggle.setText("OFF");
        }
        MusicManager.getInstance().setMusicEnabled(isMusicOn);
    }
}
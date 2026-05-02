package edu.bauet.java.cse.duckrun.scenes;

import edu.bauet.java.cse.duckrun.MainApp;
import edu.bauet.java.cse.duckrun.entities.*;
import edu.bauet.java.cse.duckrun.ui.PauseMenu;
import edu.bauet.java.cse.duckrun.ui.SettingsMenu;
import edu.bauet.java.cse.duckrun.ui.SleepBar;
import edu.bauet.java.cse.duckrun.utils.AssetLoader;
import edu.bauet.java.cse.duckrun.utils.CollisionUtil;
import edu.bauet.java.cse.duckrun.utils.MusicManager;
import edu.bauet.java.cse.duckrun.ui.HealthBar;
import edu.bauet.java.cse.duckrun.utils.HighScoreManager;

import edu.bauet.java.cse.duckrun.levels.Level;
import edu.bauet.java.cse.duckrun.levels.Level1;
import edu.bauet.java.cse.duckrun.levels.Level2;
import edu.bauet.java.cse.duckrun.levels.Level3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.input.KeyCode;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.media.MediaPlayer;

public class EndlessGameScene {

    // ── Endless-mode configuration ───────────────────────────────────────────
    private static final double ESCALATION_INTERVAL   = 20.0; // seconds between speed bumps
    private static final double SPEED_INCREMENT       = 0.5;  // world-speed added per escalation
    private static final double BG_SPEED_FACTOR       = 60.0; // bg pixels = worldSpeed * factor
    private static final double JUMP_SPEED_INCREMENT  = 50.0; // duck jump speed added per escalation
    private static final double FALL_SPEED_INCREMENT  = 25.0; // duck fall speed added per escalation
    private static final int    HIT_TIME_PENALTY      = 5;    // seconds added on hit
    private static final int    FOOD_TIME_REWARD      = 10;   // seconds removed on food eat

    // ── Core ─────────────────────────────────────────────────────────────────
    private Pane root;
    private Scene scene;
    private Duck duck;
    private AnimationTimer gameLoop;
    private StackPane menuLayer;

    private ImageView background1;
    private ImageView background2;

    private boolean isPaused = false;
    private Button pauseButton;
    private PauseMenu pauseMenu;
    private SettingsMenu settingsMenu;
    private final Level currentLevel;

    private final List<Enemy>    enemies   = new ArrayList<>();
    private final List<Food>     foods     = new ArrayList<>();
    private final List<Obstacle> obstacles = new ArrayList<>();
    private long nextSpawnTime = 0;

    // Dynamic speeds — escalate over time
    private double currentWorldSpeed;
    private double currentBgSpeed;
    private double currentJumpSpeed;
    private double currentFallSpeed;

    private HealthBar healthBar;
    private SleepBar  sleepBar;

    // Count-up timer
    private int    timerSeconds      = 0;
    private double secondAccumulator = 0;
    private Label  timerLabel;

    // Escalation tracking
    private double timeSinceLastEscalation = 0;
    private int    escalationCount         = 0;

    private final double groundY = MainApp.WINDOW_HEIGHT - 130;
    private final Random random = new Random();
    private final List<Integer> spawnHistory = new ArrayList<>();

    private long lastFrameTime = 0;

    private enum DeathCause { SLEEP, OBSTACLE, CAT, BOY, EAGLE, DOG }
    private DeathCause deathCause = null;

    // ── Constructor ───────────────────────────────────────────────────────────
    public EndlessGameScene(Level level) {
        this.currentLevel     = level;
        this.currentWorldSpeed = level.getWorldSpeed();
        this.currentBgSpeed    = level.getBackgroundScrollSpeed();
        this.currentJumpSpeed  = level.getDuckJumpSpeed();
        this.currentFallSpeed  = level.getDuckFallSpeed();
        initialize(level.getBackgroundPath());
    }

    // ── Initialization ────────────────────────────────────────────────────────
    private void initialize(String backgroundPath) {
        root = new Pane();
        root.setPrefSize(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        root.setStyle("-fx-background-color: black;");

        menuLayer = new StackPane();
        menuLayer.setPrefSize(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        menuLayer.setPickOnBounds(false);

        scene = new Scene(root, MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/styles/pause_menu.css")).toExternalForm());

        createBackground(backgroundPath);
        createPlayer();

        healthBar = new HealthBar(3);
        healthBar.getView().setLayoutX(20);
        healthBar.getView().setLayoutY(20);

        sleepBar = new SleepBar();
        sleepBar.getView().setLayoutX(20);
        sleepBar.getView().setLayoutY(60);

        // Countdown timer label
        timerLabel = new Label(formatTime(timerSeconds));
        try {
            Font pixelfont = Font.loadFont(
                    getClass().getResourceAsStream("/fonts/PressStart2P-Regular.ttf"), 32);
            timerLabel.setFont(pixelfont != null ? pixelfont : Font.font("Arial", 32));
        } catch (Exception e) {
            timerLabel.setFont(Font.font("Arial", 32));
        }
        timerLabel.setTextFill(Color.web("#AE6819"));
        timerLabel.setPrefWidth(200);
        timerLabel.setAlignment(javafx.geometry.Pos.CENTER);
        timerLabel.setLayoutX((MainApp.WINDOW_WIDTH - 200) / 2.0);
        timerLabel.setLayoutY(20);

        DropShadow border = new DropShadow();
        border.setBlurType(BlurType.ONE_PASS_BOX);
        border.setColor(Color.BLACK);
        border.setRadius(4.0);
        border.setSpread(2.0);
        timerLabel.setEffect(border);

        createPauseSystem();

        root.getChildren().addAll(
                background1, background2,
                duck.getNode(),
                healthBar.getView(),
                sleepBar.getView(),
                timerLabel,
                pauseButton,
                menuLayer
        );

        menuLayer.getChildren().addAll(pauseMenu.getRoot(), settingsMenu);

        root.setFocusTraversable(true);
        root.requestFocus();

        setupControls();
        startGameLoop();
    }

    private void createBackground(String path) {
        Image bgImage = AssetLoader.getImage(path);
        background1 = new ImageView(bgImage);
        background2 = new ImageView(bgImage);
        background1.setFitHeight(MainApp.WINDOW_HEIGHT);
        background1.setPreserveRatio(true);
        background2.setFitHeight(MainApp.WINDOW_HEIGHT);
        background2.setPreserveRatio(true);
        double width = bgImage.getWidth() * (MainApp.WINDOW_HEIGHT / bgImage.getHeight());
        background1.setLayoutX(0);
        background2.setLayoutX(width);
    }

    private void createPlayer() {
        duck = new Duck(200, groundY);
        duck.setJumpSpeed(currentJumpSpeed);
        duck.setFallSpeed(currentFallSpeed);

        if (currentLevel instanceof edu.bauet.java.cse.duckrun.levels.Level3) {
            duck.setAnimationThreshold(12); // Very fast leg movement
        } else if (currentLevel instanceof edu.bauet.java.cse.duckrun.levels.Level2) {
            duck.setAnimationThreshold(15); // Faster leg movement
        } else {
            duck.setAnimationThreshold(25); // Default/Slow movement for Level 1
        }
    }

    private void startBgMusic() {
        String introPath;
        String loopPath;

        // Define different paths based on the level
        if (currentLevel instanceof Level1) {
            introPath = "/audio/music/Pixel_Dash1.mp3";
            loopPath  = "/audio/music/Pixel_Dash2.wav";
        } else if (currentLevel instanceof Level2) {
            // Level 2 uses two distinct files for intro and loop
            introPath = "/audio/music/Pixel_Quest1.mp3";
            loopPath  = "/audio/music/Pixel_Quest2.wav";
        } else {
            introPath = "/audio/music/Terminal_Velocity1.mp3";
            loopPath  = "/audio/music/Terminal_Velocity2.wav";
        }

        // Load the two media assets
        javafx.scene.media.Media introMedia = AssetLoader.loadMusic(introPath);
        javafx.scene.media.Media loopMedia  = AssetLoader.loadMusic(loopPath);

        // Safety check: if loading fails, abort to avoid NullPointerException
        if (introMedia == null || loopMedia == null) return;

        MusicManager mm = MusicManager.getInstance();

        // Stop any currently playing background music
        if (mm.getBgPlayer() != null) {
            mm.getBgPlayer().stop();
        }

        // 1. Setup the Looping Player (to be played after the intro)
        MediaPlayer loopPlayer = new MediaPlayer(loopMedia);
        loopPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        loopPlayer.setVolume(0.6);

        // 2. Setup the Intro Player (plays once)
        MediaPlayer introPlayer = new MediaPlayer(introMedia);
        introPlayer.setCycleCount(1);
        introPlayer.setVolume(0.6);

        // 3. Define the hand-off logic
        introPlayer.setOnEndOfMedia(() -> {
            // Switch the reference in MusicManager so pause/stop works on the new player
            mm.setBgPlayer(loopPlayer);
            if (mm.isMusicEnabled()) {
                loopPlayer.play();
            }
        });

        // 4. Set the intro as the active player and start playback
        mm.setBgPlayer(introPlayer);
        if (mm.isMusicEnabled()) {
            introPlayer.play();
        }
    }

    private void stopBgMusic() {
        MediaPlayer player = MusicManager.getInstance().getBgPlayer();
        if (player != null) {
            player.stop();
            MusicManager.getInstance().setBgPlayer(null);
        }
    }

    private void createPauseSystem() {
        Image pauseImage = AssetLoader.getImage("/images/pause_menu/pause_button.png");
        ImageView pauseIcon = new ImageView(pauseImage);
        pauseIcon.setFitWidth(60);
        pauseIcon.setFitHeight(60);
        pauseIcon.setPreserveRatio(true);

        pauseButton = new Button();
        pauseButton.setGraphic(pauseIcon);
        pauseButton.setStyle("-fx-background-color: transparent;");
        pauseButton.setLayoutX(MainApp.WINDOW_WIDTH - 80);
        pauseButton.setLayoutY(10);
        pauseButton.setCursor(Cursor.HAND);
        pauseButton.getStyleClass().add("pause-button");
        pauseButton.setOnAction(e -> { if (isPaused) resumeGame(); else pauseGame(); });

        pauseMenu = new PauseMenu(this::resumeGame, this::restartGame, this::openSettings, this::exitToMenu);
        pauseMenu.getRoot().setVisible(false);

        settingsMenu = new SettingsMenu(() -> {
            settingsMenu.setVisible(false);
            pauseMenu.getRoot().getChildren().forEach(node -> node.setVisible(true));
            pauseMenu.getRoot().toFront();
            root.requestFocus();
        });
        settingsMenu.setVisible(false);
    }

    private void setupControls() {
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                if (isPaused) resumeGame(); else pauseGame();
                return;
            }
            if (isPaused) return;
            if (event.getCode() == KeyCode.SPACE ||
                    event.getCode() == KeyCode.W ||
                    event.getCode() == KeyCode.UP) {
                duck.jump();
            }
            if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.S) {
                duck.setCrouching(true);
            }
        });
        scene.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.S) {
                duck.setCrouching(false);
            }
        });
    }

    // ── Game Loop ─────────────────────────────────────────────────────────────
    private void startGameLoop() {
        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameTime == 0) { lastFrameTime = now; return; }
                double deltaTime = (now - lastFrameTime) / 1_000_000_000.0;
                lastFrameTime = now;

                if (!isPaused) {
                    duck.setSleepy(!sleepBar.isEmpty());
                    double effectiveDelta = duck.isCrouching() ? deltaTime * 0.75 : deltaTime;
                    updateBackground(effectiveDelta);
                    duck.update(deltaTime);
                    spawnEntities(now);
                    updateEnemies(effectiveDelta);
                    updateFoods(effectiveDelta);
                    updateObstacles(effectiveDelta);
                    updateCountdown(deltaTime);      // real time — timer should not slow down
                    updateEscalation(deltaTime);     // real time — escalation should not slow down


                    if (sleepBar.isFull()) {
                        deathCause = DeathCause.SLEEP;
                        gameOver();
                    }
                }
            }
        };
        startBgMusic();
        gameLoop.start();
    }

    private void updateCountdown(double deltaTime) {
        secondAccumulator += deltaTime;
        if (secondAccumulator >= 1.0) {
            secondAccumulator -= 1.0;
            timerSeconds++;
            timerLabel.setText(formatTime(timerSeconds));
        }
    }

    private void updateEscalation(double deltaTime) {
        timeSinceLastEscalation += deltaTime;
        if (timeSinceLastEscalation >= ESCALATION_INTERVAL) {
            timeSinceLastEscalation -= ESCALATION_INTERVAL;
            escalationCount++;

            currentWorldSpeed += SPEED_INCREMENT;
            currentBgSpeed    += SPEED_INCREMENT * BG_SPEED_FACTOR;
            currentJumpSpeed  += JUMP_SPEED_INCREMENT;
            currentFallSpeed  += FALL_SPEED_INCREMENT;

            duck.setJumpSpeed(currentJumpSpeed);
            duck.setFallSpeed(currentFallSpeed);

            //escalate duck movement
            int newThreshold = (int) (25 * (400.0 / currentBgSpeed));

            // max speed cap
            if (newThreshold < 10) newThreshold = 10;

            duck.setAnimationThreshold(newThreshold);
        }
    }

    private void updateBackground(double deltaTime) {
        double move = currentBgSpeed * deltaTime;
        background1.setLayoutX(background1.getLayoutX() - move);
        background2.setLayoutX(background2.getLayoutX() - move);

        double width = background1.getBoundsInParent().getWidth();
        if (background1.getLayoutX() <= -width)
            background1.setLayoutX(background2.getLayoutX() + width);
        if (background2.getLayoutX() <= -width)
            background2.setLayoutX(background1.getLayoutX() + width);
    }

    private void spawnEntities(long now) {
        if (now < nextSpawnTime) return;

        double spawnX = MainApp.WINDOW_WIDTH + 50;
        int entityType;

        if (spawnHistory.size() == 2 && spawnHistory.get(0).equals(spawnHistory.get(1))) {
            int last = spawnHistory.get(0);
            List<Integer> possible = IntStream.range(0, 3)
                    .filter(i -> i != last).boxed().collect(Collectors.toList());
            entityType = possible.get(random.nextInt(possible.size()));
        } else {
            entityType = random.nextInt(3);
        }

        switch (entityType) {
            case 0:
                Enemy enemy = currentLevel.spawnEnemy(spawnX, currentWorldSpeed);
                if (enemy != null) { enemies.add(enemy); addNodeToScene(enemy.getNode()); }
                break;
            case 1:
                Food food = currentLevel.spawnFood(spawnX, currentWorldSpeed);
                if (food != null) { foods.add(food); addNodeToScene(food.getNode()); }
                break;
            case 2:
                Obstacle obstacle = currentLevel.spawnObstacle(spawnX, currentWorldSpeed);
                if (obstacle != null) { obstacles.add(obstacle); addNodeToScene(obstacle.getNode()); }
                break;
        }

        spawnHistory.add(entityType);
        if (spawnHistory.size() > 2) spawnHistory.remove(0);

        long delay = (long) ((1.5 + Math.random() * 2.5) * 1_000_000_000L);
        nextSpawnTime = now + delay;
    }

    private void addNodeToScene(javafx.scene.Node node) {
        int idx = root.getChildren().indexOf(pauseButton);
        if (idx != -1) root.getChildren().add(idx, node);
        else root.getChildren().add(node);
    }

    // ── Collision Updates ─────────────────────────────────────────────────────
    private void updateEnemies(double deltaTime) {
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy enemy = it.next();
            // Apply current world speed scaling to entity movement
            enemy.update(deltaTime);
            if (!enemy.isActive()) { root.getChildren().remove(enemy.getNode()); it.remove(); continue; }
            if (!enemy.hasCollided() && CollisionUtil.isColliding(duck.getHitBox(), enemy.getHitBox())) {
                enemy.markCollided();
                duck.hit();
                if (enemy instanceof Boy) {
                    deathCause = DeathCause.BOY;
                    gameOver();
                } else {
                    if (enemy instanceof Dog) {
                        ((Dog) enemy).showHitImage();
                    }
                    healthBar.decreaseHealth();
                    MusicManager.getInstance().playSfx("/audio/sound_effect/hit.mp3");
                    sleepBar.decreaseSegment();
                    timerSeconds += HIT_TIME_PENALTY;
                    timerLabel.setText(formatTime(timerSeconds));
                    if (healthBar.isDead()) {
                        if (enemy instanceof Dog)        deathCause = DeathCause.DOG;
                        else if (enemy instanceof CatBrown)   deathCause = DeathCause.CAT;
                        else if (enemy instanceof Eagle) deathCause = DeathCause.EAGLE;
                        else                             deathCause = DeathCause.CAT;
                        gameOver();
                    }
                }
            }
        }
    }

    private void updateFoods(double deltaTime) {
        Iterator<Food> it = foods.iterator();
        while (it.hasNext()) {
            Food food = it.next();
            food.update(deltaTime);
            if (!food.isActive()) { root.getChildren().remove(food.getNode()); it.remove(); continue; }
            if (CollisionUtil.isColliding(duck.getHitBox(), food.getHitBox())) {
                food.deactivate();
                if (!healthBar.isFull()) healthBar.increaseHealth();
                sleepBar.addSegment();
                duck.eat();
                duck.powerUp();
                timerSeconds = Math.max(0, timerSeconds - FOOD_TIME_REWARD);
                timerLabel.setText(formatTime(timerSeconds));
            }
        }
    }

    private void updateObstacles(double deltaTime) {
        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle obstacle = it.next();
            obstacle.update(deltaTime);
            if (!obstacle.isActive()) { root.getChildren().remove(obstacle.getNode()); it.remove(); continue; }
            if (!obstacle.hasCollided() && CollisionUtil.isColliding(duck.getHitBox(), obstacle.getHitBox())) {
                obstacle.markCollided();
                duck.hit();
                healthBar.decreaseHealth();
                MusicManager.getInstance().playSfx("/audio/sound_effect/hit.mp3");
                sleepBar.decreaseSegment();
                timerSeconds += HIT_TIME_PENALTY;
                timerLabel.setText(formatTime(timerSeconds));
                if (healthBar.isDead()) {
                    deathCause = DeathCause.OBSTACLE;
                    gameOver();
                }
            }
        }
    }

    // ── Pause / Resume ────────────────────────────────────────────────────────
    private void pauseGame() {
        if (isPaused) return;
        isPaused = true;
        MediaPlayer bgm = MusicManager.getInstance().getBgPlayer();
        if (bgm != null && MusicManager.getInstance().isMusicEnabled()) bgm.pause();
        pauseMenu.setVisible(true, background1, background2);
        menuLayer.toFront();
        pauseMenu.getRoot().toFront();
        pauseButton.setVisible(false);
    }

    private void resumeGame() {
        if (!isPaused) return;
        isPaused = false;
        lastFrameTime = 0;
        MediaPlayer bgm = MusicManager.getInstance().getBgPlayer();
        if (bgm != null && MusicManager.getInstance().isMusicEnabled()) bgm.play();
        pauseMenu.setVisible(false, background1, background2);
        pauseButton.setVisible(true);
        root.requestFocus();
    }

    private void restartGame() {
        resumeGame();
        duck.resetState();
        background1.setLayoutX(0);
        background2.setLayoutX(background1.getBoundsInLocal().getWidth());

        for (Enemy e : enemies) root.getChildren().remove(e.getNode());
        enemies.clear();
        for (Food f : foods) root.getChildren().remove(f.getNode());
        foods.clear();
        for (Obstacle o : obstacles) root.getChildren().remove(o.getNode());
        obstacles.clear();

        nextSpawnTime = 0;
        healthBar.reset();
        sleepBar.reset();
        duck.setSleepy(false);
        spawnHistory.clear();

        // Reset speeds
        currentWorldSpeed = currentLevel.getWorldSpeed();
        currentBgSpeed    = currentLevel.getBackgroundScrollSpeed();
        currentJumpSpeed  = currentLevel.getDuckJumpSpeed();
        currentFallSpeed  = currentLevel.getDuckFallSpeed();
        duck.setJumpSpeed(currentJumpSpeed);
        duck.setFallSpeed(currentFallSpeed);

        // Reset timers
        timerSeconds           = 0;
        secondAccumulator      = 0;
        timeSinceLastEscalation = 0;
        escalationCount        = 0;
        timerLabel.setText(formatTime(timerSeconds));

        Image normalBg = AssetLoader.getImage(currentLevel.getBackgroundPath());
        background1.setImage(normalBg);
        background2.setImage(normalBg);
        startBgMusic();
    }

    private void openSettings() {
        pauseMenu.getRoot().getChildren().forEach(node -> {
            if (!(node instanceof javafx.scene.shape.Rectangle)) node.setVisible(false);
        });
        settingsMenu.setVisible(true);
        settingsMenu.toFront();
        menuLayer.toFront();
        isPaused = true;
    }

    private void exitToMenu() {
        if (gameLoop != null) gameLoop.stop();
        stopBgMusic();
        MenuScene menuScene = new MenuScene(MainApp.getPrimaryStage());
        MainApp.switchScene(menuScene.createScene());
    }

    // ── Game Over ─────────────────────────────────────────────────────────────
    private void gameOver() {
        if (gameLoop == null) return;
        gameLoop.stop();
        gameLoop = null;
        stopBgMusic();

        // Add this line:
        MusicManager.getInstance().playOneShot("/audio/music/game_over.mp3", 1.0);

        // Save best endless survival time
        saveEndlessScore();

        if (deathCause == DeathCause.SLEEP) {
            showGameOverScreen("/images/duck/sleeping.png");
        } else if (deathCause == DeathCause.OBSTACLE) {
            showGameOverScreen("/images/game_over/game_over_bump.png");
        } else if (deathCause == DeathCause.CAT) {
            showGameOverScreen("/images/game_over/game_over_cat.png");
        } else if (deathCause == DeathCause.BOY) {
            showGameOverScreen("/images/game_over/game_over_caught.png");
        } else if (deathCause == DeathCause.EAGLE) {
            showGameOverScreen("/images/game_over/game_over_eagle.png");
        } else if (deathCause == DeathCause.DOG) {
            showGameOverScreen("/images/game_over/game_over_dog.png");
        } else {
            exitToMenu();
        }
    }

    private void saveEndlessScore() {
        if (currentLevel instanceof edu.bauet.java.cse.duckrun.levels.Level1)
            HighScoreManager.submitEndless1(timerSeconds);
        else if (currentLevel instanceof edu.bauet.java.cse.duckrun.levels.Level2)
            HighScoreManager.submitEndless2(timerSeconds);
        else if (currentLevel instanceof edu.bauet.java.cse.duckrun.levels.Level3)
            HighScoreManager.submitEndless3(timerSeconds);
    }

    private void showGameOverScreen(String imagePath) {
        javafx.scene.shape.Rectangle darkOverlay = new javafx.scene.shape.Rectangle(
                MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT, Color.rgb(0, 0, 0, 0.8));

        StackPane frameContainer = new StackPane();
        ImageView frameView = new ImageView(AssetLoader.getImage("/images/game_over/game_over_frame.png"));
        frameView.setFitWidth(750);
        frameView.setPreserveRatio(true);

        VBox contentLayout = new VBox(25);
        contentLayout.setAlignment(Pos.CENTER);

        Label gameOverLabel = new Label("GAME OVER");
        gameOverLabel.getStyleClass().add("game-over-title");

        // Show survival time
        Label survivalLabel = new Label("Survived: " + formatTime(timerSeconds));
        survivalLabel.getStyleClass().add("survival-time-text");

        ImageView deathView = new ImageView(AssetLoader.getImage(imagePath));
        deathView.setFitHeight(180);
        deathView.setPreserveRatio(true);

        HBox buttonRow = new HBox(50);
        buttonRow.setAlignment(Pos.CENTER);

        Button restartBtn = new Button();
        restartBtn.setGraphic(createButtonIcon("/images/pause_menu/restart_button.png"));
        restartBtn.getStyleClass().add("game-over-button");
        restartBtn.setOnAction(e -> {
            EndlessGameScene fresh = new EndlessGameScene(currentLevel);
            MainApp.switchScene(fresh.getScene());
        });

        Button exitBtn = new Button();
        exitBtn.setGraphic(createButtonIcon("/images/pause_menu/exit_button.png"));
        exitBtn.getStyleClass().add("game-over-button");
        exitBtn.setOnAction(e -> exitToMenu());

        buttonRow.getChildren().addAll(restartBtn, exitBtn);
        contentLayout.getChildren().addAll(gameOverLabel, survivalLabel, deathView, buttonRow);

        frameContainer.getChildren().addAll(frameView, contentLayout);
        frameContainer.setTranslateX((MainApp.WINDOW_WIDTH - 750) / 2.0);
        frameContainer.setTranslateY((MainApp.WINDOW_HEIGHT - 480) / 2.0);

        root.getChildren().addAll(darkOverlay, frameContainer);

        String css = Objects.requireNonNull(getClass().getResource("/styles/game_over_endless.css")).toExternalForm();
        if (!scene.getStylesheets().contains(css)) scene.getStylesheets().add(css);
    }

    private ImageView createButtonIcon(String path) {
        ImageView iv = new ImageView(AssetLoader.getImage(path));
        iv.setFitWidth(100);
        iv.setPreserveRatio(true);
        return iv;
    }

    private String formatTime(int totalSeconds) {
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    public Scene getScene() { return scene; }
}
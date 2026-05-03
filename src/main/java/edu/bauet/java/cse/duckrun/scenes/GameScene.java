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
import edu.bauet.java.cse.duckrun.ui.LevelProgressBar;
import edu.bauet.java.cse.duckrun.utils.TimeUtil;
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

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Scale;
import javafx.scene.media.Media;
import javafx.util.Duration;
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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.media.MediaPlayer;

public class GameScene {

    private Pane root;
    private Scene scene;
    private Duck duck;
    private AnimationTimer gameLoop;
    private StackPane menuLayer;

    private ImageView background1;
    private ImageView background2;
    private javafx.scene.image.Image transitionImage;

    private boolean isPaused = false;
    private Button pauseButton;
    private PauseMenu pauseMenu;
    private SettingsMenu settingsMenu;
    private final Level currentLevel;

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Food> foods = new ArrayList<>();
    private final List<Obstacle> obstacles = new ArrayList<>();
    private long nextSpawnTime = 0;

    private final double backgroundScrollSpeed;

    private HealthBar healthBar;
    private SleepBar sleepBar;
    private TimeUtil timeUtil;

    // ── Level Progress Bar ───────────────────────────────────────────────────
    private LevelProgressBar levelProgressBar;
    private Canvas progressCanvas;
    private double levelScrolledPixels    = 0.0;
    private double levelTotalScrollPixels = 10000.0;

    // During the run-off phase the background stops scrolling, so we switch to
    // tracking the duck's actual screen X position to drive the bar from its
    // frozen scroll value up to 1.0 — reaching 1.0 exactly when the duck exits.
    private double runOffStartX = 200.0;   // duck's X when run-off begins (set in startRunOff)
    // ────────────────────────────────────────────────────────────────────────

    private final double groundY = MainApp.WINDOW_HEIGHT - 130;
    private final Random random = new Random();
    private final List<Integer> spawnHistory = new ArrayList<>();

    private long lastFrameTime = 0;

    // --- Spawn pacing rules ---
    private final boolean isLevel1;
    private final int minNonFoodSpawnsBeforeFoodOpportunity;
    private final int forceFoodAfterNonFoodSpawns;
    private int nonFoodSpawnsSinceLastFood = 0;
    private int nonFoodSpawnsSinceCycleReset = 0;
    private int totalSpawnCount = 0;

    // Tracks what killed the duck
    private enum DeathCause { SLEEP, OBSTACLE, CAT, BOY, EAGLE, DOG }
    private DeathCause deathCause = null;

    // Level completion tracking
    private int     bgScrolledTotal = 0;
    private int     loopsToComplete = 0;
    private boolean levelCompleted  = false;
    private boolean duckRunningOff  = false;

    // Cheat Code tracking: press E-N-D in sequence to jump to end of level
    private final List<KeyCode> cheatSequence = List.of(KeyCode.E, KeyCode.N, KeyCode.D);
    private final List<KeyCode> cheatBuffer = new ArrayList<>();

    public GameScene(Level level) {
        this.currentLevel = level;
        this.isLevel1 = level instanceof Level1;
        this.minNonFoodSpawnsBeforeFoodOpportunity = isLevel1 ? 3 : 4;
        this.forceFoodAfterNonFoodSpawns = isLevel1 ? 7 : 10;
        this.backgroundScrollSpeed = level.getBackgroundScrollSpeed();
        this.loopsToComplete = level.getLoopsToComplete();
        initialize(level.getBackgroundPath());
    }

    // Scale transform applied to root so the 1280x720 game fills any window size
    private final Scale gameScale = new Scale(1, 1, 0, 0);

    /** Recompute uniform scale to letterbox/pillarbox the game inside the window. */
    private void updateScale(double w, double h) {
        double s = Math.min(w / MainApp.WINDOW_WIDTH, h / MainApp.WINDOW_HEIGHT);
        gameScale.setX(s);
        gameScale.setY(s);
    }

    private void initialize(String backgroundPath) {
        root = new Pane();
        root.setPrefSize(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        root.setStyle("-fx-background-color: black;");
        root.getTransforms().add(gameScale);

        menuLayer = new StackPane();
        menuLayer.setPrefSize(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        menuLayer.setPickOnBounds(false);

        // Wrap root in a Group — Group reports transformed bounds correctly,
        // unlike StackPane which ignores transforms during layout.
        javafx.scene.Group scaleWrapper = new javafx.scene.Group(root);

        scene = new Scene(scaleWrapper);

        // Recompute scale whenever the window is resized
        scene.widthProperty().addListener( (obs, o, n) -> updateScale(n.doubleValue(), scene.getHeight()));
        scene.heightProperty().addListener((obs, o, n) -> updateScale(scene.getWidth(),  n.doubleValue()));

        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/pause_menu.css")).toExternalForm());

        createBackground(backgroundPath);
        createPlayer();

        healthBar = new HealthBar(3);
        healthBar.getView().setLayoutX(20);
        healthBar.getView().setLayoutY(20);

        sleepBar = new SleepBar();
        sleepBar.getView().setLayoutX(20);
        sleepBar.getView().setLayoutY(60);

        timeUtil = new TimeUtil();
        Label timerLabel = new Label();
        timerLabel.textProperty().bind(timeUtil.timeProperty());
        try {
            Font pixelfont = Font.loadFont(getClass().getResourceAsStream("/fonts/PressStart2P-Regular.ttf"), 32);
            if (pixelfont != null) {
                timerLabel.setFont(pixelfont);
            } else {
                timerLabel.setFont(Font.font("Arial", 32));
            }
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

        // ── Create the progress bar canvas ───────────────────────────────────
        // A transparent Canvas that sits on top of all game elements.
        // Mouse events pass through it so gameplay is unaffected.
        progressCanvas = new Canvas(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        progressCanvas.setMouseTransparent(true);
        levelProgressBar = new LevelProgressBar(MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT);
        levelProgressBar.setLevelLength(levelTotalScrollPixels); // refined in createBackground()
        // ────────────────────────────────────────────────────────────────────

        createPauseSystem();

        root.getChildren().addAll(
                background1,
                background2,
                duck.getNode(),
                healthBar.getView(),
                sleepBar.getView(),
                timerLabel,
                pauseButton,
                progressCanvas,   // <-- progress bar canvas, above game, below menus
                menuLayer
        );

        menuLayer.getChildren().addAll(pauseMenu.getRoot(), settingsMenu);

        root.setFocusTraversable(true);
        root.requestFocus();

        setupControls();
        startGameLoop();
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

        pauseButton.setOnAction(e -> {
            if (isPaused) resumeGame();
            else pauseGame();
        });

        pauseMenu = new PauseMenu(this::resumeGame,
                this::restartGame,
                this::openSettings,
                this::exitToMenu
        );

        pauseMenu.getRoot().setVisible(false);

        settingsMenu = new SettingsMenu(() -> {
            settingsMenu.setVisible(false);
            pauseMenu.getRoot().getChildren().forEach(node -> node.setVisible(true));
            pauseMenu.getRoot().toFront();
            root.requestFocus();
        });

        settingsMenu.setVisible(false);
    }

    private void createBackground(String path) {
        Image bgImage = AssetLoader.getImage(path);
        String transPath = currentLevel.getTransitionImagePath();
        transitionImage = (transPath != null) ? AssetLoader.getImage(transPath) : null;
        background1 = new ImageView(bgImage);
        background2 = new ImageView(bgImage);

        background1.setFitHeight(MainApp.WINDOW_HEIGHT);
        background1.setPreserveRatio(true);
        background2.setFitHeight(MainApp.WINDOW_HEIGHT);
        background2.setPreserveRatio(true);

        double width = bgImage.getWidth() * (MainApp.WINDOW_HEIGHT / bgImage.getHeight());

        background1.setLayoutX(0);
        background2.setLayoutX(width);

        // ── Compute the real level total length for the progress bar ─────────
        // The level triggers completion at bgScrolledTotal == loopsToComplete - 2
        // (see onTileWrapped). So the bar must be full exactly when that loop count
        // is reached — i.e. total scroll = (loopsToComplete - 2) * bgWidth.
        double normalLoops = Math.max(1.0, (loopsToComplete - 2));
        levelTotalScrollPixels = normalLoops * width + width + MainApp.WINDOW_WIDTH;
        if (levelProgressBar != null) {
            levelProgressBar.setLevelLength(levelTotalScrollPixels);
        }
        // ────────────────────────────────────────────────────────────────────
    }

    private void updateBackground(double deltaTime) {
        double moveAmount = backgroundScrollSpeed * deltaTime;

        background1.setLayoutX(background1.getLayoutX() - moveAmount);
        background2.setLayoutX(background2.getLayoutX() - moveAmount);

        // ── Accumulate scroll distance for the progress bar ──────────────────
        if (!duckRunningOff) {
            levelScrolledPixels += moveAmount;
        }
        // ────────────────────────────────────────────────────────────────────

        double width = background1.getBoundsInParent().getWidth();

        if (background1.getLayoutX() <= -width) {
            background1.setLayoutX(background2.getLayoutX() + width);
            bgScrolledTotal++;
            onTileWrapped(background1);
        }
        if (background2.getLayoutX() <= -width) {
            background2.setLayoutX(background1.getLayoutX() + width);
            bgScrolledTotal++;
            onTileWrapped(background2);
        }

        if (levelCompleted && !duckRunningOff) {
            ImageView transitionTile = (background1.getImage() == transitionImage)
                    ? background1 : background2;
            double rightEdge = transitionTile.getLayoutX() + transitionTile.getBoundsInParent().getWidth();
            if (rightEdge <= MainApp.WINDOW_WIDTH) {
                levelCompleted = false;
                duckRunningOff = true;
                runOffStartX = duck.getNode().getLayoutX(); // snapshot X so bar tracks real position
                duck.startRunOff();
            }
        }
    }

    private void onTileWrapped(ImageView tile) {
        if (transitionImage == null) return;
        if (bgScrolledTotal == loopsToComplete - 2 && !levelCompleted) {
            tile.setImage(transitionImage);
            levelCompleted = true;
        }
    }

    // ── Draw the progress bar onto its canvas ─────────────────────────────────
    private void drawProgressBar() {
        GraphicsContext gc = progressCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, progressCanvas.getWidth(), progressCanvas.getHeight());

        if (duckRunningOff) {
            // Run-off phase: mini duck has already reached the flag (bar = 1.0).
            // Keep it pinned at 1.0 while the main duck exits the screen.
            levelProgressBar.updateDirect(1.0);
        } else {
            // Normal + transition phase: drive bar by actual pixels scrolled.
            // levelTotalScrollPixels = normalLoops*bgW + transitionW + WINDOW_WIDTH,
            // so progress hits 1.0 exactly when the transition right edge exits.
            double scrollProgress = Math.min(1.0, levelScrolledPixels / levelTotalScrollPixels);
            levelProgressBar.updateDirect(scrollProgress);
        }

        levelProgressBar.draw(gc);
    }
    // ─────────────────────────────────────────────────────────────────────────

    private void createPlayer() {
        duck = new Duck(200, groundY);
        duck.setJumpSpeed(currentLevel.getDuckJumpSpeed());
        duck.setFallSpeed(currentLevel.getDuckFallSpeed());

        if (currentLevel instanceof Level3) {
            duck.setAnimationThreshold(12);
        } else if (currentLevel instanceof Level2) {
            duck.setAnimationThreshold(15);
        } else {
            duck.setAnimationThreshold(25);
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
            introPath = "/audio/music/Pixel_Quest1.mp3";
            loopPath  = "/audio/music/Pixel_Quest2.wav";
        } else {
            introPath = "/audio/music/Terminal_Velocity1.mp3";
            loopPath  = "/audio/music/Terminal_Velocity2.wav";
        }

        // Load the two distinct files
        javafx.scene.media.Media intro = AssetLoader.loadMusic(introPath);
        javafx.scene.media.Media loop  = AssetLoader.loadMusic(loopPath);

        if (intro == null || loop == null) return;

        MusicManager mm = MusicManager.getInstance();
        if (mm.getBgPlayer() != null) mm.getBgPlayer().stop();

        // Prepare the looping player
        MediaPlayer loopPlayer = new MediaPlayer(loop);
        loopPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        loopPlayer.setVolume(0.6);

        // Prepare the intro player
        MediaPlayer introPlayer = new MediaPlayer(intro);
        introPlayer.setCycleCount(1);
        introPlayer.setVolume(0.6);

        // Switch to loopPlayer when intro finishes
        introPlayer.setOnEndOfMedia(() -> {
            mm.setBgPlayer(loopPlayer);
            if (mm.isMusicEnabled()) loopPlayer.play();
        });

        mm.setBgPlayer(introPlayer);
        if (mm.isMusicEnabled()) introPlayer.play();
    }

    private void stopBgMusic() {
        MediaPlayer player = MusicManager.getInstance().getBgPlayer();
        if (player != null) {
            player.stop();
            MusicManager.getInstance().setBgPlayer(null);
        }
    }

    // ONLY showing the UPDATED method — rest of your file stays EXACTLY the same

    private void setupControls() {

        scene.setOnKeyPressed(event -> {
            // Cheat code: E → N → D jumps to end of level
            cheatBuffer.add(event.getCode());
            if (cheatBuffer.size() > cheatSequence.size()) {
                cheatBuffer.remove(0);
            }
            if (cheatBuffer.equals(cheatSequence)) {
                cheatBuffer.clear();
                levelScrolledPixels = levelTotalScrollPixels * 1.0;
                bgScrolledTotal = loopsToComplete - 3;
                System.out.println("Cheat activated — jumped to end of level.");
            }

            if (event.getCode() == KeyCode.ESCAPE) {
                if (isPaused) resumeGame();
                else pauseGame();
                return;
            }
            if (isPaused) return;

            if (event.getCode() == KeyCode.SPACE ||
                    event.getCode() == KeyCode.W ||
                    event.getCode() == KeyCode.UP) {
                duck.jump();
            }

            if (event.getCode() == KeyCode.DOWN ||
                    event.getCode() == KeyCode.S) {
                duck.setCrouching(true);
            }
        });

        scene.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.DOWN ||
                    event.getCode() == KeyCode.S) {
                duck.setCrouching(false);
            }

            // 🔥 THIS LINE FIXES THE CONTINUOUS JUMP BUG
            if (event.getCode() == KeyCode.SPACE ||
                    event.getCode() == KeyCode.W ||
                    event.getCode() == KeyCode.UP) {
                duck.cancelQueuedJump();
            }
        });
    }

    private void spawnEntities(long now) {
        if (now < nextSpawnTime) return;

        double spawnX = MainApp.WINDOW_WIDTH + 50;
        int entityType;

        int nextSpawnNumber = totalSpawnCount + 1;
        boolean forceFoodOnLevel1Cycle = isLevel1 && (nextSpawnNumber % 4 == 0);
        boolean forceFood = forceFoodOnLevel1Cycle
                || nonFoodSpawnsSinceLastFood >= forceFoodAfterNonFoodSpawns;
        List<Integer> allowedTypes;
        if (forceFood) {
            allowedTypes = List.of(1);
        } else if (nonFoodSpawnsSinceCycleReset < minNonFoodSpawnsBeforeFoodOpportunity) {
            allowedTypes = List.of(0, 2);
        } else {
            allowedTypes = List.of(0, 1, 2);
        }

        if (allowedTypes.size() == 1) {
            entityType = allowedTypes.get(0);
        } else if (spawnHistory.size() == 2 && spawnHistory.get(0).equals(spawnHistory.get(1))) {
            int lastSpawnedType = spawnHistory.get(0);
            List<Integer> possibleTypes = allowedTypes.stream()
                    .filter(i -> i != lastSpawnedType)
                    .collect(Collectors.toList());
            if (possibleTypes.isEmpty()) {
                entityType = allowedTypes.get(random.nextInt(allowedTypes.size()));
            } else {
                entityType = possibleTypes.get(random.nextInt(possibleTypes.size()));
            }
        } else {
            entityType = allowedTypes.get(random.nextInt(allowedTypes.size()));
        }

        switch (entityType) {
            case 0:
                Enemy enemy = currentLevel.spawnEnemy(spawnX);
                if (enemy != null) {
                    enemies.add(enemy);
                    addNodeToScene(enemy.getNode());
                }
                break;
            case 1:
                Food food = currentLevel.spawnFood(spawnX);
                if (food != null) {
                    foods.add(food);
                    addNodeToScene(food.getNode());
                }
                break;
            case 2:
                Obstacle obstacle = currentLevel.spawnObstacle(spawnX);
                if (obstacle != null) {
                    obstacles.add(obstacle);
                    addNodeToScene(obstacle.getNode());
                }
                break;
        }

        if (entityType == 1) {
            nonFoodSpawnsSinceLastFood = 0;
            nonFoodSpawnsSinceCycleReset = 0;
        } else {
            nonFoodSpawnsSinceLastFood++;
            if (nonFoodSpawnsSinceCycleReset < minNonFoodSpawnsBeforeFoodOpportunity) {
                nonFoodSpawnsSinceCycleReset++;
            } else {
                nonFoodSpawnsSinceCycleReset = 1;
            }
        }

        spawnHistory.add(entityType);
        if (spawnHistory.size() > 2) {
            spawnHistory.remove(0);
        }

        totalSpawnCount++;

        long delay = (long) ((1.5 + Math.random() * 2.5) * 1_000_000_000);
        nextSpawnTime = now + delay;
    }

    private void addNodeToScene(javafx.scene.Node node) {
        int uiIndex = root.getChildren().indexOf(pauseButton);
        if (uiIndex != -1) {
            root.getChildren().add(uiIndex, node);
        } else {
            root.getChildren().add(node);
        }
    }

    private void updateEnemies(double deltaTime) {
        Iterator<Enemy> iterator = enemies.iterator();
        while (iterator.hasNext()) {
            Enemy enemy = iterator.next();
            enemy.update(deltaTime);
            if (!enemy.isActive()) {
                root.getChildren().remove(enemy.getNode());
                iterator.remove();
                continue;
            }
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
                    timeUtil.increaseTime(5);
                    if (healthBar.isDead()) {
                        if (enemy instanceof Dog)             deathCause = DeathCause.DOG;
                        else if (enemy instanceof CatBrown)   deathCause = DeathCause.CAT;
                        else if (enemy instanceof Eagle)      deathCause = DeathCause.EAGLE;
                        else                                  deathCause = DeathCause.CAT;
                        gameOver();
                    }
                }
            }
        }
    }

    private void updateFoods(double deltaTime) {
        Iterator<Food> iterator = foods.iterator();
        while (iterator.hasNext()) {
            Food food = iterator.next();
            food.update(deltaTime);
            if (!food.isActive()) {
                root.getChildren().remove(food.getNode());
                iterator.remove();
                continue;
            }
            if (CollisionUtil.isColliding(duck.getHitBox(), food.getHitBox())) {
                food.deactivate();
                if (!healthBar.isFull()) {
                    healthBar.increaseHealth();
                }
                sleepBar.addSegment();
                duck.eat();
                duck.powerUp();
                timeUtil.increaseTime(10);
            }
        }
    }

    private void updateObstacles(double deltaTime) {
        Iterator<Obstacle> iterator = obstacles.iterator();
        while (iterator.hasNext()) {
            Obstacle obstacle = iterator.next();
            obstacle.update(deltaTime);
            if (!obstacle.isActive()) {
                root.getChildren().remove(obstacle.getNode());
                iterator.remove();
                continue;
            }
            if (!obstacle.hasCollided() && CollisionUtil.isColliding(duck.getHitBox(), obstacle.getHitBox())) {
                obstacle.markCollided();
                duck.hit();
                healthBar.decreaseHealth();
                MusicManager.getInstance().playSfx("/audio/sound_effect/hit.mp3");
                sleepBar.decreaseSegment();
                timeUtil.increaseTime(5);
                if (healthBar.isDead()) {
                    deathCause = DeathCause.OBSTACLE;
                    gameOver();
                }
            }
        }
    }

    private void startGameLoop() {
        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameTime == 0) {
                    lastFrameTime = now;
                    return;
                }

                double deltaTime = (now - lastFrameTime) / 1_000_000_000.0;
                lastFrameTime = now;

                if (!isPaused) {
                    if (duckRunningOff) {
                        duck.update(deltaTime);
                        drawProgressBar();
                        if (duck.hasRunOff(MainApp.WINDOW_WIDTH)) {
                            duckRunningOff = false;
                            showVictoryScreen();
                        }
                        return;
                    }

                    duck.setSleepy(!sleepBar.isEmpty());
                    double effectiveDelta = duck.isCrouching() ? deltaTime * 0.75 : deltaTime;
                    updateBackground(effectiveDelta);
                    duck.update(deltaTime);
                    if (!levelCompleted) spawnEntities(now);
                    updateEnemies(effectiveDelta);
                    updateFoods(effectiveDelta);
                    updateObstacles(effectiveDelta);

                    // ── Redraw progress bar every frame ──────────────────────
                    drawProgressBar();
                    // ─────────────────────────────────────────────────────────

                    if (sleepBar.isFull()) {
                        deathCause = DeathCause.SLEEP;
                        gameOver();
                    }
                }
            }
        };
        timeUtil.start();
        startBgMusic();
        gameLoop.start();
    }

    private void pauseGame() {
        if (isPaused) return;
        isPaused = true;
        timeUtil.stop();
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
        timeUtil.start();
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
        timeUtil.reset();
        spawnHistory.clear();
        nonFoodSpawnsSinceLastFood = 0;
        nonFoodSpawnsSinceCycleReset = 0;
        totalSpawnCount = 0;
        bgScrolledTotal = 0;
        levelCompleted  = false;
        duckRunningOff  = false;

        // ── Reset progress bar ───────────────────────────────────────────────
        levelScrolledPixels = 0.0;
        runOffStartX        = 200.0;
        levelProgressBar.reset();
        // ────────────────────────────────────────────────────────────────────

        Image normalBg = AssetLoader.getImage(currentLevel.getBackgroundPath());
        background1.setImage(normalBg);
        background2.setImage(normalBg);
        startBgMusic();
    }

    private void gameOver() {
        if (gameLoop == null) return;

        gameLoop.stop();
        gameLoop = null;
        timeUtil.stop();
        stopBgMusic();

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

    private void showGameOverScreen(String imagePath) {
        // play game over sound
        MusicManager.getInstance().playOneShot("/audio/music/game_over.mp3", 1.0);

        javafx.scene.shape.Rectangle darkOverlay = new javafx.scene.shape.Rectangle(
                MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT, Color.rgb(0, 0, 0, 0.8)
        );

        StackPane frameContainer = new StackPane();
        frameContainer.getStyleClass().add("game-over-frame-container");

        ImageView frameView = new ImageView(AssetLoader.getImage("/images/game_over/game_over_frame.png"));
        frameView.setFitWidth(750);
        frameView.setPreserveRatio(true);

        VBox contentLayout = new VBox(25);
        contentLayout.setAlignment(javafx.geometry.Pos.CENTER);

        Label gameOverLabel = new Label("GAME OVER");
        gameOverLabel.getStyleClass().add("game-over-title");

        ImageView deathView = new ImageView(AssetLoader.getImage(imagePath));
        deathView.setFitHeight(180);
        deathView.setPreserveRatio(true);

        HBox buttonRow = new HBox(50);
        buttonRow.setAlignment(Pos.CENTER);

        Button restartBtn = new Button();
        restartBtn.setGraphic(createButtonIcon("/images/pause_menu/restart_button.png"));
        restartBtn.getStyleClass().add("game-over-button");
        restartBtn.setOnAction(e -> {
            GameScene fresh = new GameScene(currentLevel);
            MainApp.switchScene(fresh.getScene());
        });

        Button exitBtn = new Button();
        exitBtn.setGraphic(createButtonIcon("/images/pause_menu/exit_button.png"));
        exitBtn.getStyleClass().add("game-over-button");
        exitBtn.setOnAction(e -> exitToMenu());

        buttonRow.getChildren().addAll(restartBtn, exitBtn);
        contentLayout.getChildren().addAll(gameOverLabel, deathView, buttonRow);

        frameContainer.getChildren().addAll(frameView, contentLayout);
        frameContainer.setTranslateX((MainApp.WINDOW_WIDTH - 750) / 2.0);
        frameContainer.setTranslateY((MainApp.WINDOW_HEIGHT - 480) / 2.0);

        root.getChildren().addAll(darkOverlay, frameContainer);

        String css = Objects.requireNonNull(getClass().getResource("/styles/game_over.css")).toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }

    private ImageView createButtonIcon(String path) {
        ImageView iv = new ImageView(AssetLoader.getImage(path));
        iv.setFitWidth(100);
        iv.setPreserveRatio(true);
        return iv;
    }

    private void showVictoryScreen() {
        if (gameLoop != null) {
            gameLoop.stop();
            gameLoop = null;
        }
        timeUtil.stop();
        stopBgMusic();

        int elapsed = timeUtil.getElapsedSeconds();
        if (currentLevel instanceof Level1) {
            HighScoreManager.submitLevel1(elapsed);
        } else if (currentLevel instanceof Level2) {
            HighScoreManager.submitLevel2(elapsed);
        } else if (currentLevel instanceof Level3) {
            HighScoreManager.submitLevel3(elapsed);
            // Skip victory screen — show ending cutscene instead
            showEndingCutscene();
            return;
        }

        // play victory music
        MusicManager.getInstance().playOneShot("/audio/music/victory.mp3", 1.0);

        for (Enemy e : enemies) root.getChildren().remove(e.getNode());
        enemies.clear();
        for (Food f : foods) root.getChildren().remove(f.getNode());
        foods.clear();
        for (Obstacle o : obstacles) root.getChildren().remove(o.getNode());
        obstacles.clear();

        javafx.scene.shape.Rectangle darkOverlay = new javafx.scene.shape.Rectangle(
                MainApp.WINDOW_WIDTH, MainApp.WINDOW_HEIGHT, Color.rgb(0, 0, 0, 0.8));
        darkOverlay.setMouseTransparent(true);

        StackPane frameContainer = new StackPane();
        ImageView frameView = new ImageView(
                AssetLoader.getImage("/images/game_over/game_over_frame.png"));
        frameView.setFitWidth(750);
        frameView.setPreserveRatio(true);

        VBox contentLayout = new VBox(25);
        contentLayout.setAlignment(Pos.CENTER);

        Label levelPassedLabel = new Label("LEVEL PASSED!");
        levelPassedLabel.getStyleClass().add("game-over-title");

        ImageView victoryView = new ImageView(
                AssetLoader.getImage("/images/duck/victory.png"));
        victoryView.setFitHeight(180);
        victoryView.setPreserveRatio(true);

        HBox buttonRow = new HBox(40);
        buttonRow.setAlignment(Pos.CENTER);

        Button restartBtn = new Button();
        restartBtn.setGraphic(createButtonIcon("/images/pause_menu/restart_button.png"));
        restartBtn.getStyleClass().add("game-over-button");
        restartBtn.setOnAction(e -> {
            GameScene fresh = new GameScene(currentLevel);
            MainApp.switchScene(fresh.getScene());
        });

        Button playBtn = new Button();
        playBtn.setGraphic(createButtonIcon("/images/pause_menu/play_button.png"));
        playBtn.getStyleClass().add("game-over-button");
        playBtn.setOnAction(e -> {
            Level nextLevel = getNextLevel();
            if (nextLevel != null) {
                GameScene next = new GameScene(nextLevel);
                MainApp.switchScene(next.getScene());
            } else {
                exitToMenu();
            }
        });

        Button exitBtn = new Button();
        exitBtn.setGraphic(createButtonIcon("/images/pause_menu/exit_button.png"));
        exitBtn.getStyleClass().add("game-over-button");
        exitBtn.setOnAction(e -> exitToMenu());

        buttonRow.getChildren().addAll(restartBtn, playBtn, exitBtn);
        contentLayout.getChildren().addAll(levelPassedLabel, victoryView, buttonRow);

        frameContainer.getChildren().addAll(frameView, contentLayout);
        frameContainer.setTranslateX((MainApp.WINDOW_WIDTH - 750) / 2.0);
        frameContainer.setTranslateY((MainApp.WINDOW_HEIGHT - 480) / 2.0);

        root.getChildren().addAll(darkOverlay, frameContainer);

        String css = Objects.requireNonNull(
                getClass().getResource("/styles/game_over.css")).toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }

    private void showEndingCutscene() {
        // Stop any leftover music
        // play victory music only for Level 1 and 2
        if (!(currentLevel instanceof Level3)) {
            MusicManager.getInstance().playOneShot("/audio/music/victory.mp3", 1.0);
        }

        // Clean up remaining entities from the scene
        for (Enemy e : enemies) root.getChildren().remove(e.getNode());
        enemies.clear();
        for (Food f : foods) root.getChildren().remove(f.getNode());
        foods.clear();
        for (Obstacle o : obstacles) root.getChildren().remove(o.getNode());
        obstacles.clear();

        // Switch to EndingScene
        EndingScene endingScene = new EndingScene();
        MainApp.switchScene(endingScene.createScene());
    }

    private Level getNextLevel() {
        if (currentLevel instanceof Level1) {
            return new Level2(groundY);
        } else if (currentLevel instanceof Level2) {
            return new Level3(groundY);
        } else {
            return null;
        }
    }

    private void openSettings() {
        pauseMenu.getRoot().getChildren().forEach(node -> {
            if (!(node instanceof javafx.scene.shape.Rectangle)) {
                node.setVisible(false);
            }
        });

        settingsMenu.setVisible(true);
        settingsMenu.toFront();
        menuLayer.toFront();
        isPaused = true;
    }

    private void exitToMenu() {
        if (gameLoop != null) {
            gameLoop.stop();
        }
        stopBgMusic();
        MenuScene menuScene = new MenuScene(MainApp.getPrimaryStage());
        MainApp.switchScene(menuScene.createScene());
    }

    public Scene getScene() {
        return scene;
    }
}
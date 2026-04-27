package edu.bauet.java.cse.duckrun.entities;

import edu.bauet.java.cse.duckrun.utils.AssetLoader;
import edu.bauet.java.cse.duckrun.utils.MusicManager;
import javafx.animation.PauseTransition;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.Light;
import javafx.scene.effect.Lighting;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class Duck {

    private final Group duckGroup;
    private final ImageView duckView;
    private final ImageView duckShadow;
    private final Rectangle debugHitbox;

    private final Image runningImage;
    private final Image runningMidPointImage;
    private final Image duckingImage;
    private final Image duckingMidPointImage;
    private final Image jumpingImage;
    private final Image normalShadowImage;
    private final Image jumpShadowImage;

    // Sleepy variants — used when the sleep bar has any segments filled
    private final Image runningImageSleepy;
    private final Image runningMidPointImageSleepy;
    private final Image duckingImageSleepy;
    private final Image duckingMidPointImageSleepy;
    private final Image jumpingImageSleepy;

    private boolean sleepy = false;

    private final double groundLine;

    // FIX: was "public" — kept private so external code uses isJumping()
    private boolean jumping = false;
    private boolean goingUp = false;
    private boolean comingDown = false;

    // Default values — overridden per-level via setJumpSpeed() / setFallSpeed()
    private double jumpHeight = 250;
    private double jumpSpeed  = 900; // pixels per second
    private double fallSpeed  = 400; // pixels per second

    private double effectDuration   = 0.5;
    private double hitIntensity     = 3;
    private double powerUpIntensity = 3;

    private double maxY;
    private boolean crouching = false;

    private int frameCounter = 0;
    private boolean toggleFrame = false;

    // Path to the stepping sound — swap this to whatever file you place in resources
    private static final String STEP_SFX = "/audio/sound_effect/step3x.mp3";

    private final double DISPLAY_HEIGHT = 90;

    public Duck(double x, double groundLine) {

        this.groundLine = groundLine;

        runningImage             = AssetLoader.getImage("/images/duck/running.png");
        runningMidPointImage     = AssetLoader.getImage("/images/duck/running_mid_point.png");
        duckingImage             = AssetLoader.getImage("/images/duck/ducking.png");
        duckingMidPointImage     = AssetLoader.getImage("/images/duck/ducking_mid_point.png");
        jumpingImage             = AssetLoader.getImage("/images/duck/jumping.png");

        runningImageSleepy           = AssetLoader.getImage("/images/duck/running_sleepy.png");
        runningMidPointImageSleepy   = AssetLoader.getImage("/images/duck/running_mid_point_sleepy.png");
        duckingImageSleepy           = AssetLoader.getImage("/images/duck/ducking_sleepy.png");
        duckingMidPointImageSleepy   = AssetLoader.getImage("/images/duck/ducking_mid_point_sleepy.png");
        jumpingImageSleepy           = AssetLoader.getImage("/images/duck/jumping_sleepy.png");

        normalShadowImage = AssetLoader.getImage("/images/shadow/Shadow(normal).png");
        jumpShadowImage   = AssetLoader.getImage("/images/shadow/Shadow(small).png");

        duckView = new ImageView(runningImage);
        duckView.setFitHeight(DISPLAY_HEIGHT);
        duckView.setPreserveRatio(true);
        duckView.setLayoutY(groundLine - DISPLAY_HEIGHT);

        duckShadow = new ImageView(normalShadowImage);
        duckShadow.setFitHeight(55);
        duckShadow.setFitWidth(DISPLAY_HEIGHT);
        duckShadow.setPreserveRatio(false);
        duckShadow.setLayoutY(groundLine - DISPLAY_HEIGHT);

        debugHitbox = new Rectangle();
        debugHitbox.setFill(Color.TRANSPARENT);
        debugHitbox.setStroke(Color.TRANSPARENT);
        debugHitbox.setStrokeWidth(0);

        duckGroup = new Group(duckShadow, duckView, debugHitbox);
        duckGroup.setLayoutX(x);
    }

    // ------------------------------------------------------------------
    // Per-level speed configuration — call these right after construction
    // ------------------------------------------------------------------

    /**
     * Sets the speed at which the duck rises during a jump (pixels per second).
     * Called by GameScene using the value from the current Level.
     */
    public void setJumpSpeed(double jumpSpeed) {
        this.jumpSpeed = jumpSpeed;
    }

    /**
     * Sets the speed at which the duck falls back down after a jump (pixels per second).
     * Called by GameScene using the value from the current Level.
     */
    public void setFallSpeed(double fallSpeed) {
        this.fallSpeed = fallSpeed;
    }

    /**
     * Called by GameScene whenever the sleep bar changes.
     * When true, the duck uses drowsy sprite variants.
     */
    public void setSleepy(boolean sleepy) {
        this.sleepy = sleepy;
    }

    // ------------------------------------------------------------------

    public void update(double deltaTime) {

        if (runningOff) {
            duckGroup.setLayoutX(duckGroup.getLayoutX() + RUN_OFF_SPEED * deltaTime);
            animate();
            return;
        }

        duckShadow.setLayoutY(groundLine - DISPLAY_HEIGHT + 40);

        if (jumping) {
            duckShadow.setImage(jumpShadowImage);
            duckShadow.setLayoutY(groundLine - DISPLAY_HEIGHT + 52.5);
            duckShadow.setLayoutX(20);
            duckShadow.setOpacity(0.6);
            duckShadow.setFitHeight(30);
            duckShadow.setFitWidth(30);
        } else {
            duckShadow.setImage(normalShadowImage);
            duckShadow.setOpacity(1.0);
            duckShadow.setFitHeight(60);
            duckShadow.setFitWidth(80);
            duckShadow.setLayoutX(5);
            duckShadow.setOpacity(0.8);
        }

        if (goingUp) {
            duckView.setImage(sleepy ? runningImageSleepy : runningImage);
            duckView.setLayoutY(duckView.getLayoutY() - jumpSpeed * deltaTime);
            if (duckView.getLayoutY() <= maxY) {
                goingUp = false;
                comingDown = true;
                duckView.setImage(sleepy ? jumpingImageSleepy : jumpingImage);
            }
        } else if (comingDown) {
            duckView.setLayoutY(duckView.getLayoutY() + fallSpeed * deltaTime);
            if (duckView.getLayoutY() >= groundLine - DISPLAY_HEIGHT) {
                duckView.setLayoutY(groundLine - DISPLAY_HEIGHT);
                comingDown = false;
                jumping = false;
            }
        }

        animate();
        updateDebugHitbox();
    }

    private int animationThreshold = 25;

    public void setAnimationThreshold(int threshold) {
        this.animationThreshold = threshold;
    }

    private void animate() {

        frameCounter++;
        boolean frameJustToggled = false;

        if (frameCounter >= animationThreshold) {
            toggleFrame = !toggleFrame;
            frameCounter = 0;
            frameJustToggled = true;
        }

        if (crouching && !jumping) {
            duckView.setImage(toggleFrame
                    ? (sleepy ? duckingImageSleepy         : duckingImage)
                    : (sleepy ? duckingMidPointImageSleepy : duckingMidPointImage));
            duckView.setLayoutY(groundLine - DISPLAY_HEIGHT + 15);

            // Play step sound on each waddle frame while crouching
            if (frameJustToggled) {
                MusicManager.getInstance().playSfx(STEP_SFX, 0.4);
            }

        } else if (!jumping) {
            duckView.setImage(toggleFrame
                    ? (sleepy ? runningImageSleepy         : runningImage)
                    : (sleepy ? runningMidPointImageSleepy : runningMidPointImage));
            double runOffset = toggleFrame ? -2 : 0;
            duckView.setLayoutY(groundLine - DISPLAY_HEIGHT + runOffset);

            // Play step sound on every frame toggle while running on the ground
            if (frameJustToggled) {
                MusicManager.getInstance().playSfx(STEP_SFX, 0.4);
            }
        }
        // No step sound while jumping — duck is airborne
    }

    private void updateDebugHitbox() {
        Bounds localBounds = getLocalHitbox();
        debugHitbox.setX(localBounds.getMinX());
        debugHitbox.setY(localBounds.getMinY());
        debugHitbox.setWidth(localBounds.getWidth());
        debugHitbox.setHeight(localBounds.getHeight());
    }

    public boolean isJumping() {
        return jumping;
    }

    public boolean isCrouching() {
        return crouching;
    }

    public void jump() {
        if (!jumping && !crouching) {
            jumping = true;
            goingUp = true;
            maxY = duckView.getLayoutY() - jumpHeight;
        }
    }

    public void setCrouching(boolean crouch) {
        if (!jumping) {
            this.crouching = crouch;
        }
    }

    public void eat() {
        if (!jumping) {
            crouching = true;
            PauseTransition pause = new PauseTransition(Duration.seconds(0.25));
            pause.setOnFinished(e -> crouching = false);
            pause.play();
        }
    }

    public void hit() {
        applyEffect(Color.RED, hitIntensity);
    }

    public void powerUp() {
        applyEffect(Color.LIMEGREEN, powerUpIntensity);
    }

    private void applyEffect(Color color, double intensity) {
        Lighting lighting = new Lighting();
        lighting.setSurfaceScale(0.0);
        lighting.setSpecularConstant(intensity);
        lighting.setDiffuseConstant(intensity);

        Light.Distant light = new Light.Distant();
        light.setColor(color);
        lighting.setLight(light);

        duckView.setEffect(lighting);

        PauseTransition pause = new PauseTransition(Duration.seconds(effectDuration));
        pause.setOnFinished(e -> duckView.setEffect(null));
        pause.play();
    }


    private boolean runningOff = false;
    private static final double RUN_OFF_SPEED = 600; // pixels per second

    /** Starts the duck running off the right side of the screen. */
    public void startRunOff() {
        runningOff = true;
        jumping = false;
        goingUp = false;
        comingDown = false;
        crouching = false;
        duckView.setLayoutY(groundLine - DISPLAY_HEIGHT);
    }

    /** Returns true once the duck has fully exited the right edge. */
    public boolean hasRunOff(double screenWidth) {
        return runningOff && duckGroup.getLayoutX() > screenWidth + 50;
    }

    public boolean isRunningOff() {
        return runningOff;
    }

    public void resetState() {
        jumping = false;
        crouching = false;
        goingUp = false;
        comingDown = false;
        runningOff = false;
        duckGroup.setLayoutX(200);
        duckView.setLayoutY(groundLine - DISPLAY_HEIGHT);
        duckView.setEffect(null);
    }

    public Node getNode() {
        return duckGroup;
    }

    private Bounds getLocalHitbox() {
        Bounds b = duckView.getBoundsInParent();

        double shrinkX = b.getWidth() * 0.3;
        double shrinkY;

        if (jumping) {
            shrinkY = b.getHeight() * 0.5;
        } else {
            shrinkY = b.getHeight() * 0.3;
        }

        return new BoundingBox(
                b.getMinX() + shrinkX,
                b.getMinY() + shrinkY,
                b.getWidth() - shrinkX * 2,
                b.getHeight() - shrinkY
        );
    }

    public Bounds getHitBox() {
        return debugHitbox.localToScene(debugHitbox.getBoundsInLocal());
    }

    public void setEffectDuration(double seconds) {
        this.effectDuration = seconds;
    }

    public void setHitIntensity(double intensity) {
        this.hitIntensity = intensity;
    }

    public void setPowerUpIntensity(double intensity) {
        this.powerUpIntensity = intensity;
    }
}
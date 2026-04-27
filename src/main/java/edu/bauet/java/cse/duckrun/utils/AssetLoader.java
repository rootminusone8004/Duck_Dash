package edu.bauet.java.cse.duckrun.utils;

import javafx.scene.image.Image;
import javafx.scene.media.Media;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AssetLoader {

    private static final Logger LOGGER = Logger.getLogger(AssetLoader.class.getName());
    private static final boolean FAIL_FAST = false;

    private static final Map<String, Image> imageCache = new HashMap<>();
    private static final Map<String, Media> videoCache = new HashMap<>();
    private static final Map<String, Media> musicCache = new HashMap<>();

    public static Media loadMusic(String resourcePath) {
        if (musicCache.containsKey(resourcePath)) {
            return musicCache.get(resourcePath);
        }

        try {
            URL url = AssetLoader.class.getResource(resourcePath);
            if (url == null) {
                LOGGER.severe("MUSIC NOT FOUND: " + resourcePath);
                return null;
            }
            Media media = new Media(url.toExternalForm());
            musicCache.put(resourcePath, media);
            return media;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading music: " + resourcePath, e);
            return null;
        }
    }


    public static Media loadVideo(String resourcePath) {
        try {
            URL url = AssetLoader.class.getResource(resourcePath);
            if (url == null) {
                System.out.println("VIDEO NOT FOUND: " + resourcePath);
                return null;
            }
            return new Media(url.toExternalForm()); // fresh every time for video
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public static Image getImage(String path) {

        if (imageCache.containsKey(path)) {
            return imageCache.get(path);
        }

        Image image = loadImageInternal(path);
        imageCache.put(path, image);

        return image;
    }


    private static Image loadImageInternal(String path) {

        try (InputStream stream = AssetLoader.class.getResourceAsStream(path)) {

            if (stream == null) {
                return handleMissingImage(path);
            }

            return new Image(stream);

        } catch (Exception e) {

            LOGGER.log(Level.SEVERE, "Error loading image: " + path, e);
            return handleMissingImage(path);
        }
    }


    private static Image handleMissingImage(String path) {

        String message = "Image not found: " + path;

        if (FAIL_FAST) {
            throw new RuntimeException(message);
        }

        LOGGER.warning(message + " | Using placeholder image.");

        return createPlaceholderImage();
    }


    private static Image createPlaceholderImage() {

        return new Image(
                "data:image/png;base64,"
                        + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
        );
    }


    private static final String[] PRELOAD_IMAGE_PATHS = {

            "/images/duck/running.png",
            "/images/duck/running_mid_point.png",
            "/images/duck/running_sleepy.png",
            "/images/duck/running_mid_point_sleepy.png",
            "/images/duck/ducking.png",
            "/images/duck/ducking_mid_point.png",
            "/images/duck/ducking_sleepy.png",
            "/images/duck/ducking_mid_point_sleepy.png",
            "/images/duck/jumping.png",
            "/images/duck/jumping_sleepy.png",
            "/images/duck/defeat.png",
            "/images/duck/victory.png",
            "/images/duck/sleeping.png",
            "/images/duck/base_duck.png",

            "/images/shadow/Shadow(normal).png",
            "/images/shadow/Shadow(small).png",

            "/images/enemies/Worm.png",
            "/images/enemies/Bread.png",
            "/images/enemies/Cockroach.png",
            "/images/enemies/boy_state_1.png",
            "/images/enemies/boy_state_2.png",
            "/images/enemies/Cat_state_1.png",
            "/images/enemies/Cat_state_2.png",
            "/images/enemies/Eagle_state_1.png",
            "/images/enemies/Eagle_state_2.png",

            "/images/game_over/game_over_cat.png",
            "/images/game_over/game_over_bump.png",
            "/images/game_over/game_over_eagle.png",
            "/images/game_over/game_over_caught.png",

            "/images/indicator/heart_full.png",
            "/images/indicator/heart_empty.png",
            "/images/indicator/sleep_bar_full.png",
            "/images/indicator/sleep_bar_empty.png",

            "/images/obstacles/bottle.png",
            "/images/obstacles/plant1.png",
            "/images/obstacles/plant2.png",
            "/images/obstacles/Chair_wood.png",
            "/images/obstacles/chair_black.png",

            "/images/pause_menu/exit_button.png",
            "/images/pause_menu/play_button.png",
            "/images/pause_menu/pause_button.png",
            "/images/pause_menu/restart_button.png",
            "/images/pause_menu/settings_button.png",
            "/images/pause_menu/pause_menu_frame.png",
            "/images/pause_menu/settings_menu_frame.png",

            "/images/backgrounds/level1.png",
            "/images/backgrounds/level2.png",
            "/images/backgrounds/level3.png",

            "/images/ui/duck_emoji.png",
            "/images/ui/hall_background.png",
            "/images/ui/hall_ui_1200x600.png",

            "/Story/startstory.png"
    };


    private static final String[] PRELOAD_VIDEO_PATHS = {

            "/Story/opening.mp4"
    };


    public static void preloadAssets() {

        LOGGER.info("Preloading image assets...");

        for (String path : PRELOAD_IMAGE_PATHS) {
            getImage(path);
        }

        LOGGER.info("Image preloading complete.");
    }


    public static void preloadVideos() {
        // Videos are NOT cached because MediaPlayer.dispose() kills the underlying
        // Media object — caching a disposed Media causes black screen on next use.
        // This method is intentionally a no-op; loadFreshVideo() creates on demand.
        LOGGER.info("Video preloading skipped (videos are always created fresh).");
    }


    /**
     * Always returns a brand-new Media object for the given path.
     * Use this for videos that will be played then disposed (e.g. cutscenes).
     * Never use the old getCachedVideo() for disposable media.
     */
    public static Media loadFreshVideo(String resourcePath) {
        return loadVideo(resourcePath);
    }


    public static void clearAll() {

        imageCache.clear();
        videoCache.clear();

        LOGGER.info("Image and video cache cleared.");
    }
}
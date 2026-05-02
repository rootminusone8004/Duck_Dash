package edu.bauet.java.cse.duckrun.utils;

import javafx.scene.image.Image;
import javafx.scene.media.Media;

import java.io.FileOutputStream;
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
    private static final Map<String, Media> musicCache = new HashMap<>();
    private static final Map<String, String> videoUriCache = new HashMap<>();

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

    public static String loadVideoUri(String resourcePath) {
        if (videoUriCache.containsKey(resourcePath)) {
            return videoUriCache.get(resourcePath);
        }

        try (InputStream is = AssetLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOGGER.severe("VIDEO NOT FOUND: " + resourcePath);
                return null;
            }
            byte[] videoBytes = is.readAllBytes();
            java.io.File tempFile = java.io.File.createTempFile("duckrun_video_", ".mp4");
            tempFile.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(videoBytes);
            }
            String uri = tempFile.toURI().toString();
            videoUriCache.put(resourcePath, uri);
            LOGGER.info("Video preloaded to temp file: " + uri);
            return uri;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading video: " + resourcePath, e);
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
        try {
            URL url = AssetLoader.class.getResource(path);
            if (url == null) {
                return handleMissingImage(path);
            }
            return new Image(url.toExternalForm());
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
            "/Story/opening2.mp4",
            "/Story/ending.mp4"
    };

    public static void preloadAssets() {
        LOGGER.info("Preloading image assets...");
        for (String path : PRELOAD_IMAGE_PATHS) {
            getImage(path);
        }
        LOGGER.info("Image preloading complete.");
    }

    public static void preloadVideos() {
        LOGGER.info("Preloading videos...");
        for (String path : PRELOAD_VIDEO_PATHS) {
            loadVideoUri(path);
        }
        LOGGER.info("Video preloading complete.");
    }

    public static void clearAll() {
        imageCache.clear();
        videoUriCache.clear();
        LOGGER.info("Image and video cache cleared.");
    }
}
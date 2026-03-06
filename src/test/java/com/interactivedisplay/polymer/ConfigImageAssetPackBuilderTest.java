package com.interactivedisplay.polymer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigImageAssetPackBuilderTest {
    @Test
    void syncShouldExportConfigImagesIntoPolymerSourceAssets(@TempDir Path tempDir) throws Exception {
        Path configDir = tempDir.resolve("config");
        Path gameDir = tempDir.resolve("game");
        Path imagesDir = configDir.resolve("interactivedisplay").resolve("images").resolve("ui");
        Files.createDirectories(imagesDir);
        writePng(imagesDir.resolve("sample.png"), 0xFF00FF00);

        ConfigImageAssetPackBuilder builder = new ConfigImageAssetPackBuilder(configDir, gameDir);
        int exported = builder.sync();

        assertEquals(1, exported);
        assertTrue(Files.exists(builder.targetTexturesDir().resolve("ui").resolve("sample.png")));
    }

    @Test
    void syncShouldRemoveStaleGeneratedAssets(@TempDir Path tempDir) throws Exception {
        Path configDir = tempDir.resolve("config");
        Path gameDir = tempDir.resolve("game");
        Path imagesDir = configDir.resolve("interactivedisplay").resolve("images");
        Files.createDirectories(imagesDir);
        Path source = imagesDir.resolve("sample.png");
        writePng(source, 0xFFFF0000);

        ConfigImageAssetPackBuilder builder = new ConfigImageAssetPackBuilder(configDir, gameDir);
        builder.sync();
        Path generated = builder.targetTexturesDir().resolve("sample.png");
        assertTrue(Files.exists(generated));

        Files.delete(source);
        int exported = builder.sync();

        assertEquals(0, exported);
        assertFalse(Files.exists(generated));
    }

    private static void writePng(Path path, int color) throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, new Color(color, true).getRGB());
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }
}

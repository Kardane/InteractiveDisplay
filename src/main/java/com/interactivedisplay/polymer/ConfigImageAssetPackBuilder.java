package com.interactivedisplay.polymer;

import com.interactivedisplay.InteractiveDisplay;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

public final class ConfigImageAssetPackBuilder {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("png", "jpg", "jpeg");

    private final Path imagesDir;
    private final Path targetTexturesDir;

    public ConfigImageAssetPackBuilder(Path configDir, Path gameDir) {
        this.imagesDir = configDir.resolve("interactivedisplay").resolve("images");
        this.targetTexturesDir = gameDir
                .resolve("polymer")
                .resolve("source_assets")
                .resolve("assets")
                .resolve(InteractiveDisplay.MOD_ID)
                .resolve("textures")
                .resolve("config_images");
    }

    public int sync() {
        try {
            Files.createDirectories(this.imagesDir);
            clearGeneratedAssets();

            int exported = 0;
            try (Stream<Path> paths = Files.walk(this.imagesDir)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    if (!isSupportedImage(path)) {
                        continue;
                    }

                    BufferedImage image = ImageIO.read(path.toFile());
                    if (image == null) {
                        continue;
                    }

                    Path output = outputPath(path);
                    Files.createDirectories(output.getParent());
                    ImageIO.write(image, "png", output.toFile());
                    exported++;
                }
            }

            InteractiveDisplay.LOGGER.info("[{}] config image pack sync exported={}", InteractiveDisplay.MOD_ID, exported);
            return exported;
        } catch (IOException exception) {
            InteractiveDisplay.LOGGER.warn("[{}] config image pack sync 실패 message={}", InteractiveDisplay.MOD_ID, exception.getMessage());
            return 0;
        }
    }

    Path targetTexturesDir() {
        return this.targetTexturesDir;
    }

    private boolean isSupportedImage(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    private Path outputPath(Path path) {
        Path relative = this.imagesDir.relativize(path).normalize();
        Path parent = relative.getParent() == null ? Path.of("") : relative.getParent();
        String fileName = relative.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot < 0 ? fileName : fileName.substring(0, dot);
        Path output = this.targetTexturesDir.resolve(parent).resolve(baseName + ".png").normalize();
        if (!output.startsWith(this.targetTexturesDir)) {
            throw new IllegalArgumentException("잘못된 이미지 경로: " + path);
        }
        return output;
    }

    private void clearGeneratedAssets() throws IOException {
        if (!Files.exists(this.targetTexturesDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(this.targetTexturesDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}

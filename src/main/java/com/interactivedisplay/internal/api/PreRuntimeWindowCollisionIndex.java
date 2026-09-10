package com.interactivedisplay.internal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.schema.ConfigDocumentLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

final class PreRuntimeWindowCollisionIndex {
    private static final Set<String> BUILT_IN_WINDOW_IDS = Set.of(
            "main_menu",
            "main_menu2",
            "gallery"
    );

    private final Path windowsDir;
    private final ConfigDocumentLoader documentLoader = new ConfigDocumentLoader();

    PreRuntimeWindowCollisionIndex(Path configDir) {
        this.windowsDir = configDir.resolve("interactivedisplay").resolve("windows");
    }

    boolean contains(ResourceLocation publicId) {
        if (publicId == null) {
            return false;
        }
        String internalId = PublicIdCodec.toInternalWindowId(publicId);
        if (BUILT_IN_WINDOW_IDS.contains(internalId)) {
            return true;
        }
        if (!Files.isDirectory(this.windowsDir)) {
            return false;
        }

        try (var files = Files.list(this.windowsDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .anyMatch(path -> internalId.equals(readId(path)));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String readId(Path path) {
        try {
            JsonNode root = this.documentLoader.load(path);
            return root.path("id").isTextual() ? root.path("id").textValue() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}

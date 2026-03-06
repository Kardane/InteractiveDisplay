package com.interactivedisplay.polymer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interactivedisplay.InteractiveDisplay;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PolymerConfigEnsurer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path polymerDir;

    public PolymerConfigEnsurer(Path configDir) {
        this.polymerDir = configDir.resolve("polymer");
    }

    public void ensure() {
        try {
            Files.createDirectories(this.polymerDir);
            patchAutoHost();
            patchResourcePack();
        } catch (IOException exception) {
            InteractiveDisplay.LOGGER.warn("[{}] polymer config patch 실패 message={}", InteractiveDisplay.MOD_ID, exception.getMessage());
        }
    }

    Path autoHostPath() {
        return this.polymerDir.resolve("auto-host.json");
    }

    Path resourcePackPath() {
        return this.polymerDir.resolve("resource-pack.json");
    }

    private void patchAutoHost() throws IOException {
        JsonObject root = readOrCreate(autoHostPath());
        root.addProperty("enabled", true);
        root.addProperty("required", true);
        root.addProperty("mod_override", true);
        root.addProperty("type", "polymer:automatic");
        write(autoHostPath(), root);
    }

    private void patchResourcePack() throws IOException {
        JsonObject root = readOrCreate(resourcePackPath());
        root.addProperty("resource_pack_location", "polymer/resource_pack.zip");
        write(resourcePackPath(), root);
    }

    private static JsonObject readOrCreate(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new JsonObject();
        }
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void write(Path path, JsonObject root) throws IOException {
        Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
    }
}

package com.interactivedisplay.core.interaction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CommandWhitelist {
    private static final String DEFAULT_FILE = "command_whitelist.json";

    private final Path filePath;
    private volatile List<String> allowedPrefixes = List.of();

    public CommandWhitelist(Path configDir) {
        this.filePath = configDir.resolve("interactivedisplay").resolve(DEFAULT_FILE);
    }

    public void reload() throws IOException {
        Files.createDirectories(this.filePath.getParent());
        ensureDefaultFile();

        JsonObject root = JsonParser.parseString(Files.readString(this.filePath, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray prefixes = root.getAsJsonArray("allowedPrefixes");
        List<String> values = new ArrayList<>();
        if (prefixes != null) {
            prefixes.forEach(element -> values.add(element.getAsString()));
        }
        this.allowedPrefixes = List.copyOf(values);
    }

    public boolean isAllowed(String command) {
        String normalized = normalize(command);
        for (String prefix : this.allowedPrefixes) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public List<String> allowedPrefixes() {
        return this.allowedPrefixes;
    }

    private void ensureDefaultFile() throws IOException {
        if (Files.exists(this.filePath)) {
            return;
        }

        JsonObject root = new JsonObject();
        JsonArray prefixes = new JsonArray();
        root.add("allowedPrefixes", prefixes);
        Files.writeString(this.filePath, root.toString(), StandardCharsets.UTF_8);
    }

    private static String normalize(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }
}

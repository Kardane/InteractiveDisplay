package com.interactivedisplay.core.interaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.schema.ConfigDocumentLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CommandWhitelist {
    private static final String DEFAULT_FILE = "command_whitelist.yaml";
    private static final String LEGACY_FILE = "command_whitelist.json";
    private static final String DEFAULT_RESOURCE = "/defaults/interactivedisplay/command_whitelist.yaml";

    private final Path filePath;
    private final ConfigDocumentLoader documentLoader = new ConfigDocumentLoader();
    private volatile List<String> allowedPrefixes = List.of();

    public CommandWhitelist(Path configDir) {
        this.filePath = configDir.resolve("interactivedisplay").resolve(DEFAULT_FILE);
    }

    public void reload() throws IOException {
        Files.createDirectories(this.filePath.getParent());
        ensureDefaultFile();
        warnLegacyFile();

        JsonNode root = this.documentLoader.load(this.filePath);
        if (!root.isObject()) {
            throw new IOException("command whitelist root must be object");
        }

        JsonNode prefixes = root.get("allowedPrefixes");
        List<String> values = new ArrayList<>();
        if (prefixes != null) {
            if (!prefixes.isArray()) {
                throw new IOException("command whitelist allowedPrefixes must be array");
            }
            for (JsonNode element : prefixes) {
                if (!element.isTextual()) {
                    throw new IOException("command whitelist allowedPrefixes entries must be strings");
                }
                values.add(element.textValue());
            }
        }
        this.allowedPrefixes = List.copyOf(values);
    }

    public boolean isAllowed(String command) {
        String normalized = normalize(command);
        if (normalized.isEmpty()) {
            return false;
        }
        for (String prefix : this.allowedPrefixes) {
            if (prefix == null || prefix.isEmpty() || !normalized.startsWith(prefix)) {
                continue;
            }
            if (Character.isWhitespace(prefix.charAt(prefix.length() - 1))) {
                return true;
            }
            if (normalized.length() == prefix.length() || Character.isWhitespace(normalized.charAt(prefix.length()))) {
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

        try (var input = CommandWhitelist.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new IOException("기본 command whitelist 리소스를 찾을 수 없음");
            }
            Files.copy(input, this.filePath);
        }
    }

    private void warnLegacyFile() {
        Path legacyPath = this.filePath.resolveSibling(LEGACY_FILE);
        if (Files.exists(legacyPath)) {
            InteractiveDisplay.LOGGER.warn(
                    "[{}] Unsupported legacy config file: {}. InteractiveDisplay supports .yaml configuration only.",
                    InteractiveDisplay.MOD_ID,
                    legacyPath
            );
        }
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

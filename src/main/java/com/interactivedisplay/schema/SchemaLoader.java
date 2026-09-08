package com.interactivedisplay.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.window.WindowGroupDefinition;
import com.interactivedisplay.core.window.WindowDefinition;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;

public final class SchemaLoader {
    private static final String DEFAULT_RESOURCE_ROOT = "/defaults/interactivedisplay/";
    private static final String DEFAULT_WINDOW_FILE = "main_menu.yaml";
    private static final String DEFAULT_MAIN_MENU2_FILE = "main_menu2.yaml";
    private static final String DEFAULT_GALLERY_FILE = "gallery.yaml";
    private static final String DEFAULT_GROUP_FILE = "menu_group.yaml";
    private static final String DEFAULT_REMOTE_EXAMPLE_FILE = "gallery_remote.example.yaml.disabled";
    private static final String DEFAULT_SAMPLE_IMAGE = "sample_local.png";

    private final Path configRoot;
    private final Path windowsDir;
    private final Path groupsDir;
    private final Path imagesDir;
    private final SchemaValidator validator;
    private final ConfigDocumentLoader documentLoader;
    private final DebugRecorder debugRecorder;
    private final RemoteImageCache remoteImageCache;
    private final MapImageResolver mapImageResolver;
    private final WindowDefinitionParser windowDefinitionParser;
    private final GroupDefinitionParser groupDefinitionParser;

    public SchemaLoader(Path configDir, SchemaValidator validator, DebugRecorder debugRecorder) {
        this.configRoot = configDir.resolve("interactivedisplay");
        this.windowsDir = this.configRoot.resolve("windows");
        this.groupsDir = this.configRoot.resolve("groups");
        this.imagesDir = this.configRoot.resolve("images");
        this.validator = validator;
        this.documentLoader = new ConfigDocumentLoader();
        this.debugRecorder = debugRecorder;
        this.remoteImageCache = new RemoteImageCache(configDir, debugRecorder);
        this.mapImageResolver = new MapImageResolver(configDir, this.remoteImageCache);
        this.windowDefinitionParser = new WindowDefinitionParser(this.mapImageResolver, this.debugRecorder);
        this.groupDefinitionParser = new GroupDefinitionParser();
    }

    public LoadResult loadAll() {
        Map<String, WindowDefinition> definitions = new HashMap<>();
        Map<String, WindowGroupDefinition> groups = new HashMap<>();
        List<String> errors = new ArrayList<>();
        Set<String> brokenWindowIds = new TreeSet<>();
        Set<String> brokenGroupIds = new TreeSet<>();

        try {
            Files.createDirectories(this.windowsDir);
            Files.createDirectories(this.groupsDir);
            Files.createDirectories(this.imagesDir);
            this.ensureDefaultAssets();
        } catch (IOException exception) {
            String message = "window config 디렉터리 준비 실패: " + exception.getMessage();
            errors.add(message);
            recordSchemaFailure(null, message, exception);
            return new LoadResult(definitions, groups, errors, brokenWindowIds, brokenGroupIds);
        }

        warnUnsupportedLegacyConfigFiles(this.windowsDir);
        try (var paths = Files.list(this.windowsDir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .sorted()
                    .forEach(path -> loadSingleFile(path, definitions, errors, brokenWindowIds));
        } catch (IOException exception) {
            String message = "window definition 목록 조회 실패: " + exception.getMessage();
            errors.add(message);
            recordSchemaFailure(null, message, exception);
        }

        warnUnsupportedLegacyConfigFiles(this.groupsDir);
        try (var paths = Files.list(this.groupsDir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .sorted()
                    .forEach(path -> loadSingleGroupFile(path, groups, errors, brokenGroupIds));
        } catch (IOException exception) {
            String message = "group definition 목록 조회 실패: " + exception.getMessage();
            errors.add(message);
            recordSchemaFailure(null, message, exception);
        }

        return new LoadResult(definitions, groups, errors, brokenWindowIds, brokenGroupIds);
    }

    public int mapCacheEntryCount() {
        return this.remoteImageCache.cacheEntryCount();
    }

    public Set<String> discoverWindowIds() {
        Set<String> windowIds = new TreeSet<>();
        if (!Files.isDirectory(this.windowsDir)) {
            return windowIds;
        }

        warnUnsupportedLegacyConfigFiles(this.windowsDir);
        try (var paths = Files.list(this.windowsDir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .sorted()
                    .forEach(path -> windowIds.add(readWindowId(path)));
        } catch (IOException exception) {
            recordSchemaFailure(null, "window id 목록 조회 실패: " + exception.getMessage(), exception);
        }
        return windowIds;
    }

    public Set<String> discoverGroupIds() {
        Set<String> groupIds = new TreeSet<>();
        if (!Files.isDirectory(this.groupsDir)) {
            return groupIds;
        }

        warnUnsupportedLegacyConfigFiles(this.groupsDir);
        try (var paths = Files.list(this.groupsDir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .sorted()
                    .forEach(path -> groupIds.add(readGroupId(path)));
        } catch (IOException exception) {
            recordSchemaFailure(null, "group id 목록 조회 실패: " + exception.getMessage(), exception);
        }
        return groupIds;
    }

    private void loadSingleFile(Path path,
                                Map<String, WindowDefinition> definitions,
                                List<String> errors,
                                Set<String> brokenWindowIds) {
        String sourceName = path.getFileName().toString();

        try {
            JsonNode root = this.documentLoader.load(path);
            List<String> validationErrors = this.validator.validate(root, sourceName);
            if (!validationErrors.isEmpty()) {
                errors.addAll(validationErrors);
                brokenWindowIds.add(readId(root, "id", stripYamlExtension(sourceName)));
                recordSchemaValidationFailure(sourceName, validationErrors);
                return;
            }

            WindowDefinition definition = this.windowDefinitionParser.parse(root, sourceName);
            definitions.put(definition.id(), definition);
        } catch (Exception exception) {
            String message = sourceName + ": " + exception.getMessage();
            errors.add(message);
            brokenWindowIds.add(readWindowId(path));
            recordSchemaFailure(sourceName, message, exception);
        }
    }

    private void loadSingleGroupFile(Path path,
                                     Map<String, WindowGroupDefinition> groups,
                                     List<String> errors,
                                     Set<String> brokenGroupIds) {
        String sourceName = path.getFileName().toString();

        try {
            JsonNode root = this.documentLoader.load(path);
            List<String> validationErrors = this.validator.validateGroup(root, sourceName);
            if (!validationErrors.isEmpty()) {
                errors.addAll(validationErrors);
                brokenGroupIds.add(readId(root, "id", stripYamlExtension(sourceName)));
                recordSchemaValidationFailure(sourceName, validationErrors);
                return;
            }

            WindowGroupDefinition definition = this.groupDefinitionParser.parse(root);
            groups.put(definition.id(), definition);
        } catch (Exception exception) {
            String message = sourceName + ": " + exception.getMessage();
            errors.add(message);
            brokenGroupIds.add(readGroupId(path));
            recordSchemaFailure(sourceName, message, exception);
        }
    }

    private String readWindowId(Path path) {
        String fileName = path.getFileName().toString();
        try {
            JsonNode root = this.documentLoader.load(path);
            String id = readId(root, "id", null);
            if (id != null) {
                return id;
            }
        } catch (Exception ignored) {
            return stripYamlExtension(fileName);
        }
        return stripYamlExtension(fileName);
    }

    private String readGroupId(Path path) {
        String fileName = path.getFileName().toString();
        try {
            JsonNode root = this.documentLoader.load(path);
            String id = readId(root, "id", null);
            if (id != null) {
                return id;
            }
        } catch (Exception ignored) {
            return stripYamlExtension(fileName);
        }
        return stripYamlExtension(fileName);
    }

    private void ensureDefaultAssets() throws IOException {
        copyDefaultResource("windows/" + DEFAULT_WINDOW_FILE);
        copyDefaultResource("windows/" + DEFAULT_MAIN_MENU2_FILE);
        copyDefaultResource("windows/" + DEFAULT_GALLERY_FILE);
        copyDefaultResource("windows/" + DEFAULT_REMOTE_EXAMPLE_FILE);
        copyDefaultResource("groups/" + DEFAULT_GROUP_FILE);
        ensureSampleImage();
    }

    private void copyDefaultResource(String relativePath) throws IOException {
        Path target = this.configRoot.resolve(relativePath).normalize();
        if (Files.exists(target)) {
            return;
        }

        Files.createDirectories(target.getParent());
        try (InputStream input = SchemaLoader.class.getResourceAsStream(DEFAULT_RESOURCE_ROOT + relativePath)) {
            if (input == null) {
                throw new IOException("기본 설정 리소스를 찾을 수 없음: " + relativePath);
            }
            Files.copy(input, target);
        }
    }

    private void ensureSampleImage() throws IOException {
        Path imagePath = this.imagesDir.resolve(DEFAULT_SAMPLE_IMAGE);
        if (Files.exists(imagePath)) {
            return;
        }

        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(26, 83, 92));
        graphics.fillRect(0, 0, 128, 128);
        graphics.setColor(new Color(255, 255, 255, 200));
        graphics.fillOval(24, 24, 80, 80);
        graphics.setColor(new Color(11, 34, 50));
        graphics.drawString("ID", 56, 68);
        graphics.dispose();
        ImageIO.write(image, "png", imagePath.toFile());
    }

    private void recordSchemaValidationFailure(String sourceName, List<String> validationErrors) {
        this.debugRecorder.record(
                DebugEventType.SCHEMA_LOAD,
                DebugLevel.WARN,
                null,
                null,
                sourceName,
                null,
                null,
                DebugReason.SCHEMA_VALIDATION_FAILED,
                sourceName + ": validation errors=" + validationErrors.size(),
                null
        );
        InteractiveDisplay.LOGGER.warn(
                "[{}] schema validation failed source={} reasonCode={} errorCount={}",
                InteractiveDisplay.MOD_ID,
                sourceName,
                DebugReason.SCHEMA_VALIDATION_FAILED,
                validationErrors.size()
        );
    }

    private void recordSchemaFailure(String sourceName, String message, Exception exception) {
        this.debugRecorder.record(
                DebugEventType.SCHEMA_LOAD,
                DebugLevel.ERROR,
                null,
                null,
                sourceName,
                null,
                null,
                DebugReason.SCHEMA_VALIDATION_FAILED,
                message,
                exception
        );
        InteractiveDisplay.LOGGER.error(
                "[{}] schema load failed source={} reasonCode={} message={}",
                InteractiveDisplay.MOD_ID,
                sourceName,
                DebugReason.SCHEMA_VALIDATION_FAILED,
                message,
                exception
        );
    }

    private static String readId(JsonNode root, String key, String fallback) {
        if (root != null && root.isObject()) {
            JsonNode value = root.get(key);
            if (value != null && value.isTextual()) {
                return value.textValue();
            }
        }
        return fallback;
    }

    private static String stripYamlExtension(String fileName) {
        return fileName.endsWith(".yaml") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    private void warnUnsupportedLegacyConfigFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> InteractiveDisplay.LOGGER.warn(
                            "[{}] Unsupported legacy config file: {}. InteractiveDisplay supports .yaml configuration only.",
                            InteractiveDisplay.MOD_ID,
                            this.configRoot.relativize(path)
                    ));
        } catch (IOException exception) {
            InteractiveDisplay.LOGGER.warn(
                    "[{}] Unsupported config scan failed for {}: {}",
                    InteractiveDisplay.MOD_ID,
                    directory,
                    exception.getMessage()
            );
        }
    }

    public record LoadResult(Map<String, WindowDefinition> definitions,
                             Map<String, WindowGroupDefinition> groups,
                             List<String> errors,
                             Set<String> brokenWindowIds,
                             Set<String> brokenGroupIds) {
        public boolean hasErrors() {
            return !this.errors.isEmpty();
        }
    }
}

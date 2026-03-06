package com.interactivedisplay.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentActionType;
import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.ImageComponentDefinition;
import com.interactivedisplay.core.component.ImageSource;
import com.interactivedisplay.core.component.ImageType;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.positioning.WindowOffset;
import com.interactivedisplay.core.window.WindowDefinition;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private static final String DEFAULT_WINDOW_FILE = "main_menu.json";
    private static final String DEFAULT_GALLERY_FILE = "gallery.json";
    private static final String DEFAULT_REMOTE_EXAMPLE_FILE = "gallery_remote.example.json.disabled";
    private static final String DEFAULT_SAMPLE_IMAGE = "sample_local.png";

    private final Path configRoot;
    private final Path windowsDir;
    private final Path imagesDir;
    private final SchemaValidator validator;
    private final DebugRecorder debugRecorder;
    private final RemoteImageCache remoteImageCache;
    private final MapImageResolver mapImageResolver;

    public SchemaLoader(Path configDir, SchemaValidator validator, DebugRecorder debugRecorder) {
        this.configRoot = configDir.resolve("interactivedisplay");
        this.windowsDir = this.configRoot.resolve("windows");
        this.imagesDir = this.configRoot.resolve("images");
        this.validator = validator;
        this.debugRecorder = debugRecorder;
        this.remoteImageCache = new RemoteImageCache(configDir, debugRecorder);
        this.mapImageResolver = new MapImageResolver(configDir, this.remoteImageCache);
    }

    public LoadResult loadAll() {
        Map<String, WindowDefinition> definitions = new HashMap<>();
        List<String> errors = new ArrayList<>();
        Set<String> brokenWindowIds = new TreeSet<>();

        try {
            Files.createDirectories(this.windowsDir);
            Files.createDirectories(this.imagesDir);
            this.ensureDefaultAssets();
        } catch (IOException exception) {
            String message = "window config 디렉터리 준비 실패: " + exception.getMessage();
            errors.add(message);
            recordSchemaFailure(null, message, exception);
            return new LoadResult(definitions, errors, brokenWindowIds);
        }

        try (var paths = Files.list(this.windowsDir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> loadSingleFile(path, definitions, errors, brokenWindowIds));
        } catch (IOException exception) {
            String message = "window definition 목록 조회 실패: " + exception.getMessage();
            errors.add(message);
            recordSchemaFailure(null, message, exception);
        }

        return new LoadResult(definitions, errors, brokenWindowIds);
    }

    public int mapCacheEntryCount() {
        return this.remoteImageCache.cacheEntryCount();
    }

    public Set<String> discoverWindowIds() {
        Set<String> windowIds = new TreeSet<>();
        if (!Files.isDirectory(this.windowsDir)) {
            return windowIds;
        }

        try (var paths = Files.list(this.windowsDir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> windowIds.add(readWindowId(path)));
        } catch (IOException exception) {
            recordSchemaFailure(null, "window id 목록 조회 실패: " + exception.getMessage(), exception);
        }
        return windowIds;
    }

    private void loadSingleFile(Path path,
                                Map<String, WindowDefinition> definitions,
                                List<String> errors,
                                Set<String> brokenWindowIds) {
        String sourceName = path.getFileName().toString();

        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> validationErrors = this.validator.validate(root, sourceName);
            if (!validationErrors.isEmpty()) {
                errors.addAll(validationErrors);
                brokenWindowIds.add(root.has("id") ? root.get("id").getAsString() : stripJsonExtension(sourceName));
                recordSchemaValidationFailure(sourceName, validationErrors);
                return;
            }

            WindowDefinition definition = parseDefinition(root, sourceName);
            definitions.put(definition.id(), definition);
        } catch (Exception exception) {
            String message = sourceName + ": " + exception.getMessage();
            errors.add(message);
            brokenWindowIds.add(readWindowId(path));
            recordSchemaFailure(sourceName, message, exception);
        }
    }

    private String readWindowId(Path path) {
        String fileName = path.getFileName().toString();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("id") && root.get("id").isJsonPrimitive() && root.get("id").getAsJsonPrimitive().isString()) {
                return root.get("id").getAsString();
            }
        } catch (Exception ignored) {
            return stripJsonExtension(fileName);
        }
        return stripJsonExtension(fileName);
    }

    private WindowDefinition parseDefinition(JsonObject root, String sourceName) throws IOException, InterruptedException {
        String id = root.get("id").getAsString();
        ComponentSize size = parseSize(root, 1.0f, 1.0f);
        WindowOffset offset = parseOffset(root);
        LayoutMode layoutMode = LayoutMode.fromString(getString(root, "layout", null));
        List<ComponentDefinition> components = parseComponents(root.getAsJsonArray("components"), sourceName + ".components");
        return new WindowDefinition(id, size, offset, layoutMode, components);
    }

    private List<ComponentDefinition> parseComponents(JsonArray array, String sourceName) throws IOException, InterruptedException {
        List<ComponentDefinition> components = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject component = array.get(i).getAsJsonObject();
            components.add(parseComponent(component, sourceName + "[" + i + "]"));
        }
        return components;
    }

    private ComponentDefinition parseComponent(JsonObject component, String sourceName) throws IOException, InterruptedException {
        String type = component.get("type").getAsString();
        String id = component.get("id").getAsString();
        ComponentPosition position = parsePosition(component.getAsJsonObject("position"));
        boolean visible = getBoolean(component, "visible", true);
        float opacity = getFloat(component, "opacity", 1.0f);

        if ("text".equals(type)) {
            return new TextComponentDefinition(
                    id,
                    position,
                    parseSize(component, 1.0f, 0.25f),
                    visible,
                    opacity,
                    component.get("content").getAsString(),
                    getFloat(component, "fontSize", 1.0f),
                    getString(component, "color", "#FFFFFF"),
                    getString(component, "alignment", "left"),
                    getInt(component, "lineWidth", 200),
                    getBoolean(component, "shadow", true),
                    getString(component, "background", "#00000000")
            );
        }

        if ("button".equals(type)) {
            ClickType parsedClickType = parseClickType(getString(component, "clickType", "RIGHT"));
            if (parsedClickType != ClickType.RIGHT) {
                this.debugRecorder.record(
                        DebugEventType.SCHEMA_LOAD,
                        DebugLevel.WARN,
                        null,
                        null,
                        null,
                        id,
                        null,
                        null,
                        sourceName + ": legacy clickType=" + parsedClickType + " ignored, RIGHT로 정규화",
                        null
                );
                InteractiveDisplay.LOGGER.warn(
                        "[{}] schema warn componentId={} source={} legacy clickType={} -> RIGHT",
                        InteractiveDisplay.MOD_ID,
                        id,
                        sourceName,
                        parsedClickType
                );
            }

            return new ButtonComponentDefinition(
                    id,
                    position,
                    parseSize(component, 1.0f, 0.35f),
                    visible,
                    opacity,
                    component.get("label").getAsString(),
                    getString(component, "hoverColor", "#44FFFFFF"),
                    ClickType.RIGHT,
                    parseAction(component.getAsJsonObject("action"))
            );
        }

        if ("image".equals(type)) {
            ImageType imageType = ImageType.fromString(component.get("imageType").getAsString());
            String value = component.get("value").getAsString();
            ImageSource source = imageType == ImageType.MAP ? this.mapImageResolver.resolve(value) : null;
            return new ImageComponentDefinition(
                    id,
                    position,
                    parseSize(component, 1.0f, 1.0f),
                    visible,
                    opacity,
                    imageType,
                    value,
                    getFloat(component, "scale", 1.0f),
                    source
            );
        }

        if ("panel".equals(type)) {
            return new PanelComponentDefinition(
                    id,
                    position,
                    parseSize(component, 1.0f, 1.0f),
                    visible,
                    opacity,
                    getString(component, "backgroundColor", "#00000000"),
                    getFloat(component, "padding", 0.0f),
                    LayoutMode.fromString(getString(component, "layout", "absolute")),
                    parseComponents(component.getAsJsonArray("children"), sourceName + ".children")
            );
        }

        throw new SchemaValidationException("지원하지 않는 component type: " + type);
    }

    private static ComponentAction parseAction(JsonObject action) {
        String actionType = action.get("type").getAsString();
        return switch (actionType) {
            case "close_window" -> ComponentAction.closeWindow();
            case "open_window" -> ComponentAction.openWindow(action.get("target").getAsString());
            case "run_command" -> ComponentAction.runCommand(action.get("command").getAsString());
            case "callback" -> ComponentAction.callback(action.get("id").getAsString());
            default -> throw new SchemaValidationException("지원하지 않는 action type: " + actionType);
        };
    }

    private WindowOffset parseOffset(JsonObject root) {
        JsonObject offsetObject = root.getAsJsonObject("offset");
        if (offsetObject == null) {
            return WindowOffset.defaults();
        }
        return new WindowOffset(
                getFloat(offsetObject, "forward", 2.0f),
                getFloat(offsetObject, "horizontal", 0.0f),
                getFloat(offsetObject, "vertical", 0.5f)
        );
    }

    private static ComponentSize parseSize(JsonObject object, float defaultWidth, float defaultHeight) {
        JsonObject sizeObject = object.getAsJsonObject("size");
        if (sizeObject != null) {
            return new ComponentSize(sizeObject.get("width").getAsFloat(), sizeObject.get("height").getAsFloat());
        }
        return new ComponentSize(
                getFloat(object, "width", defaultWidth),
                getFloat(object, "height", defaultHeight)
        );
    }

    private static ComponentPosition parsePosition(JsonObject object) {
        return new ComponentPosition(
                object.get("x").getAsFloat(),
                object.get("y").getAsFloat(),
                object.get("z").getAsFloat()
        );
    }

    private void ensureDefaultAssets() throws IOException {
        ensureDefaultWindowFile();
        ensureGalleryWindowFile();
        ensureRemoteExampleFile();
        ensureSampleImage();
    }

    private void ensureDefaultWindowFile() throws IOException {
        Path path = this.windowsDir.resolve(DEFAULT_WINDOW_FILE);
        if (Files.exists(path)) {
            return;
        }
        String json = """
                {
                  "id": "main_menu",
                  "size": { "width": 3.0, "height": 2.0 },
                  "offset": { "forward": 2.0, "horizontal": 0.0, "vertical": 0.5 },
                  "components": [
                    {
                      "id": "title",
                      "type": "text",
                      "position": { "x": 0.0, "y": 0.0, "z": 0.0 },
                      "width": 3.0,
                      "height": 0.4,
                      "content": "InteractiveDisplay",
                      "fontSize": 1.2,
                      "alignment": "center",
                      "background": "#22000000"
                    },
                    {
                      "id": "close",
                      "type": "button",
                      "position": { "x": 0.1, "y": 0.55, "z": 0.0 },
                      "size": { "width": 1.4, "height": 0.35 },
                      "label": "닫기",
                      "hoverColor": "#55FFFFFF",
                      "action": { "type": "close_window" }
                    }
                  ]
                }
                """;
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private void ensureGalleryWindowFile() throws IOException {
        Path path = this.windowsDir.resolve(DEFAULT_GALLERY_FILE);
        if (Files.exists(path)) {
            return;
        }
        String json = """
                {
                  "id": "gallery",
                  "size": { "width": 4.0, "height": 2.5 },
                  "layout": "horizontal",
                  "components": [
                    {
                      "id": "local_map",
                      "type": "image",
                      "position": { "x": 0.0, "y": 0.0, "z": 0.0 },
                      "size": { "width": 1.0, "height": 1.0 },
                      "imageType": "MAP",
                      "value": "sample_local.png",
                      "scale": 1.0
                    }
                  ]
                }
                """;
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private void ensureRemoteExampleFile() throws IOException {
        Path path = this.windowsDir.resolve(DEFAULT_REMOTE_EXAMPLE_FILE);
        if (Files.exists(path)) {
            return;
        }
        String json = """
                {
                  "id": "gallery_remote",
                  "size": { "width": 2.0, "height": 1.5 },
                  "components": [
                    {
                      "id": "remote_map",
                      "type": "image",
                      "position": { "x": 0.0, "y": 0.0, "z": 0.0 },
                      "size": { "width": 1.0, "height": 1.0 },
                      "imageType": "MAP",
                      "value": "https://textures.minecraft.net/texture/4f7d0baf5f5931c37d399a8ce28ed5ab828a53ea50ccaa43d562e2eb06b1e0cc",
                      "scale": 1.0
                    }
                  ]
                }
                """;
        Files.writeString(path, json, StandardCharsets.UTF_8);
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

    private static ClickType parseClickType(String value) {
        return switch (value.toUpperCase()) {
            case "LEFT" -> ClickType.LEFT;
            case "BOTH" -> ClickType.BOTH;
            default -> ClickType.RIGHT;
        };
    }

    private static String stripJsonExtension(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    private static boolean getBoolean(JsonObject object, String key, boolean defaultValue) {
        return object.has(key) ? object.get(key).getAsBoolean() : defaultValue;
    }

    private static int getInt(JsonObject object, String key, int defaultValue) {
        return object.has(key) ? object.get(key).getAsInt() : defaultValue;
    }

    private static float getFloat(JsonObject object, String key, float defaultValue) {
        return object.has(key) ? object.get(key).getAsFloat() : defaultValue;
    }

    private static String getString(JsonObject object, String key, String defaultValue) {
        return object.has(key) ? object.get(key).getAsString() : defaultValue;
    }

    public record LoadResult(Map<String, WindowDefinition> definitions, List<String> errors, Set<String> brokenWindowIds) {
        public boolean hasErrors() {
            return !this.errors.isEmpty();
        }
    }
}

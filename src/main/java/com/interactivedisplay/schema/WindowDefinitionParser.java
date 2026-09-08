package com.interactivedisplay.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
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
import com.interactivedisplay.debug.DebugRecorder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class WindowDefinitionParser {
    private final MapImageResolver mapImageResolver;
    private final DebugRecorder debugRecorder;

    public WindowDefinitionParser(MapImageResolver mapImageResolver, DebugRecorder debugRecorder) {
        this.mapImageResolver = mapImageResolver;
        this.debugRecorder = debugRecorder;
    }

    public WindowDefinition parse(JsonNode root, String sourceName) throws IOException, InterruptedException {
        String id = root.get("id").textValue();
        ComponentSize size = parseSize(root, 1.0f, 1.0f);
        WindowOffset offset = parseOffset(root, WindowOffset.defaults());
        LayoutMode layoutMode = LayoutMode.fromString(getString(root, "layout", null));
        List<ComponentDefinition> components = parseComponents(root.get("components"), sourceName + ".components");
        return new WindowDefinition(id, size, offset, layoutMode, components);
    }

    private List<ComponentDefinition> parseComponents(JsonNode array, String sourceName) throws IOException, InterruptedException {
        List<ComponentDefinition> components = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonNode component = array.get(i);
            components.add(parseComponent(component, sourceName + "[" + i + "]"));
        }
        return components;
    }

    private ComponentDefinition parseComponent(JsonNode component, String sourceName) throws IOException, InterruptedException {
        String type = component.get("type").textValue();
        String id = component.get("id").textValue();
        ComponentPosition position = parsePosition(component.get("position"));
        boolean visible = getBoolean(component, "visible", true);
        float opacity = getFloat(component, "opacity", 1.0f);

        if ("text".equals(type)) {
            return new TextComponentDefinition(
                    id,
                    position,
                    parseSize(component, 1.0f, 0.25f),
                    visible,
                    opacity,
                    component.get("content").textValue(),
                    getFloat(component, "fontSize", 0.5f),
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
                    component.get("label").textValue(),
                    getFloat(component, "fontSize", 1.0f),
                    getString(component, "backgroundColor", "#00000000"),
                    getString(component, "hoverColor", "#44FFFFFF"),
                    getString(component, "clickSound", null),
                    ClickType.RIGHT,
                    parseAction(component.get("action"))
            );
        }

        if ("image".equals(type)) {
            ImageType imageType = ImageType.fromString(component.get("imageType").textValue());
            String value = component.get("value").textValue();
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
                    parseComponents(component.get("children"), sourceName + ".children")
            );
        }

        throw new SchemaValidationException("지원하지 않는 component type: " + type);
    }

    private static ComponentAction parseAction(JsonNode action) {
        String actionType = action.get("type").textValue();
        return switch (actionType) {
            case "close_window" -> ComponentAction.closeWindow();
            case "open_window" -> ComponentAction.openWindow(action.get("target").textValue());
            case "switch_mode_fixed" -> ComponentAction.switchModeFixed();
            case "switch_mode_player_fixed" -> ComponentAction.switchModePlayerFixed();
            case "toggle_placement_tracking" -> ComponentAction.togglePlacementTracking();
            case "run_command" -> ComponentAction.runCommand(
                    action.get("command").textValue(),
                    action.has("permissionLevel") ? action.get("permissionLevel").intValue() : null
            );
            case "callback" -> ComponentAction.callback(action.get("id").textValue());
            default -> throw new SchemaValidationException("지원하지 않는 action type: " + actionType);
        };
    }

    private static WindowOffset parseOffset(JsonNode root, WindowOffset fallback) {
        JsonNode offsetObject = root.get("offset");
        if (offsetObject == null || !offsetObject.isObject()) {
            return fallback;
        }
        return new WindowOffset(
                getFloat(offsetObject, "forward", fallback.forward()),
                getFloat(offsetObject, "horizontal", fallback.horizontal()),
                getFloat(offsetObject, "vertical", fallback.vertical())
        );
    }

    private static ComponentSize parseSize(JsonNode object, float defaultWidth, float defaultHeight) {
        JsonNode sizeObject = object.get("size");
        if (sizeObject != null && sizeObject.isObject()) {
            return new ComponentSize(sizeObject.get("width").floatValue(), sizeObject.get("height").floatValue());
        }
        return new ComponentSize(
                getFloat(object, "width", defaultWidth),
                getFloat(object, "height", defaultHeight)
        );
    }

    private static ComponentPosition parsePosition(JsonNode object) {
        return new ComponentPosition(
                object.get("x").floatValue(),
                object.get("y").floatValue(),
                object.get("z").floatValue()
        );
    }

    private static ClickType parseClickType(String value) {
        return switch (value.toUpperCase()) {
            case "LEFT" -> ClickType.LEFT;
            case "BOTH" -> ClickType.BOTH;
            default -> ClickType.RIGHT;
        };
    }

    private static boolean getBoolean(JsonNode object, String key, boolean defaultValue) {
        JsonNode value = object.get(key);
        return value == null ? defaultValue : value.booleanValue();
    }

    private static int getInt(JsonNode object, String key, int defaultValue) {
        JsonNode value = object.get(key);
        return value == null ? defaultValue : value.intValue();
    }

    private static float getFloat(JsonNode object, String key, float defaultValue) {
        JsonNode value = object.get(key);
        return value == null ? defaultValue : value.floatValue();
    }

    private static String getString(JsonNode object, String key, String defaultValue) {
        JsonNode value = object.get(key);
        return value != null && value.isTextual() ? value.textValue() : defaultValue;
    }
}

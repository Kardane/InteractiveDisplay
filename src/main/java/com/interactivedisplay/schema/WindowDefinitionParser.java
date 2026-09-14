package com.interactivedisplay.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.core.animation.AnimationDefinition;
import com.interactivedisplay.core.animation.AnimationInterpolation;
import com.interactivedisplay.core.animation.AnimationRegistry;
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
import com.interactivedisplay.core.window.WindowTransition;
import com.interactivedisplay.core.window.WindowTransitionType;
import com.interactivedisplay.debug.DebugRecorder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class WindowDefinitionParser {
    private static final Set<String> ANIMATION_INTERPOLATIONS = Set.of("linear", "smooth", "cut");

    private final MapImageResolver mapImageResolver;
    @SuppressWarnings("unused")
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
        WindowTransition transition = parseTransition(root.get("transition"));
        return new WindowDefinition(id, size, offset, layoutMode, components, transition);
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
                    getString(component, "background", "#00000000"),
                    getInt(component, "refreshInterval", 0),
                    parseAnimations(component.get("animations"), sourceName)
            );
        }

        if ("button".equals(type)) {
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
                    parseClickType(getString(component, "clickType", "RIGHT")),
                    parseAction(component.get("action")),
                    getFloat(component, "hoverScale", 1.0f)
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

    private static List<AnimationDefinition> parseAnimations(JsonNode animations, String sourceName) {
        if (animations == null) {
            return List.of();
        }
        String animationSource = sourceName + ".animations";
        if (!animations.isArray()) {
            throw new SchemaValidationException(animationSource + " must be array");
        }

        List<AnimationDefinition> definitions = new ArrayList<>();
        Set<String> seenTypes = new HashSet<>();
        for (int index = 0; index < animations.size(); index++) {
            JsonNode animation = animations.get(index);
            String entrySource = animationSource + "[" + index + "]";
            if (!animation.isObject()) {
                throw new SchemaValidationException(entrySource + " must be object");
            }
            JsonNode typeNode = animation.get("type");
            if (typeNode == null || !typeNode.isTextual() || typeNode.textValue().isBlank()) {
                throw new SchemaValidationException(entrySource + ".type must be a non-blank string");
            }
            String type = typeNode.textValue();
            String normalizedType;
            try {
                normalizedType = AnimationRegistry.normalize(type);
            } catch (IllegalArgumentException exception) {
                throw new SchemaValidationException(entrySource + ".type: " + exception.getMessage());
            }
            if (!AnimationRegistry.isRegistered(normalizedType)) {
                throw new SchemaValidationException(entrySource + ": unsupported animation type " + type);
            }
            if (!seenTypes.add(normalizedType)) {
                throw new SchemaValidationException(entrySource + ": duplicate animation type " + type);
            }
            if (animation.has("delay") && animation.has("start")) {
                throw new SchemaValidationException(entrySource + ": use either delay or start, not both");
            }

            int delay = animation.has("delay")
                    ? readNonNegativeInt(animation, "delay", 0, entrySource)
                    : readNonNegativeInt(animation, "start", 0, entrySource);
            int defaultDuration = normalizedType.endsWith(":fade") ? 5 : 0;
            int duration = readNonNegativeInt(animation, "duration", defaultDuration, entrySource);
            int interval = readPositiveInt(animation, "interval", 1, entrySource);
            int charsPerStep = readPositiveInt(animation, "charsPerStep", 1, entrySource);
            if (normalizedType.endsWith(":fade") && duration == 0) {
                throw new SchemaValidationException(entrySource + ".duration must be > 0 for fade");
            }

            String interpolationValue = readInterpolation(animation, entrySource);
            definitions.add(new AnimationDefinition(
                    type,
                    delay,
                    duration,
                    interval,
                    charsPerStep,
                    AnimationInterpolation.fromString(interpolationValue)
            ));
        }
        return List.copyOf(definitions);
    }

    private static String readInterpolation(JsonNode animation, String sourceName) {
        JsonNode value = animation.get("interpolation");
        if (value == null) {
            return "linear";
        }
        if (!value.isTextual()) {
            throw new SchemaValidationException(sourceName + ".interpolation must be string");
        }
        String normalized = value.textValue().toLowerCase(Locale.ROOT);
        if (!ANIMATION_INTERPOLATIONS.contains(normalized)) {
            throw new SchemaValidationException(sourceName + ".interpolation must be linear, smooth, or cut");
        }
        return normalized;
    }

    private static int readNonNegativeInt(JsonNode object, String key, int defaultValue, String sourceName) {
        JsonNode value = object.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isIntegralNumber() || value.longValue() < 0L || value.longValue() > Integer.MAX_VALUE) {
            throw new SchemaValidationException(sourceName + "." + key + " must be a non-negative integer");
        }
        return value.intValue();
    }

    private static int readPositiveInt(JsonNode object, String key, int defaultValue, String sourceName) {
        int value = readNonNegativeInt(object, key, defaultValue, sourceName);
        if (value < 1) {
            throw new SchemaValidationException(sourceName + "." + key + " must be >= 1");
        }
        return value;
    }

    private static WindowTransition parseTransition(JsonNode transition) {
        if (transition == null || !transition.isObject()) {
            return WindowTransition.none();
        }
        return new WindowTransition(
                getInt(transition, "duration", WindowTransition.DEFAULT_DURATION),
                WindowTransitionType.fromString(getString(transition, "enter", "none")),
                WindowTransitionType.fromString(getString(transition, "exit", "none"))
        );
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

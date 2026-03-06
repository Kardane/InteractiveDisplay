package com.interactivedisplay.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SchemaValidator {
    private static final Set<String> COMPONENT_TYPES = Set.of("text", "button", "image", "panel");
    private static final Set<String> ACTION_TYPES = Set.of("close_window", "open_window", "run_command", "callback");
    private static final Set<String> IMAGE_TYPES = Set.of("item", "block", "map");
    private static final Set<String> LAYOUT_TYPES = Set.of("absolute", "vertical", "horizontal");
    private static final Set<String> CLICK_TYPES = Set.of("left", "right", "both");

    public List<String> validate(JsonObject root, String sourceName) {
        List<String> errors = new ArrayList<>();

        requireString(root, "id", sourceName, errors);
        requireObject(root, "size", sourceName, errors);
        requireArray(root, "components", sourceName, errors);
        requireLayout(root, sourceName, errors);
        validateOffset(root, sourceName, errors);

        JsonObject size = getObject(root, "size");
        if (size != null) {
            requirePositiveNumber(size, "width", sourceName + ".size", errors);
            requirePositiveNumber(size, "height", sourceName + ".size", errors);
        }

        JsonArray components = getArray(root, "components");
        if (components != null) {
            validateComponents(components, sourceName + ".components", errors);
        }

        return errors;
    }

    private void validateComponents(JsonArray components, String sourceName, List<String> errors) {
        for (int i = 0; i < components.size(); i++) {
            JsonElement element = components.get(i);
            if (!element.isJsonObject()) {
                errors.add(sourceName + "[" + i + "]: component must be object");
                continue;
            }

            JsonObject component = element.getAsJsonObject();
            String componentName = sourceName + "[" + i + "]";
            requireString(component, "id", componentName, errors);
            String type = requireString(component, "type", componentName, errors);
            if (type != null && !COMPONENT_TYPES.contains(type)) {
                errors.add(componentName + ": unsupported type " + type);
                continue;
            }

            requireObject(component, "position", componentName, errors);
            validatePosition(component, componentName, errors);
            validateOpacity(component, componentName, errors);
            requireLayout(component, componentName, errors);

            if ("text".equals(type)) {
                requireString(component, "content", componentName, errors);
                validateSize(component, componentName, errors, false);
                continue;
            }

            if ("button".equals(type)) {
                requireString(component, "label", componentName, errors);
                validateSize(component, componentName, errors, true);
                String clickType = optionalString(component, "clickType");
                if (clickType != null && !CLICK_TYPES.contains(clickType.toLowerCase())) {
                    errors.add(componentName + ": clickType must be LEFT, RIGHT, BOTH");
                }
                validateAction(component, componentName, errors);
                continue;
            }

            if ("image".equals(type)) {
                validateSize(component, componentName, errors, true);
                String imageType = requireString(component, "imageType", componentName, errors);
                if (imageType != null && !IMAGE_TYPES.contains(imageType.toLowerCase())) {
                    errors.add(componentName + ": imageType must be ITEM, BLOCK, MAP");
                }
                requireString(component, "value", componentName, errors);
                validatePositiveOptional(component, "scale", componentName, errors);
                continue;
            }

            if ("panel".equals(type)) {
                validateSize(component, componentName, errors, true);
                requireArray(component, "children", componentName, errors);
                validateNonNegativeOptional(component, "padding", componentName, errors);
                requireLayout(component, componentName, errors);
                JsonArray children = getArray(component, "children");
                if (children != null) {
                    validateComponents(children, componentName + ".children", errors);
                }
            }
        }
    }

    private static void validateAction(JsonObject component, String sourceName, List<String> errors) {
        JsonObject action = getObject(component, "action");
        if (action == null) {
            errors.add(sourceName + ": action is required");
            return;
        }

        String type = requireString(action, "type", sourceName + ".action", errors);
        if (type == null) {
            return;
        }
        if (!ACTION_TYPES.contains(type)) {
            errors.add(sourceName + ".action: unsupported type " + type);
            return;
        }

        if ("open_window".equals(type)) {
            requireString(action, "target", sourceName + ".action", errors);
        } else if ("run_command".equals(type)) {
            requireString(action, "command", sourceName + ".action", errors);
        } else if ("callback".equals(type)) {
            requireString(action, "id", sourceName + ".action", errors);
        }
    }

    private static void validatePosition(JsonObject component, String sourceName, List<String> errors) {
        JsonObject position = getObject(component, "position");
        if (position == null) {
            return;
        }
        requireNumber(position, "x", sourceName + ".position", errors);
        requireNumber(position, "y", sourceName + ".position", errors);
        requireNumber(position, "z", sourceName + ".position", errors);
    }

    private static void validateOffset(JsonObject root, String sourceName, List<String> errors) {
        JsonObject offset = getObject(root, "offset");
        if (offset == null) {
            return;
        }
        requireNumber(offset, "forward", sourceName + ".offset", errors);
        requireNumber(offset, "horizontal", sourceName + ".offset", errors);
        requireNumber(offset, "vertical", sourceName + ".offset", errors);
    }

    private static void validateOpacity(JsonObject component, String sourceName, List<String> errors) {
        JsonElement opacity = component.get("opacity");
        if (opacity == null) {
            return;
        }
        if (!opacity.isJsonPrimitive() || !opacity.getAsJsonPrimitive().isNumber()) {
            errors.add(sourceName + ": opacity must be number");
            return;
        }
        float value = opacity.getAsFloat();
        if (value < 0.0f || value > 1.0f) {
            errors.add(sourceName + ": opacity must be between 0 and 1");
        }
    }

    private static void validateSize(JsonObject component, String sourceName, List<String> errors, boolean requireSizeObject) {
        JsonObject size = getObject(component, "size");
        if (size != null) {
            requirePositiveNumber(size, "width", sourceName + ".size", errors);
            requirePositiveNumber(size, "height", sourceName + ".size", errors);
            return;
        }

        if (!requireSizeObject && component.has("width") && component.has("height")) {
            requirePositiveNumber(component, "width", sourceName, errors);
            requirePositiveNumber(component, "height", sourceName, errors);
            return;
        }

        errors.add(sourceName + ": size is required");
    }

    private static void requireLayout(JsonObject object, String sourceName, List<String> errors) {
        String layout = optionalString(object, "layout");
        if (layout == null) {
            return;
        }
        if (!LAYOUT_TYPES.contains(layout.toLowerCase())) {
            errors.add(sourceName + ": layout must be absolute, vertical, horizontal");
        }
    }

    private static void validatePositiveOptional(JsonObject object, String key, String sourceName, List<String> errors) {
        JsonElement element = object.get(key);
        if (element == null) {
            return;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            errors.add(sourceName + ": " + key + " must be number");
            return;
        }
        if (element.getAsFloat() <= 0.0f) {
            errors.add(sourceName + ": " + key + " must be > 0");
        }
    }

    private static void validateNonNegativeOptional(JsonObject object, String key, String sourceName, List<String> errors) {
        JsonElement element = object.get(key);
        if (element == null) {
            return;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            errors.add(sourceName + ": " + key + " must be number");
            return;
        }
        if (element.getAsFloat() < 0.0f) {
            errors.add(sourceName + ": " + key + " must be >= 0");
        }
    }

    private static String requireString(JsonObject object, String key, String sourceName, List<String> errors) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            errors.add(sourceName + ": " + key + " must be string");
            return null;
        }
        return element.getAsString();
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private static void requireObject(JsonObject object, String key, String sourceName, List<String> errors) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            errors.add(sourceName + ": " + key + " must be object");
        }
    }

    private static void requireArray(JsonObject object, String key, String sourceName, List<String> errors) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            errors.add(sourceName + ": " + key + " must be array");
        }
    }

    private static void requireNumber(JsonObject object, String key, String sourceName, List<String> errors) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            errors.add(sourceName + ": " + key + " must be number");
        }
    }

    private static void requirePositiveNumber(JsonObject object, String key, String sourceName, List<String> errors) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            errors.add(sourceName + ": " + key + " must be number");
            return;
        }
        if (element.getAsFloat() <= 0.0f) {
            errors.add(sourceName + ": " + key + " must be > 0");
        }
    }

    private static JsonObject getObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray getArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }
}

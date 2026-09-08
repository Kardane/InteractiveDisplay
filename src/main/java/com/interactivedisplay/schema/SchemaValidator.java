package com.interactivedisplay.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SchemaValidator {
    private static final Set<String> COMPONENT_TYPES = Set.of("text", "button", "image", "panel");
    private static final Set<String> ACTION_TYPES = Set.of("close_window", "open_window", "switch_mode_fixed", "switch_mode_player_fixed", "toggle_placement_tracking", "run_command", "callback");
    private static final Set<String> IMAGE_TYPES = Set.of("item", "block", "map");
    private static final Set<String> LAYOUT_TYPES = Set.of("absolute", "vertical", "horizontal");
    private static final Set<String> CLICK_TYPES = Set.of("left", "right", "both");
    private static final Set<String> ALIGNMENTS = Set.of("left", "center", "right");
    private static final Set<String> POSITION_MODES = Set.of("fixed", "player_fixed", "player_view");
    private static final Set<String> TRANSITION_TYPES = Set.of("none", "scale", "slide_up", "slide_down");
    private static final List<String> COLOR_FIELDS = List.of("color", "backgroundColor", "hoverColor", "background");

    public List<String> validate(JsonNode root, String sourceName) {
        List<String> errors = new ArrayList<>();
        if (!isObject(root)) {
            errors.add(sourceName + ": root must be object");
            return errors;
        }

        requireString(root, "id", sourceName, errors);
        requireObject(root, "size", sourceName, errors);
        requireArray(root, "components", sourceName, errors);
        requireLayout(root, sourceName, errors);
        validateOffset(root, sourceName, errors);
        validateTransition(root, sourceName, errors);

        JsonNode size = getObject(root, "size");
        if (size != null) {
            requirePositiveNumber(size, "width", sourceName + ".size", errors);
            requirePositiveNumber(size, "height", sourceName + ".size", errors);
        }

        JsonNode components = getArray(root, "components");
        if (components != null) {
            validateComponents(components, sourceName + ".components", errors, new HashSet<>());
        }

        return errors;
    }

    public List<String> validateGroup(JsonNode root, String sourceName) {
        List<String> errors = new ArrayList<>();
        if (!isObject(root)) {
            errors.add(sourceName + ": root must be object");
            return errors;
        }

        requireString(root, "id", sourceName, errors);
        requireString(root, "initialWindowId", sourceName, errors);
        String defaultMode = requireString(root, "defaultMode", sourceName, errors);
        if (defaultMode != null && !POSITION_MODES.contains(defaultMode.toLowerCase())) {
            errors.add(sourceName + ": defaultMode must be " + String.join(", ", POSITION_MODES));
        }
        requireArray(root, "windows", sourceName, errors);

        JsonNode windows = getArray(root, "windows");
        if (windows != null) {
            validateGroupWindows(windows, sourceName + ".windows", errors);
        }
        return errors;
    }

    private void validateComponents(JsonNode components,
                                    String sourceName,
                                    List<String> errors,
                                    Set<String> componentIds) {
        for (int i = 0; i < components.size(); i++) {
            JsonNode element = components.get(i);
            if (!isObject(element)) {
                errors.add(sourceName + "[" + i + "]: component must be object");
                continue;
            }

            JsonNode component = element;
            String componentName = sourceName + "[" + i + "]";
            String id = requireString(component, "id", componentName, errors);
            if (id != null && !componentIds.add(id)) {
                errors.add(componentName + ": duplicate component id " + id);
            }
            String type = requireString(component, "type", componentName, errors);
            if (type != null && !COMPONENT_TYPES.contains(type)) {
                errors.add(componentName + ": unsupported type " + type);
                continue;
            }

            requireObject(component, "position", componentName, errors);
            validatePosition(component, componentName, errors);
            validateOptionalBoolean(component, "visible", componentName, errors);
            validateOpacity(component, componentName, errors);
            requireLayout(component, componentName, errors);
            validateColorFields(component, componentName, errors);

            if ("text".equals(type)) {
                requireString(component, "content", componentName, errors);
                validateOptionalTextSize(component, componentName, errors);
                validatePositiveOptional(component, "fontSize", componentName, errors);
                validateAlignment(component, componentName, errors);
                validateOptionalNumber(component, "lineWidth", componentName, errors);
                validateOptionalBoolean(component, "shadow", componentName, errors);
                validateNonNegativeIntegerOptional(component, "refreshInterval", componentName, errors);
                continue;
            }

            if ("button".equals(type)) {
                requireString(component, "label", componentName, errors);
                validateSize(component, componentName, errors, true);
                validatePositiveOptional(component, "fontSize", componentName, errors);
                validateOptionalString(component, "clickType", componentName, errors);
                String clickType = optionalString(component, "clickType");
                if (clickType != null && !CLICK_TYPES.contains(clickType.toLowerCase())) {
                    errors.add(componentName + ": clickType must be LEFT, RIGHT, BOTH");
                }
                validateOptionalString(component, "clickSound", componentName, errors);
                validatePositiveOptional(component, "hoverScale", componentName, errors);
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
                JsonNode children = getArray(component, "children");
                if (children != null) {
                    validateComponents(children, componentName + ".children", errors, componentIds);
                }
            }
        }
    }

    private static void validateAction(JsonNode component, String sourceName, List<String> errors) {
        JsonNode action = getObject(component, "action");
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
            validatePermissionLevel(action, sourceName + ".action", errors);
        } else if ("callback".equals(type)) {
            requireString(action, "id", sourceName + ".action", errors);
        }
    }

    private static void validatePermissionLevel(JsonNode action, String sourceName, List<String> errors) {
        JsonNode element = action.get("permissionLevel");
        if (element == null) {
            return;
        }
        if (!element.isNumber()) {
            errors.add(sourceName + ": permissionLevel must be integer between 0 and 4");
            return;
        }
        double value = element.doubleValue();
        if (value != Math.rint(value) || value < 0.0D || value > 4.0D) {
            errors.add(sourceName + ": permissionLevel must be integer between 0 and 4");
        }
    }

    private static void validateTransition(JsonNode root, String sourceName, List<String> errors) {
        JsonNode transition = root.get("transition");
        if (transition == null) {
            return;
        }
        if (!transition.isObject()) {
            errors.add(sourceName + ": transition must be object");
            return;
        }
        String transitionName = sourceName + ".transition";
        validateNonNegativeIntegerOptional(transition, "duration", transitionName, errors);
        validateTransitionType(transition, "enter", transitionName, errors);
        validateTransitionType(transition, "exit", transitionName, errors);
    }

    private static void validateTransitionType(JsonNode transition, String key, String sourceName, List<String> errors) {
        JsonNode element = transition.get(key);
        if (element == null) {
            return;
        }
        if (!element.isTextual()) {
            errors.add(sourceName + ": " + key + " must be string");
            return;
        }
        if (!TRANSITION_TYPES.contains(element.textValue().toLowerCase())) {
            errors.add(sourceName + ": " + key + " must be none, scale, slide_up, slide_down");
        }
    }

    private static void validateGroupWindows(JsonNode windows, String sourceName, List<String> errors) {
        for (int i = 0; i < windows.size(); i++) {
            JsonNode element = windows.get(i);
            if (!isObject(element)) {
                errors.add(sourceName + "[" + i + "]: window entry must be object");
                continue;
            }
            JsonNode entry = element;
            String entryName = sourceName + "[" + i + "]";
            requireString(entry, "windowId", entryName, errors);
            JsonNode offset = getObject(entry, "offset");
            if (offset != null) {
                requireNumber(offset, "forward", entryName + ".offset", errors);
                requireNumber(offset, "horizontal", entryName + ".offset", errors);
                requireNumber(offset, "vertical", entryName + ".offset", errors);
            }
            JsonNode orbit = getObject(entry, "orbit");
            if (orbit != null) {
                requireNumber(orbit, "yaw", entryName + ".orbit", errors);
                requireNumber(orbit, "pitch", entryName + ".orbit", errors);
            }
        }
    }

    private static void validatePosition(JsonNode component, String sourceName, List<String> errors) {
        JsonNode position = getObject(component, "position");
        if (position == null) {
            return;
        }
        requireNumber(position, "x", sourceName + ".position", errors);
        requireNumber(position, "y", sourceName + ".position", errors);
        requireNumber(position, "z", sourceName + ".position", errors);
    }

    private static void validateOffset(JsonNode root, String sourceName, List<String> errors) {
        JsonNode offset = getObject(root, "offset");
        if (offset == null) {
            return;
        }
        requireNumber(offset, "forward", sourceName + ".offset", errors);
        requireNumber(offset, "horizontal", sourceName + ".offset", errors);
        requireNumber(offset, "vertical", sourceName + ".offset", errors);
    }

    private static void validateOpacity(JsonNode component, String sourceName, List<String> errors) {
        JsonNode opacity = component.get("opacity");
        if (opacity == null) {
            return;
        }
        if (!opacity.isNumber()) {
            errors.add(sourceName + ": opacity must be number");
            return;
        }
        float value = opacity.floatValue();
        if (value < 0.0f || value > 1.0f) {
            errors.add(sourceName + ": opacity must be between 0 and 1");
        }
    }

    private static void validateSize(JsonNode component, String sourceName, List<String> errors, boolean requireSizeObject) {
        JsonNode size = getObject(component, "size");
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

    private static void validateOptionalTextSize(JsonNode component, String sourceName, List<String> errors) {
        JsonNode size = getObject(component, "size");
        if (size != null) {
            requirePositiveNumber(size, "width", sourceName + ".size", errors);
            requirePositiveNumber(size, "height", sourceName + ".size", errors);
            return;
        }

        boolean hasWidth = component.has("width");
        boolean hasHeight = component.has("height");
        if (!hasWidth && !hasHeight) {
            return;
        }
        if (!hasWidth || !hasHeight) {
            errors.add(sourceName + ": width and height must be provided together");
            return;
        }
        requirePositiveNumber(component, "width", sourceName, errors);
        requirePositiveNumber(component, "height", sourceName, errors);
    }

    private static void requireLayout(JsonNode object, String sourceName, List<String> errors) {
        JsonNode element = object.get("layout");
        if (element == null) {
            return;
        }
        if (!element.isTextual()) {
            errors.add(sourceName + ": layout must be string");
            return;
        }
        if (!LAYOUT_TYPES.contains(element.textValue().toLowerCase())) {
            errors.add(sourceName + ": layout must be absolute, vertical, horizontal");
        }
    }

    private static void validateAlignment(JsonNode object, String sourceName, List<String> errors) {
        JsonNode element = object.get("alignment");
        if (element == null) {
            return;
        }
        if (!element.isTextual()) {
            errors.add(sourceName + ": alignment must be string");
            return;
        }
        if (!ALIGNMENTS.contains(element.textValue().toLowerCase())) {
            errors.add(sourceName + ": alignment must be left, center, right");
        }
    }

    private static void validateColorFields(JsonNode object, String sourceName, List<String> errors) {
        for (String key : COLOR_FIELDS) {
            validateOptionalString(object, key, sourceName, errors);
        }
    }

    private static void validateOptionalString(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element != null && !element.isTextual()) {
            errors.add(sourceName + ": " + key + " must be string");
        }
    }

    private static void validateOptionalNumber(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element != null && !element.isNumber()) {
            errors.add(sourceName + ": " + key + " must be number");
        }
    }

    private static void validateOptionalBoolean(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element != null && !element.isBoolean()) {
            errors.add(sourceName + ": " + key + " must be boolean");
        }
    }

    private static void validatePositiveOptional(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element == null) {
            return;
        }
        if (!element.isNumber()) {
            errors.add(sourceName + ": " + key + " must be number");
            return;
        }
        if (element.floatValue() <= 0.0f) {
            errors.add(sourceName + ": " + key + " must be > 0");
        }
    }

    private static void validateNonNegativeOptional(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element == null) {
            return;
        }
        if (!element.isNumber()) {
            errors.add(sourceName + ": " + key + " must be number");
            return;
        }
        if (element.floatValue() < 0.0f) {
            errors.add(sourceName + ": " + key + " must be >= 0");
        }
    }

    private static void validateNonNegativeIntegerOptional(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element == null) {
            return;
        }
        if (!element.isIntegralNumber() || element.longValue() < 0L || element.longValue() > Integer.MAX_VALUE) {
            errors.add(sourceName + ": " + key + " must be a non-negative integer");
        }
    }

    private static String requireString(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element == null || !element.isTextual()) {
            errors.add(sourceName + ": " + key + " must be string");
            return null;
        }
        return element.textValue();
    }

    private static String optionalString(JsonNode object, String key) {
        JsonNode element = object.get(key);
        return element != null && element.isTextual() ? element.textValue() : null;
    }

    private static void requireObject(JsonNode object, String key, String sourceName, List<String> errors) {
        if (!isObject(object.get(key))) {
            errors.add(sourceName + ": " + key + " must be object");
        }
    }

    private static void requireArray(JsonNode object, String key, String sourceName, List<String> errors) {
        if (!isArray(object.get(key))) {
            errors.add(sourceName + ": " + key + " must be array");
        }
    }

    private static void requireNumber(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element == null || !element.isNumber()) {
            errors.add(sourceName + ": " + key + " must be number");
        }
    }

    private static void requirePositiveNumber(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element == null || !element.isNumber()) {
            errors.add(sourceName + ": " + key + " must be number");
            return;
        }
        if (element.floatValue() <= 0.0f) {
            errors.add(sourceName + ": " + key + " must be > 0");
        }
    }

    private static JsonNode getObject(JsonNode object, String key) {
        JsonNode element = object.get(key);
        return isObject(element) ? element : null;
    }

    private static JsonNode getArray(JsonNode object, String key) {
        JsonNode element = object.get(key);
        return isArray(element) ? element : null;
    }

    private static boolean isObject(JsonNode node) {
        return node != null && node.isObject();
    }

    private static boolean isArray(JsonNode node) {
        return node != null && node.isArray();
    }
}

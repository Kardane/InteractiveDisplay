package com.interactivedisplay.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SchemaValidator {
    private static final Set<String> COMPONENT_TYPES = Set.of("text", "button", "text_input", "image", "panel");
    private static final Set<String> ACTION_TYPES = Set.of("close_window", "open_window", "switch_mode_fixed", "switch_mode_player_fixed", "switch_mode_player_view", "toggle_placement_tracking", "run_command", "callback");
    private static final Set<String> IMAGE_TYPES = Set.of("item", "block", "map");
    private static final Set<String> LAYOUT_TYPES = Set.of("absolute", "vertical", "horizontal", "grid");
    private static final Set<String> CLICK_TYPES = Set.of("left", "right", "both");
    private static final Set<String> ALIGNMENTS = Set.of("left", "center", "right");
    private static final Set<String> BUTTON_VERTICAL_ALIGNMENTS = Set.of("bottom", "center", "top");
    private static final Set<String> ITEM_ALIGNMENTS = Set.of("start", "center", "end");
    private static final Set<String> OVERFLOW_POLICIES = Set.of("visible", "error");
    private static final Set<String> ANCHORS = Set.of(
            "top-left", "top-center", "top-right",
            "center-left", "center", "center-right",
            "bottom-left", "bottom-center", "bottom-right"
    );
    private static final Set<String> BUTTON_SIZE_MODES = Set.of("fixed", "content");
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
        validateLayout(root, sourceName, errors, true);
        validateOffset(root, sourceName, errors);
        validateTransition(root, sourceName, errors);

        JsonNode size = getObject(root, "size");
        if (size != null) {
            validateWindowSizeAxis(size, "width", sourceName + ".size", errors);
            validateWindowSizeAxis(size, "height", sourceName + ".size", errors);
        }
        validateSizeConstraints(root, sourceName, errors);

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

            JsonNode position = component.get("position");
            if (position != null && !position.isObject()) {
                errors.add(componentName + ": position must be object");
            }
            validatePosition(component, componentName, errors);
            validateAnchorAndMargin(component, componentName, errors);
            validateSizeConstraints(component, componentName, errors);
            validateOptionalBoolean(component, "visible", componentName, errors);
            validateOpacity(component, componentName, errors);
            validateLayout(component, componentName, errors, "panel".equals(type));
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
                validateButtonPadding(component, componentName, errors);
                validateButtonAlignment(component, componentName, errors);
                validateButtonSizing(component, componentName, errors);
                validateButtonContentArea(component, componentName, errors);
                validateAction(component, componentName, errors);
                continue;
            }

            if ("text_input".equals(type)) {
                validateSize(component, componentName, errors, true);
                validateOptionalString(component, "initialValue", componentName, errors);
                validateOptionalString(component, "placeholder", componentName, errors);
                validatePositiveOptional(component, "maxLength", componentName, errors);
                validatePositiveOptional(component, "fontSize", componentName, errors);
                validateOptionalString(component, "clickType", componentName, errors);
                String clickType = optionalString(component, "clickType");
                if (clickType != null && !CLICK_TYPES.contains(clickType.toLowerCase())) {
                    errors.add(componentName + ": clickType must be LEFT, RIGHT, BOTH");
                }
                validateOptionalString(component, "clickSound", componentName, errors);
                validateOptionalString(component, "dialogTitle", componentName, errors);
                validateOptionalString(component, "dialogLabel", componentName, errors);
                validateOptionalString(component, "confirmLabel", componentName, errors);
                validateOptionalString(component, "cancelLabel", componentName, errors);
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

    private static void validateAnchorAndMargin(JsonNode component, String sourceName, List<String> errors) {
        JsonNode anchor = component.get("anchor");
        if (anchor != null) {
            if (!anchor.isTextual()) {
                errors.add(sourceName + ": anchor must be string");
            } else if (!ANCHORS.contains(anchor.textValue().toLowerCase())) {
                errors.add(sourceName + ": anchor must be top-left, top-center, top-right, center-left, center, center-right, bottom-left, bottom-center, or bottom-right");
            }
        }

        JsonNode margin = component.get("margin");
        if (margin == null) {
            return;
        }
        if (margin.isNumber()) {
            if (!Float.isFinite(margin.floatValue()) || margin.floatValue() < 0.0f) {
                errors.add(sourceName + ": margin must be a finite non-negative number");
            }
            return;
        }
        if (!margin.isObject()) {
            errors.add(sourceName + ": margin must be a number or object");
            return;
        }
        for (String side : List.of("top", "right", "bottom", "left")) {
            JsonNode value = margin.get(side);
            if (value != null && (!value.isNumber() || !Float.isFinite(value.floatValue()) || value.floatValue() < 0.0f)) {
                errors.add(sourceName + ".margin: " + side + " must be a finite non-negative number");
            }
        }
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
            validateComponentSizeAxis(size, "width", sourceName + ".size", errors);
            validateComponentSizeAxis(size, "height", sourceName + ".size", errors);
            return;
        }

        if (!requireSizeObject && component.has("width") && component.has("height")) {
            validateComponentSizeAxis(component, "width", sourceName, errors);
            validateComponentSizeAxis(component, "height", sourceName, errors);
            return;
        }

        errors.add(sourceName + ": size is required");
    }

    private static void validateOptionalTextSize(JsonNode component, String sourceName, List<String> errors) {
        JsonNode size = getObject(component, "size");
        if (size != null) {
            validateComponentSizeAxis(size, "width", sourceName + ".size", errors);
            validateComponentSizeAxis(size, "height", sourceName + ".size", errors);
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
        validateComponentSizeAxis(component, "width", sourceName, errors);
        validateComponentSizeAxis(component, "height", sourceName, errors);
    }

    private static void validateComponentSizeAxis(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element == null) {
            errors.add(sourceName + ": " + key + " is required");
            return;
        }
        if (element.isTextual()) {
            String value = element.textValue().toLowerCase();
            if (!"fill".equals(value) && !"auto".equals(value)) {
                errors.add(sourceName + ": " + key + " must be a positive number, fill, or auto");
            }
            return;
        }
        if (!element.isNumber()) {
            errors.add(sourceName + ": " + key + " must be a positive number, fill, or auto");
            return;
        }
        if (!Float.isFinite(element.floatValue()) || element.floatValue() <= 0.0f) {
            errors.add(sourceName + ": " + key + " must be > 0");
        }
    }

    private static void validateWindowSizeAxis(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element == null) {
            errors.add(sourceName + ": " + key + " is required");
            return;
        }
        if (element.isTextual()) {
            if (!"auto".equalsIgnoreCase(element.textValue())) {
                errors.add(sourceName + ": " + key + " must be a positive number or auto");
            }
            return;
        }
        if (!element.isNumber()) {
            errors.add(sourceName + ": " + key + " must be a positive number or auto");
            return;
        }
        if (!Float.isFinite(element.floatValue()) || element.floatValue() <= 0.0f) {
            errors.add(sourceName + ": " + key + " must be > 0");
        }
    }

    private static void validateSizeConstraints(JsonNode component, String sourceName, List<String> errors) {
        validateConstraintObject(component.get("minSize"), sourceName + ".minSize", true, errors);
        validateConstraintObject(component.get("maxSize"), sourceName + ".maxSize", false, errors);

        JsonNode minSize = getObject(component, "minSize");
        JsonNode maxSize = getObject(component, "maxSize");
        if (minSize == null || maxSize == null) {
            return;
        }
        for (String axis : List.of("width", "height")) {
            JsonNode min = minSize.get(axis);
            JsonNode max = maxSize.get(axis);
            if (min != null && max != null && min.isNumber() && max.isNumber()
                    && min.floatValue() > max.floatValue()) {
                errors.add(sourceName + ": minSize." + axis + " must be <= maxSize." + axis);
            }
        }
    }

    private static void validateConstraintObject(JsonNode object,
                                                 String sourceName,
                                                 boolean allowZero,
                                                 List<String> errors) {
        if (object == null) {
            return;
        }
        if (!object.isObject()) {
            errors.add(sourceName + " must be object");
            return;
        }
        for (String axis : List.of("width", "height")) {
            JsonNode value = object.get(axis);
            if (value == null) {
                continue;
            }
            if (!value.isNumber() || !Float.isFinite(value.floatValue())
                    || (allowZero ? value.floatValue() < 0.0f : value.floatValue() <= 0.0f)) {
                errors.add(sourceName + ": " + axis + (allowZero ? " must be >= 0" : " must be > 0"));
            }
        }
    }

    private static void validateLayout(JsonNode object, String sourceName, List<String> errors, boolean container) {
        JsonNode element = object.get("layout");
        if (element == null) {
            return;
        }
        if (element.isTextual()) {
            String type = element.textValue().toLowerCase();
            if (!LAYOUT_TYPES.contains(type) || (!container && "grid".equals(type))) {
                errors.add(sourceName + ": layout must be absolute, vertical, horizontal" + (container ? ", grid" : ""));
            }
            return;
        }
        if (!container || !element.isObject()) {
            errors.add(sourceName + ": layout must be string" + (container ? " or object" : ""));
            return;
        }

        String layoutName = sourceName + ".layout";
        JsonNode typeNode = element.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            errors.add(layoutName + ": type must be string");
            return;
        }
        String type = typeNode.textValue().toLowerCase();
        if (!LAYOUT_TYPES.contains(type)) {
            errors.add(layoutName + ": type must be absolute, vertical, horizontal, grid");
            return;
        }

        validateNonNegativeLayoutNumber(element, "gap", layoutName, errors);
        validateNonNegativeLayoutNumber(element, "rowGap", layoutName, errors);
        validateNonNegativeLayoutNumber(element, "columnGap", layoutName, errors);
        validateItemAlignment(element, "justifyItems", type, layoutName, errors);
        validateItemAlignment(element, "alignItems", type, layoutName, errors);
        validateOverflowPolicy(element, layoutName, errors);
        JsonNode columns = element.get("columns");
        if ("grid".equals(type)) {
            if (!isPositiveInteger(columns)) {
                errors.add(layoutName + ": columns must be a positive integer");
            }
        } else if (columns != null && !isPositiveInteger(columns)) {
            errors.add(layoutName + ": columns must be a positive integer");
        }
    }

    private static void validateOverflowPolicy(JsonNode layout, String sourceName, List<String> errors) {
        JsonNode overflow = layout.get("overflow");
        if (overflow == null) {
            return;
        }
        if (!overflow.isTextual()) {
            errors.add(sourceName + ": overflow must be string");
            return;
        }
        if (!OVERFLOW_POLICIES.contains(overflow.textValue().toLowerCase())) {
            errors.add(sourceName + ": overflow must be visible or error");
        }
    }

    private static void validateNonNegativeLayoutNumber(JsonNode object, String key, String sourceName, List<String> errors) {
        JsonNode element = object.get(key);
        if (element == null) {
            return;
        }
        if (!element.isNumber() || !Float.isFinite(element.floatValue()) || element.floatValue() < 0.0f) {
            errors.add(sourceName + ": " + key + " must be a finite non-negative number");
        }
    }

    private static boolean isPositiveInteger(JsonNode element) {
        return element != null && element.isIntegralNumber() && element.canConvertToInt() && element.intValue() >= 1;
    }

    private static void validateItemAlignment(JsonNode object,
                                              String key,
                                              String layoutType,
                                              String sourceName,
                                              List<String> errors) {
        JsonNode element = object.get(key);
        if (element == null) {
            return;
        }
        if (!element.isTextual()) {
            errors.add(sourceName + ": " + key + " must be string");
            return;
        }
        String value = element.textValue().toLowerCase();
        if (!ITEM_ALIGNMENTS.contains(value)) {
            errors.add(sourceName + ": " + key + " must be start, center, or end");
        } else if (!"grid".equals(layoutType)) {
            errors.add(sourceName + ": " + key + " is only supported by grid layout");
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

    private static void validateButtonSizing(JsonNode component, String sourceName, List<String> errors) {
        JsonNode sizing = component.get("sizing");
        if (sizing == null) {
            return;
        }
        if (!sizing.isObject()) {
            errors.add(sourceName + ": sizing must be object");
            return;
        }
        validateOptionalString(sizing, "width", sourceName + ".sizing", errors);
        validateOptionalString(sizing, "height", sourceName + ".sizing", errors);
        String width = optionalString(sizing, "width");
        if (width != null && !BUTTON_SIZE_MODES.contains(width.toLowerCase())) {
            errors.add(sourceName + ".sizing: width must be fixed or content");
        }
        String height = optionalString(sizing, "height");
        if (height != null && !BUTTON_SIZE_MODES.contains(height.toLowerCase())) {
            errors.add(sourceName + ".sizing: height must be fixed or content");
        }
    }

    private static void validateButtonPadding(JsonNode component, String sourceName, List<String> errors) {
        JsonNode padding = component.get("padding");
        if (padding == null) {
            return;
        }
        if (padding.isNumber()) {
            if (padding.floatValue() < 0.0f) {
                errors.add(sourceName + ": padding must be >= 0");
            }
            return;
        }
        if (!padding.isObject()) {
            errors.add(sourceName + ": padding must be a number or object");
            return;
        }
        validateNonNegativeOptional(padding, "horizontal", sourceName + ".padding", errors);
        validateNonNegativeOptional(padding, "vertical", sourceName + ".padding", errors);
    }

    private static void validateButtonAlignment(JsonNode component, String sourceName, List<String> errors) {
        JsonNode alignment = component.get("alignment");
        if (alignment == null) {
            return;
        }
        if (!alignment.isObject()) {
            errors.add(sourceName + ": alignment must be object");
            return;
        }
        validateOptionalString(alignment, "horizontal", sourceName + ".alignment", errors);
        validateOptionalString(alignment, "vertical", sourceName + ".alignment", errors);
        String horizontal = optionalString(alignment, "horizontal");
        if (horizontal != null && !ALIGNMENTS.contains(horizontal.toLowerCase())) {
            errors.add(sourceName + ".alignment: horizontal must be left, center, right");
        }
        String vertical = optionalString(alignment, "vertical");
        if (vertical != null && !BUTTON_VERTICAL_ALIGNMENTS.contains(vertical.toLowerCase())) {
            errors.add(sourceName + ".alignment: vertical must be bottom, center, top");
        }
    }

    private static void validateButtonContentArea(JsonNode component, String sourceName, List<String> errors) {
        JsonNode size = getObject(component, "size");
        if (size == null || !size.has("width") || !size.has("height")
                || !size.get("width").isNumber() || !size.get("height").isNumber()) {
            return;
        }
        JsonNode padding = component.get("padding");
        float horizontal = 0.0f;
        float vertical = 0.0f;
        if (padding != null && padding.isNumber()) {
            horizontal = padding.floatValue();
            vertical = padding.floatValue();
        } else if (padding != null && padding.isObject()) {
            JsonNode horizontalNode = padding.get("horizontal");
            JsonNode verticalNode = padding.get("vertical");
            if (horizontalNode != null && horizontalNode.isNumber()) {
                horizontal = horizontalNode.floatValue();
            }
            if (verticalNode != null && verticalNode.isNumber()) {
                vertical = verticalNode.floatValue();
            }
        }
        JsonNode sizing = component.get("sizing");
        String widthMode = sizing != null && sizing.isObject() ? optionalString(sizing, "width") : null;
        String heightMode = sizing != null && sizing.isObject() ? optionalString(sizing, "height") : null;
        boolean fixedWidth = widthMode == null || !"content".equalsIgnoreCase(widthMode);
        boolean fixedHeight = heightMode == null || !"content".equalsIgnoreCase(heightMode);

        if (fixedWidth && horizontal >= 0.0f && size.get("width").floatValue() <= horizontal * 2.0f) {
            errors.add(sourceName + ": horizontal padding must leave positive content width");
        }
        if (fixedHeight && vertical >= 0.0f && size.get("height").floatValue() <= vertical * 2.0f) {
            errors.add(sourceName + ": vertical padding must leave positive content height");
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

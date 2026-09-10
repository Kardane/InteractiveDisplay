package com.interactivedisplay.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

final class CustomActionNormalizer {
    private static final Set<String> BUILTIN_ACTION_TYPES = Set.of(
            "close_window",
            "open_window",
            "switch_mode_fixed",
            "switch_mode_player_fixed",
            "toggle_placement_tracking",
            "run_command",
            "callback"
    );

    private CustomActionNormalizer() {
    }

    static void normalize(JsonNode root) throws IOException {
        if (root == null || !root.isObject()) {
            return;
        }
        normalizeComponents(root.get("components"));
    }

    private static void normalizeComponents(JsonNode components) throws IOException {
        if (components == null || !components.isArray()) {
            return;
        }
        for (JsonNode component : components) {
            if (!component.isObject()) {
                continue;
            }
            normalizeAction(component.get("action"));
            normalizeComponents(component.get("children"));
        }
    }

    private static void normalizeAction(JsonNode actionNode) throws IOException {
        if (!(actionNode instanceof ObjectNode action)) {
            return;
        }
        JsonNode typeNode = action.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            return;
        }
        String rawType = typeNode.textValue();
        if (BUILTIN_ACTION_TYPES.contains(rawType)) {
            return;
        }
        ResourceLocation actionId = ResourceLocation.tryParse(rawType);
        if (actionId == null || rawType.indexOf(':') < 0) {
            return;
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        var fields = action.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if ("type".equals(entry.getKey())) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (value == null || value.isContainerNode()) {
                throw new IOException("custom action parameter must be scalar: " + entry.getKey());
            }
            parameters.put(entry.getKey(), value.asText());
        }

        String token = CustomActionToken.encode(actionId, parameters);
        action.removeAll();
        action.put("type", "callback");
        action.put("id", token);
    }
}

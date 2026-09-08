package com.interactivedisplay.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.positioning.WindowOffset;
import com.interactivedisplay.core.window.WindowGroupDefinition;
import com.interactivedisplay.core.window.WindowGroupEntry;
import com.interactivedisplay.core.window.WindowOrbit;
import java.util.ArrayList;
import java.util.List;

public final class GroupDefinitionParser {
    public WindowGroupDefinition parse(JsonNode root) {
        String id = root.get("id").textValue();
        String initialWindowId = root.get("initialWindowId").textValue();
        PositionMode defaultMode = PositionMode.fromArgument(root.get("defaultMode").textValue());
        JsonNode windows = root.get("windows");
        List<WindowGroupEntry> entries = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            JsonNode entry = windows.get(i);
            entries.add(new WindowGroupEntry(
                    entry.get("windowId").textValue(),
                    parseOffset(entry, WindowOffset.zero()),
                    parseOrbit(entry.get("orbit"))
            ));
        }
        return new WindowGroupDefinition(id, initialWindowId, defaultMode, entries);
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

    private static WindowOrbit parseOrbit(JsonNode orbitObject) {
        if (orbitObject == null || !orbitObject.isObject()) {
            return WindowOrbit.zero();
        }
        return new WindowOrbit(
                getFloat(orbitObject, "yaw", 0.0f),
                getFloat(orbitObject, "pitch", 0.0f)
        );
    }

    private static float getFloat(JsonNode object, String key, float defaultValue) {
        JsonNode value = object.get(key);
        return value == null ? defaultValue : value.floatValue();
    }
}

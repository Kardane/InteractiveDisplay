package com.interactivedisplay.core.window;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.TextComponentDefinition;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WindowComponentRuntimeRefreshTest {
    @Test
    void refreshCadenceShouldWaitForConfiguredInterval() {
        TextComponentDefinition definition = definition("status", 20);
        WindowComponentRuntime runtime = runtime(definition);

        assertTrue(runtime.shouldRefreshText(100L, 20));
        assertFalse(runtime.shouldRefreshText(119L, 20));
        assertTrue(runtime.shouldRefreshText(120L, 20));
    }

    private static WindowComponentRuntime runtime(TextComponentDefinition definition) {
        return new WindowComponentRuntime(
                Level.OVERWORLD,
                definition,
                new Vector3f(),
                null,
                null
        );
    }

    private static TextComponentDefinition definition(String id, int refreshInterval) {
        return new TextComponentDefinition(
                id,
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(1.0f, 0.3f),
                true,
                1.0f,
                "value",
                0.5f,
                "#FFFFFF",
                "left",
                200,
                true,
                "#00000000",
                refreshInterval
        );
    }
}

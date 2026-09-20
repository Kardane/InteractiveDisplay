package com.interactivedisplay.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WindowComponentRuntimeTest {
    @Test
    void buttonHitboxShouldMatchConfiguredBackgroundWidth() {
        WindowComponentRuntime iconRuntime = runtime("☒", 1.6f, 0.35f);
        WindowComponentRuntime textRuntime = runtime("닫기", 1.6f, 0.35f);

        assertEquals(0.8f, iconRuntime.hitHalfWidth(), 0.0001f);
        assertEquals(iconRuntime.hitHalfWidth(), textRuntime.hitHalfWidth(), 0.0001f);
    }

    @Test
    void wrappedLabelShouldIncreaseHitHeight() {
        WindowComponentRuntime shortRuntime = runtime("닫기", 0.5f, 0.35f);
        WindowComponentRuntime longRuntime = runtime("인터랙티브 디스플레이 닫기", 0.5f, 0.35f);

        assertTrue(longRuntime.hitHalfHeight() > shortRuntime.hitHalfHeight());
    }

    @Test
    void buttonHitCenterShouldBeAboveTextDisplayOrigin() {
        WindowComponentRuntime buttonRuntime = runtime("☒", 0.45f, 0.35f);

        assertEquals(buttonRuntime.hitHalfHeight(), buttonRuntime.hitCenterLocalPosition().y, 0.0001f);
        assertEquals(0.0f, buttonRuntime.hitCenterLocalPosition().x, 0.0001f);
        assertEquals(0.0f, buttonRuntime.hitCenterLocalPosition().z, 0.0001f);
    }

    @Test
    void hitboxShouldMatchRenderedBackgroundForSingleLineButton() {
        WindowComponentRuntime buttonRuntime = runtime("R1C2", 0.9f, 0.5f, 0.45f);

        assertEquals(0.45f, buttonRuntime.hitHalfWidth(), 0.0001f);
        assertEquals(0.05625f, buttonRuntime.hitHalfHeight(), 0.0001f);
        assertEquals(buttonRuntime.hitHalfHeight(), buttonRuntime.hitCenterLocalPosition().y, 0.0001f);
    }

    @Test
    void runtimeWithoutMaterializedDisplayShouldReportZeroPacketEntities() {
        assertEquals(0, runtime("닫기", 1.0f, 0.35f).entityCount());
    }

    private static WindowComponentRuntime runtime(String label, float width, float height) {
        return runtime(label, width, height, 1.0f);
    }

    private static WindowComponentRuntime runtime(String label, float width, float height, float fontSize) {
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "close",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(width, height),
                true,
                1.0f,
                label,
                fontSize,
                "#CC992222",
                "#EECC4444",
                null,
                ClickType.RIGHT,
                ComponentAction.closeWindow()
        );
        return new WindowComponentRuntime(Level.OVERWORLD, button, new Vector3f(), null, null);
    }
}

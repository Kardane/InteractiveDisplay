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
    void iconButtonHitboxShouldBeNarrowerThanLongLabelButton() {
        WindowComponentRuntime iconRuntime = runtime("☒", 1.6f, 0.35f);
        WindowComponentRuntime textRuntime = runtime("닫기", 1.6f, 0.35f);

        assertTrue(iconRuntime.hitHalfWidth() < textRuntime.hitHalfWidth());
        assertTrue(iconRuntime.hitHalfWidth() < 0.4f);
    }

    @Test
    void wrappedLabelShouldIncreaseHitHeight() {
        WindowComponentRuntime shortRuntime = runtime("닫기", 0.5f, 0.35f);
        WindowComponentRuntime longRuntime = runtime("인터랙티브 디스플레이 닫기", 0.5f, 0.35f);

        assertTrue(longRuntime.hitHalfHeight() > shortRuntime.hitHalfHeight());
    }

    @Test
    void runtimeWithoutMaterializedDisplayShouldReportZeroPacketEntities() {
        assertEquals(0, runtime("닫기", 1.0f, 0.35f).entityCount());
    }

    private static WindowComponentRuntime runtime(String label, float width, float height) {
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "close",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(width, height),
                true,
                1.0f,
                label,
                1.0f,
                "#CC992222",
                "#EECC4444",
                null,
                ClickType.RIGHT,
                ComponentAction.closeWindow()
        );
        return new WindowComponentRuntime(Level.OVERWORLD, button, new Vector3f(), null, null);
    }
}

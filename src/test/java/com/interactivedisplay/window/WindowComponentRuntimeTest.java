package com.interactivedisplay.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ButtonHorizontalAlignment;
import com.interactivedisplay.core.component.ButtonPadding;
import com.interactivedisplay.core.component.ButtonSizeMode;
import com.interactivedisplay.core.component.ButtonSizing;
import com.interactivedisplay.core.component.ButtonVerticalAlignment;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.ComponentActionType;
import com.interactivedisplay.core.component.TextInputComponentDefinition;
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
    void buttonHitHeightShouldUseConfiguredHeightRegardlessOfLabelWrapping() {
        WindowComponentRuntime shortRuntime = runtime("닫기", 0.5f, 0.35f);
        WindowComponentRuntime longRuntime = runtime("인터랙티브 디스플레이 닫기", 0.5f, 0.35f);

        assertEquals(0.175f, shortRuntime.hitHalfHeight(), 0.0001f);
        assertEquals(shortRuntime.hitHalfHeight(), longRuntime.hitHalfHeight(), 0.0001f);
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
        assertEquals(0.25f, buttonRuntime.hitHalfHeight(), 0.0001f);
        assertEquals(buttonRuntime.hitHalfHeight(), buttonRuntime.hitCenterLocalPosition().y, 0.0001f);
    }

    @Test
    void contentSizedButtonHitboxShouldUseResolvedBox() {
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "content",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(0.3f, 0.1f),
                true,
                1.0f,
                "ABCDEFGH",
                0.4f,
                "#CC222222",
                "#EE444444",
                null,
                ClickType.BOTH,
                ComponentAction.closeWindow(),
                1.0f,
                new ButtonPadding(0.05f, 0.05f),
                ButtonHorizontalAlignment.CENTER,
                ButtonVerticalAlignment.CENTER,
                new ButtonSizing(ButtonSizeMode.FIXED, ButtonSizeMode.CONTENT)
        );
        WindowComponentRuntime runtime =
                new WindowComponentRuntime(Level.OVERWORLD, button, new Vector3f(), null, null);

        assertEquals(0.15f, runtime.hitHalfWidth(), 0.0001f);
        assertEquals(0.2f, runtime.hitHalfHeight(), 0.0001f);
        assertEquals(0.2f, runtime.hitCenterLocalPosition().y, 0.0001f);
    }

    @Test
    void textInputShouldUseConfiguredHitboxAndMaintainRuntimeValue() {
        TextInputComponentDefinition input = new TextInputComponentDefinition(
                "search",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(2.0f, 0.4f),
                true,
                1.0f,
                "initial",
                "Search...",
                10,
                0.4f,
                "#FFFFFF",
                "#CC222222",
                "#EE444444",
                null,
                ClickType.RIGHT,
                "Search",
                "Query",
                "Done",
                "Cancel"
        );
        WindowComponentRuntime runtime = new WindowComponentRuntime(Level.OVERWORLD, input, new Vector3f(), null, null);

        assertTrue(runtime.interactive());
        assertEquals(1.0f, runtime.hitHalfWidth(), 0.0001f);
        assertEquals(0.2f, runtime.hitHalfHeight(), 0.0001f);
        assertEquals(0.2f, runtime.hitCenterLocalPosition().y, 0.0001f);
        assertEquals(ComponentActionType.OPEN_TEXT_INPUT, runtime.action().type());
        assertEquals("initial", runtime.inputValue());

        runtime.setInputValue("0123456789overflow");
        assertEquals("0123456789", runtime.inputValue());
    }

    @Test
    void runtimeWithoutMaterializedDisplayShouldReportZeroPacketEntities() {
        assertEquals(0, runtime("닫기", 1.0f, 0.35f).entityCount());
    }

    private static ButtonComponentDefinition button(String label, float width, float height, float fontSize) {
        return new ButtonComponentDefinition(
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
    }

    private static WindowComponentRuntime runtime(String label, float width, float height) {
        return runtime(label, width, height, 1.0f);
    }

    private static WindowComponentRuntime runtime(String label, float width, float height, float fontSize) {
        return new WindowComponentRuntime(Level.OVERWORLD, button(label, width, height, fontSize), new Vector3f(), null, null);
    }
}

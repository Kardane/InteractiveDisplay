package com.interactivedisplay.core.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class TypewriterAnimationRuntimeTest {
    @Test
    void revealsResolvedTextWithoutRebuildingTheDisplay() {
        AnimationDefinition animation = new AnimationDefinition(
                "typewriter", 0, 0, 1, 1, AnimationInterpolation.LINEAR
        );
        TextComponentDefinition definition = new TextComponentDefinition(
                "dialogue",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(2.0f, 0.4f),
                true,
                1.0f,
                "가나다",
                0.5f,
                "#FFFFFF",
                "left",
                200,
                true,
                "#00000000",
                20,
                List.of(animation)
        );
        TextDisplayElement display = new TextDisplayElement();
        display.setText(Component.literal("가나다"));
        WindowComponentRuntime runtime = new WindowComponentRuntime(
                Level.OVERWORLD, definition, new Vector3f(), display, null
        );

        runtime.redefine(definition, new Vector3f());
        assertEquals("", display.getText().getString());
        assertTrue(runtime.hasActiveTextAnimation());

        assertFalse(runtime.shouldRefreshText(100L, definition.refreshInterval()));
        assertEquals("가", display.getText().getString());
        assertFalse(runtime.shouldRefreshText(101L, definition.refreshInterval()));
        assertEquals("가나", display.getText().getString());
        assertTrue(runtime.shouldRefreshText(102L, definition.refreshInterval()));
        assertEquals("가나다", display.getText().getString());
        assertFalse(runtime.hasActiveTextAnimation());
    }

    @Test
    void redefinitionInvalidatesOldAnimationGeneration() {
        AnimationDefinition animation = new AnimationDefinition(
                "typewriter", 0, 0, 1, 1, AnimationInterpolation.LINEAR
        );
        TextComponentDefinition definition = new TextComponentDefinition(
                "dialogue", new ComponentPosition(0, 0, 0), new ComponentSize(1, 1), true, 1.0f,
                "AB", 0.5f, "#FFFFFF", "left", 200, true, "#00000000", 0, List.of(animation)
        );
        TextDisplayElement display = new TextDisplayElement();
        display.setText(Component.literal("AB"));
        WindowComponentRuntime runtime = new WindowComponentRuntime(Level.OVERWORLD, definition, new Vector3f(), display, null);

        runtime.redefine(definition, new Vector3f());
        long firstGeneration = runtime.animationGeneration();
        runtime.redefine(definition, new Vector3f());

        assertTrue(runtime.animationGeneration() > firstGeneration);
        assertEquals(1, runtime.activeAnimationCount());
    }
}

package com.interactivedisplay.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.debug.DebugRecorder;
import com.mojang.math.Transformation;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class DisplayEntityFactoryTest {
    @Test
    void transformationShouldPreserveTranslationAndScale() {
        Transformation transformation = DisplayEntityFactory.buildTransformation(
                new Vector3f(0.5f, 1.25f, 0.001f),
                new Vector3f(1.0f, 2.0f, 3.0f)
        );

        assertEquals(0.5f, transformation.getScale().x, 0.0001f);
        assertEquals(1.25f, transformation.getScale().y, 0.0001f);
        assertEquals(0.001f, transformation.getScale().z, 0.0001f);
        assertEquals(1.0f, transformation.getTranslation().x, 0.0001f);
        assertEquals(2.0f, transformation.getTranslation().y, 0.0001f);
        assertEquals(3.0f, transformation.getTranslation().z, 0.0001f);
    }

    @Test
    void transformationShouldClampZeroZScale() {
        Transformation transformation = DisplayEntityFactory.buildTransformation(
                new Vector3f(1.0f, 1.0f, 0.0f),
                new Vector3f()
        );

        assertEquals(0.001f, transformation.getScale().z, 0.0001f);
    }

    @Test
    void textFlagsShouldEncodeShadowAndAlignment() {
        byte leftShadow = DisplayEntityFactory.buildTextFlags(true, "left");
        byte right = DisplayEntityFactory.buildTextFlags(false, "right");
        byte center = DisplayEntityFactory.buildTextFlags(false, "center");

        assertTrue((leftShadow & Display.TextDisplay.FLAG_SHADOW) != 0);
        assertTrue((leftShadow & Display.TextDisplay.FLAG_ALIGN_LEFT) != 0);
        assertTrue((right & Display.TextDisplay.FLAG_ALIGN_RIGHT) != 0);
        assertEquals(0, center);
    }

    @Test
    void opacityShouldClampToByteRange() {
        assertEquals((byte) 0, DisplayEntityFactory.toTextOpacity(-1.0f));
        assertEquals((byte) 128, DisplayEntityFactory.toTextOpacity(0.5f));
        assertEquals((byte) 255, DisplayEntityFactory.toTextOpacity(2.0f));
    }

    @Test
    void panelRenderSpecShouldGrowWithConfiguredSize() {
        DisplayEntityFactory.PanelRenderSpec small = DisplayEntityFactory.buildPanelRenderSpec(panel(1.0f, 0.5f));
        DisplayEntityFactory.PanelRenderSpec large = DisplayEntityFactory.buildPanelRenderSpec(panel(2.0f, 1.5f));

        assertTrue(large.text().getString().length() > small.text().getString().length());
        assertTrue(large.lineWidth() > small.lineWidth());
        assertTrue(large.fontSize() >= small.fontSize());
    }

    @Test
    void panelRenderSpecShouldUseMultipleLinesForTallPanels() {
        DisplayEntityFactory.PanelRenderSpec spec = DisplayEntityFactory.buildPanelRenderSpec(panel(1.0f, 1.5f));

        assertTrue(spec.text().getString().contains("\n"));
        assertFalse(spec.text().getString().contains("█"));
        assertEquals(0.0f, spec.textOpacity());
    }

    @Test
    void textAndButtonRenderingShouldUsePlaceholderResolver() {
        DisplayEntityFactory factory = new DisplayEntityFactory(
                new DebugRecorder(10),
                (player, text) -> Component.literal("resolved:" + text.getString())
        );

        Component content = factory.renderTextContent("안녕 {player:name}", "#FFFFFF", null);
        Component label = factory.renderButtonLabel("열기 {player:name}", null);

        assertEquals("resolved:안녕 {player:name}", content.getString());
        assertEquals("resolved:열기 {player:name}", label.getString());
    }

    private static PanelComponentDefinition panel(float width, float height) {
        return new PanelComponentDefinition(
                "background",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(width, height),
                true,
                1.0f,
                "#88000000",
                0.0f,
                LayoutMode.ABSOLUTE,
                List.of()
        );
    }
}

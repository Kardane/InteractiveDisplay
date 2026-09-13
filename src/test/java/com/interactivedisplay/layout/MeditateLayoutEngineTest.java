package com.interactivedisplay.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.layout.LayoutComponent;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.layout.MeditateLayoutEngine;
import com.interactivedisplay.core.positioning.WindowOffset;
import com.interactivedisplay.core.window.WindowDefinition;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeditateLayoutEngineTest {
    @Test
    void absoluteLayoutShouldPreserveGivenPositions() {
        TextComponentDefinition a = text("a", 1f, 2f, 3f, 1f, 0.2f);
        WindowDefinition window = new WindowDefinition("main", new ComponentSize(3f, 2f), WindowOffset.defaults(), LayoutMode.ABSOLUTE, List.of(a));

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);
        assertEquals(1f, out.get(0).localPosition().x());
        assertEquals(2f, out.get(0).localPosition().y());
        assertEquals(3f, out.get(0).localPosition().z());
    }

    @Test
    void verticalLayoutShouldAdvanceByHeightAndGap() {
        TextComponentDefinition a = text("a", 0.1f, 0.2f, 0.0f, 1f, 0.2f);
        TextComponentDefinition b = text("b", -0.1f, 0.3f, 0.0f, 1f, 0.4f);
        WindowDefinition window = new WindowDefinition("vertical", new ComponentSize(3f, 2f), WindowOffset.defaults(), LayoutMode.VERTICAL, List.of(a, b));

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(0.1f, out.get(0).localPosition().x(), 0.0001f);
        assertEquals(0.2f, out.get(0).localPosition().y(), 0.0001f);
        assertEquals(-0.1f, out.get(1).localPosition().x(), 0.0001f);
        assertEquals(0.55f, out.get(1).localPosition().y(), 0.0001f);
    }

    @Test
    void panelChildrenShouldBeFlattenedWithPaddingAndHorizontalLayout() {
        TextComponentDefinition childA = text("a", 0f, 0f, 0f, 0.5f, 0.2f);
        TextComponentDefinition childB = text("b", 0f, 0f, 0f, 0.5f, 0.2f);
        PanelComponentDefinition panel = new PanelComponentDefinition("panel", new ComponentPosition(1f, 1f, 0f), new ComponentSize(2f, 1f), true, 1f, "#22000000", 0.2f, LayoutMode.HORIZONTAL, List.of(childA, childB));
        WindowDefinition window = new WindowDefinition("main", new ComponentSize(4f, 3f), WindowOffset.defaults(), LayoutMode.ABSOLUTE, List.of(panel));

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(3, out.size());
        assertEquals("panel", out.get(0).definition().id());
        assertEquals(2.0f, out.get(0).definition().size().width(), 0.0001f);
        assertEquals(1.0f, out.get(0).definition().size().height(), 0.0001f);
        assertEquals(1.2f, out.get(1).localPosition().x(), 0.0001f);
        assertEquals(1.2f, out.get(1).localPosition().y(), 0.0001f);
        assertEquals(1.75f, out.get(2).localPosition().x(), 0.0001f);
    }

    @Test
    void nestedPanelsShouldAccumulateParentPositionsPaddingAndDepth() {
        TextComponentDefinition leaf = text("leaf", 0.2f, 0.3f, 0.04f, 0.4f, 0.2f);
        PanelComponentDefinition inner = new PanelComponentDefinition(
                "inner",
                new ComponentPosition(0.5f, 0.25f, 0.02f),
                new ComponentSize(1.0f, 0.8f),
                true,
                1f,
                "#11000000",
                0.1f,
                LayoutMode.ABSOLUTE,
                List.of(leaf)
        );
        PanelComponentDefinition outer = new PanelComponentDefinition(
                "outer",
                new ComponentPosition(1.0f, 2.0f, 0.1f),
                new ComponentSize(3.0f, 2.0f),
                true,
                1f,
                "#22000000",
                0.2f,
                LayoutMode.ABSOLUTE,
                List.of(inner)
        );
        WindowDefinition window = new WindowDefinition(
                "nested",
                new ComponentSize(5f, 4f),
                WindowOffset.defaults(),
                LayoutMode.ABSOLUTE,
                List.of(outer)
        );

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(3, out.size());
        assertEquals("outer", out.get(0).definition().id());
        assertEquals("inner", out.get(1).definition().id());
        assertEquals("leaf", out.get(2).definition().id());
        assertEquals(1.7f, out.get(1).localPosition().x(), 0.0001f);
        assertEquals(2.45f, out.get(1).localPosition().y(), 0.0001f);
        assertEquals(0.13f, out.get(1).localPosition().z(), 0.0001f);
        assertEquals(2.0f, out.get(2).localPosition().x(), 0.0001f);
        assertEquals(2.85f, out.get(2).localPosition().y(), 0.0001f);
        assertEquals(0.18f, out.get(2).localPosition().z(), 0.0001f);
    }

    @Test
    void bundledMainMenu2ShouldKeepExpectedBackgroundPanelSize() throws Exception {
        try (InputStream input = MeditateLayoutEngineTest.class.getResourceAsStream(
                "/defaults/interactivedisplay/windows/main_menu2.yaml")) {
            assertNotNull(input, "bundled main_menu2.yaml missing from test classpath");
            JsonNode root = new ObjectMapper(new YAMLFactory()).readTree(input);
            JsonNode background = null;
            for (JsonNode component : root.path("components")) {
                if ("background".equals(component.path("id").asText())) {
                    background = component;
                    break;
                }
            }
            assertNotNull(background, "main_menu2 background panel missing");
            assertEquals("panel", background.path("type").asText());
            assertEquals(7.0f, background.path("size").path("width").floatValue(), 0.0001f);
            assertEquals(4.0f, background.path("size").path("height").floatValue(), 0.0001f);
        }
    }

    @Test
    void verySmallAndLargeComponentSizesShouldRemainFiniteAndDeterministic() {
        TextComponentDefinition tiny = text("tiny", 0f, 0f, 0f, 0.0001f, 0.0001f);
        TextComponentDefinition huge = text("huge", 0f, 0f, 0f, 10000f, 5000f);
        WindowDefinition window = new WindowDefinition("sizes", new ComponentSize(20000f, 10000f), WindowOffset.defaults(), LayoutMode.VERTICAL, List.of(tiny, huge));

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(2, out.size());
        assertEquals(0.0f, out.get(0).localPosition().y(), 0.0001f);
        assertEquals(0.0501f, out.get(1).localPosition().y(), 0.0001f);
    }

    private static TextComponentDefinition text(String id, float x, float y, float z, float width, float height) {
        return new TextComponentDefinition(
                id,
                new ComponentPosition(x, y, z),
                new ComponentSize(width, height),
                true,
                1f,
                id,
                1f,
                "#fff",
                "left",
                100,
                true,
                "#00000000"
        );
    }
}

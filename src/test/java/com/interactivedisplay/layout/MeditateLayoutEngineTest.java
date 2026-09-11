package com.interactivedisplay.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.layout.LayoutComponent;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.layout.MeditateLayoutEngine;
import com.interactivedisplay.core.positioning.WindowOffset;
import com.interactivedisplay.core.window.WindowDefinition;
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
        assertEquals(1.2f, out.get(1).localPosition().x(), 0.0001f);
        assertEquals(1.2f, out.get(1).localPosition().y(), 0.0001f);
        assertEquals(1.75f, out.get(2).localPosition().x(), 0.0001f);
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

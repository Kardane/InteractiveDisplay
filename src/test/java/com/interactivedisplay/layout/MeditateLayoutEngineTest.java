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
        TextComponentDefinition a = new TextComponentDefinition("a", new ComponentPosition(1f, 2f, 3f), new ComponentSize(1f, 0.2f), true, 1f, "A", 1f, "#fff", "left", 100, true, "#00000000");
        WindowDefinition window = new WindowDefinition("main", new ComponentSize(3f, 2f), WindowOffset.defaults(), LayoutMode.ABSOLUTE, List.of(a));

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);
        assertEquals(1f, out.get(0).localPosition().x());
        assertEquals(2f, out.get(0).localPosition().y());
        assertEquals(3f, out.get(0).localPosition().z());
    }

    @Test
    void panelChildrenShouldBeFlattenedWithPaddingAndHorizontalLayout() {
        TextComponentDefinition childA = new TextComponentDefinition("a", new ComponentPosition(0f, 0f, 0f), new ComponentSize(0.5f, 0.2f), true, 1f, "A", 1f, "#fff", "left", 100, true, "#00000000");
        TextComponentDefinition childB = new TextComponentDefinition("b", new ComponentPosition(0f, 0f, 0f), new ComponentSize(0.5f, 0.2f), true, 1f, "B", 1f, "#fff", "left", 100, true, "#00000000");
        PanelComponentDefinition panel = new PanelComponentDefinition("panel", new ComponentPosition(1f, 1f, 0f), new ComponentSize(2f, 1f), true, 1f, "#22000000", 0.2f, LayoutMode.HORIZONTAL, List.of(childA, childB));
        WindowDefinition window = new WindowDefinition("main", new ComponentSize(4f, 3f), WindowOffset.defaults(), LayoutMode.ABSOLUTE, List.of(panel));

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(3, out.size());
        assertEquals("panel", out.get(0).definition().id());
        assertEquals(1.2f, out.get(1).localPosition().x(), 0.0001f);
        assertEquals(1.2f, out.get(1).localPosition().y(), 0.0001f);
        assertEquals(1.75f, out.get(2).localPosition().x(), 0.0001f);
    }
}

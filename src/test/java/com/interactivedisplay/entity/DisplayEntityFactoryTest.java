package com.interactivedisplay.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.layout.LayoutMode;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class DisplayEntityFactoryTest {
    @Test
    void buildDisplayDataShouldIncludeInterpolationKeys() {
        String snbt = DisplayEntityFactory.buildDisplayDataSnbt("fixed", 1.0f);

        assertTrue(snbt.contains("start_interpolation:0"));
        assertTrue(snbt.contains("interpolation_duration:3"));
        assertTrue(snbt.contains("teleport_duration:3"));
    }

    @Test
    void vectorScaleDisplayDataShouldContainDistinctAxes() {
        String snbt = DisplayEntityFactory.buildDisplayDataSnbt("fixed", new Vector3f(0.5f, 1.25f, 0.001f), new Vector3f());

        assertTrue(snbt.contains("scale:[0.5f,1.25f,0.001f]"));
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
        assertEquals(0.0f, spec.textOpacity());
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

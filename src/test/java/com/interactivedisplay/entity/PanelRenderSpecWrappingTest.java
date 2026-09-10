package com.interactivedisplay.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.layout.LayoutMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PanelRenderSpecWrappingTest {
    private static final int SPACE_ADVANCE_PIXELS = 4;

    @Test
    void whitespaceRowsShouldFitLineWidthExactlyForOddAndEvenColumnCounts() {
        for (PanelComponentDefinition panel : List.of(
                panel(1.0f, 1.0f),
                panel(2.0f, 1.0f),
                panel(3.0f, 1.0f),
                panel(7.0f, 4.0f)
        )) {
            DisplayEntityFactory.PanelRenderSpec spec = DisplayEntityFactory.buildPanelRenderSpec(panel);
            String[] rows = spec.text().getString().split("\\n", -1);

            for (String row : rows) {
                assertEquals(spec.lineWidth(), row.length() * SPACE_ADVANCE_PIXELS);
                assertFalse(row.contains("█"));
            }
        }
    }

    @Test
    void sevenByFourPanelShouldRoundLineWidthUpInsteadOfWrapping() {
        DisplayEntityFactory.PanelRenderSpec spec = DisplayEntityFactory.buildPanelRenderSpec(panel(7.0f, 4.0f));
        String firstRow = spec.text().getString().split("\\n", -1)[0];

        assertEquals(71, firstRow.length());
        assertEquals(284, spec.lineWidth());
        assertEquals(spec.lineWidth(), firstRow.length() * SPACE_ADVANCE_PIXELS);
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

package com.interactivedisplay.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ButtonBoxModel;
import com.interactivedisplay.core.component.ButtonHorizontalAlignment;
import com.interactivedisplay.core.component.ButtonPadding;
import com.interactivedisplay.core.component.ButtonSizeMode;
import com.interactivedisplay.core.component.ButtonSizing;
import com.interactivedisplay.core.component.ButtonVerticalAlignment;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentAnchor;
import com.interactivedisplay.core.component.ComponentMargin;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.ComponentSizeMode;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.layout.LayoutComponent;
import com.interactivedisplay.core.layout.ItemAlignment;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.layout.LayoutOptions;
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
    void verticalLayoutShouldUseCustomGap() {
        TextComponentDefinition a = text("a", 0f, 0f, 0f, 1f, 0.2f);
        TextComponentDefinition b = text("b", 0f, 0f, 0f, 1f, 0.4f);
        WindowDefinition window = new WindowDefinition(
                "vertical-gap",
                new ComponentSize(3f, 2f),
                WindowOffset.defaults(),
                new LayoutOptions(LayoutMode.VERTICAL, 0.12f, 1, 0.05f, 0.05f),
                List.of(a, b),
                null
        );

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(0.32f, out.get(1).localPosition().y(), 0.0001f);
    }

    @Test
    void horizontalLayoutShouldUseCustomGap() {
        TextComponentDefinition a = text("a", 0f, 0f, 0f, 0.5f, 0.2f);
        TextComponentDefinition b = text("b", 0f, 0f, 0f, 0.4f, 0.2f);
        WindowDefinition window = new WindowDefinition(
                "horizontal-gap",
                new ComponentSize(3f, 2f),
                WindowOffset.defaults(),
                new LayoutOptions(LayoutMode.HORIZONTAL, 0.2f, 1, 0.05f, 0.05f),
                List.of(a, b),
                null
        );

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(0.7f, out.get(1).localPosition().x(), 0.0001f);
    }

    @Test
    void flowLayoutShouldAdvanceByResolvedContentSizedButtonBox() {
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "button",
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
        TextComponentDefinition next = text("next", 0.0f, 0.0f, 0.0f, 1.0f, 0.2f);
        WindowDefinition window = new WindowDefinition(
                "vertical-content-button",
                new ComponentSize(3.0f, 2.0f),
                WindowOffset.defaults(),
                LayoutMode.VERTICAL,
                List.of(button, next)
        );

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(0.45f, out.get(1).localPosition().y(), 0.0001f);
    }

    @Test
    void gridShouldSizeTracksFromChildrenAndPlaceOddChildOnNextRow() {
        TextComponentDefinition a = text("a", 0f, 0f, 0f, 1.0f, 0.2f);
        TextComponentDefinition b = text("b", 0f, 0f, 0f, 0.6f, 0.4f);
        TextComponentDefinition c = text("c", 0f, 0f, 0f, 0.8f, 0.3f);
        TextComponentDefinition d = text("d", 0f, 0f, 0f, 0.5f, 0.1f);
        TextComponentDefinition e = text("e", 0f, 0f, 0f, 0.4f, 0.25f);
        WindowDefinition window = new WindowDefinition(
                "grid-tracks",
                new ComponentSize(4f, 3f),
                WindowOffset.defaults(),
                new LayoutOptions(LayoutMode.GRID, 0.05f, 2, 0.15f, 0.25f),
                List.of(a, b, c, d, e),
                null
        );

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(0.0f, out.get(0).localPosition().x(), 0.0001f);
        assertEquals(1.25f, out.get(1).localPosition().x(), 0.0001f);
        assertEquals(0.55f, out.get(2).localPosition().y(), 0.0001f);
        assertEquals(1.25f, out.get(3).localPosition().x(), 0.0001f);
        assertEquals(0.55f, out.get(3).localPosition().y(), 0.0001f);
        assertEquals(1.0f, out.get(4).localPosition().y(), 0.0001f);
    }

    @Test
    void threeColumnGridShouldPlaceChildrenRowMajorUsingPerTrackSizes() {
        TextComponentDefinition a = text("a", 0f, 0f, 0f, 0.5f, 0.2f);
        TextComponentDefinition b = text("b", 0f, 0f, 0f, 1.1f, 0.4f);
        TextComponentDefinition c = text("c", 0f, 0f, 0f, 0.8f, 0.3f);
        TextComponentDefinition d = text("d", 0f, 0f, 0f, 0.7f, 0.6f);
        TextComponentDefinition e = text("e", 0f, 0f, 0f, 0.9f, 0.25f);
        WindowDefinition window = new WindowDefinition(
                "grid-three-columns",
                new ComponentSize(4f, 3f),
                WindowOffset.defaults(),
                new LayoutOptions(LayoutMode.GRID, 0.05f, 3, 0.15f, 0.2f),
                List.of(a, b, c, d, e),
                null
        );

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(0.0f, out.get(0).localPosition().x(), 0.0001f);
        assertEquals(0.9f, out.get(1).localPosition().x(), 0.0001f);
        assertEquals(2.2f, out.get(2).localPosition().x(), 0.0001f);
        assertEquals(0.55f, out.get(3).localPosition().y(), 0.0001f);
        assertEquals(0.9f, out.get(4).localPosition().x(), 0.0001f);
        assertEquals(0.55f, out.get(4).localPosition().y(), 0.0001f);
    }

    @Test
    void gridShouldUseResolvedContentSizedButtonDimensions() {
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "content",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(0.3f, 0.1f),
                true,
                1.0f,
                "Long\nlabel",
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
                new ButtonSizing(ButtonSizeMode.CONTENT, ButtonSizeMode.CONTENT)
        );
        TextComponentDefinition sameRow = text("same-row", 0f, 0f, 0f, 0.2f, 0.15f);
        TextComponentDefinition nextRow = text("next-row", 0f, 0f, 0f, 0.4f, 0.2f);
        WindowDefinition window = new WindowDefinition(
                "content-grid",
                new ComponentSize(4f, 3f),
                WindowOffset.defaults(),
                new LayoutOptions(LayoutMode.GRID, 0.05f, 2, 0.2f, 0.3f),
                List.of(button, sameRow, nextRow),
                null
        );

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);
        var resolved = ButtonBoxModel.resolve(button);

        assertEquals(resolved.width() + 0.3f, out.get(1).localPosition().x(), 0.0001f);
        assertEquals(resolved.height() + 0.2f, out.get(2).localPosition().y(), 0.0001f);
    }

    @Test
    void gridShouldAlignItemsAtStartCenterAndEndWithinEachCell() {
        List<TextComponentDefinition> children = List.of(
                text("a", 0f, 0f, 0f, 0.6f, 0.2f),
                text("a_track", 0f, 0f, 0f, 2.2f, 0.6f),
                text("b", 0f, 0f, 0f, 1.4f, 0.4f),
                text("b_track", 0f, 0f, 0f, 2.2f, 0.6f),
                text("c", 0f, 0f, 0f, 2.2f, 0.6f),
                text("c_track", 0f, 0f, 0f, 2.2f, 0.6f)
        );
        float[][] expectedXOffsets = {
                {0.0f, 0.0f, 0.0f},
                {0.8f, 0.4f, 0.0f},
                {1.6f, 0.8f, 0.0f}
        };
        float[][] expectedYOffsets = {
                {0.0f, 0.0f, 0.0f},
                {0.2f, 0.1f, 0.0f},
                {0.4f, 0.2f, 0.0f}
        };
        ItemAlignment[] alignments = ItemAlignment.values();

        for (int alignmentIndex = 0; alignmentIndex < alignments.length; alignmentIndex++) {
            ItemAlignment alignment = alignments[alignmentIndex];
            for (int rowAlignmentIndex = 0; rowAlignmentIndex < alignments.length; rowAlignmentIndex++) {
                ItemAlignment rowAlignment = alignments[rowAlignmentIndex];
                WindowDefinition window = new WindowDefinition(
                        "grid-align-" + alignment + "-" + rowAlignment,
                        new ComponentSize(5f, 3f),
                        WindowOffset.defaults(),
                        new LayoutOptions(
                                LayoutMode.GRID,
                                0.05f,
                                2,
                                0.05f,
                                0.2f,
                                alignment,
                                rowAlignment
                        ),
                        List.copyOf(children),
                        null
                );
                List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);
                float rowOneStart = 0.6f + 0.05f;
                float rowTwoStart = 0.6f + 0.05f + 0.6f + 0.05f;

                assertEquals(expectedXOffsets[alignmentIndex][0], out.get(0).localPosition().x(), 0.0001f);
                assertEquals(expectedYOffsets[rowAlignmentIndex][0], out.get(0).localPosition().y(), 0.0001f);
                assertEquals(expectedXOffsets[alignmentIndex][1], out.get(2).localPosition().x(), 0.0001f);
                assertEquals(rowOneStart + expectedYOffsets[rowAlignmentIndex][1],
                        out.get(2).localPosition().y(), 0.0001f);
                assertEquals(expectedXOffsets[alignmentIndex][2], out.get(4).localPosition().x(), 0.0001f);
                assertEquals(rowTwoStart, out.get(4).localPosition().y(), 0.0001f);
            }
        }
    }

    @Test
    void nestedPanelGridShouldRespectParentPositionPaddingAndTrackGaps() {
        TextComponentDefinition a = text("a", 0f, 0f, 0f, 0.5f, 0.2f);
        TextComponentDefinition b = text("b", 0f, 0f, 0f, 0.8f, 0.3f);
        TextComponentDefinition c = text("c", 0f, 0f, 0f, 0.4f, 0.2f);
        PanelComponentDefinition panel = new PanelComponentDefinition(
                "grid-panel",
                new ComponentPosition(1f, 2f, 0f),
                new ComponentSize(3f, 2f),
                true,
                1f,
                "#00000000",
                0.2f,
                new LayoutOptions(LayoutMode.GRID, 0.05f, 2, 0.1f, 0.3f),
                List.of(a, b, c)
        );
        WindowDefinition window = new WindowDefinition(
                "nested-grid",
                new ComponentSize(5f, 4f),
                WindowOffset.defaults(),
                LayoutMode.ABSOLUTE,
                List.of(panel)
        );

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(4, out.size());
        assertEquals(1.2f, out.get(1).localPosition().x(), 0.0001f);
        assertEquals(2.2f, out.get(1).localPosition().y(), 0.0001f);
        assertEquals(2.0f, out.get(2).localPosition().x(), 0.0001f);
        assertEquals(2.6f, out.get(3).localPosition().y(), 0.0001f);
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
    void topRightAnchorShouldUseWindowBoundsAndMargin() {
        TextComponentDefinition close = new TextComponentDefinition(
                "close",
                new ComponentPosition(
                        0.0f,
                        0.0f,
                        0.02f,
                        ComponentAnchor.TOP_RIGHT,
                        new ComponentMargin(0.1f, 0.2f, 0.0f, 0.0f)
                ),
                new ComponentSize(0.4f, 0.3f),
                true,
                1.0f,
                "x",
                1.0f,
                "#fff",
                "center",
                100,
                false,
                "#00000000"
        );
        WindowDefinition window = new WindowDefinition(
                "anchored",
                new ComponentSize(4.0f, 2.0f),
                WindowOffset.defaults(),
                LayoutMode.ABSOLUTE,
                List.of(close)
        );

        LayoutComponent placed = new MeditateLayoutEngine().calculate(window).getFirst();

        assertEquals(1.6f, placed.localPosition().x(), 0.0001f);
        assertEquals(0.6f, placed.localPosition().y(), 0.0001f);
        assertEquals(0.02f, placed.localPosition().z(), 0.0001f);
    }

    @Test
    void fillWidthShouldUseWindowContentWidthAndRespectMinMax() {
        ComponentSize fill = new ComponentSize(
                1.0f,
                0.4f,
                ComponentSizeMode.FILL,
                ComponentSizeMode.FIXED,
                1.0f,
                3.0f,
                0.0f,
                Float.POSITIVE_INFINITY
        );
        TextComponentDefinition child = new TextComponentDefinition(
                "fill",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                fill,
                true,
                1.0f,
                "fill",
                1.0f,
                "#fff",
                "left",
                100,
                false,
                "#00000000"
        );
        WindowDefinition window = new WindowDefinition(
                "fill-window",
                new ComponentSize(4.0f, 2.0f),
                WindowOffset.defaults(),
                LayoutMode.ABSOLUTE,
                List.of(child)
        );

        LayoutComponent placed = new MeditateLayoutEngine().calculate(window).getFirst();

        assertEquals(3.0f, placed.definition().size().width(), 0.0001f);
        assertEquals(0.0f, placed.localPosition().x(), 0.0001f);
    }

    @Test
    void panelChildAnchorShouldUsePaddedContentBounds() {
        TextComponentDefinition child = new TextComponentDefinition(
                "child",
                new ComponentPosition(
                        0.0f,
                        0.0f,
                        0.0f,
                        ComponentAnchor.TOP_RIGHT,
                        ComponentMargin.zero()
                ),
                new ComponentSize(0.4f, 0.2f),
                true,
                1.0f,
                "child",
                1.0f,
                "#fff",
                "left",
                100,
                false,
                "#00000000"
        );
        PanelComponentDefinition panel = new PanelComponentDefinition(
                "panel",
                new ComponentPosition(0.0f, -1.0f, 0.0f),
                new ComponentSize(4.0f, 2.0f),
                true,
                1.0f,
                "#22000000",
                0.2f,
                LayoutMode.ABSOLUTE,
                List.of(child)
        );
        WindowDefinition window = new WindowDefinition(
                "panel-content",
                new ComponentSize(5.0f, 3.0f),
                WindowOffset.defaults(),
                LayoutMode.ABSOLUTE,
                List.of(panel)
        );

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);
        LayoutComponent placed = out.get(1);

        assertEquals(1.6f, placed.localPosition().x(), 0.0001f);
        assertEquals(0.6f, placed.localPosition().y(), 0.0001f);
        assertEquals(0.01f, placed.localPosition().z(), 0.0001f);
    }

    @Test
    void fillPanelShouldResolveBeforeLayingOutAnchoredChildren() {
        ComponentSize panelSize = new ComponentSize(
                1.0f,
                1.0f,
                ComponentSizeMode.FILL,
                ComponentSizeMode.FILL,
                0.0f,
                Float.POSITIVE_INFINITY,
                0.0f,
                Float.POSITIVE_INFINITY
        );
        TextComponentDefinition child = new TextComponentDefinition(
                "center",
                new ComponentPosition(0.0f, 0.0f, 0.0f, ComponentAnchor.CENTER, ComponentMargin.zero()),
                new ComponentSize(0.5f, 0.5f),
                true,
                1.0f,
                "center",
                1.0f,
                "#fff",
                "center",
                100,
                false,
                "#00000000"
        );
        PanelComponentDefinition panel = new PanelComponentDefinition(
                "fill-panel",
                new ComponentPosition(0.0f, 0.0f, 0.0f, ComponentAnchor.CENTER, ComponentMargin.zero()),
                panelSize,
                true,
                1.0f,
                "#22000000",
                0.25f,
                LayoutMode.ABSOLUTE,
                List.of(child)
        );
        WindowDefinition window = new WindowDefinition(
                "fill-parent",
                new ComponentSize(4.0f, 3.0f),
                WindowOffset.defaults(),
                LayoutMode.ABSOLUTE,
                List.of(panel)
        );

        List<LayoutComponent> out = new MeditateLayoutEngine().calculate(window);

        assertEquals(4.0f, out.get(0).definition().size().width(), 0.0001f);
        assertEquals(3.0f, out.get(0).definition().size().height(), 0.0001f);
        assertEquals(-1.5f, out.get(0).localPosition().y(), 0.0001f);
        assertEquals(0.0f, out.get(1).localPosition().x(), 0.0001f);
        assertEquals(-0.25f, out.get(1).localPosition().y(), 0.0001f);
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

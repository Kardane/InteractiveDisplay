package com.interactivedisplay.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ButtonHorizontalAlignment;
import com.interactivedisplay.core.component.ButtonPadding;
import com.interactivedisplay.core.component.ButtonVerticalAlignment;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.debug.DebugRecorder;
import com.mojang.math.Transformation;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
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
    void panelRenderSpecShouldMatchConfiguredWorldGeometry() {
        DisplayEntityFactory.PanelRenderSpec spec = DisplayEntityFactory.buildPanelRenderSpec(panel(4.0f, 2.0f));
        String[] rows = spec.text().getString().split("\\n", -1);

        assertEquals(2.0f, rows.length * 10.0f * 0.025f * spec.fontSize(), 0.0001f);
        assertTrue(spec.lineWidth() * 0.025f * spec.fontSize() >= 4.0f);
    }

    @Test
    void buttonBackgroundRenderSpecShouldMatchConfiguredWorldGeometry() {
        ButtonComponentDefinition button = button("Button", 2.2f, 0.45f, 0.5f);
        DisplayEntityFactory.ButtonBackgroundRenderSpec spec = DisplayEntityFactory.buildButtonBackgroundRenderSpec(button);
        String[] rows = spec.text().getString().split("\\n", -1);

        assertEquals(button.size().width(), (spec.lineWidth() + 1.0f) * 0.025f * spec.scale().x, 0.0001f);
        assertEquals(button.size().height(), rows.length * 10.0f * 0.025f * spec.scale().y, 0.0001f);
        assertEquals(0.0f, spec.textOpacity(), 0.0001f);
    }

    @Test
    void buttonLabelOffsetShouldCenterSingleLineTextInsideConfiguredHeight() {
        ButtonComponentDefinition button = button("Button", 2.2f, 0.45f, 0.5f);

        Vector3f offset = DisplayEntityFactory.buttonLabelLocalOffset(button);

        assertEquals(0.0f, offset.x, 0.0001f);
        assertEquals(0.1625f, offset.y, 0.0001f);
        assertEquals(0.001f, offset.z, 0.0001f);
    }

    @Test
    void buttonLineWidthShouldMatchConfiguredWorldWidthAfterTextScaling() {
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "test",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(0.9f, 0.5f),
                true,
                1.0f,
                "R1C2",
                0.45f,
                "#AA174A7E",
                "#EEFFE4A6",
                null,
                ClickType.BOTH,
                ComponentAction.closeWindow()
        );

        int lineWidth = DisplayEntityFactory.buttonLineWidth(button);

        assertEquals(80, lineWidth);
        assertEquals(button.size().width(), lineWidth * 0.025f * button.fontSize(), 0.0001f);
    }

    @Test
    void buttonPaddingShouldReduceLabelLineWidthWithoutChangingOuterBackground() {
        ButtonComponentDefinition button = button(
                "Button", 0.9f, 0.5f, 0.5f,
                new ButtonPadding(0.1f, 0.05f),
                ButtonHorizontalAlignment.LEFT,
                ButtonVerticalAlignment.BOTTOM
        );

        assertEquals(0.7f, DisplayEntityFactory.buttonContentWidth(button), 0.0001f);
        assertEquals(56, DisplayEntityFactory.buttonLineWidth(button));

        DisplayEntityFactory.ButtonBackgroundRenderSpec background =
                DisplayEntityFactory.buildButtonBackgroundRenderSpec(button);
        assertEquals(button.size().width(), (background.lineWidth() + 1.0f) * 0.025f * background.scale().x, 0.0001f);
    }

    @Test
    void buttonVerticalAlignmentShouldPositionLabelInsidePadding() {
        ButtonPadding padding = new ButtonPadding(0.1f, 0.05f);
        ButtonComponentDefinition bottom = button(
                "Button", 2.2f, 0.45f, 0.5f, padding,
                ButtonHorizontalAlignment.CENTER, ButtonVerticalAlignment.BOTTOM
        );
        ButtonComponentDefinition center = button(
                "Button", 2.2f, 0.45f, 0.5f, padding,
                ButtonHorizontalAlignment.CENTER, ButtonVerticalAlignment.CENTER
        );
        ButtonComponentDefinition top = button(
                "Button", 2.2f, 0.45f, 0.5f, padding,
                ButtonHorizontalAlignment.CENTER, ButtonVerticalAlignment.TOP
        );

        assertEquals(0.05f, DisplayEntityFactory.buttonLabelLocalOffset(bottom).y, 0.0001f);
        assertEquals(0.1625f, DisplayEntityFactory.buttonLabelLocalOffset(center).y, 0.0001f);
        assertEquals(0.275f, DisplayEntityFactory.buttonLabelLocalOffset(top).y, 0.0001f);
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

    @Test
    void malformedJsonLookingTextShouldRenderLiterally() {
        DisplayEntityFactory factory = new DisplayEntityFactory(new DebugRecorder(10), (player, text) -> text);

        assertEquals("[ 클릭해서 닉네임 입력 ]", factory.renderTextContent("[ 클릭해서 닉네임 입력 ]", "#FFFFFF", null).getString());
        assertEquals("{plain text}", factory.renderTextContent("{plain text}", "#FFFFFF", null).getString());
    }

    @Test
    void attachedRotationShouldMatchLogicalWindowBasis() {
        assertAttachedRotationMatchesBasis(PositionMode.PLAYER_VIEW, 0.0f, 30.0f);
        assertAttachedRotationMatchesBasis(PositionMode.PLAYER_VIEW, 90.0f, -25.0f);
        assertAttachedRotationMatchesBasis(PositionMode.PLAYER_FIXED, -135.0f, 45.0f);
        assertAttachedRotationMatchesBasis(PositionMode.PLAYER_VIEW, 45.0f, 90.0f);
        assertAttachedRotationMatchesBasis(PositionMode.PLAYER_VIEW, -70.0f, -90.0f);
    }

    private static void assertAttachedRotationMatchesBasis(PositionMode mode, float yaw, float pitch) {
        CoordinateTransformer.WindowBasis basis = new CoordinateTransformer().basis(mode, yaw, pitch);
        Quaternionf rotation = DisplayEntityFactory.attachedRotation(mode, yaw, pitch);

        assertVectorEquals(basis.right(), rotation.transform(new Vector3f(1.0f, 0.0f, 0.0f)));
        assertVectorEquals(basis.up(), rotation.transform(new Vector3f(0.0f, 1.0f, 0.0f)));
        assertVectorEquals(basis.normal(), rotation.transform(new Vector3f(0.0f, 0.0f, 1.0f)));
    }

    private static void assertVectorEquals(Vec3 expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 0.0001);
        assertEquals(expected.y, actual.y, 0.0001);
        assertEquals(expected.z, actual.z, 0.0001);
    }

    private static ButtonComponentDefinition button(String label, float width, float height, float fontSize) {
        return new ButtonComponentDefinition(
                "test",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(width, height),
                true,
                1.0f,
                label,
                fontSize,
                "#AA174A7E",
                "#EEFFE4A6",
                null,
                ClickType.BOTH,
                ComponentAction.closeWindow()
        );
    }

    private static ButtonComponentDefinition button(String label,
                                                            float width,
                                                            float height,
                                                            float fontSize,
                                                            ButtonPadding padding,
                                                            ButtonHorizontalAlignment horizontalAlignment,
                                                            ButtonVerticalAlignment verticalAlignment) {
        return new ButtonComponentDefinition(
                "test",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(width, height),
                true,
                1.0f,
                label,
                fontSize,
                "#AA174A7E",
                "#EEFFE4A6",
                null,
                ClickType.BOTH,
                ComponentAction.closeWindow(),
                1.0f,
                padding,
                horizontalAlignment,
                verticalAlignment
        );
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

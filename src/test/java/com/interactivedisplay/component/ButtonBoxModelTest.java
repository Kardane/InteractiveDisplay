package com.interactivedisplay.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.interactivedisplay.core.component.ButtonBoxModel;
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
import com.interactivedisplay.core.component.ResolvedButtonBox;
import org.junit.jupiter.api.Test;

class ButtonBoxModelTest {
    @Test
    void fixedSizingShouldPreserveConfiguredOuterBox() {
        ButtonComponentDefinition button = button(
                "OK",
                new ComponentSize(1.2f, 0.5f),
                new ButtonPadding(0.1f, 0.05f),
                ButtonSizing.fixed()
        );

        ResolvedButtonBox box = ButtonBoxModel.resolve(button);

        assertEquals(1.2f, box.width(), 0.0001f);
        assertEquals(0.5f, box.height(), 0.0001f);
        assertEquals(1.0f, box.contentWidth(), 0.0001f);
        assertEquals(0.4f, box.contentHeight(), 0.0001f);
    }

    @Test
    void contentSizingShouldMeasureLabelAndPaddingOnBothAxes() {
        ButtonComponentDefinition button = button(
                "OK",
                new ComponentSize(9.0f, 9.0f),
                new ButtonPadding(0.1f, 0.05f),
                new ButtonSizing(ButtonSizeMode.CONTENT, ButtonSizeMode.CONTENT)
        );

        ResolvedButtonBox box = ButtonBoxModel.resolve(button);

        assertEquals(0.324f, box.width(), 0.0001f);
        assertEquals(0.2f, box.height(), 0.0001f);
        assertEquals(0.124f, box.contentWidth(), 0.0001f);
        assertEquals(0.1f, box.labelHeight(), 0.0001f);
        assertEquals(1, box.lineCount());
    }

    @Test
    void fixedWidthAndContentHeightShouldGrowForWrappedText() {
        ButtonComponentDefinition button = button(
                "ABCDEFGH",
                new ComponentSize(0.3f, 0.1f),
                new ButtonPadding(0.05f, 0.05f),
                new ButtonSizing(ButtonSizeMode.FIXED, ButtonSizeMode.CONTENT)
        );

        ResolvedButtonBox box = ButtonBoxModel.resolve(button);

        assertEquals(0.3f, box.width(), 0.0001f);
        assertEquals(0.4f, box.height(), 0.0001f);
        assertEquals(3, box.lineCount());
        assertEquals(0.3f, box.labelHeight(), 0.0001f);
    }

    @Test
    void contentWidthShouldPreserveExplicitLineBreaksWithoutAutoWrapping() {
        ButtonComponentDefinition button = button(
                "A\nBC",
                new ComponentSize(0.1f, 0.1f),
                new ButtonPadding(0.1f, 0.05f),
                new ButtonSizing(ButtonSizeMode.CONTENT, ButtonSizeMode.CONTENT)
        );

        ResolvedButtonBox box = ButtonBoxModel.resolve(button);

        assertEquals(0.324f, box.width(), 0.0001f);
        assertEquals(0.3f, box.height(), 0.0001f);
        assertEquals(2, box.lineCount());
    }

    private static ButtonComponentDefinition button(String label,
                                                    ComponentSize size,
                                                    ButtonPadding padding,
                                                    ButtonSizing sizing) {
        return new ButtonComponentDefinition(
                "button",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                size,
                true,
                1.0f,
                label,
                0.4f,
                "#CC222222",
                "#EE444444",
                null,
                ClickType.BOTH,
                ComponentAction.closeWindow(),
                1.0f,
                padding,
                ButtonHorizontalAlignment.CENTER,
                ButtonVerticalAlignment.CENTER,
                sizing
        );
    }
}

package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.interactivedisplay.api.window.WindowSpec;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentActionType;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.window.WindowDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class WindowSpecAdapterTest {
    @Test
    void shouldAdaptNamespacedProgrammaticWindowToExistingRuntimeModel() {
        ResourceLocation callbackId = ResourceLocation.fromNamespaceAndPath("economy", "buy");
        WindowSpec spec = WindowSpec.builder(ResourceLocation.fromNamespaceAndPath("economy", "shop"))
                .size(3.0f, 1.5f)
                .offset(2.5f, 0.25f, 0.4f)
                .transition(6, WindowSpec.TransitionType.SCALE, WindowSpec.TransitionType.SLIDE_DOWN)
                .text("balance", text -> text
                        .position(0.0f, 0.35f, 0.01f)
                        .content("Balance: %economy:balance%")
                        .refreshInterval(10))
                .button("buy", button -> button
                        .position(0.0f, -0.35f, 0.01f)
                        .click(WindowSpec.Click.BOTH)
                        .action(WindowSpec.Actions.callback(callbackId)))
                .build();

        WindowDefinition definition = WindowSpecAdapter.toDefinition(spec);

        assertEquals("economy:shop", definition.id());
        assertEquals(3.0f, definition.size().width());
        assertEquals(2.5f, definition.offset().forward());
        assertEquals(6, definition.transition().duration());

        TextComponentDefinition text = assertInstanceOf(TextComponentDefinition.class, definition.components().get(0));
        assertEquals(10, text.refreshInterval());

        ButtonComponentDefinition button = assertInstanceOf(ButtonComponentDefinition.class, definition.components().get(1));
        assertEquals(ClickType.BOTH, button.clickType());
        assertEquals(ComponentActionType.CALLBACK, button.action().type());
        assertEquals("economy:buy", button.action().target());
    }
}

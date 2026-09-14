package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.interactivedisplay.api.window.WindowSpec;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentActionType;
import com.interactivedisplay.core.component.ImageComponentDefinition;
import com.interactivedisplay.core.component.ImageType;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.window.WindowDefinition;
import com.interactivedisplay.core.window.WindowTransitionType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class WindowSpecAdapterTest {
    @Test
    void shouldAdaptNamespacedProgrammaticWindowToExistingRuntimeModel() {
        ResourceLocation callbackId = ResourceLocation.fromNamespaceAndPath("economy", "buy");
        WindowSpec spec = WindowSpec.builder(ResourceLocation.fromNamespaceAndPath("economy", "shop"))
                .size(3.0f, 1.5f)
                .offset(2.5f, 0.25f, 0.4f)
                .layout(WindowSpec.Layout.VERTICAL)
                .transition(6, WindowSpec.TransitionType.SCALE, WindowSpec.TransitionType.SLIDE_DOWN)
                .text("balance", text -> text
                        .position(0.1f, 0.35f, 0.01f)
                        .size(2.2f, 0.4f)
                        .content("Balance: %economy:balance%")
                        .fontSize(0.45f)
                        .alignment("right")
                        .lineWidth(180)
                        .shadow(true)
                        .background("#22000000")
                        .opacity(0.8f)
                        .refreshInterval(10))
                .panel("frame", panel -> panel
                        .position(0.2f, -0.1f, 0.03f)
                        .size(2.8f, 1.2f)
                        .background("#99000000")
                        .padding(0.15f)
                        .layout(WindowSpec.Layout.HORIZONTAL)
                        .opacity(0.65f))
                .item("icon", ResourceLocation.withDefaultNamespace("diamond"), image -> image
                        .position(-0.8f, 0.0f, 0.02f)
                        .size(0.5f, 0.5f)
                        .scale(0.75f))
                .block("block", ResourceLocation.withDefaultNamespace("stone"), image -> image
                        .position(0.8f, 0.0f, 0.02f)
                        .size(0.6f, 0.6f)
                        .scale(0.8f))
                .button("buy", button -> button
                        .position(0.0f, -0.35f, 0.01f)
                        .size(1.2f, 0.35f)
                        .label("Buy")
                        .fontSize(0.3f)
                        .background("#CC111111", "#EE333333")
                        .clickSound("minecraft:ui.button.click")
                        .hoverScale(1.1f)
                        .click(WindowSpec.Click.BOTH)
                        .action(WindowSpec.Actions.callback(callbackId)))
                .build();

        WindowDefinition definition = WindowSpecAdapter.toDefinition(spec);

        assertEquals("economy:shop", definition.id());
        assertEquals(3.0f, definition.size().width());
        assertEquals(2.5f, definition.offset().forward());
        assertEquals(LayoutMode.VERTICAL, definition.layoutMode());
        assertEquals(6, definition.transition().duration());
        assertEquals(WindowTransitionType.SCALE, definition.transition().enter());
        assertEquals(WindowTransitionType.SLIDE_DOWN, definition.transition().exit());

        TextComponentDefinition text = assertInstanceOf(TextComponentDefinition.class, definition.components().get(0));
        assertEquals("Balance: %economy:balance%", text.content());
        assertEquals(0.1f, text.position().x());
        assertEquals(0.35f, text.position().y());
        assertEquals(0.01f, text.position().z());
        assertEquals(2.2f, text.size().width());
        assertEquals(0.4f, text.size().height());
        assertEquals(0.45f, text.fontSize());
        assertEquals("right", text.alignment());
        assertEquals(180, text.lineWidth());
        assertEquals(0.8f, text.opacity());
        assertEquals(10, text.refreshInterval());

        PanelComponentDefinition panel = assertInstanceOf(PanelComponentDefinition.class, definition.components().get(1));
        assertEquals(0.2f, panel.position().x());
        assertEquals(-0.1f, panel.position().y());
        assertEquals(2.8f, panel.size().width());
        assertEquals(1.2f, panel.size().height());
        assertEquals("#99000000", panel.backgroundColor());
        assertEquals(0.15f, panel.padding());
        assertEquals(LayoutMode.HORIZONTAL, panel.layoutMode());
        assertEquals(0.65f, panel.opacity());

        ImageComponentDefinition item = assertInstanceOf(ImageComponentDefinition.class, definition.components().get(2));
        assertEquals(ImageType.ITEM, item.imageType());
        assertEquals("minecraft:diamond", item.value());
        assertEquals(-0.8f, item.position().x());
        assertEquals(0.5f, item.size().width());
        assertEquals(0.75f, item.scale());

        ImageComponentDefinition block = assertInstanceOf(ImageComponentDefinition.class, definition.components().get(3));
        assertEquals(ImageType.BLOCK, block.imageType());
        assertEquals("minecraft:stone", block.value());
        assertEquals(0.8f, block.position().x());
        assertEquals(0.6f, block.size().width());
        assertEquals(0.8f, block.scale());

        ButtonComponentDefinition button = assertInstanceOf(ButtonComponentDefinition.class, definition.components().get(4));
        assertEquals("Buy", button.label());
        assertEquals(0.0f, button.position().x());
        assertEquals(-0.35f, button.position().y());
        assertEquals(1.2f, button.size().width());
        assertEquals(0.35f, button.size().height());
        assertEquals(ClickType.BOTH, button.clickType());
        assertEquals(ComponentActionType.CALLBACK, button.action().type());
        assertEquals("economy:buy", button.action().target());
        assertEquals(1.1f, button.hoverScale());
    }

    @Test
    void shouldAdaptAllBuiltInButtonActions() {
        ResourceLocation target = ResourceLocation.fromNamespaceAndPath("economy", "details");
        WindowSpec spec = WindowSpec.builder(ResourceLocation.fromNamespaceAndPath("economy", "actions"))
                .button("close", b -> b.action(WindowSpec.Actions.close()))
                .button("open", b -> b.action(WindowSpec.Actions.open(target)))
                .button("callback", b -> b.action(WindowSpec.Actions.callback(target)))
                .button("command", b -> b.action(WindowSpec.Actions.runCommand("say hello", 3)))
                .build();

        WindowDefinition definition = WindowSpecAdapter.toDefinition(spec);
        ButtonComponentDefinition close = (ButtonComponentDefinition) definition.components().get(0);
        ButtonComponentDefinition open = (ButtonComponentDefinition) definition.components().get(1);
        ButtonComponentDefinition callback = (ButtonComponentDefinition) definition.components().get(2);
        ButtonComponentDefinition command = (ButtonComponentDefinition) definition.components().get(3);

        assertEquals(ComponentActionType.CLOSE_WINDOW, close.action().type());
        assertEquals(ComponentActionType.OPEN_WINDOW, open.action().type());
        assertEquals("economy:details", open.action().target());
        assertEquals(ComponentActionType.CALLBACK, callback.action().type());
        assertEquals("economy:details", callback.action().target());
        assertEquals(ComponentActionType.RUN_COMMAND, command.action().type());
        assertEquals("say hello", command.action().target());
        assertEquals(3, command.action().permissionLevel());
    }

    @Test
    void shouldAdaptLeftRightAndBothClickModes() {
        WindowSpec spec = WindowSpec.builder(ResourceLocation.fromNamespaceAndPath("test", "clicks"))
                .button("left", b -> b.click(WindowSpec.Click.LEFT).action(WindowSpec.Actions.close()))
                .button("right", b -> b.click(WindowSpec.Click.RIGHT).action(WindowSpec.Actions.close()))
                .button("both", b -> b.click(WindowSpec.Click.BOTH).action(WindowSpec.Actions.close()))
                .build();

        WindowDefinition definition = WindowSpecAdapter.toDefinition(spec);
        assertEquals(ClickType.LEFT, ((ButtonComponentDefinition) definition.components().get(0)).clickType());
        assertEquals(ClickType.RIGHT, ((ButtonComponentDefinition) definition.components().get(1)).clickType());
        assertEquals(ClickType.BOTH, ((ButtonComponentDefinition) definition.components().get(2)).clickType());
    }

    @Test
    void shouldAdaptAllProgrammaticTransitionTypes() {
        assertTransition(WindowSpec.TransitionType.NONE, WindowTransitionType.NONE);
        assertTransition(WindowSpec.TransitionType.SCALE, WindowTransitionType.SCALE);
        assertTransition(WindowSpec.TransitionType.SLIDE_UP, WindowTransitionType.SLIDE_UP);
        assertTransition(WindowSpec.TransitionType.SLIDE_DOWN, WindowTransitionType.SLIDE_DOWN);
    }

    private static void assertTransition(WindowSpec.TransitionType publicType, WindowTransitionType internalType) {
        WindowSpec spec = WindowSpec.builder(ResourceLocation.fromNamespaceAndPath("test", "transition_" + publicType.name().toLowerCase()))
                .transition(5, publicType, publicType)
                .build();
        WindowDefinition definition = WindowSpecAdapter.toDefinition(spec);
        assertEquals(5, definition.transition().duration());
        assertEquals(internalType, definition.transition().enter());
        assertEquals(internalType, definition.transition().exit());
    }
}

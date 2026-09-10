package com.interactivedisplay.internal.api;

import com.interactivedisplay.api.window.WindowSpec;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.ImageComponentDefinition;
import com.interactivedisplay.core.component.ImageType;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.positioning.WindowOffset;
import com.interactivedisplay.core.window.WindowDefinition;
import com.interactivedisplay.core.window.WindowTransition;
import com.interactivedisplay.core.window.WindowTransitionType;
import java.util.ArrayList;
import java.util.List;

final class WindowSpecAdapter {
    private WindowSpecAdapter() {
    }

    static WindowDefinition toDefinition(WindowSpec spec) {
        List<ComponentDefinition> components = new ArrayList<>(spec.components().size());
        for (WindowSpec.ComponentSpec component : spec.components()) {
            components.add(toComponent(component));
        }
        return new WindowDefinition(
                PublicIdCodec.toInternalWindowId(spec.id()),
                new ComponentSize(spec.size().width(), spec.size().height()),
                new WindowOffset(spec.offset().forward(), spec.offset().horizontal(), spec.offset().vertical()),
                LayoutMode.valueOf(spec.layout().name()),
                List.copyOf(components),
                new WindowTransition(
                        spec.transition().duration(),
                        WindowTransitionType.valueOf(spec.transition().enter().name()),
                        WindowTransitionType.valueOf(spec.transition().exit().name())
                )
        );
    }

    private static ComponentDefinition toComponent(WindowSpec.ComponentSpec component) {
        ComponentPosition position = new ComponentPosition(
                component.position().x(),
                component.position().y(),
                component.position().z()
        );
        ComponentSize size = new ComponentSize(component.size().width(), component.size().height());

        if (component instanceof WindowSpec.TextSpec text) {
            return new TextComponentDefinition(
                    text.id(), position, size, text.visible(), text.opacity(), text.content(), text.fontSize(),
                    text.color(), text.alignment(), text.lineWidth(), text.shadow(), text.background(), text.refreshInterval()
            );
        }
        if (component instanceof WindowSpec.ButtonSpec button) {
            return new ButtonComponentDefinition(
                    button.id(), position, size, button.visible(), button.opacity(), button.label(), button.fontSize(),
                    button.backgroundColor(), button.hoverColor(), button.clickSound(), ClickType.valueOf(button.click().name()),
                    toAction(button.action()), button.hoverScale()
            );
        }
        if (component instanceof WindowSpec.PanelSpec panel) {
            return new PanelComponentDefinition(
                    panel.id(), position, size, panel.visible(), panel.opacity(), panel.backgroundColor(), panel.padding(),
                    LayoutMode.valueOf(panel.layout().name()), List.of()
            );
        }
        if (component instanceof WindowSpec.ImageSpec image) {
            ImageType type = image.kind() == WindowSpec.ImageKind.ITEM ? ImageType.ITEM : ImageType.BLOCK;
            return new ImageComponentDefinition(
                    image.id(), position, size, image.visible(), image.opacity(), type, image.value().toString(), image.scale(), null
            );
        }
        throw new IllegalArgumentException("unsupported public component: " + component.getClass().getName());
    }

    private static ComponentAction toAction(WindowSpec.ButtonAction action) {
        if (action instanceof WindowSpec.CloseAction) {
            return ComponentAction.closeWindow();
        }
        if (action instanceof WindowSpec.OpenAction open) {
            return ComponentAction.openWindow(PublicIdCodec.toInternalWindowId(open.target()));
        }
        if (action instanceof WindowSpec.CallbackAction callback) {
            return ComponentAction.callback(callback.callbackId().toString());
        }
        if (action instanceof WindowSpec.RunCommandAction command) {
            return ComponentAction.runCommand(command.command(), command.permissionLevel());
        }
        throw new IllegalArgumentException("unsupported public button action: " + action.getClass().getName());
    }
}

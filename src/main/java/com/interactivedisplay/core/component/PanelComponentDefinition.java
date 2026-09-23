package com.interactivedisplay.core.component;

import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.layout.LayoutOptions;
import java.util.List;

public record PanelComponentDefinition(
        String id,
        ComponentPosition position,
        ComponentSize size,
        boolean visible,
        float opacity,
        String backgroundColor,
        float padding,
        LayoutOptions layoutOptions,
        List<ComponentDefinition> children
) implements ComponentDefinition {
    public PanelComponentDefinition(
            String id,
            ComponentPosition position,
            ComponentSize size,
            boolean visible,
            float opacity,
            String backgroundColor,
            float padding,
            LayoutMode layoutMode,
            List<ComponentDefinition> children
    ) {
        this(id, position, size, visible, opacity, backgroundColor, padding,
                LayoutOptions.defaults(layoutMode), children);
    }

    public PanelComponentDefinition {
        layoutOptions = layoutOptions == null ? LayoutOptions.defaults(LayoutMode.ABSOLUTE) : layoutOptions;
        children = children == null ? List.of() : List.copyOf(children);
    }

    public LayoutMode layoutMode() {
        return layoutOptions.mode();
    }

    @Override
    public ComponentType type() {
        return ComponentType.PANEL;
    }
}

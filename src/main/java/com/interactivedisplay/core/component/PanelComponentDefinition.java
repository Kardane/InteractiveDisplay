package com.interactivedisplay.core.component;

import com.interactivedisplay.core.layout.LayoutMode;
import java.util.List;

public record PanelComponentDefinition(
        String id,
        ComponentPosition position,
        ComponentSize size,
        boolean visible,
        float opacity,
        String backgroundColor,
        float padding,
        LayoutMode layoutMode,
        List<ComponentDefinition> children
) implements ComponentDefinition {
    @Override
    public ComponentType type() {
        return ComponentType.PANEL;
    }
}

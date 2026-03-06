package com.interactivedisplay.core.component;

public record ButtonComponentDefinition(
        String id,
        ComponentPosition position,
        ComponentSize size,
        boolean visible,
        float opacity,
        String label,
        String hoverColor,
        ClickType clickType,
        ComponentAction action
) implements ComponentDefinition {
    public ButtonComponentDefinition {
        clickType = ClickType.RIGHT;
    }

    @Override
    public ComponentType type() {
        return ComponentType.BUTTON;
    }
}

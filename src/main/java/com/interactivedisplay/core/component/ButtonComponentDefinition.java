package com.interactivedisplay.core.component;

public record ButtonComponentDefinition(
        String id,
        ComponentPosition position,
        ComponentSize size,
        boolean visible,
        float opacity,
        String label,
        float fontSize,
        String backgroundColor,
        String hoverColor,
        String clickSound,
        ClickType clickType,
        ComponentAction action,
        float hoverScale
) implements ComponentDefinition {
    public ButtonComponentDefinition(
            String id,
            ComponentPosition position,
            ComponentSize size,
            boolean visible,
            float opacity,
            String label,
            float fontSize,
            String backgroundColor,
            String hoverColor,
            String clickSound,
            ClickType clickType,
            ComponentAction action
    ) {
        this(id, position, size, visible, opacity, label, fontSize, backgroundColor, hoverColor, clickSound, clickType, action, 1.0f);
    }

    public ButtonComponentDefinition {
        clickType = clickType == null ? ClickType.RIGHT : clickType;
        hoverScale = hoverScale > 0.0f ? hoverScale : 1.0f;
    }

    @Override
    public ComponentType type() {
        return ComponentType.BUTTON;
    }
}

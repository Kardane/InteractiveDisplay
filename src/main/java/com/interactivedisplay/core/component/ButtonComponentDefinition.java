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
        float hoverScale,
        ButtonPadding padding,
        ButtonHorizontalAlignment horizontalAlignment,
        ButtonVerticalAlignment verticalAlignment
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
            ComponentAction action,
            float hoverScale
    ) {
        this(
                id, position, size, visible, opacity, label, fontSize, backgroundColor, hoverColor, clickSound,
                clickType, action, hoverScale, ButtonPadding.zero(), ButtonHorizontalAlignment.CENTER,
                ButtonVerticalAlignment.CENTER
        );
    }

    public ButtonComponentDefinition {
        clickType = clickType == null ? ClickType.RIGHT : clickType;
        hoverScale = hoverScale > 0.0f ? hoverScale : 1.0f;
        padding = padding == null ? ButtonPadding.zero() : padding;
        horizontalAlignment = horizontalAlignment == null ? ButtonHorizontalAlignment.CENTER : horizontalAlignment;
        verticalAlignment = verticalAlignment == null ? ButtonVerticalAlignment.CENTER : verticalAlignment;
        if (size.width() <= padding.horizontal() * 2.0f) {
            throw new IllegalArgumentException("button horizontal padding leaves no content width");
        }
        if (size.height() <= padding.vertical() * 2.0f) {
            throw new IllegalArgumentException("button vertical padding leaves no content height");
        }
    }

    @Override
    public ComponentType type() {
        return ComponentType.BUTTON;
    }
}

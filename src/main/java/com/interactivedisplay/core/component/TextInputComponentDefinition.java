package com.interactivedisplay.core.component;

public record TextInputComponentDefinition(
        String id,
        ComponentPosition position,
        ComponentSize size,
        boolean visible,
        float opacity,
        String initialValue,
        String placeholder,
        int maxLength,
        float fontSize,
        String color,
        String backgroundColor,
        String hoverColor,
        String clickSound,
        ClickType clickType,
        String dialogTitle,
        String dialogLabel,
        String confirmLabel,
        String cancelLabel
) implements ComponentDefinition {
    public TextInputComponentDefinition {
        initialValue = initialValue == null ? "" : initialValue;
        placeholder = placeholder == null ? "" : placeholder;
        maxLength = maxLength > 0 ? maxLength : 64;
        fontSize = fontSize > 0.0f ? fontSize : 0.4f;
        color = color == null ? "#FFFFFF" : color;
        backgroundColor = backgroundColor == null ? "#CC222222" : backgroundColor;
        hoverColor = hoverColor == null ? "#EE444444" : hoverColor;
        clickType = clickType == null ? ClickType.RIGHT : clickType;
        dialogTitle = dialogTitle == null || dialogTitle.isBlank() ? "Text Input" : dialogTitle;
        dialogLabel = dialogLabel == null || dialogLabel.isBlank()
                ? (placeholder.isBlank() ? "Value" : placeholder)
                : dialogLabel;
        confirmLabel = confirmLabel == null || confirmLabel.isBlank() ? "Done" : confirmLabel;
        cancelLabel = cancelLabel == null || cancelLabel.isBlank() ? "Cancel" : cancelLabel;
        opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        if (initialValue.length() > maxLength) {
            initialValue = initialValue.substring(0, maxLength);
        }
    }

    @Override
    public ComponentType type() {
        return ComponentType.TEXT_INPUT;
    }
}

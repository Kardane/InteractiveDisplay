package com.interactivedisplay.core.component;

public record ButtonSizing(ButtonSizeMode width, ButtonSizeMode height) {
    public ButtonSizing {
        width = width == null ? ButtonSizeMode.FIXED : width;
        height = height == null ? ButtonSizeMode.FIXED : height;
    }

    public static ButtonSizing fixed() {
        return new ButtonSizing(ButtonSizeMode.FIXED, ButtonSizeMode.FIXED);
    }
}

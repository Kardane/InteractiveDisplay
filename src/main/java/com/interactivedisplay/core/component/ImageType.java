package com.interactivedisplay.core.component;

public enum ImageType {
    ITEM,
    BLOCK,
    MAP;

    public static ImageType fromString(String value) {
        return ImageType.valueOf(value.toUpperCase());
    }
}

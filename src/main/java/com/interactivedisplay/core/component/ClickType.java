package com.interactivedisplay.core.component;

public enum ClickType {
    LEFT,
    RIGHT,
    BOTH;

    public boolean allows(boolean attack) {
        if (this == BOTH) {
            return true;
        }
        return attack ? this == LEFT : this == RIGHT;
    }
}

package com.interactivedisplay.debug;

public enum DebugLevel {
    DEBUG,
    WARN,
    ERROR;

    public boolean isFailure() {
        return this != DEBUG;
    }
}

package com.interactivedisplay.api;

@FunctionalInterface
public interface InteractiveDisplayEntrypoint {
    void register(InteractiveDisplayRegistrar registrar);
}

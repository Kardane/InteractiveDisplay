package com.interactivedisplay.api;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.interactivedisplay.api.action.ActionApi;
import com.interactivedisplay.api.callback.CallbackApi;
import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.api.window.WindowApi;
import com.interactivedisplay.api.window.WindowOpenOptions;
import com.interactivedisplay.api.window.WindowSpec;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicApiBoundaryTest {
    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            "com.interactivedisplay.core",
            "com.interactivedisplay.entity",
            "com.interactivedisplay.schema",
            "com.interactivedisplay.polymer",
            "com.interactivedisplay.internal"
    );

    @Test
    void publicApiSignaturesShouldNotExposeImplementationPackages() {
        for (Class<?> apiType : List.of(
                InteractiveDisplayApi.class,
                InteractiveDisplayRegistrar.class,
                InteractiveDisplayEntrypoint.class,
                ActionApi.class,
                CallbackApi.class,
                EventApi.class,
                WindowApi.class,
                WindowOpenOptions.class,
                WindowSpec.class
        )) {
            for (Method method : apiType.getDeclaredMethods()) {
                String signature = method.toGenericString();
                for (String forbidden : FORBIDDEN_PACKAGES) {
                    assertFalse(signature.contains(forbidden), () -> apiType.getName() + " leaks " + forbidden + ": " + signature);
                }
            }
        }
    }
}

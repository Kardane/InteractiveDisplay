package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicActionDispatcherFailureTest {
    @Test
    void invalidActionIdShouldFailWithoutInvokingRuntimeContext() {
        PublicActionDispatcher.ExecutionResult result = PublicActionDispatcher.execute(
                null,
                "main_menu",
                "button",
                "not_namespaced",
                Map.of()
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("invalid custom action id"));
    }

    @Test
    void unknownNamespacedActionShouldFailCleanly() {
        PublicActionDispatcher.ExecutionResult result = PublicActionDispatcher.execute(
                null,
                "main_menu",
                "button",
                "qa_missing:action",
                Map.of("key", "value")
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("unregistered custom action"));
    }
}

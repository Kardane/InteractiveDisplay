package com.interactivedisplay.core.window;

import com.interactivedisplay.debug.DebugReason;

public record ActionExecutionResult(boolean success, DebugReason reasonCode, String message) {
    public static ActionExecutionResult success(String message) {
        return new ActionExecutionResult(true, null, message);
    }

    public static ActionExecutionResult failure(DebugReason reasonCode, String message) {
        return new ActionExecutionResult(false, reasonCode, message);
    }
}

package com.interactivedisplay.core.window;

import com.interactivedisplay.debug.DebugReason;
import java.util.List;

public record ReloadWindowResult(
        boolean success,
        DebugReason reasonCode,
        String windowId,
        int definitionCount,
        int errorCount,
        List<String> errors,
        String message
) {
    public static ReloadWindowResult success(String windowId,
                                             int definitionCount,
                                             int errorCount,
                                             List<String> errors,
                                             String message) {
        return new ReloadWindowResult(true, null, windowId, definitionCount, errorCount, List.copyOf(errors), message);
    }

    public static ReloadWindowResult failure(DebugReason reasonCode,
                                             String windowId,
                                             int definitionCount,
                                             int errorCount,
                                             List<String> errors,
                                             String message) {
        return new ReloadWindowResult(false, reasonCode, windowId, definitionCount, errorCount, List.copyOf(errors), message);
    }
}

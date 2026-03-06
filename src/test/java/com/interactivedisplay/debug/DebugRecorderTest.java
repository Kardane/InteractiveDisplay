package com.interactivedisplay.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DebugRecorderTest {
    @Test
    void ringBufferShouldTrimOldEvents() {
        DebugRecorder recorder = new DebugRecorder(2);

        recorder.record(new DebugEvent(Instant.parse("2026-03-06T00:00:00Z"), DebugEventType.WINDOW_CREATE, DebugLevel.DEBUG, null, null, "one", null, null, null, "1", null));
        recorder.record(new DebugEvent(Instant.parse("2026-03-06T00:00:01Z"), DebugEventType.WINDOW_CREATE, DebugLevel.WARN, null, null, "two", null, null, DebugReason.WINDOW_DEFINITION_NOT_FOUND, "2", null));
        recorder.record(new DebugEvent(Instant.parse("2026-03-06T00:00:02Z"), DebugEventType.WINDOW_REMOVE, DebugLevel.ERROR, null, null, "three", null, null, DebugReason.NO_ACTIVE_WINDOW, "3", null));

        assertEquals(2, recorder.size());
        assertEquals("two", recorder.recentEvents().get(0).windowId());
        assertEquals("three", recorder.recentEvents().get(1).windowId());
    }

    @Test
    void latestFailureShouldRespectPlayerFilter() {
        DebugRecorder recorder = new DebugRecorder(10);
        UUID alex = UUID.randomUUID();
        UUID steve = UUID.randomUUID();

        recorder.record(new DebugEvent(Instant.parse("2026-03-06T00:00:00Z"), DebugEventType.CLICK_HANDLE, DebugLevel.WARN, alex, "Alex", "main", "a", null, DebugReason.OWNER_MISMATCH, "1", null));
        recorder.record(new DebugEvent(Instant.parse("2026-03-06T00:00:01Z"), DebugEventType.WINDOW_CREATE, DebugLevel.ERROR, steve, "Steve", "settings", null, null, DebugReason.ENTITY_SPAWN_FAILED, "2", null));

        assertEquals(DebugReason.OWNER_MISMATCH, recorder.latestFailure(alex, null).orElseThrow().reasonCode());
        assertEquals(DebugReason.ENTITY_SPAWN_FAILED, recorder.latestFailure(steve, null).orElseThrow().reasonCode());
        assertEquals(2, recorder.recentFailureCount());
    }
}

package com.interactivedisplay.debug;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DebugRecorder {
    private final int capacity;
    private final Deque<DebugEvent> events = new ArrayDeque<>();

    public DebugRecorder(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
    }

    public synchronized void record(DebugEvent event) {
        Objects.requireNonNull(event, "event");

        if (this.events.size() == this.capacity) {
            this.events.removeFirst();
        }
        this.events.addLast(event);
    }

    public synchronized DebugEvent record(DebugEventType type,
                                          DebugLevel level,
                                          UUID playerUuid,
                                          String playerName,
                                          String windowId,
                                          String componentId,
                                          UUID entityUuid,
                                          DebugReason reasonCode,
                                          String message,
                                          Throwable throwable) {
        DebugEvent event = new DebugEvent(
                Instant.now(),
                type,
                level,
                playerUuid,
                playerName,
                windowId,
                componentId,
                entityUuid,
                reasonCode,
                message,
                summarizeException(throwable)
        );
        record(event);
        return event;
    }

    public synchronized List<DebugEvent> recentEvents() {
        return new ArrayList<>(this.events);
    }

    public synchronized List<DebugEvent> recentFailures() {
        return recentFailures(null, 0);
    }

    public synchronized List<DebugEvent> recentFailures(UUID playerUuid, int limit) {
        List<DebugEvent> failures = new ArrayList<>();
        for (DebugEvent event : this.events) {
            if (!event.isFailure() || !event.matchesPlayer(playerUuid)) {
                continue;
            }
            failures.add(event);
        }

        if (limit <= 0 || failures.size() <= limit) {
            return failures;
        }
        return new ArrayList<>(failures.subList(failures.size() - limit, failures.size()));
    }

    public synchronized Optional<DebugEvent> latestFailure(UUID playerUuid, String windowId) {
        DebugEvent latest = null;
        for (DebugEvent event : this.events) {
            if (!event.isFailure()) {
                continue;
            }
            if (!event.matchesPlayer(playerUuid) || !event.matchesWindow(windowId)) {
                continue;
            }
            latest = event;
        }
        return Optional.ofNullable(latest);
    }

    public synchronized int recentFailureCount() {
        int count = 0;
        for (DebugEvent event : this.events) {
            if (event.isFailure()) {
                count++;
            }
        }
        return count;
    }

    public synchronized int size() {
        return this.events.size();
    }

    private static String summarizeException(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }
}

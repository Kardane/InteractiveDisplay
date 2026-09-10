package com.interactivedisplay.core.interaction;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;

public final class CallbackRegistry {
    private final Map<String, InteractiveDisplayCallback> callbacks = new ConcurrentHashMap<>();
    private volatile Function<String, Optional<InteractiveDisplayCallback>> fallbackResolver = ignored -> Optional.empty();

    public void register(String id, InteractiveDisplayCallback callback) {
        this.callbacks.put(id, callback);
    }

    public boolean registerIfAbsent(String id, InteractiveDisplayCallback callback) {
        return this.callbacks.putIfAbsent(id, callback) == null;
    }

    public void setFallbackResolver(Function<String, Optional<InteractiveDisplayCallback>> fallbackResolver) {
        this.fallbackResolver = Objects.requireNonNull(fallbackResolver, "fallbackResolver");
    }

    public Optional<InteractiveDisplayCallback> find(String id) {
        InteractiveDisplayCallback callback = this.callbacks.get(id);
        if (callback != null) {
            return Optional.of(callback);
        }
        try {
            return this.fallbackResolver.apply(id);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @FunctionalInterface
    public interface InteractiveDisplayCallback {
        void execute(ServerPlayer player, String windowId, String componentId);
    }
}

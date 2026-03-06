package com.interactivedisplay.core.interaction;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.network.ServerPlayerEntity;

public final class CallbackRegistry {
    private final Map<String, InteractiveDisplayCallback> callbacks = new ConcurrentHashMap<>();

    public void register(String id, InteractiveDisplayCallback callback) {
        this.callbacks.put(id, callback);
    }

    public Optional<InteractiveDisplayCallback> find(String id) {
        return Optional.ofNullable(this.callbacks.get(id));
    }

    @FunctionalInterface
    public interface InteractiveDisplayCallback {
        void execute(ServerPlayerEntity player, String windowId, String componentId);
    }
}

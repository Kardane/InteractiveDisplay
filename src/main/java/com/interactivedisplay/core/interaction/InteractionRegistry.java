package com.interactivedisplay.core.interaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InteractionRegistry {
    private final Map<UUID, InteractionBinding> bindings = new ConcurrentHashMap<>();

    public void register(UUID interactionEntityId, InteractionBinding binding) {
        this.bindings.put(interactionEntityId, binding);
    }

    public Optional<InteractionBinding> find(UUID interactionEntityId) {
        return Optional.ofNullable(this.bindings.get(interactionEntityId));
    }

    public void unregister(UUID interactionEntityId) {
        this.bindings.remove(interactionEntityId);
    }

    public void unregisterWindow(UUID owner, String windowId) {
        this.bindings.entrySet().removeIf(entry ->
                entry.getValue().owner().equals(owner) && entry.getValue().windowId().equals(windowId));
    }

    public void clearOwner(UUID owner) {
        this.bindings.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
    }

    public int size() {
        return this.bindings.size();
    }

    public Map<UUID, InteractionBinding> snapshotOwnerBindings(UUID owner) {
        Map<UUID, InteractionBinding> out = new HashMap<>();
        for (Map.Entry<UUID, InteractionBinding> entry : this.bindings.entrySet()) {
            if (entry.getValue().owner().equals(owner)) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }
}

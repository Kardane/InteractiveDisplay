package com.interactivedisplay.core.interaction;

import com.interactivedisplay.internal.api.PublicActionDispatcher;
import com.interactivedisplay.schema.CustomActionToken;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

public final class CallbackRegistry {
    private final Map<String, InteractiveDisplayCallback> callbacks = new ConcurrentHashMap<>();

    public void register(String id, InteractiveDisplayCallback callback) {
        this.callbacks.put(id, callback);
    }

    public Optional<InteractiveDisplayCallback> find(String id) {
        InteractiveDisplayCallback callback = this.callbacks.get(id);
        if (callback != null) {
            return Optional.of(callback);
        }
        if (!CustomActionToken.isToken(id)) {
            return Optional.empty();
        }
        try {
            CustomActionToken.Decoded decoded = CustomActionToken.decode(id);
            if (!PublicActionDispatcher.isRegistered(decoded.actionId())) {
                return Optional.empty();
            }
            return Optional.of((player, windowId, componentId) ->
                    PublicActionDispatcher.execute(
                            player,
                            windowId,
                            componentId,
                            decoded.actionId().toString(),
                            decoded.parameters()
                    ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @FunctionalInterface
    public interface InteractiveDisplayCallback {
        void execute(ServerPlayer player, String windowId, String componentId);
    }
}

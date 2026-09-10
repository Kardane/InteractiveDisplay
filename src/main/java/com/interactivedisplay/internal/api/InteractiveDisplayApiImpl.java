package com.interactivedisplay.internal.api;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.api.InteractiveDisplayRegistrar;
import com.interactivedisplay.api.callback.CallbackApi;
import com.interactivedisplay.api.window.WindowApi;
import com.interactivedisplay.api.window.WindowApi.OperationResult;
import com.interactivedisplay.api.window.WindowOpenOptions;
import com.interactivedisplay.api.window.WindowPositionMode;
import com.interactivedisplay.api.window.WindowSpec;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.CreateWindowResult;
import com.interactivedisplay.core.window.RemoveWindowResult;
import com.interactivedisplay.core.window.WindowInstance;
import com.interactivedisplay.core.window.WindowManager;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class InteractiveDisplayApiImpl implements InteractiveDisplayApi, InteractiveDisplayRegistrar {
    private final CallbackRegistry callbackRegistry;
    private final ConcurrentHashMap<ResourceLocation, WindowSpec> windowSpecs = new ConcurrentHashMap<>();
    private final Set<ResourceLocation> callbackIds = ConcurrentHashMap.newKeySet();
    private final WindowApi windowApi = new WindowApiImpl();
    private final CallbackApi callbackApi = new CallbackApiImpl();
    private volatile WindowManager manager;

    public InteractiveDisplayApiImpl(CallbackRegistry callbackRegistry) {
        this.callbackRegistry = callbackRegistry;
    }

    @Override
    public WindowApi windows() {
        return this.windowApi;
    }

    @Override
    public CallbackApi callbacks() {
        return this.callbackApi;
    }

    public synchronized void attach(WindowManager manager) {
        if (this.manager == manager) {
            return;
        }
        this.manager = manager;
        for (WindowSpec spec : this.windowSpecs.values()) {
            if (!manager.registerProgrammaticWindow(WindowSpecAdapter.toDefinition(spec))) {
                InteractiveDisplay.LOGGER.error(
                        "[{}] public API window registration collision id={}",
                        InteractiveDisplay.MOD_ID,
                        spec.id()
                );
            }
        }
    }

    public synchronized void detach(WindowManager manager) {
        if (this.manager == manager) {
            this.manager = null;
        }
    }

    public boolean attached() {
        return this.manager != null;
    }

    private final class WindowApiImpl implements WindowApi {
        @Override
        public RegistrationResult register(WindowSpec spec) {
            if (spec == null) {
                return RegistrationResult.failure(null, "window spec must not be null");
            }
            WindowSpec previous = windowSpecs.putIfAbsent(spec.id(), spec);
            if (previous != null) {
                return RegistrationResult.failure(spec.id(), "window id is already registered by the public API");
            }

            WindowManager current = manager;
            if (current != null && !current.registerProgrammaticWindow(WindowSpecAdapter.toDefinition(spec))) {
                windowSpecs.remove(spec.id(), spec);
                return RegistrationResult.failure(spec.id(), "window id collides with an existing definition");
            }
            return RegistrationResult.success(spec.id());
        }

        @Override
        public OperationResult open(ServerPlayer player, ResourceLocation windowId, WindowOpenOptions options) {
            if (player == null || windowId == null || options == null) {
                return OperationResult.failure("invalid_argument", "player, windowId and options are required");
            }
            WindowManager current = manager;
            if (current == null) {
                return OperationResult.failure("runtime_not_ready", "InteractiveDisplay runtime is not ready");
            }
            String internalId = PublicIdCodec.toInternalWindowId(windowId);
            if (!current.hasDefinition(internalId)) {
                return OperationResult.failure("window_not_found", "window definition not found: " + windowId);
            }

            CreateWindowResult result = current.createWindow(
                    player,
                    internalId,
                    toInternalMode(options.mode()),
                    options.fixedAnchor(),
                    options.fixedYaw(),
                    options.fixedPitch()
            );
            return result.success()
                    ? OperationResult.success(result.message())
                    : OperationResult.failure(reason(result.reasonCode()), result.message());
        }

        @Override
        public OperationResult close(ServerPlayer player, ResourceLocation windowId) {
            if (player == null || windowId == null) {
                return OperationResult.failure("invalid_argument", "player and windowId are required");
            }
            WindowManager current = manager;
            if (current == null) {
                return OperationResult.failure("runtime_not_ready", "InteractiveDisplay runtime is not ready");
            }
            RemoveWindowResult result = current.removeWindow(player.getUUID(), PublicIdCodec.toInternalWindowId(windowId));
            return result.success()
                    ? OperationResult.success(result.message())
                    : OperationResult.failure(reason(result.reasonCode()), result.message());
        }

        @Override
        public void closeAll(ServerPlayer player) {
            WindowManager current = manager;
            if (current != null && player != null) {
                current.removeAll(player.getUUID());
            }
        }

        @Override
        public boolean isOpen(ServerPlayer player, ResourceLocation windowId) {
            WindowManager current = manager;
            return current != null
                    && player != null
                    && windowId != null
                    && current.findActiveWindow(player.getUUID(), PublicIdCodec.toInternalWindowId(windowId)) != null;
        }

        @Override
        public Optional<WindowHandle> find(ServerPlayer player, ResourceLocation windowId) {
            if (!isOpen(player, windowId)) {
                return Optional.empty();
            }
            return Optional.of(new WindowHandleImpl(player.getUUID(), windowId));
        }

        @Override
        public Set<ResourceLocation> registeredIds() {
            return Set.copyOf(windowSpecs.keySet());
        }
    }

    private final class CallbackApiImpl implements CallbackApi {
        @Override
        public RegistrationResult register(ResourceLocation id, DisplayCallback callback) {
            if (id == null || callback == null) {
                return RegistrationResult.failure(id, "callback id and callback are required");
            }
            String internalId = id.toString();
            if (callbackRegistry.find(internalId).isPresent() || !callbackIds.add(id)) {
                return RegistrationResult.failure(id, "callback id is already registered");
            }
            callbackRegistry.register(internalId, (player, windowId, componentId) -> callback.execute(
                    new CallbackContext(player, PublicIdCodec.toPublicWindowId(windowId), componentId, windowApi)
            ));
            return RegistrationResult.success(id);
        }
    }

    private final class WindowHandleImpl implements WindowApi.WindowHandle {
        private final UUID ownerId;
        private final ResourceLocation id;

        private WindowHandleImpl(UUID ownerId, ResourceLocation id) {
            this.ownerId = ownerId;
            this.id = id;
        }

        @Override
        public ResourceLocation id() {
            return this.id;
        }

        @Override
        public UUID ownerId() {
            return this.ownerId;
        }

        @Override
        public Optional<WindowPositionMode> mode() {
            WindowManager current = manager;
            if (current == null) {
                return Optional.empty();
            }
            WindowInstance instance = current.findActiveWindow(this.ownerId, PublicIdCodec.toInternalWindowId(this.id));
            return instance == null ? Optional.empty() : Optional.of(toPublicMode(instance.positionMode()));
        }

        @Override
        public boolean isOpen() {
            WindowManager current = manager;
            return current != null
                    && current.findActiveWindow(this.ownerId, PublicIdCodec.toInternalWindowId(this.id)) != null;
        }

        @Override
        public OperationResult close() {
            WindowManager current = manager;
            if (current == null) {
                return OperationResult.failure("runtime_not_ready", "InteractiveDisplay runtime is not ready");
            }
            RemoveWindowResult result = current.removeWindow(this.ownerId, PublicIdCodec.toInternalWindowId(this.id));
            return result.success()
                    ? OperationResult.success(result.message())
                    : OperationResult.failure(reason(result.reasonCode()), result.message());
        }
    }

    private static PositionMode toInternalMode(WindowPositionMode mode) {
        return PositionMode.valueOf(mode.name());
    }

    private static WindowPositionMode toPublicMode(PositionMode mode) {
        return WindowPositionMode.valueOf(mode.name());
    }

    private static String reason(Object reason) {
        return reason == null ? "unknown" : reason.toString().toLowerCase(java.util.Locale.ROOT);
    }
}

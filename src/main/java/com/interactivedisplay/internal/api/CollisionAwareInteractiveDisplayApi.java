package com.interactivedisplay.internal.api;

import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.api.InteractiveDisplayRegistrar;
import com.interactivedisplay.api.action.ActionApi;
import com.interactivedisplay.api.callback.CallbackApi;
import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.api.group.GroupApi;
import com.interactivedisplay.api.window.WindowApi;
import com.interactivedisplay.api.window.WindowOpenOptions;
import com.interactivedisplay.api.window.WindowSpec;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

final class CollisionAwareInteractiveDisplayApi implements InteractiveDisplayApi, InteractiveDisplayRegistrar {
    private final InteractiveDisplayApiImpl delegate;
    private final Predicate<ResourceLocation> preRuntimeWindowCollision;
    private final WindowApi windowApi;

    CollisionAwareInteractiveDisplayApi(
            InteractiveDisplayApiImpl delegate,
            Predicate<ResourceLocation> preRuntimeWindowCollision
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.preRuntimeWindowCollision = Objects.requireNonNull(preRuntimeWindowCollision, "preRuntimeWindowCollision");
        WindowApi delegateWindows = delegate.windows();
        this.windowApi = new WindowApi() {
            @Override
            public RegistrationResult register(WindowSpec spec) {
                if (spec != null
                        && !CollisionAwareInteractiveDisplayApi.this.delegate.attached()
                        && CollisionAwareInteractiveDisplayApi.this.preRuntimeWindowCollision.test(spec.id())) {
                    return RegistrationResult.failure(spec.id(), "window id collides with an existing definition");
                }
                return delegateWindows.register(spec);
            }

            @Override
            public OperationResult open(ServerPlayer player, ResourceLocation windowId, WindowOpenOptions options) {
                return delegateWindows.open(player, windowId, options);
            }

            @Override
            public OperationResult close(ServerPlayer player, ResourceLocation windowId) {
                return delegateWindows.close(player, windowId);
            }

            @Override
            public void closeAll(ServerPlayer player) {
                delegateWindows.closeAll(player);
            }

            @Override
            public boolean isOpen(ServerPlayer player, ResourceLocation windowId) {
                return delegateWindows.isOpen(player, windowId);
            }

            @Override
            public Optional<WindowHandle> find(ServerPlayer player, ResourceLocation windowId) {
                return delegateWindows.find(player, windowId);
            }

            @Override
            public Set<ResourceLocation> registeredIds() {
                return delegateWindows.registeredIds();
            }
        };
    }

    @Override
    public WindowApi windows() {
        return this.windowApi;
    }

    @Override
    public GroupApi groups() {
        return this.delegate.groups();
    }

    @Override
    public CallbackApi callbacks() {
        return this.delegate.callbacks();
    }

    @Override
    public ActionApi actions() {
        return this.delegate.actions();
    }

    @Override
    public EventApi events() {
        return this.delegate.events();
    }
}

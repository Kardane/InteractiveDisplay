package com.interactivedisplay.internal.api;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.api.InteractiveDisplayEntrypoint;
import com.interactivedisplay.core.window.WindowManager;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class InteractiveDisplayApiBootstrap implements DedicatedServerModInitializer {
    private final InteractiveDisplayApiImpl api = new InteractiveDisplayApiImpl(InteractiveDisplay.callbackRegistry());

    @Override
    public void onInitializeServer() {
        FabricLoader loader = FabricLoader.getInstance();
        BundledDefinitionInstaller.log(BundledDefinitionInstaller.installAll(loader, loader.getConfigDir()));
        loader.getObjectShare().put(InteractiveDisplayApi.OBJECT_SHARE_KEY, this.api);

        for (InteractiveDisplayEntrypoint entrypoint : loader.getEntrypoints(
                InteractiveDisplayApi.EXTENSION_ENTRYPOINT,
                InteractiveDisplayEntrypoint.class
        )) {
            try {
                entrypoint.register(this.api);
            } catch (RuntimeException exception) {
                InteractiveDisplay.LOGGER.error(
                        "[{}] public API extension registration failed entrypoint={}",
                        InteractiveDisplay.MOD_ID,
                        entrypoint.getClass().getName(),
                        exception
                );
            }
        }

        ServerLifecycleEvents.SERVER_STARTED.register(server -> tryAttach());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!this.api.attached()) {
                tryAttach();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> this.api.detach());
    }

    private void tryAttach() {
        InteractiveDisplay instance = InteractiveDisplay.instance();
        if (instance == null) {
            return;
        }
        WindowManager manager = instance.windowManager();
        if (manager != null) {
            this.api.attach(manager);
        }
    }
}

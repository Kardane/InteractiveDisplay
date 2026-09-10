package com.interactivedisplay.api;

import com.interactivedisplay.api.action.ActionApi;
import com.interactivedisplay.api.callback.CallbackApi;
import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.api.window.WindowApi;
import net.fabricmc.loader.api.FabricLoader;

public interface InteractiveDisplayApi {
    String OBJECT_SHARE_KEY = "interactivedisplay:api";
    String EXTENSION_ENTRYPOINT = "interactivedisplay";

    static InteractiveDisplayApi get() {
        Object value = FabricLoader.getInstance().getObjectShare().get(OBJECT_SHARE_KEY);
        if (value instanceof InteractiveDisplayApi api) {
            return api;
        }
        throw new IllegalStateException(
                "InteractiveDisplay API is not initialized. Ensure your mod depends on 'interactivedisplay'."
        );
    }

    WindowApi windows();

    CallbackApi callbacks();

    ActionApi actions();

    EventApi events();
}

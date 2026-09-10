package com.interactivedisplay.api;

import com.interactivedisplay.api.callback.CallbackApi;
import com.interactivedisplay.api.window.WindowApi;

public interface InteractiveDisplayRegistrar {
    WindowApi windows();

    CallbackApi callbacks();
}

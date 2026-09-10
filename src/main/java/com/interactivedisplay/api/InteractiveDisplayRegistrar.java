package com.interactivedisplay.api;

import com.interactivedisplay.api.action.ActionApi;
import com.interactivedisplay.api.callback.CallbackApi;
import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.api.window.WindowApi;

public interface InteractiveDisplayRegistrar {
    WindowApi windows();

    CallbackApi callbacks();

    ActionApi actions();

    EventApi events();
}

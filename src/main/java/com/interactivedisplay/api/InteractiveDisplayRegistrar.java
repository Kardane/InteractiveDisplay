package com.interactivedisplay.api;

import com.interactivedisplay.api.action.ActionApi;
import com.interactivedisplay.api.callback.CallbackApi;
import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.api.group.GroupApi;
import com.interactivedisplay.api.window.WindowApi;

public interface InteractiveDisplayRegistrar {
    WindowApi windows();

    GroupApi groups();

    CallbackApi callbacks();

    ActionApi actions();

    EventApi events();
}

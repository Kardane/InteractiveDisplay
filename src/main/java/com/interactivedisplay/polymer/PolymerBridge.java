package com.interactivedisplay.polymer;

import eu.pb4.polymer.autohost.impl.AutoHost;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;

public class PolymerBridge {
    public void enableAutoHost() {
        if (AutoHost.config != null) {
            AutoHost.config.enabled = true;
            AutoHost.config.require = true;
            AutoHost.config.modOverride = true;
            AutoHost.config.type = "polymer:automatic";
        }
    }

    public boolean addModAssets(String modId) {
        return PolymerResourcePackUtils.addModAssets(modId);
    }

    public void markPackRequired() {
        PolymerResourcePackUtils.markAsRequired();
    }

    public boolean buildMain() {
        return PolymerResourcePackUtils.buildMain();
    }
}

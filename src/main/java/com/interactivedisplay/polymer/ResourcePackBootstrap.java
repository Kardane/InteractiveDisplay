package com.interactivedisplay.polymer;

import com.interactivedisplay.InteractiveDisplay;

public final class ResourcePackBootstrap {
    private final PolymerConfigEnsurer configEnsurer;
    private final PolymerBridge polymerBridge;
    private final ConfigImageAssetPackBuilder configImageAssetPackBuilder;
    private volatile boolean ready;

    public ResourcePackBootstrap(PolymerConfigEnsurer configEnsurer, PolymerBridge polymerBridge) {
        this(configEnsurer, polymerBridge, null);
    }

    public ResourcePackBootstrap(PolymerConfigEnsurer configEnsurer,
                                 PolymerBridge polymerBridge,
                                 ConfigImageAssetPackBuilder configImageAssetPackBuilder) {
        this.configEnsurer = configEnsurer;
        this.polymerBridge = polymerBridge;
        this.configImageAssetPackBuilder = configImageAssetPackBuilder;
    }

    public void prepareFiles() {
        this.configEnsurer.ensure();
        syncConfigImages();
    }

    public boolean bootstrap(String modId) {
        this.configEnsurer.ensure();
        syncConfigImages();
        try {
            this.polymerBridge.enableAutoHost();
            boolean assetsAdded = this.polymerBridge.addModAssets(modId);
            this.polymerBridge.markPackRequired();
            boolean built = this.polymerBridge.buildMain();
            this.ready = assetsAdded && built;
            if (!this.ready) {
                InteractiveDisplay.LOGGER.warn("[{}] resource pack bootstrap 경고 assetsAdded={} built={}", InteractiveDisplay.MOD_ID, assetsAdded, built);
            }
            return this.ready;
        } catch (RuntimeException exception) {
            this.ready = false;
            InteractiveDisplay.LOGGER.warn("[{}] resource pack bootstrap 실패 message={}", InteractiveDisplay.MOD_ID, exception.getMessage(), exception);
            return false;
        }
    }

    public boolean ready() {
        return this.ready;
    }

    private void syncConfigImages() {
        if (this.configImageAssetPackBuilder != null) {
            this.configImageAssetPackBuilder.sync();
        }
    }
}

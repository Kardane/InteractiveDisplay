package com.interactivedisplay.core.window;

import com.interactivedisplay.core.positioning.PositionMode;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class WindowGroupInstance {
    private final UUID owner;
    private final String groupId;
    private final Vec3 baseAnchor;
    private final float baseYaw;
    private final float basePitch;
    private PositionMode currentMode;
    private String currentWindowId;
    private WindowInstance currentWindow;

    public WindowGroupInstance(UUID owner,
                               String groupId,
                               Vec3 baseAnchor,
                               float baseYaw,
                               float basePitch,
                               PositionMode currentMode,
                               String currentWindowId,
                               WindowInstance currentWindow) {
        this.owner = owner;
        this.groupId = groupId;
        this.baseAnchor = baseAnchor;
        this.baseYaw = baseYaw;
        this.basePitch = basePitch;
        this.currentMode = currentMode;
        this.currentWindowId = currentWindowId;
        this.currentWindow = currentWindow;
    }

    public UUID owner() {
        return this.owner;
    }

    public String groupId() {
        return this.groupId;
    }

    public Vec3 baseAnchor() {
        return this.baseAnchor;
    }

    public float baseYaw() {
        return this.baseYaw;
    }

    public float basePitch() {
        return this.basePitch;
    }

    public PositionMode currentMode() {
        return this.currentMode;
    }

    public String currentWindowId() {
        return this.currentWindowId;
    }

    public WindowInstance currentWindow() {
        return this.currentWindow;
    }

    public void update(PositionMode currentMode, String currentWindowId, WindowInstance currentWindow) {
        this.currentMode = currentMode;
        this.currentWindowId = currentWindowId;
        this.currentWindow = currentWindow;
    }
}

package com.interactivedisplay.core.window;

import com.interactivedisplay.core.positioning.PositionMode;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;

public final class WindowGroupInstance {
    private final UUID owner;
    private final String groupId;
    private final Vec3d baseAnchor;
    private final float baseYaw;
    private final float basePitch;
    private PositionMode currentMode;
    private String currentWindowId;
    private WindowInstance currentWindow;

    public WindowGroupInstance(UUID owner,
                               String groupId,
                               Vec3d baseAnchor,
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

    public Vec3d baseAnchor() {
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

package com.interactivedisplay.core.window;

import com.interactivedisplay.core.positioning.PositionMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class WindowInstance {
    private final UUID owner;
    private final String windowId;
    private final RegistryKey<World> worldKey;
    private final PositionMode positionMode;
    private final Vec3d fixedAnchor;
    private final Map<String, WindowComponentRuntime> components = new LinkedHashMap<>();
    private Vec3d currentAnchor;
    private float currentYaw;
    private float currentPitch;
    private long lastUpdateTick;

    public WindowInstance(UUID owner,
                          String windowId,
                          RegistryKey<World> worldKey,
                          PositionMode positionMode,
                          Vec3d fixedAnchor,
                          Vec3d currentAnchor,
                          float currentYaw,
                          float currentPitch,
                          long lastUpdateTick) {
        this.owner = owner;
        this.windowId = windowId;
        this.worldKey = worldKey;
        this.positionMode = positionMode;
        this.fixedAnchor = fixedAnchor;
        this.currentAnchor = currentAnchor;
        this.currentYaw = currentYaw;
        this.currentPitch = currentPitch;
        this.lastUpdateTick = lastUpdateTick;
    }

    public UUID owner() {
        return this.owner;
    }

    public String windowId() {
        return this.windowId;
    }

    public RegistryKey<World> worldKey() {
        return this.worldKey;
    }

    public PositionMode positionMode() {
        return this.positionMode;
    }

    public Vec3d fixedAnchor() {
        return this.fixedAnchor;
    }

    public Vec3d currentAnchor() {
        return this.currentAnchor;
    }

    public float currentYaw() {
        return this.currentYaw;
    }

    public float currentPitch() {
        return this.currentPitch;
    }

    public void updateTransform(Vec3d currentAnchor, float currentYaw, float currentPitch, long tick) {
        this.currentAnchor = currentAnchor;
        this.currentYaw = currentYaw;
        this.currentPitch = currentPitch;
        this.lastUpdateTick = tick;
    }

    public long lastUpdateTick() {
        return this.lastUpdateTick;
    }

    public void addRuntime(WindowComponentRuntime runtime) {
        this.components.put(runtime.definition().id(), runtime);
    }

    public Collection<WindowComponentRuntime> runtimes() {
        return this.components.values();
    }

    public WindowComponentRuntime runtime(String componentId) {
        return this.components.get(componentId);
    }

    public Set<UUID> entityIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (WindowComponentRuntime runtime : this.components.values()) {
            ids.addAll(runtime.entityIds());
        }
        return ids;
    }

    public int bindingCount() {
        int count = 0;
        for (WindowComponentRuntime runtime : this.components.values()) {
            if (runtime.interactionEntityId() != null) {
                count++;
            }
        }
        return count;
    }
}

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
    private final String groupId;
    private final String groupWindowId;
    private final RegistryKey<World> worldKey;
    private final PositionMode positionMode;
    private final Vec3d fixedAnchor;
    private final float fixedYaw;
    private final float fixedPitch;
    private final UUID rootEntityId;
    private final Map<String, WindowComponentRuntime> components = new LinkedHashMap<>();
    private Vec3d targetAnchor;
    private float targetYaw;
    private float targetPitch;
    private Vec3d currentAnchor;
    private float currentYaw;
    private float currentPitch;
    private long lastUpdateTick;

    public WindowInstance(UUID owner,
                          String windowId,
                          RegistryKey<World> worldKey,
                          PositionMode positionMode,
                          Vec3d fixedAnchor,
                          float fixedYaw,
                          float fixedPitch,
                          UUID rootEntityId,
                          Vec3d targetAnchor,
                          float targetYaw,
                          float targetPitch,
                          Vec3d currentAnchor,
                          float currentYaw,
                          float currentPitch,
                          long lastUpdateTick) {
        this(owner, windowId, null, null, worldKey, positionMode, fixedAnchor, fixedYaw, fixedPitch, rootEntityId, targetAnchor, targetYaw, targetPitch, currentAnchor, currentYaw, currentPitch, lastUpdateTick);
    }

    public WindowInstance(UUID owner,
                          String windowId,
                          String groupId,
                          String groupWindowId,
                          RegistryKey<World> worldKey,
                          PositionMode positionMode,
                          Vec3d fixedAnchor,
                          float fixedYaw,
                          float fixedPitch,
                          UUID rootEntityId,
                          Vec3d targetAnchor,
                          float targetYaw,
                          float targetPitch,
                          Vec3d currentAnchor,
                          float currentYaw,
                          float currentPitch,
                          long lastUpdateTick) {
        this.owner = owner;
        this.windowId = windowId;
        this.groupId = groupId;
        this.groupWindowId = groupWindowId;
        this.worldKey = worldKey;
        this.positionMode = positionMode;
        this.fixedAnchor = fixedAnchor;
        this.fixedYaw = fixedYaw;
        this.fixedPitch = fixedPitch;
        this.rootEntityId = rootEntityId;
        this.targetAnchor = targetAnchor;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
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

    public String groupId() {
        return this.groupId;
    }

    public String groupWindowId() {
        return this.groupWindowId;
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

    public float fixedYaw() {
        return this.fixedYaw;
    }

    public float fixedPitch() {
        return this.fixedPitch;
    }

    public UUID rootEntityId() {
        return this.rootEntityId;
    }

    public Vec3d currentAnchor() {
        return this.currentAnchor;
    }

    public Vec3d targetAnchor() {
        return this.targetAnchor;
    }

    public float targetYaw() {
        return this.targetYaw;
    }

    public float targetPitch() {
        return this.targetPitch;
    }

    public float currentYaw() {
        return this.currentYaw;
    }

    public float currentPitch() {
        return this.currentPitch;
    }

    public void updateTarget(Vec3d targetAnchor, float targetYaw, float targetPitch) {
        this.targetAnchor = targetAnchor;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
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
        if (this.rootEntityId != null) {
            ids.add(this.rootEntityId);
        }
        for (WindowComponentRuntime runtime : this.components.values()) {
            ids.addAll(runtime.entityIds());
        }
        return ids;
    }

    public int bindingCount() {
        int count = 0;
        for (WindowComponentRuntime runtime : this.components.values()) {
            if (runtime.interactive()) {
                count++;
            }
        }
        return count;
    }
}

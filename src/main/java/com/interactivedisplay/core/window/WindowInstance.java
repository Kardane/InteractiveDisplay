package com.interactivedisplay.core.window;

import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.positioning.WindowOffset;
import com.interactivedisplay.entity.VirtualWindowHolder;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class WindowInstance {
    private final UUID owner;
    private final String windowId;
    private final String groupId;
    private final String groupWindowId;
    private final ResourceKey<Level> worldKey;
    private final PositionMode positionMode;
    private final Vec3 fixedAnchor;
    private final float fixedYaw;
    private final float fixedPitch;
    private final VirtualWindowHolder virtualHolder;
    private final Map<String, WindowComponentRuntime> components = new LinkedHashMap<>();
    private WindowOffset runtimeOffset = WindowOffset.zero();
    private Vec3 targetAnchor;
    private float targetYaw;
    private float targetPitch;
    private Vec3 currentAnchor;
    private float currentYaw;
    private float currentPitch;
    private Vec3 currentFocusPoint;
    private long lastUpdateTick;

    public WindowInstance(UUID owner,
                          String windowId,
                          ResourceKey<Level> worldKey,
                          PositionMode positionMode,
                          Vec3 fixedAnchor,
                          float fixedYaw,
                          float fixedPitch,
                          VirtualWindowHolder virtualHolder,
                          Vec3 targetAnchor,
                          float targetYaw,
                          float targetPitch,
                          Vec3 currentAnchor,
                          float currentYaw,
                          float currentPitch,
                          long lastUpdateTick) {
        this(owner, windowId, null, null, worldKey, positionMode, fixedAnchor, fixedYaw, fixedPitch, virtualHolder, targetAnchor, targetYaw, targetPitch, currentAnchor, currentYaw, currentPitch, lastUpdateTick);
    }

    public WindowInstance(UUID owner,
                          String windowId,
                          String groupId,
                          String groupWindowId,
                          ResourceKey<Level> worldKey,
                          PositionMode positionMode,
                          Vec3 fixedAnchor,
                          float fixedYaw,
                          float fixedPitch,
                          VirtualWindowHolder virtualHolder,
                          Vec3 targetAnchor,
                          float targetYaw,
                          float targetPitch,
                          Vec3 currentAnchor,
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
        this.virtualHolder = virtualHolder;
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

    public ResourceKey<Level> worldKey() {
        return this.worldKey;
    }

    public PositionMode positionMode() {
        return this.positionMode;
    }

    public Vec3 fixedAnchor() {
        return this.fixedAnchor;
    }

    public float fixedYaw() {
        return this.fixedYaw;
    }

    public float fixedPitch() {
        return this.fixedPitch;
    }

    public VirtualWindowHolder virtualHolder() {
        return this.virtualHolder;
    }

    public WindowOffset runtimeOffset() {
        return this.runtimeOffset;
    }

    public void setRuntimeOffset(WindowOffset runtimeOffset) {
        this.runtimeOffset = runtimeOffset == null ? WindowOffset.zero() : runtimeOffset;
    }

    public Vec3 currentAnchor() {
        return this.currentAnchor;
    }

    public Vec3 targetAnchor() {
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

    public Vec3 currentFocusPoint() {
        return this.currentFocusPoint;
    }

    public void updateTarget(Vec3 targetAnchor, float targetYaw, float targetPitch) {
        this.targetAnchor = targetAnchor;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
    }

    public void updateTransform(Vec3 currentAnchor, float currentYaw, float currentPitch, long tick) {
        updateTransform(currentAnchor, currentYaw, currentPitch, this.currentFocusPoint, tick);
    }

    public void updateTransform(Vec3 currentAnchor,
                                float currentYaw,
                                float currentPitch,
                                Vec3 currentFocusPoint,
                                long tick) {
        this.currentAnchor = currentAnchor;
        this.currentYaw = currentYaw;
        this.currentPitch = currentPitch;
        this.currentFocusPoint = currentFocusPoint;
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

    public Set<Integer> entityIds() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (WindowComponentRuntime runtime : this.components.values()) {
            if (runtime.displayElement() != null) {
                for (int entityId : runtime.displayElement().getEntityIds()) {
                    ids.add(entityId);
                }
            }
        }
        return ids;
    }

    public int entityCount() {
        if (this.virtualHolder != null) {
            return this.virtualHolder.entityCount();
        }
        return entityIds().size();
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

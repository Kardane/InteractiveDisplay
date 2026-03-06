package com.interactivedisplay.core.window;

import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.positioning.WindowPositionTracker;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class WindowPlacementController {
    private final CoordinateTransformer transformer;
    private final Map<UUID, PlacementSession> sessions = new ConcurrentHashMap<>();

    WindowPlacementController(CoordinateTransformer transformer) {
        this.transformer = transformer;
    }

    void start(UUID owner, WindowNavigationContext context) {
        this.sessions.put(owner, new PlacementSession(context.windowId(), context.groupId()));
    }

    void stop(UUID owner) {
        this.sessions.remove(owner);
    }

    void stopIfTracking(UUID owner, String windowId, String groupId) {
        PlacementSession session = this.sessions.get(owner);
        if (session != null && session.matches(windowId, groupId)) {
            this.sessions.remove(owner);
        }
    }

    boolean isTracking(UUID owner, WindowNavigationContext context) {
        PlacementSession session = this.sessions.get(owner);
        return session != null && session.matches(context.windowId(), context.groupId());
    }

    boolean isTracking(UUID owner, WindowInstance instance) {
        PlacementSession session = this.sessions.get(owner);
        return session != null && session.matches(instance.windowId(), instance.groupId());
    }

    WindowPositionTracker.WindowTransformState previewStandalone(WindowInstance instance,
                                                                WindowDefinition definition,
                                                                Vec3d eyePos,
                                                                float playerYaw,
                                                                float playerPitch) {
        return switch (instance.positionMode()) {
            case FIXED -> new WindowPositionTracker.WindowTransformState(
                    this.transformer.toPlayerFixedAnchor(eyePos, definition.offset(), playerYaw, playerPitch),
                    MathHelper.wrapDegrees(playerYaw),
                    0.0f,
                    eyePos
            );
            case PLAYER_FIXED -> new WindowPositionTracker.WindowTransformState(
                    this.transformer.toPlayerFixedAnchor(eyePos, definition.offset(), playerYaw, playerPitch),
                    MathHelper.wrapDegrees(playerYaw),
                    MathHelper.clamp(playerPitch, -90.0f, 90.0f),
                    eyePos
            );
            case PLAYER_VIEW -> new WindowPositionTracker.WindowTransformState(instance.currentAnchor(), instance.currentYaw(), instance.currentPitch(), eyePos);
        };
    }

    WindowPositionTracker.WindowTransformState previewGroup(WindowGroupInstance groupInstance,
                                                            WindowGroupDefinition groupDefinition,
                                                            WindowDefinition definition,
                                                            Vec3d eyePos,
                                                            float playerYaw,
                                                            float playerPitch) {
        WindowGroupEntry entry = groupDefinition.entry(groupInstance.currentWindowId());
        if (entry == null || groupInstance.currentMode() != PositionMode.PLAYER_FIXED) {
            return new WindowPositionTracker.WindowTransformState(groupInstance.currentWindow().currentAnchor(), groupInstance.currentWindow().currentYaw(), groupInstance.currentWindow().currentPitch(), eyePos);
        }
        var effectiveOffset = definition.offset().plus(entry.offset());
        float resolvedYaw = MathHelper.wrapDegrees(playerYaw);
        float resolvedPitch = MathHelper.clamp(playerPitch, -90.0f, 90.0f);
        return new WindowPositionTracker.WindowTransformState(
                this.transformer.toPlayerFixedAnchor(eyePos, effectiveOffset, resolvedYaw, resolvedPitch),
                resolvedYaw,
                resolvedPitch,
                eyePos
        );
    }

    StandaloneCommit commitStandalone(WindowInstance instance,
                                      WindowDefinition definition,
                                      Vec3d eyePos,
                                      float playerYaw,
                                      float playerPitch) {
        WindowPositionTracker.WindowTransformState preview = previewStandalone(instance, definition, eyePos, playerYaw, playerPitch);
        return switch (instance.positionMode()) {
            case FIXED -> new StandaloneCommit(preview.anchor(), preview.yaw(), 0.0f);
            case PLAYER_FIXED -> new StandaloneCommit(null, preview.yaw(), preview.pitch());
            case PLAYER_VIEW -> new StandaloneCommit(instance.fixedAnchor(), instance.fixedYaw(), instance.fixedPitch());
        };
    }

    GroupCommit commitGroup(WindowGroupInstance groupInstance,
                            WindowGroupDefinition groupDefinition,
                            WindowDefinition definition,
                            Vec3d eyePos,
                            float playerYaw,
                            float playerPitch) {
        WindowGroupEntry entry = groupDefinition.entry(groupInstance.currentWindowId());
        if (entry == null) {
            return new GroupCommit(groupInstance.baseAnchor(), groupInstance.baseYaw(), groupInstance.basePitch());
        }
        WindowPositionTracker.WindowTransformState preview = previewGroup(groupInstance, groupDefinition, definition, eyePos, playerYaw, playerPitch);
        float baseYaw = MathHelper.wrapDegrees(preview.yaw() - entry.orbit().yaw());
        float basePitch = MathHelper.clamp(preview.pitch() - entry.orbit().pitch(), -90.0f, 90.0f);
        return new GroupCommit(groupInstance.baseAnchor(), baseYaw, basePitch);
    }

    record StandaloneCommit(Vec3d fixedAnchor, float fixedYaw, float fixedPitch) {
    }

    record GroupCommit(Vec3d baseAnchor, float baseYaw, float basePitch) {
    }

    private record PlacementSession(String windowId, String groupId) {
        boolean matches(String windowId, String groupId) {
            return java.util.Objects.equals(this.windowId, windowId) && java.util.Objects.equals(this.groupId, groupId);
        }
    }
}

package com.interactivedisplay.core.window;

import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class UiHitResolver {
    private final WindowStateStore stateStore;
    private final CoordinateTransformer transformer;

    UiHitResolver(WindowStateStore stateStore, CoordinateTransformer transformer) {
        this.stateStore = stateStore;
        this.transformer = transformer;
    }

    UiHitResult findUiHit(ServerPlayer player) {
        return findHit(
                player.getUUID(),
                player.level().dimension(),
                player.getEyePosition(),
                player.getViewVector(1.0f).normalize(),
                true,
                false
        );
    }

    UiHitResult findPlacementSurfaceHit(ServerPlayer player) {
        return findHit(
                player.getUUID(),
                player.level().dimension(),
                player.getEyePosition(),
                player.getViewVector(1.0f).normalize(),
                true,
                true
        );
    }

    UiHitResult findUiHit(UUID owner, ResourceKey<Level> worldKey, Vec3 start, Vec3 direction) {
        return findHit(owner, worldKey, start, direction, false, false);
    }

    UiHitResult findPlacementSurfaceHit(UUID owner, ResourceKey<Level> worldKey, Vec3 start, Vec3 direction) {
        return findHit(owner, worldKey, start, direction, false, true);
    }

    private UiHitResult findHit(UUID owner,
                                ResourceKey<Level> worldKey,
                                Vec3 start,
                                Vec3 direction,
                                boolean useLivePassengerAnchor,
                                boolean placementSurface) {
        List<WindowContext> windows = this.stateStore.ownerWindowContexts(owner);
        if (windows.isEmpty()) {
            return null;
        }

        Vec3 normalizedDirection = direction.normalize();
        UiHitResult best = null;
        double closest = Double.MAX_VALUE;
        for (WindowContext windowContext : windows) {
            WindowInstance instance = windowContext.instance();
            if (!worldKey.equals(instance.worldKey())) {
                continue;
            }
            Vec3 anchor = useLivePassengerAnchor && instance.virtualHolder() != null
                    ? instance.virtualHolder().worldAnchor()
                    : instance.currentAnchor();
            CoordinateTransformer.WindowBasis basis = this.transformer.basis(instance.positionMode(), instance.currentYaw(), instance.currentPitch());
            for (WindowComponentRuntime runtime : instance.runtimes()) {
                PanelComponentDefinition panel = runtime.definition() instanceof PanelComponentDefinition definition
                        ? definition
                        : null;
                if (placementSurface ? panel == null : !runtime.interactive()) {
                    continue;
                }
                Vector3f hitCenter = placementSurface
                        ? new Vector3f(runtime.localPosition()).add(0.0f, panel.size().height() / 2.0f, 0.0f)
                        : runtime.hitCenterLocalPosition();
                float halfWidth = placementSurface ? panel.size().width() / 2.0f : runtime.hitHalfWidth();
                float halfHeight = placementSurface ? panel.size().height() / 2.0f : runtime.hitHalfHeight();
                Vec3 center = this.transformer.toWorld(anchor, hitCenter, instance.positionMode(), instance.currentYaw(), instance.currentPitch());
                double distance = this.transformer.raycastQuadDistance(
                        start,
                        normalizedDirection,
                        center,
                        basis.right(),
                        basis.up(),
                        basis.normal(),
                        halfWidth,
                        halfHeight,
                        runtime.maxDistance()
                );
                if (distance < 0.0D) {
                    continue;
                }
                double squared = distance * distance;
                if (squared < closest) {
                    closest = squared;
                    best = new UiHitResult(
                            instance.windowId(),
                            windowContext.navigationContext(),
                            runtime.definition().id(),
                            runtime,
                            placementSurface ? ComponentAction.togglePlacementTracking() : runtime.action(),
                            start.add(normalizedDirection.scale(distance)),
                            squared
                    );
                }
            }
        }
        return best;
    }
}

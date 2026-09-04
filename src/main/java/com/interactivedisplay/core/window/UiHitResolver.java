package com.interactivedisplay.core.window;

import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class UiHitResolver {
    private final WindowStateStore stateStore;
    private final CoordinateTransformer transformer;

    UiHitResolver(WindowStateStore stateStore, CoordinateTransformer transformer) {
        this.stateStore = stateStore;
        this.transformer = transformer;
    }

    UiHitResult findUiHit(ServerPlayer player) {
        return findUiHit(
                player.getUUID(),
                player.level().dimension(),
                player.getEyePosition(),
                player.getViewVector(1.0f).normalize()
        );
    }

    UiHitResult findUiHit(UUID owner, ResourceKey<Level> worldKey, Vec3 start, Vec3 direction) {
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
            CoordinateTransformer.WindowBasis basis = this.transformer.basis(instance.positionMode(), instance.currentYaw(), instance.currentPitch());
            for (WindowComponentRuntime runtime : instance.runtimes()) {
                if (!runtime.interactive()) {
                    continue;
                }
                Vec3 center = this.transformer.toWorld(instance.currentAnchor(), runtime.localPosition(), instance.positionMode(), instance.currentYaw(), instance.currentPitch());
                double distance = this.transformer.raycastQuadDistance(
                        start,
                        normalizedDirection,
                        center,
                        basis.right(),
                        basis.up(),
                        basis.normal(),
                        runtime.hitHalfWidth(),
                        runtime.hitHalfHeight(),
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
                            runtime.action(),
                            start.add(normalizedDirection.scale(distance)),
                            squared
                    );
                }
            }
        }
        return best;
    }
}

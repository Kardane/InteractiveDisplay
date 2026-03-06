package com.interactivedisplay.core.window;

import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import java.util.List;
import java.util.UUID;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

final class UiHitResolver {
    private final WindowStateStore stateStore;
    private final CoordinateTransformer transformer;

    UiHitResolver(WindowStateStore stateStore, CoordinateTransformer transformer) {
        this.stateStore = stateStore;
        this.transformer = transformer;
    }

    UiHitResult findUiHit(ServerPlayerEntity player) {
        return findUiHit(
                player.getUuid(),
                player.getWorld().getRegistryKey(),
                player.getEyePos(),
                player.getRotationVec(1.0f).normalize()
        );
    }

    UiHitResult findUiHit(UUID owner, RegistryKey<World> worldKey, Vec3d start, Vec3d direction) {
        List<WindowContext> windows = this.stateStore.ownerWindowContexts(owner);
        if (windows.isEmpty()) {
            return null;
        }

        Vec3d normalizedDirection = direction.normalize();
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
                Vec3d center = this.transformer.toWorld(instance.currentAnchor(), runtime.localPosition(), instance.positionMode(), instance.currentYaw(), instance.currentPitch());
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
                            start.add(normalizedDirection.multiply(distance)),
                            squared
                    );
                }
            }
        }
        return best;
    }
}

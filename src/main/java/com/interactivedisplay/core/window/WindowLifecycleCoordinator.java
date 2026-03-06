package com.interactivedisplay.core.window;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.core.interaction.CommandWhitelist;
import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.layout.LayoutComponent;
import com.interactivedisplay.core.layout.LayoutEngine;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.positioning.WindowOffset;
import com.interactivedisplay.core.positioning.WindowPositionTracker;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.DisplayEntityFactory;
import com.interactivedisplay.entity.DisplayEntityPool;
import com.interactivedisplay.entity.EntitySpawnException;
import com.interactivedisplay.item.InteractiveDisplayItems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class WindowLifecycleCoordinator {
    private static final DustParticleEffect BUTTON_HOVER_PARTICLE = new DustParticleEffect(DustParticleEffect.RED, 0.5f);

    private final MinecraftServer server;
    private final WindowStateStore stateStore;
    private final LayoutEngine layoutEngine;
    private final CoordinateTransformer transformer;
    private final WindowPositionTracker positionTracker;
    private final DisplayEntityFactory entityFactory;
    private final DebugRecorder debugRecorder;
    private final CommandWhitelist commandWhitelist;
    private final CallbackRegistry callbackRegistry;
    private final DisplayEntityPool displayEntityPool = new DisplayEntityPool();
    private final WindowPlacementController placementController;

    WindowLifecycleCoordinator(MinecraftServer server,
                               WindowStateStore stateStore,
                               LayoutEngine layoutEngine,
                               CoordinateTransformer transformer,
                               WindowPositionTracker positionTracker,
                               DisplayEntityFactory entityFactory,
                               DebugRecorder debugRecorder,
                               CommandWhitelist commandWhitelist,
                               CallbackRegistry callbackRegistry) {
        this.server = server;
        this.stateStore = stateStore;
        this.layoutEngine = layoutEngine;
        this.transformer = transformer;
        this.positionTracker = positionTracker;
        this.entityFactory = entityFactory;
        this.debugRecorder = debugRecorder;
        this.commandWhitelist = commandWhitelist;
        this.callbackRegistry = callbackRegistry;
        this.placementController = new WindowPlacementController(transformer);
    }

    CreateWindowResult createWindow(ServerPlayerEntity player, String windowId, PositionMode positionMode, Vec3d overrideAnchor) {
        float fixedYaw = positionMode == PositionMode.FIXED ? player.getYaw() : 0.0f;
        return createWindow(player, windowId, positionMode, overrideAnchor, fixedYaw, 0.0f);
    }

    CreateWindowResult createWindow(ServerPlayerEntity player,
                                    String windowId,
                                    PositionMode positionMode,
                                    Vec3d overrideAnchor,
                                    float fixedYaw,
                                    float fixedPitch) {
        removeWindowInternal(player.getUuid(), windowId, false);
        SpawnedWindow spawned = spawnWindowInstance(
                player,
                windowId,
                positionMode,
                overrideAnchor,
                fixedYaw,
                fixedPitch,
                null,
                null,
                null
        );
        if (!spawned.result().success()) {
            return spawned.result();
        }
        this.stateStore.putActiveWindow(player.getUuid(), windowId, spawned.instance());
        return spawned.result();
    }

    CreateWindowResult createGroup(ServerPlayerEntity player,
                                   String groupId,
                                   PositionMode positionMode,
                                   Vec3d baseAnchor,
                                   float baseYaw,
                                   float basePitch) {
        WindowGroupDefinition groupDefinition = this.stateStore.groupDefinition(groupId);
        if (groupDefinition == null) {
            return CreateWindowResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, player.getUuid(), player.getGameProfile().getName(), groupId, null, null, 0, 0, "그룹 정의를 찾을 수 없음");
        }
        WindowGroupEntry initialEntry = groupDefinition.entry(groupDefinition.initialWindowId());
        if (initialEntry == null) {
            return CreateWindowResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, player.getUuid(), player.getGameProfile().getName(), groupDefinition.initialWindowId(), null, null, 0, 0, "초기 그룹 창 정의를 찾을 수 없음");
        }

        removeGroupInternal(player.getUuid(), groupId, false);
        GroupPlacement placement = initialGroupPlacement(player, positionMode, baseAnchor, baseYaw, basePitch);
        SpawnedWindow spawned = spawnGroupWindow(player, groupDefinition, initialEntry, positionMode, placement.baseAnchor(), placement.baseYaw(), placement.basePitch());
        if (!spawned.result().success()) {
            return spawned.result();
        }
        this.stateStore.putActiveGroup(player.getUuid(), groupId, new WindowGroupInstance(
                player.getUuid(),
                groupId,
                placement.baseAnchor(),
                placement.baseYaw(),
                placement.basePitch(),
                positionMode,
                initialEntry.windowId(),
                spawned.instance()
        ));
        return spawned.result();
    }

    CreateWindowResult rebuildWindow(UUID owner, String windowId) {
        WindowInstance instance = this.stateStore.findActiveWindow(owner, windowId);
        if (instance == null) {
            return CreateWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, null, windowId, null, null, 0, 0, "재구성 대상 활성 창이 없음");
        }
        ServerPlayerEntity player = player(owner);
        if (player == null) {
            return CreateWindowResult.failure(DebugReason.ACTION_EXECUTION_FAILED, owner, null, windowId, null, instance.currentAnchor(), 0, 0, "플레이어를 찾을 수 없음");
        }
        return createWindow(player, windowId, instance.positionMode(), instance.fixedAnchor(), instance.fixedYaw(), instance.fixedPitch());
    }

    CreateWindowResult rebuildGroup(UUID owner, String groupId) {
        WindowGroupInstance instance = this.stateStore.findActiveGroup(owner, groupId);
        if (instance == null) {
            return CreateWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, null, groupId, null, null, 0, 0, "재구성 대상 활성 그룹이 없음");
        }
        ServerPlayerEntity player = player(owner);
        if (player == null) {
            return CreateWindowResult.failure(DebugReason.ACTION_EXECUTION_FAILED, owner, null, instance.currentWindowId(), null, null, 0, 0, "플레이어를 찾을 수 없음");
        }
        return openGroupWindow(owner, groupId, instance.currentMode(), instance.currentWindowId(), instance.baseAnchor(), instance.baseYaw(), instance.basePitch(), player);
    }

    void expirePooledEntities(long currentTick) {
        this.displayEntityPool.expire(currentTick, runtime -> this.entityFactory.destroyRuntime(this.server, runtime));
    }

    int pooledEntityCount() {
        return this.displayEntityPool.size();
    }

    void handlePlayerJoin(ServerPlayerEntity player) {
        for (WindowInstance instance : this.stateStore.ownerWindows(player.getUuid())) {
            syncCanvases(instance, List.of(player));
        }
    }

    RemoveWindowResult removeWindow(UUID owner, String windowId) {
        return removeWindowInternal(owner, windowId, true);
    }

    RemoveWindowResult removeWindowSilently(UUID owner, String windowId) {
        return removeWindowInternal(owner, windowId, false);
    }

    RemoveWindowResult removeGroup(UUID owner, String groupId) {
        return removeGroupInternal(owner, groupId, true);
    }

    RemoveWindowResult removeGroupSilently(UUID owner, String groupId) {
        return removeGroupInternal(owner, groupId, false);
    }

    void removeAll(UUID owner) {
        for (String windowId : this.stateStore.activeWindowIds(owner)) {
            removeWindowInternal(owner, windowId, false);
        }
        for (String groupId : this.stateStore.activeGroupIds(owner)) {
            removeGroupInternal(owner, groupId, false);
        }
    }

    RemoveWindowResult closeWindow(UUID owner, WindowNavigationContext context) {
        if (context.groupId() != null) {
            return removeGroup(owner, context.groupId());
        }
        return removeWindow(owner, context.windowId());
    }

    CreateWindowResult openWindow(UUID owner, WindowNavigationContext context, String windowId) {
        ServerPlayerEntity player = player(owner);
        if (player == null) {
            return CreateWindowResult.failure(DebugReason.ACTION_EXECUTION_FAILED, owner, null, windowId, null, null, 0, 0, "action 대상 플레이어를 찾을 수 없음");
        }
        if (context.groupId() != null) {
            return openGroupWindow(owner, context.groupId(), context.positionMode(), windowId, context.fixedAnchor(), context.fixedYaw(), context.fixedPitch(), player);
        }
        if (!this.stateStore.hasDefinition(windowId)) {
            return CreateWindowResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, owner, player.getGameProfile().getName(), windowId, null, null, 0, 0, "창 정의를 찾을 수 없음");
        }
        removeWindowInternal(owner, context.windowId(), false);
        return createWindow(player, windowId, context.positionMode(), context.fixedAnchor(), context.fixedYaw(), context.fixedPitch());
    }

    CreateWindowResult switchMode(UUID owner, WindowNavigationContext context, PositionMode positionMode) {
        ServerPlayerEntity player = player(owner);
        if (player == null) {
            return CreateWindowResult.failure(DebugReason.ACTION_EXECUTION_FAILED, owner, null, context.windowId(), null, null, 0, 0, "action 대상 플레이어를 찾을 수 없음");
        }
        if (context.groupId() != null) {
            return openGroupWindow(owner, context.groupId(), positionMode, context.windowId(), context.fixedAnchor(), 0.0f, 0.0f, player);
        }

        removeWindowInternal(owner, context.windowId(), false);
        return switch (positionMode) {
            case FIXED -> createWindow(player, context.windowId(), PositionMode.FIXED, context.fixedAnchor(), context.fixedYaw(), 0.0f);
            case PLAYER_FIXED -> createWindow(player, context.windowId(), PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            case PLAYER_VIEW -> createWindow(player, context.windowId(), PositionMode.PLAYER_VIEW, null, 0.0f, 0.0f);
        };
    }

    ActionExecutionResult runCommand(UUID owner, UiHitResult hitResult, Integer permissionLevel, String command) {
        ServerPlayerEntity player = player(owner);
        if (player == null) {
            return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "command 대상 플레이어를 찾을 수 없음");
        }
        if (!this.commandWhitelist.isAllowed(command)) {
            return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "허용되지 않은 명령 prefix");
        }

        try {
            ServerWorld commandWorld = world(hitResult.runtime().worldKey());
            CommandActionSourceContext sourceContext = CommandActionSourceContext.of(
                    hitResult.runtime().worldKey(),
                    hitResult.hitPosition(),
                    player.getYaw(),
                    player.getPitch(),
                    permissionLevel,
                    command
            );
            ServerCommandSource source = player.getCommandSource()
                    .withSilent()
                    .withWorld(commandWorld == null ? player.getWorld() : commandWorld)
                    .withPosition(sourceContext.position())
                    .withRotation(new Vec2f(sourceContext.pitch(), sourceContext.yaw()));
            if (sourceContext.hasPermissionOverride()) {
                source = source.withLevel(sourceContext.permissionLevel());
            }
            this.server.getCommandManager().executeWithPrefix(source, sourceContext.normalizedCommand());
            InteractiveDisplay.LOGGER.info("[{}] run_command player={} windowId={} componentId={} command={}", InteractiveDisplay.MOD_ID, player.getGameProfile().getName(), hitResult.windowId(), hitResult.componentId(), command);
            return ActionExecutionResult.success("run_command 처리 완료");
        } catch (RuntimeException exception) {
            return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "run_command 실패: " + exception.getMessage());
        }
    }

    ActionExecutionResult executeCallback(UUID owner, String windowId, String componentId, String callbackId) {
        ServerPlayerEntity player = player(owner);
        if (player == null) {
            return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "callback 대상 플레이어를 찾을 수 없음");
        }
        return this.callbackRegistry.find(callbackId)
                .map(callback -> {
                    callback.execute(player, windowId, componentId);
                    return ActionExecutionResult.success("callback 처리 완료");
                })
                .orElseGet(() -> ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "등록되지 않은 callback id"));
    }

    ActionExecutionResult togglePlacementTracking(UUID owner, WindowNavigationContext context) {
        ServerPlayerEntity player = player(owner);
        if (player == null) {
            return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "action 대상 플레이어를 찾을 수 없음");
        }
        if (context.positionMode() == PositionMode.PLAYER_VIEW) {
            return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "PLAYER_VIEW 배치 추적은 지원하지 않음");
        }
        if (context.groupId() != null) {
            WindowGroupInstance groupInstance = this.stateStore.findActiveGroup(owner, context.groupId());
            if (groupInstance == null) {
                return ActionExecutionResult.failure(DebugReason.NO_ACTIVE_WINDOW, "배치 추적 대상 그룹이 없음");
            }
            if (groupInstance.currentMode() != PositionMode.PLAYER_FIXED) {
                return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "그룹 배치 추적은 PLAYER_FIXED만 지원");
            }
            if (this.placementController.isTracking(owner, context)) {
                this.placementController.stop(owner);
                WindowGroupDefinition groupDefinition = this.stateStore.groupDefinition(context.groupId());
                WindowDefinition definition = this.stateStore.definition(groupInstance.currentWindowId());
                if (groupDefinition == null || definition == null) {
                    return ActionExecutionResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, "배치 추적 커밋 대상 정의를 찾을 수 없음");
                }
                WindowPlacementController.GroupCommit commit = this.placementController.commitGroup(groupInstance, groupDefinition, definition, player.getEyePos(), player.getYaw(), player.getPitch());
                CreateWindowResult result = openGroupWindow(owner, context.groupId(), groupInstance.currentMode(), groupInstance.currentWindowId(), commit.baseAnchor(), commit.baseYaw(), commit.basePitch(), player);
                return result.success()
                        ? ActionExecutionResult.success("toggle_placement_tracking 처리 완료")
                        : ActionExecutionResult.failure(result.reasonCode(), result.message());
            }
            this.placementController.start(owner, context);
            return ActionExecutionResult.success("toggle_placement_tracking 처리 완료");
        }

        WindowInstance instance = this.stateStore.findActiveWindow(owner, context.windowId());
        if (instance == null) {
            return ActionExecutionResult.failure(DebugReason.NO_ACTIVE_WINDOW, "배치 추적 대상 창이 없음");
        }
        if (this.placementController.isTracking(owner, context)) {
            this.placementController.stop(owner);
            WindowDefinition definition = this.stateStore.definition(context.windowId());
            if (definition == null) {
                return ActionExecutionResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, "배치 추적 커밋 대상 창 정의를 찾을 수 없음");
            }
            WindowPlacementController.StandaloneCommit commit = this.placementController.commitStandalone(instance, definition, player.getEyePos(), player.getYaw(), player.getPitch());
            CreateWindowResult result = createWindow(player, context.windowId(), instance.positionMode(), commit.fixedAnchor(), commit.fixedYaw(), commit.fixedPitch());
            return result.success()
                    ? ActionExecutionResult.success("toggle_placement_tracking 처리 완료")
                    : ActionExecutionResult.failure(result.reasonCode(), result.message());
        }
        this.placementController.start(owner, context);
        return ActionExecutionResult.success("toggle_placement_tracking 처리 완료");
    }

    boolean isPlacementTracking(UUID owner, WindowInstance instance) {
        return this.placementController.isTracking(owner, instance);
    }

    WindowPositionTracker.WindowTransformState resolveTransform(ServerPlayerEntity player, WindowInstance instance, WindowDefinition definition) {
        if (!this.placementController.isTracking(player.getUuid(), instance)) {
            return this.positionTracker.resolve(player, instance.positionMode(), definition.offset(), instance.fixedAnchor(), instance.fixedYaw(), instance.fixedPitch());
        }
        if (instance.groupId() != null) {
            WindowGroupInstance groupInstance = this.stateStore.findActiveGroup(player.getUuid(), instance.groupId());
            WindowGroupDefinition groupDefinition = this.stateStore.groupDefinition(instance.groupId());
            if (groupInstance != null && groupDefinition != null) {
                return this.placementController.previewGroup(groupInstance, groupDefinition, definition, player.getEyePos(), player.getYaw(), player.getPitch());
            }
        }
        return this.placementController.previewStandalone(instance, definition, player.getEyePos(), player.getYaw(), player.getPitch());
    }

    void moveWindow(WindowInstance instance,
                    WindowPositionTracker.WindowTransformState nextState,
                    ServerWorld world) {
        this.entityFactory.moveRoot(world, instance.rootEntityId(), nextState.anchor(), instance.positionMode(), nextState.yaw(), nextState.pitch());
        for (WindowComponentRuntime runtime : instance.runtimes()) {
            Vec3d componentWorldPosition = this.transformer.toWorld(
                    nextState.anchor(),
                    runtime.localPosition(),
                    instance.positionMode(),
                    nextState.yaw(),
                    nextState.pitch()
            );
            this.entityFactory.moveRuntime(world, runtime, componentWorldPosition, instance.positionMode(), nextState.yaw(), nextState.pitch());
        }
    }

    void syncCanvases(WindowInstance instance, Collection<ServerPlayerEntity> viewers) {
        for (WindowComponentRuntime runtime : instance.runtimes()) {
            if (runtime.mapCanvas() != null) {
                this.entityFactory.syncMapCanvas(runtime.mapCanvas(), viewers);
            }
        }
    }

    void updateHover(ServerPlayerEntity player, List<WindowInstance> windows, UiHitResult hovered) {
        if (windows.isEmpty()) {
            return;
        }

        if (!InteractiveDisplayItems.isPointer(player.getMainHandStack())) {
            clearHover(windows);
            return;
        }

        WindowComponentRuntime hoveredRuntime = hovered == null ? null : hovered.runtime();
        if (hovered != null) {
            Vec3d hit = hovered.hitPosition();
            player.getWorld().spawnParticles(BUTTON_HOVER_PARTICLE, hit.x, hit.y, hit.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        for (WindowInstance instance : windows) {
            ServerWorld world = world(instance.worldKey());
            if (world == null) {
                continue;
            }
            for (WindowComponentRuntime runtime : instance.runtimes()) {
                if (!(runtime.definition() instanceof ButtonComponentDefinition button)) {
                    continue;
                }
                boolean shouldHover = runtime == hoveredRuntime && instance.worldKey().equals(player.getWorld().getRegistryKey());
                if (runtime.hovered() != shouldHover) {
                    this.entityFactory.setButtonHover(this.server, world, player.getUuid(), runtime, button, shouldHover, instance.positionMode());
                }
            }
        }
    }

    private SpawnedWindow spawnWindowInstance(ServerPlayerEntity player,
                                              String windowId,
                                              PositionMode positionMode,
                                              Vec3d overrideAnchor,
                                              float fixedYaw,
                                              float fixedPitch,
                                              WindowOffset offsetOverride,
                                              String groupId,
                                              String groupWindowId) {
        WindowDefinition definition = this.stateStore.definition(windowId);
        String playerName = player.getGameProfile().getName();
        if (definition == null) {
            CreateWindowResult result = CreateWindowResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, player.getUuid(), playerName, windowId, null, overrideAnchor, 0, 0, "창 정의를 찾을 수 없음");
            recordCreate(DebugLevel.WARN, positionMode, result);
            return new SpawnedWindow(null, result);
        }

        WindowOffset effectiveOffset = offsetOverride == null ? definition.offset() : offsetOverride;
        WindowPositionTracker.WindowTransformState transformState = this.positionTracker.resolve(player, positionMode, effectiveOffset, overrideAnchor, fixedYaw, fixedPitch);
        List<LayoutComponent> layout = this.layoutEngine.calculate(definition);
        ServerWorld world = player.getWorld();
        long tick = this.server == null ? 0L : this.server.getTicks();
        UUID rootEntityId = this.entityFactory.spawnRoot(this.server, world, transformState.anchor(), positionMode, transformState.yaw(), transformState.pitch());
        WindowInstance instance = new WindowInstance(
                player.getUuid(),
                windowId,
                groupId,
                groupWindowId,
                world.getRegistryKey(),
                positionMode,
                positionMode == PositionMode.FIXED ? transformState.anchor() : overrideAnchor,
                fixedYaw,
                positionMode == PositionMode.PLAYER_FIXED || positionMode == PositionMode.PLAYER_VIEW ? fixedPitch : 0.0f,
                rootEntityId,
                transformState.anchor(),
                transformState.yaw(),
                transformState.pitch(),
                transformState.anchor(),
                transformState.yaw(),
                transformState.pitch(),
                tick
        );

        int spawnedEntityCount = 1;
        List<WindowComponentRuntime> activatedRuntimes = new ArrayList<>();
        try {
            for (LayoutComponent layoutComponent : layout) {
                ComponentDefinition component = layoutComponent.definition();
                if (!component.visible()) {
                    continue;
                }

                String signature = buildSignature(component);
                Vec3d componentWorldPosition = this.transformer.toWorld(
                        transformState.anchor(),
                        layoutComponent.localPosition(),
                        positionMode,
                        transformState.yaw(),
                        transformState.pitch()
                );
                WindowComponentRuntime runtime = this.displayEntityPool.acquire(player.getUuid(), world.getRegistryKey(), signature, tick);
                if (runtime != null) {
                    runtime.redefine(component, layoutComponent.localPosition());
                    this.entityFactory.reconfigureRuntime(this.server, world, player.getUuid(), runtime, componentWorldPosition, positionMode, transformState.yaw(), transformState.pitch(), world.getPlayers());
                } else {
                    runtime = this.entityFactory.spawnRuntime(this.server, world, player.getUuid(), signature, component, componentWorldPosition, positionMode, transformState.yaw(), transformState.pitch(), world.getPlayers());
                    runtime.redefine(component, layoutComponent.localPosition());
                }

                instance.addRuntime(runtime);
                activatedRuntimes.add(runtime);
                spawnedEntityCount += runtime.entityIds().size();
            }
            CreateWindowResult result = CreateWindowResult.success(player.getUuid(), playerName, windowId, transformState.anchor(), layout.size(), spawnedEntityCount, "창 생성 완료");
            recordCreate(DebugLevel.DEBUG, positionMode, result);
            return new SpawnedWindow(instance, result);
        } catch (EntitySpawnException exception) {
            cleanupFailedCreate(activatedRuntimes);
            this.entityFactory.destroyRoot(this.server, world.getRegistryKey(), rootEntityId);
            CreateWindowResult result = CreateWindowResult.failure(DebugReason.ENTITY_SPAWN_FAILED, player.getUuid(), playerName, windowId, componentIdFrom(exception.getMessage()), transformState.anchor(), layout.size(), spawnedEntityCount, exception.getMessage());
            recordCreate(DebugLevel.WARN, positionMode, result);
            return new SpawnedWindow(null, result);
        } catch (RuntimeException exception) {
            cleanupFailedCreate(activatedRuntimes);
            this.entityFactory.destroyRoot(this.server, world.getRegistryKey(), rootEntityId);
            CreateWindowResult result = CreateWindowResult.failure(DebugReason.ENTITY_SPAWN_FAILED, player.getUuid(), playerName, windowId, null, transformState.anchor(), layout.size(), spawnedEntityCount, "창 생성 중 예외 발생: " + exception.getMessage());
            this.debugRecorder.record(DebugEventType.WINDOW_CREATE, DebugLevel.ERROR, player.getUuid(), playerName, windowId, null, null, DebugReason.ENTITY_SPAWN_FAILED, result.message(), exception);
            InteractiveDisplay.LOGGER.error("[{}] window create error player={} windowId={} mode={} anchor={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, playerName, windowId, positionMode, transformState.anchor(), result.reasonCode(), result.message(), exception);
            return new SpawnedWindow(null, result);
        }
    }

    private RemoveWindowResult removeWindowInternal(UUID owner, String windowId, boolean recordDebug) {
        List<String> activeWindowIds = this.stateStore.activeWindowIds(owner);
        if (activeWindowIds.isEmpty()) {
            RemoveWindowResult result = RemoveWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, windowId, "활성 창이 없음");
            if (recordDebug) {
                recordRemove(DebugLevel.WARN, result);
            }
            return result;
        }
        WindowInstance instance = this.stateStore.removeActiveWindow(owner, windowId);
        if (instance == null) {
            RemoveWindowResult result = RemoveWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, windowId, "제거 대상 창이 없음");
            if (recordDebug) {
                recordRemove(DebugLevel.WARN, result);
            }
            return result;
        }

        releaseWindowInstance(instance, owner);
        this.placementController.stopIfTracking(owner, windowId, null);

        RemoveWindowResult result = RemoveWindowResult.success(owner, windowId, instance.entityIds().size(), "창 제거 완료");
        if (recordDebug) {
            recordRemove(DebugLevel.DEBUG, result);
        }
        return result;
    }

    private RemoveWindowResult removeGroupInternal(UUID owner, String groupId, boolean recordDebug) {
        List<String> activeGroupIds = this.stateStore.activeGroupIds(owner);
        if (activeGroupIds.isEmpty()) {
            RemoveWindowResult result = RemoveWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, groupId, "활성 그룹이 없음");
            if (recordDebug) {
                recordRemove(DebugLevel.WARN, result);
            }
            return result;
        }
        WindowGroupInstance groupInstance = this.stateStore.removeActiveGroup(owner, groupId);
        if (groupInstance == null) {
            RemoveWindowResult result = RemoveWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, groupId, "제거 대상 그룹이 없음");
            if (recordDebug) {
                recordRemove(DebugLevel.WARN, result);
            }
            return result;
        }

        releaseWindowInstance(groupInstance.currentWindow(), owner);
        this.placementController.stopIfTracking(owner, groupInstance.currentWindowId(), groupId);

        RemoveWindowResult result = RemoveWindowResult.success(owner, groupId, groupInstance.currentWindow().entityIds().size(), "그룹 제거 완료");
        if (recordDebug) {
            recordRemove(DebugLevel.DEBUG, result);
        }
        return result;
    }

    private void releaseWindowInstance(WindowInstance instance, UUID owner) {
        ServerWorld world = world(instance.worldKey());
        if (world == null) {
            return;
        }
        long tick = this.server == null ? 0L : this.server.getTicks();
        for (WindowComponentRuntime runtime : instance.runtimes()) {
            this.entityFactory.deactivateRuntime(this.server, world, runtime);
            this.displayEntityPool.release(owner, instance.worldKey(), runtime, tick);
        }
        this.entityFactory.destroyRoot(this.server, instance.worldKey(), instance.rootEntityId());
    }

    private SpawnedWindow spawnGroupWindow(ServerPlayerEntity player,
                                           WindowGroupDefinition groupDefinition,
                                           WindowGroupEntry entry,
                                           PositionMode positionMode,
                                           Vec3d baseAnchor,
                                           float baseYaw,
                                           float basePitch) {
        WindowDefinition definition = this.stateStore.definition(entry.windowId());
        if (definition == null) {
            CreateWindowResult result = CreateWindowResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, player.getUuid(), player.getGameProfile().getName(), entry.windowId(), null, null, 0, 0, "그룹 대상 창 정의를 찾을 수 없음");
            return new SpawnedWindow(null, result);
        }
        WindowOffset effectiveOffset = definition.offset().plus(entry.offset());
        float resolvedYaw = MathHelper.wrapDegrees(baseYaw + entry.orbit().yaw());
        float resolvedPitch = MathHelper.clamp(basePitch + entry.orbit().pitch(), -90.0f, 90.0f);
        Vec3d overrideAnchor = positionMode == PositionMode.FIXED
                ? baseAnchor.add(this.transformer.orbitOffset(effectiveOffset, resolvedYaw, resolvedPitch))
                : null;
        WindowOffset runtimeOffset = positionMode == PositionMode.FIXED ? WindowOffset.zero() : effectiveOffset;
        return spawnWindowInstance(
                player,
                entry.windowId(),
                positionMode,
                overrideAnchor,
                positionMode == PositionMode.PLAYER_FIXED || positionMode == PositionMode.PLAYER_VIEW ? resolvedYaw : baseYaw,
                positionMode == PositionMode.PLAYER_FIXED || positionMode == PositionMode.PLAYER_VIEW ? resolvedPitch : basePitch,
                runtimeOffset,
                groupDefinition.id(),
                entry.windowId()
        );
    }

    private CreateWindowResult openGroupWindow(UUID owner,
                                               String groupId,
                                               PositionMode positionMode,
                                               String windowId,
                                               Vec3d baseAnchor,
                                               float baseYaw,
                                               float basePitch,
                                               ServerPlayerEntity player) {
        WindowGroupDefinition groupDefinition = this.stateStore.groupDefinition(groupId);
        if (groupDefinition == null) {
            return CreateWindowResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, owner, player.getGameProfile().getName(), groupId, null, null, 0, 0, "그룹 정의를 찾을 수 없음");
        }
        WindowGroupEntry entry = groupDefinition.entry(windowId);
        if (entry == null) {
            if (!this.stateStore.hasDefinition(windowId)) {
                return CreateWindowResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, owner, player.getGameProfile().getName(), windowId, null, null, 0, 0, "창 정의를 찾을 수 없음");
            }
            removeGroupInternal(owner, groupId, false);
            return createWindow(player, windowId, positionMode, baseAnchor, baseYaw, basePitch);
        }
        WindowGroupInstance currentGroup = this.stateStore.findActiveGroup(owner, groupId);
        Vec3d resolvedBaseAnchor = baseAnchor;
        if (resolvedBaseAnchor == null) {
            resolvedBaseAnchor = positionMode == PositionMode.FIXED ? player.getEyePos() : currentGroup != null ? currentGroup.baseAnchor() : null;
        }
        SpawnedWindow spawned = spawnGroupWindow(player, groupDefinition, entry, positionMode, resolvedBaseAnchor, baseYaw, basePitch);
        if (!spawned.result().success()) {
            return spawned.result();
        }
        if (currentGroup != null) {
            releaseWindowInstance(currentGroup.currentWindow(), owner);
        }
        this.stateStore.putActiveGroup(owner, groupId, new WindowGroupInstance(owner, groupId, resolvedBaseAnchor, baseYaw, basePitch, positionMode, windowId, spawned.instance()));
        return spawned.result();
    }

    private GroupPlacement initialGroupPlacement(ServerPlayerEntity player,
                                                 PositionMode positionMode,
                                                 Vec3d baseAnchor,
                                                 float baseYaw,
                                                 float basePitch) {
        Vec3d resolvedBaseAnchor = baseAnchor;
        if (positionMode == PositionMode.FIXED && resolvedBaseAnchor == null) {
            resolvedBaseAnchor = player.getEyePos();
        }
        return new GroupPlacement(resolvedBaseAnchor, baseYaw, basePitch);
    }

    private void cleanupFailedCreate(List<WindowComponentRuntime> runtimes) {
        for (WindowComponentRuntime runtime : runtimes) {
            this.entityFactory.destroyRuntime(this.server, runtime);
        }
    }

    private void clearHover(Collection<WindowInstance> windows) {
        for (WindowInstance instance : windows) {
            ServerWorld world = world(instance.worldKey());
            if (world == null) {
                continue;
            }
            for (WindowComponentRuntime runtime : instance.runtimes()) {
                if (!(runtime.definition() instanceof ButtonComponentDefinition button)) {
                    continue;
                }
                if (runtime.hovered()) {
                    this.entityFactory.setButtonHover(this.server, world, instance.owner(), runtime, button, false, instance.positionMode());
                }
            }
        }
    }

    private ServerPlayerEntity player(UUID owner) {
        if (this.server == null) {
            return null;
        }
        return this.server.getPlayerManager().getPlayer(owner);
    }

    private ServerWorld world(net.minecraft.registry.RegistryKey<net.minecraft.world.World> worldKey) {
        if (this.server == null) {
            return null;
        }
        return this.server.getWorld(worldKey);
    }

    private void recordCreate(DebugLevel level, PositionMode positionMode, CreateWindowResult result) {
        this.debugRecorder.record(DebugEventType.WINDOW_CREATE, level, result.playerUuid(), result.playerName(), result.windowId(), result.componentId(), null, result.reasonCode(), result.message(), null);
        if (level == DebugLevel.DEBUG) {
            InteractiveDisplay.LOGGER.debug("[{}] window create success player={} windowId={} mode={} anchor={} layoutCount={} entityCount={}", InteractiveDisplay.MOD_ID, result.playerName(), result.windowId(), positionMode, result.anchor(), result.layoutComponentCount(), result.spawnedEntityCount());
            return;
        }
        InteractiveDisplay.LOGGER.warn("[{}] window create failed player={} windowId={} componentId={} mode={} anchor={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, result.playerName(), result.windowId(), result.componentId(), positionMode, result.anchor(), result.reasonCode(), result.message());
    }

    private void recordRemove(DebugLevel level, RemoveWindowResult result) {
        this.debugRecorder.record(DebugEventType.WINDOW_REMOVE, level, result.owner(), null, result.windowId(), null, null, result.reasonCode(), result.message(), null);
        if (level == DebugLevel.DEBUG) {
            InteractiveDisplay.LOGGER.debug("[{}] window remove success owner={} windowId={} removedEntities={}", InteractiveDisplay.MOD_ID, result.owner(), result.windowId(), result.removedEntityCount());
            return;
        }
        InteractiveDisplay.LOGGER.warn("[{}] window remove failed owner={} windowId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, result.owner(), result.windowId(), result.reasonCode(), result.message());
    }

    private static String buildSignature(ComponentDefinition component) {
        return switch (component.type()) {
            case TEXT -> "text:" + component.id();
            case BUTTON -> "button:" + component.id();
            case IMAGE -> "image:" + component.id();
            case PANEL -> "panel:" + component.id();
        };
    }

    private static String componentIdFrom(String message) {
        if (message == null) {
            return null;
        }
        int index = message.indexOf("componentId=");
        if (index < 0) {
            return null;
        }
        int valueStart = index + "componentId=".length();
        int end = message.indexOf(' ', valueStart);
        return end < 0 ? message.substring(valueStart) : message.substring(valueStart, end);
    }

    private record SpawnedWindow(WindowInstance instance, CreateWindowResult result) {
    }

    private record GroupPlacement(Vec3d baseAnchor, float baseYaw, float basePitch) {
    }
}

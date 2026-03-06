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
import com.interactivedisplay.schema.SchemaLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class WindowManager implements WindowActionExecutor {
    private static final DustParticleEffect BUTTON_HOVER_PARTICLE = new DustParticleEffect(DustParticleEffect.RED, 0.5f);

    private final MinecraftServer server;
    private final SchemaLoader schemaLoader;
    private final LayoutEngine layoutEngine;
    private final CoordinateTransformer transformer;
    private final WindowPositionTracker positionTracker;
    private final DisplayEntityFactory entityFactory;
    private final DebugRecorder debugRecorder;
    private final CommandWhitelist commandWhitelist;
    private final CallbackRegistry callbackRegistry;
    private final DisplayEntityPool displayEntityPool = new DisplayEntityPool();

    private final Map<String, WindowDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, WindowGroupDefinition> groupDefinitions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, WindowInstance>> activeWindows = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, WindowGroupInstance>> activeGroups = new ConcurrentHashMap<>();
    private final Set<String> brokenWindowIds = ConcurrentHashMap.newKeySet();
    private final Set<String> brokenGroupIds = ConcurrentHashMap.newKeySet();

    public WindowManager(MinecraftServer server,
                         SchemaLoader schemaLoader,
                         LayoutEngine layoutEngine,
                         CoordinateTransformer transformer,
                         WindowPositionTracker positionTracker,
                         DisplayEntityFactory entityFactory,
                         DebugRecorder debugRecorder,
                         CommandWhitelist commandWhitelist,
                         CallbackRegistry callbackRegistry) {
        this.server = server;
        this.schemaLoader = schemaLoader;
        this.layoutEngine = layoutEngine;
        this.transformer = transformer;
        this.positionTracker = positionTracker;
        this.entityFactory = entityFactory;
        this.debugRecorder = debugRecorder;
        this.commandWhitelist = commandWhitelist;
        this.callbackRegistry = callbackRegistry;
    }

    public ReloadWindowResult reloadAll() {
        tryReloadSupport();
        SchemaLoader.LoadResult loadResult = this.schemaLoader.loadAll();
        replaceBrokenWindowIds(loadResult.brokenWindowIds());
        replaceBrokenGroupIds(loadResult.brokenGroupIds());

        if (!loadResult.hasErrors()) {
            this.definitions.clear();
            this.definitions.putAll(loadResult.definitions());
            this.groupDefinitions.clear();
            this.groupDefinitions.putAll(loadResult.groups());
            rebuildActiveWindows(loadResult.definitions().keySet());
            rebuildActiveGroups(loadResult.groups().keySet(), loadResult.definitions().keySet());
            ReloadWindowResult result = ReloadWindowResult.success(null, this.definitions.size(), 0, loadResult.errors(), "전체 창 리로드 완료");
            recordReload(DebugLevel.DEBUG, result);
            return result;
        }

        Map<String, WindowDefinition> merged = new HashMap<>(this.definitions);
        merged.putAll(loadResult.definitions());
        this.definitions.clear();
        this.definitions.putAll(merged);
        Map<String, WindowGroupDefinition> mergedGroups = new HashMap<>(this.groupDefinitions);
        mergedGroups.putAll(loadResult.groups());
        this.groupDefinitions.clear();
        this.groupDefinitions.putAll(mergedGroups);
        rebuildActiveWindows(loadResult.definitions().keySet());
        rebuildActiveGroups(loadResult.groups().keySet(), loadResult.definitions().keySet());
        ReloadWindowResult result = ReloadWindowResult.failure(DebugReason.SCHEMA_VALIDATION_FAILED, null, this.definitions.size(), loadResult.errors().size(), loadResult.errors(), "전체 창 리로드 완료 (오류 " + loadResult.errors().size() + "건)");
        recordReload(DebugLevel.WARN, result);
        return result;
    }

    public ReloadWindowResult reloadOne(String windowId) {
        tryReloadSupport();
        SchemaLoader.LoadResult loadResult = this.schemaLoader.loadAll();
        replaceBrokenWindowIds(loadResult.brokenWindowIds());
        replaceBrokenGroupIds(loadResult.brokenGroupIds());
        WindowDefinition definition = loadResult.definitions().get(windowId);
        if (definition == null) {
            DebugReason reasonCode = loadResult.hasErrors() ? DebugReason.SCHEMA_VALIDATION_FAILED : DebugReason.WINDOW_DEFINITION_NOT_FOUND;
            ReloadWindowResult result = ReloadWindowResult.failure(reasonCode, windowId, this.definitions.size(), loadResult.errors().size(), loadResult.errors(), "창 리로드 실패: " + windowId);
            recordReload(DebugLevel.WARN, result);
            return result;
        }

        this.definitions.put(windowId, definition);
        rebuildActiveWindowDefinitions(windowId);
        rebuildActiveGroupsContainingWindow(windowId);
        if (loadResult.hasErrors()) {
            ReloadWindowResult result = ReloadWindowResult.failure(DebugReason.SCHEMA_VALIDATION_FAILED, windowId, this.definitions.size(), loadResult.errors().size(), loadResult.errors(), "창 리로드 완료 (오류 " + loadResult.errors().size() + "건): " + windowId);
            recordReload(DebugLevel.WARN, result);
            return result;
        }

        ReloadWindowResult result = ReloadWindowResult.success(windowId, this.definitions.size(), 0, loadResult.errors(), "창 리로드 완료: " + windowId);
        recordReload(DebugLevel.DEBUG, result);
        return result;
    }

    public CreateWindowResult createWindow(ServerPlayerEntity player, String windowId, PositionMode positionMode, Vec3d overrideAnchor) {
        float fixedYaw = positionMode == PositionMode.FIXED ? player.getYaw() : 0.0f;
        return createWindow(player, windowId, positionMode, overrideAnchor, fixedYaw, 0.0f);
    }

    public CreateWindowResult createWindow(ServerPlayerEntity player,
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
        this.activeWindows.computeIfAbsent(player.getUuid(), ignored -> new ConcurrentHashMap<>()).put(windowId, spawned.instance());
        return spawned.result();
    }

    public CreateWindowResult createGroup(ServerPlayerEntity player,
                                          String groupId,
                                          PositionMode positionMode,
                                          Vec3d baseAnchor,
                                          float baseYaw,
                                          float basePitch) {
        WindowGroupDefinition groupDefinition = this.groupDefinitions.get(groupId);
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
        WindowGroupInstance groupInstance = new WindowGroupInstance(
                player.getUuid(),
                groupId,
                placement.baseAnchor(),
                placement.baseYaw(),
                placement.basePitch(),
                positionMode,
                initialEntry.windowId(),
                spawned.instance()
        );
        this.activeGroups.computeIfAbsent(player.getUuid(), ignored -> new ConcurrentHashMap<>()).put(groupId, groupInstance);
        return spawned.result();
    }

    public CreateWindowResult rebuildWindow(UUID owner, String windowId) {
        WindowInstance instance = findActiveWindow(owner, windowId);
        if (instance == null) {
            return CreateWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, null, windowId, null, null, 0, 0, "재구성 대상 활성 창이 없음");
        }
        ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(owner);
        if (player == null) {
            return CreateWindowResult.failure(DebugReason.ACTION_EXECUTION_FAILED, owner, null, windowId, null, instance.currentAnchor(), 0, 0, "플레이어를 찾을 수 없음");
        }
        return createWindow(player, windowId, instance.positionMode(), instance.fixedAnchor(), instance.fixedYaw(), instance.fixedPitch());
    }

    public CreateWindowResult rebuildGroup(UUID owner, String groupId) {
        WindowGroupInstance instance = findActiveGroup(owner, groupId);
        if (instance == null) {
            return CreateWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, null, groupId, null, null, 0, 0, "재구성 대상 활성 그룹이 없음");
        }
        ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(owner);
        if (player == null) {
            return CreateWindowResult.failure(DebugReason.ACTION_EXECUTION_FAILED, owner, null, instance.currentWindowId(), null, null, 0, 0, "플레이어를 찾을 수 없음");
        }
        return openGroupWindow(owner, groupId, instance.currentMode(), instance.currentWindowId(), instance.baseAnchor(), instance.baseYaw(), instance.basePitch(), player);
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
        WindowDefinition definition = this.definitions.get(windowId);
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
        long tick = this.server.getTicks();
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
                positionMode == PositionMode.PLAYER_FIXED ? fixedPitch : 0.0f,
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
                    this.entityFactory.reconfigureRuntime(this.server, world, runtime, componentWorldPosition, positionMode, transformState.yaw(), transformState.pitch(), world.getPlayers());
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

    public void tick() {
        long currentTick = this.server.getTicks();
        this.displayEntityPool.expire(currentTick, runtime -> this.entityFactory.destroyRuntime(this.server, runtime));

        Set<UUID> owners = new TreeSet<>((left, right) -> left.compareTo(right));
        owners.addAll(this.activeWindows.keySet());
        owners.addAll(this.activeGroups.keySet());
        for (UUID owner : owners) {
            ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(owner);
            if (player == null) {
                continue;
            }

            List<WindowInstance> windows = ownerWindows(owner);
            for (WindowInstance instance : windows) {
                WindowDefinition definition = this.definitions.get(instance.windowId());
                if (definition == null) {
                    continue;
                }
                if (!player.getWorld().getRegistryKey().equals(instance.worldKey())) {
                    if (instance.groupId() != null) {
                        removeGroupInternal(owner, instance.groupId(), false);
                    } else {
                        removeWindowInternal(owner, instance.windowId(), false);
                    }
                    continue;
                }

                WindowPositionTracker.WindowTransformState rawState = this.positionTracker.resolve(player, instance.positionMode(), definition.offset(), instance.fixedAnchor(), instance.fixedYaw(), instance.fixedPitch());
                WindowPositionTracker.WindowTransformState targetState = this.positionTracker.applyDeadzone(instance, rawState);
                WindowPositionTracker.WindowTransformState currentState = this.positionTracker.smooth(instance, targetState);
                instance.updateTarget(targetState.anchor(), targetState.yaw(), targetState.pitch());
                if (this.positionTracker.shouldUpdate(instance, currentState, currentTick)) {
                    if (instance.positionMode() != PositionMode.FIXED || !Objects.equals(instance.currentAnchor(), currentState.anchor())) {
                        moveWindow(instance, currentState, player.getWorld());
                    }
                    instance.updateTransform(currentState.anchor(), currentState.yaw(), currentState.pitch(), currentTick);
                }
                syncCanvases(instance, player.getWorld().getPlayers());
            }

            updateHover(player);
        }
    }

    public void handlePlayerJoin(ServerPlayerEntity player) {
        for (WindowInstance instance : ownerWindows(player.getUuid())) {
            syncCanvases(instance, List.of(player));
        }
    }

    public RemoveWindowResult removeWindow(UUID owner, String windowId) {
        return removeWindowInternal(owner, windowId, true);
    }

    public RemoveWindowResult removeGroup(UUID owner, String groupId) {
        return removeGroupInternal(owner, groupId, true);
    }

    public void removeAll(UUID owner) {
        Map<String, WindowInstance> playerWindows = this.activeWindows.get(owner);
        if (playerWindows == null) {
            Map<String, WindowGroupInstance> playerGroups = this.activeGroups.get(owner);
            if (playerGroups == null) {
                return;
            }
        }
        if (playerWindows != null) {
            for (String windowId : new ArrayList<>(playerWindows.keySet())) {
                removeWindowInternal(owner, windowId, false);
            }
        }
        Map<String, WindowGroupInstance> playerGroups = this.activeGroups.get(owner);
        if (playerGroups != null) {
            for (String groupId : new ArrayList<>(playerGroups.keySet())) {
                removeGroupInternal(owner, groupId, false);
            }
        }
    }

    public Set<String> loadedWindowIds() {
        return Set.copyOf(new TreeSet<>(this.definitions.keySet()));
    }

    public Set<String> availableWindowIds() {
        Set<String> windowIds = new TreeSet<>(this.schemaLoader.discoverWindowIds());
        windowIds.addAll(this.definitions.keySet());
        return Set.copyOf(windowIds);
    }

    public Set<String> brokenWindowIds() {
        return Set.copyOf(new TreeSet<>(this.brokenWindowIds));
    }

    public int loadedWindowCount() {
        return this.definitions.size();
    }

    public int activeWindowCount() {
        int total = 0;
        for (Map<String, WindowInstance> playerWindows : this.activeWindows.values()) {
            total += playerWindows.size();
        }
        for (Map<String, WindowGroupInstance> playerGroups : this.activeGroups.values()) {
            total += playerGroups.size();
        }
        return total;
    }

    public int activeBindingCount() {
        int total = 0;
        for (Map<String, WindowInstance> playerWindows : this.activeWindows.values()) {
            for (WindowInstance instance : playerWindows.values()) {
                total += instance.bindingCount();
            }
        }
        for (Map<String, WindowGroupInstance> playerGroups : this.activeGroups.values()) {
            for (WindowGroupInstance group : playerGroups.values()) {
                total += group.currentWindow().bindingCount();
            }
        }
        return total;
    }

    public WindowInstance findActiveWindow(UUID owner, String windowId) {
        Map<String, WindowInstance> windows = this.activeWindows.get(owner);
        return windows == null ? null : windows.get(windowId);
    }

    public WindowGroupInstance findActiveGroup(UUID owner, String groupId) {
        Map<String, WindowGroupInstance> groups = this.activeGroups.get(owner);
        return groups == null ? null : groups.get(groupId);
    }

    public WindowInstance findWindow(UUID owner, String windowId) {
        WindowInstance standalone = findActiveWindow(owner, windowId);
        if (standalone != null) {
            return standalone;
        }
        Map<String, WindowGroupInstance> groups = this.activeGroups.get(owner);
        if (groups == null) {
            return null;
        }
        for (WindowGroupInstance group : groups.values()) {
            if (group.currentWindowId().equals(windowId)) {
                return group.currentWindow();
            }
        }
        return null;
    }

    public boolean hasDefinition(String windowId) {
        return this.definitions.containsKey(windowId);
    }

    public Set<String> loadedGroupIds() {
        return Set.copyOf(new TreeSet<>(this.groupDefinitions.keySet()));
    }

    public Set<String> availableGroupIds() {
        Set<String> groupIds = new TreeSet<>(this.schemaLoader.discoverGroupIds());
        groupIds.addAll(this.groupDefinitions.keySet());
        return Set.copyOf(groupIds);
    }

    public Set<String> brokenGroupIds() {
        return Set.copyOf(new TreeSet<>(this.brokenGroupIds));
    }

    public int pooledEntityCount() {
        return this.displayEntityPool.size();
    }

    public int mapCacheEntryCount() {
        return this.schemaLoader.mapCacheEntryCount();
    }

    public UiHitResult findUiHit(ServerPlayerEntity player) {
        List<WindowContext> windows = ownerWindowContexts(player.getUuid());
        if (windows.isEmpty()) {
            return null;
        }

        Vec3d start = player.getEyePos();
        Vec3d direction = player.getRotationVec(1.0f).normalize();
        UiHitResult best = null;
        double closest = Double.MAX_VALUE;
        for (WindowContext windowContext : windows) {
            WindowInstance instance = windowContext.instance();
            if (!player.getWorld().getRegistryKey().equals(instance.worldKey())) {
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
                        direction,
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
                            start.add(direction.multiply(distance)),
                            squared
                    );
                }
            }
        }
        return best;
    }

    public List<BindingSnapshot> bindingSnapshots(UUID owner) {
        List<WindowContext> windows = ownerWindowContexts(owner);
        if (windows.isEmpty()) {
            return List.of();
        }

        List<BindingSnapshot> snapshots = new ArrayList<>();
        for (WindowContext windowContext : windows) {
            WindowInstance instance = windowContext.instance();
            for (WindowComponentRuntime runtime : instance.runtimes()) {
                if (!runtime.interactive() || runtime.action() == null) {
                    continue;
                }
                snapshots.add(new BindingSnapshot(
                        instance.windowId(),
                        runtime.definition().id(),
                        runtime.localPosition(),
                        runtime.hitHalfWidth(),
                        runtime.hitHalfHeight(),
                        runtime.action().type().name(),
                        runtime.action().target()
                ));
            }
        }
        return snapshots;
    }

    @Override
    public RemoveWindowResult closeWindow(UUID owner, WindowNavigationContext context) {
        if (context.groupId() != null) {
            return removeGroup(owner, context.groupId());
        }
        return removeWindow(owner, context.windowId());
    }

    @Override
    public CreateWindowResult openWindow(UUID owner, WindowNavigationContext context, String windowId) {
        ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(owner);
        if (player == null) {
            return CreateWindowResult.failure(DebugReason.ACTION_EXECUTION_FAILED, owner, null, windowId, null, null, 0, 0, "action 대상 플레이어를 찾을 수 없음");
        }
        if (context.groupId() != null) {
            return openGroupWindow(owner, context.groupId(), context.positionMode(), windowId, context.fixedAnchor(), context.fixedYaw(), context.fixedPitch(), player);
        }
        if (!this.definitions.containsKey(windowId)) {
            return CreateWindowResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, owner, player.getGameProfile().getName(), windowId, null, null, 0, 0, "창 정의를 찾을 수 없음");
        }
        removeWindowInternal(owner, context.windowId(), false);
        return createWindow(player, windowId, context.positionMode(), context.fixedAnchor(), context.fixedYaw(), context.fixedPitch());
    }

    @Override
    public CreateWindowResult switchMode(UUID owner, WindowNavigationContext context, PositionMode positionMode) {
        ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(owner);
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

    @Override
    public ActionExecutionResult runCommand(UUID owner, String windowId, String componentId, String command) {
        ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(owner);
        if (player == null) {
            return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "command 대상 플레이어를 찾을 수 없음");
        }
        if (!this.commandWhitelist.isAllowed(command)) {
            return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "허용되지 않은 명령 prefix");
        }

        try {
            ServerCommandSource source = player.getCommandSource().withSilent();
            this.server.getCommandManager().executeWithPrefix(source, command.startsWith("/") ? command.substring(1) : command);
            InteractiveDisplay.LOGGER.info("[{}] run_command player={} windowId={} componentId={} command={}", InteractiveDisplay.MOD_ID, player.getGameProfile().getName(), windowId, componentId, command);
            return ActionExecutionResult.success("run_command 처리 완료");
        } catch (RuntimeException exception) {
            return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "run_command 실패: " + exception.getMessage());
        }
    }

    @Override
    public ActionExecutionResult executeCallback(UUID owner, String windowId, String componentId, String callbackId) {
        ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(owner);
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

    private void moveWindow(WindowInstance instance,
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

    private void updateHover(ServerPlayerEntity player) {
        List<WindowInstance> windows = ownerWindows(player.getUuid());
        if (windows.isEmpty()) {
            return;
        }

        if (!InteractiveDisplayItems.isPointer(player.getMainHandStack())) {
            clearHover(windows);
            return;
        }

        UiHitResult hovered = findUiHit(player);
        WindowComponentRuntime hoveredRuntime = hovered == null ? null : hovered.runtime();
        if (hovered != null) {
            Vec3d hit = hovered.hitPosition();
            player.getWorld().spawnParticles(BUTTON_HOVER_PARTICLE, hit.x, hit.y, hit.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        for (WindowInstance instance : windows) {
            ServerWorld world = this.server.getWorld(instance.worldKey());
            if (world == null) {
                continue;
            }
            for (WindowComponentRuntime runtime : instance.runtimes()) {
                if (!(runtime.definition() instanceof ButtonComponentDefinition button)) {
                    continue;
                }
                boolean shouldHover = runtime == hoveredRuntime && instance.worldKey().equals(player.getWorld().getRegistryKey());
                if (runtime.hovered() != shouldHover) {
                    this.entityFactory.setButtonHover(this.server, world, runtime, button, shouldHover, instance.positionMode());
                }
            }
        }
    }

    private void syncCanvases(WindowInstance instance, Collection<ServerPlayerEntity> viewers) {
        for (WindowComponentRuntime runtime : instance.runtimes()) {
            if (runtime.mapCanvas() != null) {
                this.entityFactory.syncMapCanvas(runtime.mapCanvas(), viewers);
            }
        }
    }

    private RemoveWindowResult removeWindowInternal(UUID owner, String windowId, boolean recordDebug) {
        Map<String, WindowInstance> playerWindows = this.activeWindows.get(owner);
        if (playerWindows == null) {
            RemoveWindowResult result = RemoveWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, windowId, "활성 창이 없음");
            if (recordDebug) {
                recordRemove(DebugLevel.WARN, result);
            }
            return result;
        }

        WindowInstance instance = playerWindows.remove(windowId);
        if (instance == null) {
            RemoveWindowResult result = RemoveWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, windowId, "제거 대상 창이 없음");
            if (recordDebug) {
                recordRemove(DebugLevel.WARN, result);
            }
            return result;
        }

        releaseWindowInstance(instance, owner);

        if (playerWindows.isEmpty()) {
            this.activeWindows.remove(owner);
        }

        RemoveWindowResult result = RemoveWindowResult.success(owner, windowId, instance.entityIds().size(), "창 제거 완료");
        if (recordDebug) {
            recordRemove(DebugLevel.DEBUG, result);
        }
        return result;
    }

    private RemoveWindowResult removeGroupInternal(UUID owner, String groupId, boolean recordDebug) {
        Map<String, WindowGroupInstance> playerGroups = this.activeGroups.get(owner);
        if (playerGroups == null) {
            RemoveWindowResult result = RemoveWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, groupId, "활성 그룹이 없음");
            if (recordDebug) {
                recordRemove(DebugLevel.WARN, result);
            }
            return result;
        }

        WindowGroupInstance groupInstance = playerGroups.remove(groupId);
        if (groupInstance == null) {
            RemoveWindowResult result = RemoveWindowResult.failure(DebugReason.NO_ACTIVE_WINDOW, owner, groupId, "제거 대상 그룹이 없음");
            if (recordDebug) {
                recordRemove(DebugLevel.WARN, result);
            }
            return result;
        }

        releaseWindowInstance(groupInstance.currentWindow(), owner);
        if (playerGroups.isEmpty()) {
            this.activeGroups.remove(owner);
        }

        RemoveWindowResult result = RemoveWindowResult.success(owner, groupId, groupInstance.currentWindow().entityIds().size(), "그룹 제거 완료");
        if (recordDebug) {
            recordRemove(DebugLevel.DEBUG, result);
        }
        return result;
    }

    private void rebuildActiveWindows(Set<String> windowIds) {
        for (String windowId : windowIds) {
            rebuildActiveWindowDefinitions(windowId);
        }
    }

    private void rebuildActiveWindowDefinitions(String windowId) {
        List<UUID> owners = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, WindowInstance>> entry : this.activeWindows.entrySet()) {
            if (entry.getValue().containsKey(windowId)) {
                owners.add(entry.getKey());
            }
        }
        for (UUID owner : owners) {
            CreateWindowResult rebuild = rebuildWindow(owner, windowId);
            if (!rebuild.success()) {
                InteractiveDisplay.LOGGER.warn("[{}] rebuild failed owner={} windowId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, owner, windowId, rebuild.reasonCode(), rebuild.message());
            }
        }
    }

    private void rebuildActiveGroups(Set<String> groupIds, Set<String> changedWindowIds) {
        for (String groupId : groupIds) {
            List<UUID> owners = new ArrayList<>();
            for (Map.Entry<UUID, Map<String, WindowGroupInstance>> entry : this.activeGroups.entrySet()) {
                if (entry.getValue().containsKey(groupId)) {
                    owners.add(entry.getKey());
                }
            }
            for (UUID owner : owners) {
                CreateWindowResult rebuild = rebuildGroup(owner, groupId);
                if (!rebuild.success()) {
                    InteractiveDisplay.LOGGER.warn("[{}] group rebuild failed owner={} groupId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, owner, groupId, rebuild.reasonCode(), rebuild.message());
                }
            }
        }
        for (String changedWindowId : changedWindowIds) {
            rebuildActiveGroupsContainingWindow(changedWindowId);
        }
    }

    private void rebuildActiveGroupsContainingWindow(String windowId) {
        List<GroupOwnerRef> affected = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, WindowGroupInstance>> ownerEntry : this.activeGroups.entrySet()) {
            for (WindowGroupInstance groupInstance : ownerEntry.getValue().values()) {
                if (groupInstance.currentWindowId().equals(windowId)) {
                    affected.add(new GroupOwnerRef(ownerEntry.getKey(), groupInstance.groupId()));
                }
            }
        }
        for (GroupOwnerRef ref : affected) {
            CreateWindowResult rebuild = rebuildGroup(ref.owner(), ref.groupId());
            if (!rebuild.success()) {
                InteractiveDisplay.LOGGER.warn("[{}] group rebuild failed owner={} groupId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, ref.owner(), ref.groupId(), rebuild.reasonCode(), rebuild.message());
            }
        }
    }

    private void releaseWindowInstance(WindowInstance instance, UUID owner) {
        ServerWorld world = this.server.getWorld(instance.worldKey());
        if (world == null) {
            return;
        }
        for (WindowComponentRuntime runtime : instance.runtimes()) {
            this.entityFactory.deactivateRuntime(this.server, world, runtime);
            this.displayEntityPool.release(owner, instance.worldKey(), runtime, this.server.getTicks());
        }
        this.entityFactory.destroyRoot(this.server, instance.worldKey(), instance.rootEntityId());
    }

    private List<WindowInstance> ownerWindows(UUID owner) {
        List<WindowInstance> windows = new ArrayList<>();
        Map<String, WindowInstance> standalone = this.activeWindows.get(owner);
        if (standalone != null) {
            windows.addAll(standalone.values());
        }
        Map<String, WindowGroupInstance> groups = this.activeGroups.get(owner);
        if (groups != null) {
            for (WindowGroupInstance group : groups.values()) {
                windows.add(group.currentWindow());
            }
        }
        return windows;
    }

    private List<WindowContext> ownerWindowContexts(UUID owner) {
        List<WindowContext> windows = new ArrayList<>();
        Map<String, WindowInstance> standalone = this.activeWindows.get(owner);
        if (standalone != null) {
            for (WindowInstance instance : standalone.values()) {
                windows.add(new WindowContext(instance, new WindowNavigationContext(
                        instance.windowId(),
                        null,
                        instance.positionMode(),
                        instance.positionMode() == PositionMode.FIXED ? instance.currentAnchor() : instance.fixedAnchor(),
                        instance.currentYaw(),
                        instance.currentPitch()
                )));
            }
        }
        Map<String, WindowGroupInstance> groups = this.activeGroups.get(owner);
        if (groups != null) {
            for (WindowGroupInstance group : groups.values()) {
                windows.add(new WindowContext(group.currentWindow(), new WindowNavigationContext(
                        group.currentWindowId(),
                        group.groupId(),
                        group.currentMode(),
                        group.baseAnchor(),
                        group.baseYaw(),
                        group.basePitch()
                )));
            }
        }
        return windows;
    }

    private SpawnedWindow spawnGroupWindow(ServerPlayerEntity player,
                                           WindowGroupDefinition groupDefinition,
                                           WindowGroupEntry entry,
                                           PositionMode positionMode,
                                           Vec3d baseAnchor,
                                           float baseYaw,
                                           float basePitch) {
        WindowDefinition definition = this.definitions.get(entry.windowId());
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
                positionMode == PositionMode.PLAYER_FIXED ? resolvedYaw : baseYaw,
                positionMode == PositionMode.PLAYER_FIXED ? resolvedPitch : basePitch,
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
        WindowGroupDefinition groupDefinition = this.groupDefinitions.get(groupId);
        if (groupDefinition == null) {
            return CreateWindowResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, owner, player.getGameProfile().getName(), groupId, null, null, 0, 0, "그룹 정의를 찾을 수 없음");
        }
        WindowGroupEntry entry = groupDefinition.entry(windowId);
        if (entry == null) {
            if (!this.definitions.containsKey(windowId)) {
                return CreateWindowResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, owner, player.getGameProfile().getName(), windowId, null, null, 0, 0, "창 정의를 찾을 수 없음");
            }
            removeGroupInternal(owner, groupId, false);
            return createWindow(player, windowId, positionMode, baseAnchor, baseYaw, basePitch);
        }
        WindowGroupInstance currentGroup = findActiveGroup(owner, groupId);
        Vec3d resolvedBaseAnchor = baseAnchor;
        if (resolvedBaseAnchor == null) {
            resolvedBaseAnchor = positionMode == PositionMode.FIXED ? player.getEyePos() : currentGroup != null ? currentGroup.baseAnchor() : null;
        }
        float resolvedBaseYaw = positionMode == PositionMode.PLAYER_FIXED ? baseYaw : baseYaw;
        float resolvedBasePitch = positionMode == PositionMode.PLAYER_FIXED ? basePitch : basePitch;
        SpawnedWindow spawned = spawnGroupWindow(player, groupDefinition, entry, positionMode, resolvedBaseAnchor, resolvedBaseYaw, resolvedBasePitch);
        if (!spawned.result().success()) {
            return spawned.result();
        }
        if (currentGroup != null) {
            releaseWindowInstance(currentGroup.currentWindow(), owner);
        }
        WindowGroupInstance nextGroup = new WindowGroupInstance(owner, groupId, resolvedBaseAnchor, resolvedBaseYaw, resolvedBasePitch, positionMode, windowId, spawned.instance());
        this.activeGroups.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>()).put(groupId, nextGroup);
        return spawned.result();
    }

    private GroupPlacement initialGroupPlacement(ServerPlayerEntity player,
                                                 PositionMode positionMode,
                                                 Vec3d baseAnchor,
                                                 float baseYaw,
                                                 float basePitch) {
        Vec3d resolvedBaseAnchor = baseAnchor;
        if (positionMode == PositionMode.FIXED) {
            if (resolvedBaseAnchor == null) {
                resolvedBaseAnchor = player.getEyePos();
            }
            return new GroupPlacement(resolvedBaseAnchor, baseYaw, basePitch);
        }
        return new GroupPlacement(resolvedBaseAnchor, baseYaw, basePitch);
    }

    private void cleanupFailedCreate(List<WindowComponentRuntime> runtimes) {
        for (WindowComponentRuntime runtime : runtimes) {
            this.entityFactory.destroyRuntime(this.server, runtime);
        }
    }

    private void tryReloadSupport() {
        try {
            this.commandWhitelist.reload();
        } catch (IOException exception) {
            this.debugRecorder.record(DebugEventType.WINDOW_RELOAD, DebugLevel.WARN, null, null, null, null, null, DebugReason.SCHEMA_VALIDATION_FAILED, "command whitelist reload 실패: " + exception.getMessage(), exception);
            InteractiveDisplay.LOGGER.warn("[{}] command whitelist reload 실패 message={}", InteractiveDisplay.MOD_ID, exception.getMessage());
        }
    }

    private void replaceBrokenWindowIds(Set<String> brokenWindowIds) {
        this.brokenWindowIds.clear();
        this.brokenWindowIds.addAll(brokenWindowIds);
    }

    private void replaceBrokenGroupIds(Set<String> brokenGroupIds) {
        this.brokenGroupIds.clear();
        this.brokenGroupIds.addAll(brokenGroupIds);
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

    private void recordReload(DebugLevel level, ReloadWindowResult result) {
        this.debugRecorder.record(DebugEventType.WINDOW_RELOAD, level, null, null, result.windowId(), null, null, result.reasonCode(), result.message(), null);
        if (level == DebugLevel.DEBUG) {
            InteractiveDisplay.LOGGER.debug("[{}] window reload success windowId={} definitionCount={} errorCount={}", InteractiveDisplay.MOD_ID, result.windowId(), result.definitionCount(), result.errorCount());
            return;
        }
        InteractiveDisplay.LOGGER.warn("[{}] window reload warn windowId={} reasonCode={} errorCount={} message={}", InteractiveDisplay.MOD_ID, result.windowId(), result.reasonCode(), result.errorCount(), result.message());
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

    private void clearHover(Collection<WindowInstance> windows) {
        for (WindowInstance instance : windows) {
            ServerWorld world = this.server.getWorld(instance.worldKey());
            if (world == null) {
                continue;
            }
            for (WindowComponentRuntime runtime : instance.runtimes()) {
                if (!(runtime.definition() instanceof ButtonComponentDefinition button)) {
                    continue;
                }
                if (runtime.hovered()) {
                    this.entityFactory.setButtonHover(this.server, world, runtime, button, false, instance.positionMode());
                }
            }
        }
    }

    public record BindingSnapshot(
            String windowId,
            String componentId,
            org.joml.Vector3f localCenter,
            float halfWidth,
            float halfHeight,
            String actionType,
            String target
    ) {
    }

    private record SpawnedWindow(WindowInstance instance, CreateWindowResult result) {
    }

    private record GroupPlacement(Vec3d baseAnchor, float baseYaw, float basePitch) {
    }

    private record GroupOwnerRef(UUID owner, String groupId) {
    }

    private record WindowContext(WindowInstance instance, WindowNavigationContext navigationContext) {
    }
}

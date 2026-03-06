package com.interactivedisplay.core.window;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.core.interaction.CommandWhitelist;
import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.layout.LayoutEngine;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.positioning.WindowPositionTracker;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.DisplayEntityFactory;
import com.interactivedisplay.schema.SchemaLoader;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public final class WindowManager implements WindowActionExecutor {
    private final MinecraftServer server;
    private final SchemaLoader schemaLoader;
    private final WindowPositionTracker positionTracker;
    private final DebugRecorder debugRecorder;
    private final CommandWhitelist commandWhitelist;
    private final WindowStateStore stateStore;
    private final WindowLifecycleCoordinator lifecycleCoordinator;
    private final UiHitResolver uiHitResolver;

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
        this.positionTracker = positionTracker;
        this.debugRecorder = debugRecorder;
        this.commandWhitelist = commandWhitelist;
        this.stateStore = new WindowStateStore();
        this.lifecycleCoordinator = new WindowLifecycleCoordinator(
                server,
                this.stateStore,
                layoutEngine,
                transformer,
                positionTracker,
                entityFactory,
                debugRecorder,
                commandWhitelist,
                callbackRegistry
        );
        this.uiHitResolver = new UiHitResolver(this.stateStore, transformer);
    }

    public ReloadWindowResult reloadAll() {
        tryReloadSupport();
        SchemaLoader.LoadResult loadResult = this.schemaLoader.loadAll();
        this.stateStore.replaceBrokenWindowIds(loadResult.brokenWindowIds());
        this.stateStore.replaceBrokenGroupIds(loadResult.brokenGroupIds());

        if (!loadResult.hasErrors()) {
            this.stateStore.replaceDefinitions(loadResult.definitions());
            this.stateStore.replaceGroupDefinitions(loadResult.groups());
            rebuildActiveWindows(loadResult.definitions().keySet());
            rebuildActiveGroups(loadResult.groups().keySet(), loadResult.definitions().keySet());
            ReloadWindowResult result = ReloadWindowResult.success(null, this.stateStore.loadedWindowCount(), 0, loadResult.errors(), "전체 창 리로드 완료");
            recordReload(DebugLevel.DEBUG, result);
            return result;
        }

        this.stateStore.mergeDefinitions(loadResult.definitions());
        this.stateStore.mergeGroupDefinitions(loadResult.groups());
        rebuildActiveWindows(loadResult.definitions().keySet());
        rebuildActiveGroups(loadResult.groups().keySet(), loadResult.definitions().keySet());
        ReloadWindowResult result = ReloadWindowResult.failure(DebugReason.SCHEMA_VALIDATION_FAILED, null, this.stateStore.loadedWindowCount(), loadResult.errors().size(), loadResult.errors(), "전체 창 리로드 완료 (오류 " + loadResult.errors().size() + "건)");
        recordReload(DebugLevel.WARN, result);
        return result;
    }

    public ReloadWindowResult reloadOne(String windowId) {
        tryReloadSupport();
        SchemaLoader.LoadResult loadResult = this.schemaLoader.loadAll();
        this.stateStore.replaceBrokenWindowIds(loadResult.brokenWindowIds());
        this.stateStore.replaceBrokenGroupIds(loadResult.brokenGroupIds());
        WindowDefinition definition = loadResult.definitions().get(windowId);
        if (definition == null) {
            DebugReason reasonCode = loadResult.hasErrors() ? DebugReason.SCHEMA_VALIDATION_FAILED : DebugReason.WINDOW_DEFINITION_NOT_FOUND;
            ReloadWindowResult result = ReloadWindowResult.failure(reasonCode, windowId, this.stateStore.loadedWindowCount(), loadResult.errors().size(), loadResult.errors(), "창 리로드 실패: " + windowId);
            recordReload(DebugLevel.WARN, result);
            return result;
        }

        this.stateStore.putDefinition(windowId, definition);
        rebuildActiveWindowDefinitions(windowId);
        rebuildActiveGroupsContainingWindow(windowId);
        if (loadResult.hasErrors()) {
            ReloadWindowResult result = ReloadWindowResult.failure(DebugReason.SCHEMA_VALIDATION_FAILED, windowId, this.stateStore.loadedWindowCount(), loadResult.errors().size(), loadResult.errors(), "창 리로드 완료 (오류 " + loadResult.errors().size() + "건): " + windowId);
            recordReload(DebugLevel.WARN, result);
            return result;
        }

        ReloadWindowResult result = ReloadWindowResult.success(windowId, this.stateStore.loadedWindowCount(), 0, loadResult.errors(), "창 리로드 완료: " + windowId);
        recordReload(DebugLevel.DEBUG, result);
        return result;
    }

    public CreateWindowResult createWindow(ServerPlayerEntity player, String windowId, PositionMode positionMode, Vec3d overrideAnchor) {
        return this.lifecycleCoordinator.createWindow(player, windowId, positionMode, overrideAnchor);
    }

    public CreateWindowResult createWindow(ServerPlayerEntity player,
                                           String windowId,
                                           PositionMode positionMode,
                                           Vec3d overrideAnchor,
                                           float fixedYaw,
                                           float fixedPitch) {
        return this.lifecycleCoordinator.createWindow(player, windowId, positionMode, overrideAnchor, fixedYaw, fixedPitch);
    }

    public CreateWindowResult createGroup(ServerPlayerEntity player,
                                          String groupId,
                                          PositionMode positionMode,
                                          Vec3d baseAnchor,
                                          float baseYaw,
                                          float basePitch) {
        return this.lifecycleCoordinator.createGroup(player, groupId, positionMode, baseAnchor, baseYaw, basePitch);
    }

    public CreateWindowResult rebuildWindow(UUID owner, String windowId) {
        return this.lifecycleCoordinator.rebuildWindow(owner, windowId);
    }

    public CreateWindowResult rebuildGroup(UUID owner, String groupId) {
        return this.lifecycleCoordinator.rebuildGroup(owner, groupId);
    }

    public void tick() {
        long currentTick = this.server.getTicks();
        this.lifecycleCoordinator.expirePooledEntities(currentTick);

        for (UUID owner : this.stateStore.owners()) {
            ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(owner);
            if (player == null) {
                continue;
            }

            List<WindowInstance> windows = this.stateStore.ownerWindows(owner);
            for (WindowInstance instance : windows) {
                WindowDefinition definition = this.stateStore.definition(instance.windowId());
                if (definition == null) {
                    continue;
                }
                if (!player.getWorld().getRegistryKey().equals(instance.worldKey())) {
                    if (instance.groupId() != null) {
                        this.lifecycleCoordinator.removeGroupSilently(owner, instance.groupId());
                    } else {
                        this.lifecycleCoordinator.removeWindowSilently(owner, instance.windowId());
                    }
                    continue;
                }

                boolean placementTracking = this.lifecycleCoordinator.isPlacementTracking(owner, instance);
                WindowPositionTracker.WindowTransformState rawState = this.lifecycleCoordinator.resolveTransform(player, instance, definition);
                WindowPositionTracker.WindowTransformState targetState = this.positionTracker.applyDeadzone(instance, rawState);
                WindowPositionTracker.WindowTransformState currentState = this.positionTracker.smooth(instance, targetState);
                instance.updateTarget(targetState.anchor(), targetState.yaw(), targetState.pitch());
                if (placementTracking || this.positionTracker.shouldUpdate(instance, currentState, currentTick)) {
                    if (placementTracking || instance.positionMode() != PositionMode.FIXED || !instance.currentAnchor().equals(currentState.anchor())) {
                        this.lifecycleCoordinator.moveWindow(instance, currentState, player.getWorld());
                    }
                    instance.updateTransform(currentState.anchor(), currentState.yaw(), currentState.pitch(), currentTick);
                }
                this.lifecycleCoordinator.syncCanvases(instance, player.getWorld().getPlayers());
            }

            this.lifecycleCoordinator.updateHover(player, windows, this.uiHitResolver.findUiHit(player));
        }
    }

    public void handlePlayerJoin(ServerPlayerEntity player) {
        this.lifecycleCoordinator.handlePlayerJoin(player);
    }

    public RemoveWindowResult removeWindow(UUID owner, String windowId) {
        return this.lifecycleCoordinator.removeWindow(owner, windowId);
    }

    public RemoveWindowResult removeGroup(UUID owner, String groupId) {
        return this.lifecycleCoordinator.removeGroup(owner, groupId);
    }

    public void removeAll(UUID owner) {
        this.lifecycleCoordinator.removeAll(owner);
    }

    public Set<String> loadedWindowIds() {
        return this.stateStore.loadedWindowIds();
    }

    public Set<String> availableWindowIds() {
        return this.stateStore.availableWindowIds(this.schemaLoader.discoverWindowIds());
    }

    public Set<String> brokenWindowIds() {
        return this.stateStore.brokenWindowIds();
    }

    public int loadedWindowCount() {
        return this.stateStore.loadedWindowCount();
    }

    public int activeWindowCount() {
        return this.stateStore.activeWindowCount();
    }

    public int activeBindingCount() {
        return this.stateStore.activeBindingCount();
    }

    public WindowInstance findActiveWindow(UUID owner, String windowId) {
        return this.stateStore.findActiveWindow(owner, windowId);
    }

    public WindowGroupInstance findActiveGroup(UUID owner, String groupId) {
        return this.stateStore.findActiveGroup(owner, groupId);
    }

    public WindowInstance findWindow(UUID owner, String windowId) {
        return this.stateStore.findWindow(owner, windowId);
    }

    public boolean hasDefinition(String windowId) {
        return this.stateStore.hasDefinition(windowId);
    }

    public Set<String> loadedGroupIds() {
        return this.stateStore.loadedGroupIds();
    }

    public Set<String> availableGroupIds() {
        return this.stateStore.availableGroupIds(this.schemaLoader.discoverGroupIds());
    }

    public Set<String> brokenGroupIds() {
        return this.stateStore.brokenGroupIds();
    }

    public int pooledEntityCount() {
        return this.lifecycleCoordinator.pooledEntityCount();
    }

    public int mapCacheEntryCount() {
        return this.schemaLoader.mapCacheEntryCount();
    }

    public UiHitResult findUiHit(ServerPlayerEntity player) {
        return this.uiHitResolver.findUiHit(player);
    }

    public List<BindingSnapshot> bindingSnapshots(UUID owner) {
        return this.stateStore.bindingSnapshots(owner);
    }

    @Override
    public RemoveWindowResult closeWindow(UUID owner, WindowNavigationContext context) {
        return this.lifecycleCoordinator.closeWindow(owner, context);
    }

    @Override
    public CreateWindowResult openWindow(UUID owner, WindowNavigationContext context, String windowId) {
        return this.lifecycleCoordinator.openWindow(owner, context, windowId);
    }

    @Override
    public CreateWindowResult switchMode(UUID owner, WindowNavigationContext context, PositionMode positionMode) {
        return this.lifecycleCoordinator.switchMode(owner, context, positionMode);
    }

    @Override
    public ActionExecutionResult runCommand(UUID owner, UiHitResult hitResult, Integer permissionLevel, String command) {
        return this.lifecycleCoordinator.runCommand(owner, hitResult, permissionLevel, command);
    }

    @Override
    public ActionExecutionResult executeCallback(UUID owner, String windowId, String componentId, String callbackId) {
        return this.lifecycleCoordinator.executeCallback(owner, windowId, componentId, callbackId);
    }

    @Override
    public ActionExecutionResult togglePlacementTracking(UUID owner, WindowNavigationContext context) {
        return this.lifecycleCoordinator.togglePlacementTracking(owner, context);
    }

    private void rebuildActiveWindows(Set<String> windowIds) {
        for (String windowId : windowIds) {
            rebuildActiveWindowDefinitions(windowId);
        }
    }

    private void rebuildActiveWindowDefinitions(String windowId) {
        for (UUID owner : this.stateStore.ownersForActiveWindow(windowId)) {
            CreateWindowResult rebuild = this.lifecycleCoordinator.rebuildWindow(owner, windowId);
            if (!rebuild.success()) {
                InteractiveDisplay.LOGGER.warn("[{}] rebuild failed owner={} windowId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, owner, windowId, rebuild.reasonCode(), rebuild.message());
            }
        }
    }

    private void rebuildActiveGroups(Set<String> groupIds, Set<String> changedWindowIds) {
        for (String groupId : groupIds) {
            for (UUID owner : this.stateStore.ownersForActiveGroup(groupId)) {
                CreateWindowResult rebuild = this.lifecycleCoordinator.rebuildGroup(owner, groupId);
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
        for (ActiveGroupRef ref : this.stateStore.activeGroupsContainingWindow(windowId)) {
            CreateWindowResult rebuild = this.lifecycleCoordinator.rebuildGroup(ref.owner(), ref.groupId());
            if (!rebuild.success()) {
                InteractiveDisplay.LOGGER.warn("[{}] group rebuild failed owner={} groupId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, ref.owner(), ref.groupId(), rebuild.reasonCode(), rebuild.message());
            }
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

    private void recordReload(DebugLevel level, ReloadWindowResult result) {
        this.debugRecorder.record(DebugEventType.WINDOW_RELOAD, level, null, null, result.windowId(), null, null, result.reasonCode(), result.message(), null);
        if (level == DebugLevel.DEBUG) {
            InteractiveDisplay.LOGGER.debug("[{}] window reload success windowId={} definitionCount={} errorCount={}", InteractiveDisplay.MOD_ID, result.windowId(), result.definitionCount(), result.errorCount());
            return;
        }
        InteractiveDisplay.LOGGER.warn("[{}] window reload warn windowId={} reasonCode={} errorCount={} message={}", InteractiveDisplay.MOD_ID, result.windowId(), result.reasonCode(), result.errorCount(), result.message());
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
}

package com.interactivedisplay.core.window;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.TextComponentDefinition;
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
import com.interactivedisplay.entity.VirtualWindowHolder;
import com.interactivedisplay.item.InteractiveDisplayItems;
import com.interactivedisplay.schema.SchemaLoader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class WindowManager implements WindowActionExecutor {
    private final MinecraftServer server;
    private final SchemaLoader schemaLoader;
    private final WindowPositionTracker positionTracker;
    private final DisplayEntityFactory entityFactory;
    private final DebugRecorder debugRecorder;
    private final CommandWhitelist commandWhitelist;
    private final WindowStateStore stateStore;
    private final WindowLifecycleCoordinator lifecycleCoordinator;
    private final UiHitResolver uiHitResolver;
    private final Map<String, WindowDefinition> programmaticDefinitions = new ConcurrentHashMap<>();

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
        this.entityFactory = entityFactory;
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
            this.stateStore.mergeDefinitions(this.programmaticDefinitions);
            this.stateStore.replaceGroupDefinitions(loadResult.groups());
            rebuildActiveWindows(loadResult.definitions().keySet());
            rebuildActiveGroups(loadResult.groups().keySet(), loadResult.definitions().keySet());
            ReloadWindowResult result = ReloadWindowResult.success(null, this.stateStore.loadedWindowCount(), 0, loadResult.errors(), "전체 창 리로드 완료");
            recordReload(DebugLevel.DEBUG, result);
            return result;
        }

        this.stateStore.mergeDefinitions(loadResult.definitions());
        this.stateStore.mergeDefinitions(this.programmaticDefinitions);
        this.stateStore.mergeGroupDefinitions(loadResult.groups());
        rebuildActiveWindows(loadResult.definitions().keySet());
        rebuildActiveGroups(loadResult.groups().keySet(), loadResult.definitions().keySet());
        ReloadWindowResult result = ReloadWindowResult.failure(DebugReason.SCHEMA_VALIDATION_FAILED, null, this.stateStore.loadedWindowCount(), loadResult.errors().size(), loadResult.errors(), "전체 창 리로드 완료 (오류 " + loadResult.errors().size() + "건)");
        recordReload(DebugLevel.WARN, result);
        return result;
    }

    public ReloadWindowResult reloadOne(String windowId) {
        tryReloadSupport();

        WindowDefinition programmatic = this.programmaticDefinitions.get(windowId);
        if (programmatic != null) {
            Set<String> brokenWindowIds = new HashSet<>(this.stateStore.brokenWindowIds());
            brokenWindowIds.remove(windowId);
            this.stateStore.replaceBrokenWindowIds(brokenWindowIds);
            this.stateStore.putDefinition(windowId, programmatic);
            rebuildActiveWindowDefinitions(windowId);
            rebuildActiveGroupsContainingWindow(windowId);
            ReloadWindowResult result = ReloadWindowResult.success(
                    windowId,
                    this.stateStore.loadedWindowCount(),
                    0,
                    List.of(),
                    "프로그램 창 리로드 완료: " + windowId
            );
            recordReload(DebugLevel.DEBUG, result);
            return result;
        }

        SchemaLoader.LoadResult loadResult = this.schemaLoader.loadWindow(windowId);
        Set<String> brokenWindowIds = new HashSet<>(this.stateStore.brokenWindowIds());
        if (loadResult.brokenWindowIds().contains(windowId)) {
            brokenWindowIds.add(windowId);
        } else {
            brokenWindowIds.remove(windowId);
        }
        this.stateStore.replaceBrokenWindowIds(brokenWindowIds);

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
        ReloadWindowResult result = ReloadWindowResult.success(windowId, this.stateStore.loadedWindowCount(), 0, loadResult.errors(), "창 리로드 완료: " + windowId);
        recordReload(DebugLevel.DEBUG, result);
        return result;
    }

    public synchronized boolean registerProgrammaticWindow(WindowDefinition definition) {
        if (definition == null || definition.id() == null || definition.id().isBlank()) {
            return false;
        }
        String windowId = definition.id();
        if (this.programmaticDefinitions.containsKey(windowId) || this.stateStore.hasDefinition(windowId)) {
            return false;
        }
        this.programmaticDefinitions.put(windowId, definition);
        this.stateStore.putDefinition(windowId, definition);
        return true;
    }

    public Set<String> programmaticWindowIds() {
        return Set.copyOf(this.programmaticDefinitions.keySet());
    }

    public CreateWindowResult createWindow(ServerPlayer player, String windowId, PositionMode positionMode, Vec3 overrideAnchor) {
        CreateWindowResult result = this.lifecycleCoordinator.createWindow(player, windowId, positionMode, overrideAnchor);
        if (result.success()) {
            configureWindowEffects(this.stateStore.findActiveWindow(player.getUUID(), windowId));
        }
        return result;
    }

    public CreateWindowResult createWindow(ServerPlayer player,
                                           String windowId,
                                           PositionMode positionMode,
                                           Vec3 overrideAnchor,
                                           float fixedYaw,
                                           float fixedPitch) {
        CreateWindowResult result = this.lifecycleCoordinator.createWindow(player, windowId, positionMode, overrideAnchor, fixedYaw, fixedPitch);
        if (result.success()) {
            configureWindowEffects(this.stateStore.findActiveWindow(player.getUUID(), windowId));
        }
        return result;
    }

    public CreateWindowResult createGroup(ServerPlayer player,
                                          String groupId,
                                          PositionMode positionMode,
                                          Vec3 baseAnchor,
                                          float baseYaw,
                                          float basePitch) {
        CreateWindowResult result = this.lifecycleCoordinator.createGroup(player, groupId, positionMode, baseAnchor, baseYaw, basePitch);
        if (result.success()) {
            WindowGroupInstance group = this.stateStore.findActiveGroup(player.getUUID(), groupId);
            configureWindowEffects(group == null ? null : group.currentWindow());
        }
        return result;
    }

    public CreateWindowResult rebuildWindow(UUID owner, String windowId) {
        CreateWindowResult result = this.lifecycleCoordinator.rebuildWindow(owner, windowId);
        if (result.success()) {
            configureWindowEffects(this.stateStore.findActiveWindow(owner, windowId));
        }
        return result;
    }

    public CreateWindowResult rebuildGroup(UUID owner, String groupId) {
        CreateWindowResult result = this.lifecycleCoordinator.rebuildGroup(owner, groupId);
        if (result.success()) {
            WindowGroupInstance group = this.stateStore.findActiveGroup(owner, groupId);
            configureWindowEffects(group == null ? null : group.currentWindow());
        }
        return result;
    }

    public void tick() {
        long currentTick = this.server.getTickCount();

        for (UUID owner : this.stateStore.owners()) {
            ServerPlayer player = this.server.getPlayerList().getPlayer(owner);
            if (player == null) {
                continue;
            }

            List<WindowInstance> windows = this.stateStore.ownerWindows(owner);
            for (WindowInstance instance : windows) {
                WindowDefinition definition = this.stateStore.definition(instance.windowId());
                if (definition == null) {
                    continue;
                }
                if (!player.level().dimension().equals(instance.worldKey())) {
                    migrateOrRemoveAcrossDimension(player, instance);
                    continue;
                }

                boolean placementTracking = this.lifecycleCoordinator.isPlacementTracking(owner, instance);
                WindowPositionTracker.WindowTransformState rawState = this.lifecycleCoordinator.resolveTransform(player, instance, definition);
                WindowPositionTracker.WindowTransformState targetState = this.positionTracker.applyDeadzone(instance, rawState);
                WindowPositionTracker.WindowTransformState currentState = this.positionTracker.smooth(instance, targetState);
                instance.updateTarget(targetState.anchor(), targetState.yaw(), targetState.pitch());
                if (placementTracking || this.positionTracker.shouldUpdate(instance, currentState, currentTick)) {
                    if (placementTracking || instance.positionMode() != PositionMode.FIXED || !instance.currentAnchor().equals(currentState.anchor())) {
                        this.lifecycleCoordinator.moveWindow(instance, currentState, player.level());
                    }
                    instance.updateTransform(currentState.anchor(), currentState.yaw(), currentState.pitch(), currentTick);
                }

                refreshDynamicText(instance, currentTick);
                this.lifecycleCoordinator.syncCanvases(instance, player);
            }

            UiHitResult hovered = InteractiveDisplayItems.isPointer(player.getMainHandItem())
                    ? this.uiHitResolver.findUiHit(player)
                    : null;
            this.lifecycleCoordinator.updateHover(player, windows, hovered);
            this.lifecycleCoordinator.tickVirtualEntities(windows);
        }

        VirtualWindowHolder.tickPendingDestroys(this.server);
    }

    public void handlePlayerJoin(ServerPlayer player) {
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

    public void shutdown() {
        VirtualWindowHolder.destroyAllPending(this.server);
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

    public List<WindowInstance> ownerWindows(UUID owner) {
        return this.stateStore.ownerWindows(owner);
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
        return 0;
    }

    public int mapCacheEntryCount() {
        return this.schemaLoader.mapCacheEntryCount();
    }

    public UiHitResult findUiHit(ServerPlayer player) {
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
        CreateWindowResult result = this.lifecycleCoordinator.openWindow(owner, context, windowId);
        if (result.success()) {
            configureWindowEffects(activeWindowForContext(owner, context, windowId));
        }
        return result;
    }

    @Override
    public CreateWindowResult switchMode(UUID owner, WindowNavigationContext context, PositionMode positionMode) {
        CreateWindowResult result = this.lifecycleCoordinator.switchMode(owner, context, positionMode);
        if (result.success()) {
            configureWindowEffects(activeWindowForContext(owner, context, context.windowId()));
        }
        return result;
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
        WindowInstance current = activeWindowForContext(owner, context, context.windowId());
        boolean wasTracking = current != null && this.lifecycleCoordinator.isPlacementTracking(owner, current);
        ActionExecutionResult result = this.lifecycleCoordinator.togglePlacementTracking(owner, context);
        if (result.success() && wasTracking) {
            configureWindowEffects(activeWindowForContext(owner, context, context.windowId()));
        }
        return result;
    }

    private void migrateOrRemoveAcrossDimension(ServerPlayer player, WindowInstance instance) {
        UUID owner = player.getUUID();
        if (instance.positionMode() == PositionMode.FIXED) {
            if (instance.groupId() != null) {
                this.lifecycleCoordinator.removeGroupSilently(owner, instance.groupId());
            } else {
                this.lifecycleCoordinator.removeWindowSilently(owner, instance.windowId());
            }
            return;
        }

        CreateWindowResult result = instance.groupId() != null
                ? rebuildGroup(owner, instance.groupId())
                : rebuildWindow(owner, instance.windowId());
        if (!result.success()) {
            InteractiveDisplay.LOGGER.warn(
                    "[{}] player-bound window dimension migration failed owner={} windowId={} groupId={} reasonCode={} message={}",
                    InteractiveDisplay.MOD_ID,
                    owner,
                    instance.windowId(),
                    instance.groupId(),
                    result.reasonCode(),
                    result.message()
            );
        }
    }

    private void refreshDynamicText(WindowInstance instance, long tick) {
        for (WindowComponentRuntime runtime : instance.runtimes()) {
            if (!(runtime.definition() instanceof TextComponentDefinition text) || !runtime.shouldRefreshText(tick, text.refreshInterval())) {
                continue;
            }
            this.entityFactory.refreshText(this.server, instance.owner(), runtime, text);
        }
    }

    private void configureWindowEffects(WindowInstance instance) {
        if (instance == null) {
            return;
        }
        WindowDefinition definition = this.stateStore.definition(instance.windowId());
        if (definition == null) {
            return;
        }
        instance.virtualHolder().setTransition(definition.transition());
        instance.virtualHolder().playEnterTransition();
    }

    private WindowInstance activeWindowForContext(UUID owner, WindowNavigationContext context, String fallbackWindowId) {
        if (context.groupId() != null) {
            WindowGroupInstance group = this.stateStore.findActiveGroup(owner, context.groupId());
            if (group != null) {
                return group.currentWindow();
            }
        }
        WindowInstance exact = this.stateStore.findActiveWindow(owner, fallbackWindowId);
        return exact != null ? exact : this.stateStore.findWindow(owner, fallbackWindowId);
    }

    private void rebuildActiveWindows(Set<String> windowIds) {
        for (String windowId : windowIds) {
            rebuildActiveWindowDefinitions(windowId);
        }
    }

    private void rebuildActiveWindowDefinitions(String windowId) {
        for (UUID owner : this.stateStore.ownersForActiveWindow(windowId)) {
            CreateWindowResult rebuild = rebuildWindow(owner, windowId);
            if (!rebuild.success()) {
                InteractiveDisplay.LOGGER.warn("[{}] rebuild failed owner={} windowId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, owner, windowId, rebuild.reasonCode(), rebuild.message());
            }
        }
    }

    private void rebuildActiveGroups(Set<String> groupIds, Set<String> changedWindowIds) {
        Set<ActiveGroupRef> affected = new HashSet<>();
        for (String groupId : groupIds) {
            for (UUID owner : this.stateStore.ownersForActiveGroup(groupId)) {
                affected.add(new ActiveGroupRef(owner, groupId));
            }
        }
        for (String changedWindowId : changedWindowIds) {
            affected.addAll(this.stateStore.activeGroupsContainingWindow(changedWindowId));
        }
        for (ActiveGroupRef ref : affected) {
            CreateWindowResult rebuild = rebuildGroup(ref.owner(), ref.groupId());
            if (!rebuild.success()) {
                InteractiveDisplay.LOGGER.warn("[{}] group rebuild failed owner={} groupId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, ref.owner(), ref.groupId(), rebuild.reasonCode(), rebuild.message());
            }
        }
    }

    private void rebuildActiveGroupsContainingWindow(String windowId) {
        for (ActiveGroupRef ref : this.stateStore.activeGroupsContainingWindow(windowId)) {
            CreateWindowResult rebuild = rebuildGroup(ref.owner(), ref.groupId());
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

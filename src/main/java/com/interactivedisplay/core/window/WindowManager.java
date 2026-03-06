package com.interactivedisplay.core.window;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.ImageComponentDefinition;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.core.interaction.CommandWhitelist;
import com.interactivedisplay.core.interaction.InteractionBinding;
import com.interactivedisplay.core.interaction.InteractionRegistry;
import com.interactivedisplay.core.layout.LayoutComponent;
import com.interactivedisplay.core.layout.LayoutEngine;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.positioning.WindowPositionTracker;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.DisplayEntityFactory;
import com.interactivedisplay.entity.DisplayEntityPool;
import com.interactivedisplay.entity.EntitySpawnException;
import com.interactivedisplay.schema.SchemaLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class WindowManager implements WindowActionExecutor {
    private static final double HOVER_DISTANCE = 6.0D;

    private final MinecraftServer server;
    private final SchemaLoader schemaLoader;
    private final LayoutEngine layoutEngine;
    private final CoordinateTransformer transformer;
    private final WindowPositionTracker positionTracker;
    private final DisplayEntityFactory entityFactory;
    private final InteractionRegistry interactionRegistry;
    private final DebugRecorder debugRecorder;
    private final CommandWhitelist commandWhitelist;
    private final CallbackRegistry callbackRegistry;
    private final DisplayEntityPool displayEntityPool = new DisplayEntityPool();

    private final Map<String, WindowDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, WindowInstance>> activeWindows = new ConcurrentHashMap<>();

    public WindowManager(MinecraftServer server,
                         SchemaLoader schemaLoader,
                         LayoutEngine layoutEngine,
                         CoordinateTransformer transformer,
                         WindowPositionTracker positionTracker,
                         DisplayEntityFactory entityFactory,
                         InteractionRegistry interactionRegistry,
                         DebugRecorder debugRecorder,
                         CommandWhitelist commandWhitelist,
                         CallbackRegistry callbackRegistry) {
        this.server = server;
        this.schemaLoader = schemaLoader;
        this.layoutEngine = layoutEngine;
        this.transformer = transformer;
        this.positionTracker = positionTracker;
        this.entityFactory = entityFactory;
        this.interactionRegistry = interactionRegistry;
        this.debugRecorder = debugRecorder;
        this.commandWhitelist = commandWhitelist;
        this.callbackRegistry = callbackRegistry;
    }

    public ReloadWindowResult reloadAll() {
        tryReloadSupport();
        SchemaLoader.LoadResult loadResult = this.schemaLoader.loadAll();

        if (!loadResult.hasErrors()) {
            this.definitions.clear();
            this.definitions.putAll(loadResult.definitions());
            rebuildActiveWindows(loadResult.definitions().keySet());
            ReloadWindowResult result = ReloadWindowResult.success(null, this.definitions.size(), 0, loadResult.errors(), "전체 창 리로드 완료");
            recordReload(DebugLevel.DEBUG, result);
            return result;
        }

        Map<String, WindowDefinition> merged = new HashMap<>(this.definitions);
        merged.putAll(loadResult.definitions());
        this.definitions.clear();
        this.definitions.putAll(merged);
        rebuildActiveWindows(loadResult.definitions().keySet());
        ReloadWindowResult result = ReloadWindowResult.failure(DebugReason.SCHEMA_VALIDATION_FAILED, null, this.definitions.size(), loadResult.errors().size(), loadResult.errors(), "전체 창 리로드 완료 (오류 " + loadResult.errors().size() + "건)");
        recordReload(DebugLevel.WARN, result);
        return result;
    }

    public ReloadWindowResult reloadOne(String windowId) {
        tryReloadSupport();
        SchemaLoader.LoadResult loadResult = this.schemaLoader.loadAll();
        WindowDefinition definition = loadResult.definitions().get(windowId);
        if (definition == null) {
            DebugReason reasonCode = loadResult.hasErrors() ? DebugReason.SCHEMA_VALIDATION_FAILED : DebugReason.WINDOW_DEFINITION_NOT_FOUND;
            ReloadWindowResult result = ReloadWindowResult.failure(reasonCode, windowId, this.definitions.size(), loadResult.errors().size(), loadResult.errors(), "창 리로드 실패: " + windowId);
            recordReload(DebugLevel.WARN, result);
            return result;
        }

        this.definitions.put(windowId, definition);
        rebuildActiveWindowDefinitions(windowId);
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
        WindowDefinition definition = this.definitions.get(windowId);
        String playerName = player.getGameProfile().getName();
        if (definition == null) {
            CreateWindowResult result = CreateWindowResult.failure(DebugReason.WINDOW_DEFINITION_NOT_FOUND, player.getUuid(), playerName, windowId, null, overrideAnchor, 0, 0, "창 정의를 찾을 수 없음");
            recordCreate(DebugLevel.WARN, positionMode, result);
            return result;
        }

        removeWindowInternal(player.getUuid(), windowId, false);

        WindowPositionTracker.WindowTransformState transformState = this.positionTracker.resolve(player, positionMode, definition.offset(), overrideAnchor);
        List<LayoutComponent> layout = this.layoutEngine.calculate(definition);
        ServerWorld world = player.getWorld();
        long tick = this.server.getTicks();
        WindowInstance instance = new WindowInstance(
                player.getUuid(),
                windowId,
                world.getRegistryKey(),
                positionMode,
                positionMode == PositionMode.FIXED ? transformState.anchor() : overrideAnchor,
                transformState.anchor(),
                transformState.yaw(),
                transformState.pitch(),
                tick
        );

        int spawnedEntityCount = 0;
        List<WindowComponentRuntime> activatedRuntimes = new ArrayList<>();
        try {
            for (LayoutComponent layoutComponent : layout) {
                ComponentDefinition component = layoutComponent.definition();
                if (!component.visible()) {
                    continue;
                }

                Vec3d worldPosition = this.transformer.toWorld(transformState.anchor(), layoutComponent.localPosition());
                String signature = buildSignature(component);
                WindowComponentRuntime runtime = this.displayEntityPool.acquire(player.getUuid(), world.getRegistryKey(), signature, tick);
                if (runtime != null) {
                    runtime.redefine(component, layoutComponent.localPosition());
                    this.entityFactory.reconfigureRuntime(this.server, world, runtime, worldPosition, positionMode, world.getPlayers());
                } else {
                    runtime = this.entityFactory.spawnRuntime(this.server, world, player.getUuid(), signature, component, worldPosition, positionMode, world.getPlayers());
                    runtime.redefine(component, layoutComponent.localPosition());
                }

                instance.addRuntime(runtime);
                activatedRuntimes.add(runtime);
                spawnedEntityCount += runtime.entityIds().size();

                if (runtime.interactionEntityId() != null && component instanceof ButtonComponentDefinition button) {
                    this.interactionRegistry.register(runtime.interactionEntityId(), new InteractionBinding(player.getUuid(), windowId, button.id(), button.action()));
                }
            }

            this.activeWindows.computeIfAbsent(player.getUuid(), ignored -> new ConcurrentHashMap<>()).put(windowId, instance);
            CreateWindowResult result = CreateWindowResult.success(player.getUuid(), playerName, windowId, transformState.anchor(), layout.size(), spawnedEntityCount, "창 생성 완료");
            recordCreate(DebugLevel.DEBUG, positionMode, result);
            return result;
        } catch (EntitySpawnException exception) {
            cleanupFailedCreate(player.getUuid(), windowId, activatedRuntimes);
            CreateWindowResult result = CreateWindowResult.failure(DebugReason.ENTITY_SPAWN_FAILED, player.getUuid(), playerName, windowId, componentIdFrom(exception.getMessage()), transformState.anchor(), layout.size(), spawnedEntityCount, exception.getMessage());
            recordCreate(DebugLevel.WARN, positionMode, result);
            return result;
        } catch (RuntimeException exception) {
            cleanupFailedCreate(player.getUuid(), windowId, activatedRuntimes);
            CreateWindowResult result = CreateWindowResult.failure(DebugReason.ENTITY_SPAWN_FAILED, player.getUuid(), playerName, windowId, null, transformState.anchor(), layout.size(), spawnedEntityCount, "창 생성 중 예외 발생: " + exception.getMessage());
            this.debugRecorder.record(DebugEventType.WINDOW_CREATE, DebugLevel.ERROR, player.getUuid(), playerName, windowId, null, null, DebugReason.ENTITY_SPAWN_FAILED, result.message(), exception);
            InteractiveDisplay.LOGGER.error("[{}] window create error player={} windowId={} mode={} anchor={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, playerName, windowId, positionMode, transformState.anchor(), result.reasonCode(), result.message(), exception);
            return result;
        }
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
        return createWindow(player, windowId, instance.positionMode(), instance.fixedAnchor());
    }

    public void tick() {
        long currentTick = this.server.getTicks();
        this.displayEntityPool.expire(currentTick, runtime -> this.entityFactory.destroyRuntime(this.server, runtime));

        for (Map.Entry<UUID, Map<String, WindowInstance>> ownerEntry : this.activeWindows.entrySet()) {
            ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(ownerEntry.getKey());
            if (player == null) {
                continue;
            }

            for (WindowInstance instance : ownerEntry.getValue().values()) {
                WindowDefinition definition = this.definitions.get(instance.windowId());
                if (definition == null) {
                    continue;
                }
                if (!player.getWorld().getRegistryKey().equals(instance.worldKey())) {
                    removeWindowInternal(ownerEntry.getKey(), instance.windowId(), false);
                    continue;
                }

                WindowPositionTracker.WindowTransformState nextState = this.positionTracker.resolve(player, instance.positionMode(), definition.offset(), instance.fixedAnchor());
                if (!this.positionTracker.shouldUpdate(instance, nextState, currentTick)) {
                    syncCanvases(instance, player.getWorld().getPlayers());
                    continue;
                }

                if (instance.positionMode() != PositionMode.FIXED || !Objects.equals(instance.currentAnchor(), nextState.anchor())) {
                    moveWindow(instance, nextState, player.getWorld());
                }
                updateHover(instance, player);
                syncCanvases(instance, player.getWorld().getPlayers());
                instance.updateTransform(nextState.anchor(), nextState.yaw(), nextState.pitch(), currentTick);
            }
        }
    }

    public void handlePlayerJoin(ServerPlayerEntity player) {
        for (Map<String, WindowInstance> windows : this.activeWindows.values()) {
            for (WindowInstance instance : windows.values()) {
                syncCanvases(instance, List.of(player));
            }
        }
    }

    public RemoveWindowResult removeWindow(UUID owner, String windowId) {
        return removeWindowInternal(owner, windowId, true);
    }

    public void removeAll(UUID owner) {
        Map<String, WindowInstance> playerWindows = this.activeWindows.get(owner);
        if (playerWindows == null) {
            return;
        }
        for (String windowId : new ArrayList<>(playerWindows.keySet())) {
            removeWindowInternal(owner, windowId, false);
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

    public int loadedWindowCount() {
        return this.definitions.size();
    }

    public int activeWindowCount() {
        int total = 0;
        for (Map<String, WindowInstance> playerWindows : this.activeWindows.values()) {
            total += playerWindows.size();
        }
        return total;
    }

    public WindowInstance findActiveWindow(UUID owner, String windowId) {
        Map<String, WindowInstance> windows = this.activeWindows.get(owner);
        return windows == null ? null : windows.get(windowId);
    }

    public boolean hasDefinition(String windowId) {
        return this.definitions.containsKey(windowId);
    }

    public int pooledEntityCount() {
        return this.displayEntityPool.size();
    }

    public int mapCacheEntryCount() {
        return this.schemaLoader.mapCacheEntryCount();
    }

    @Override
    public RemoveWindowResult closeWindow(UUID owner, String windowId) {
        return removeWindow(owner, windowId);
    }

    @Override
    public CreateWindowResult openWindow(UUID owner, String windowId) {
        ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(owner);
        if (player == null) {
            return CreateWindowResult.failure(DebugReason.ACTION_EXECUTION_FAILED, owner, null, windowId, null, null, 0, 0, "action 대상 플레이어를 찾을 수 없음");
        }
        return createWindow(player, windowId, PositionMode.FIXED, null);
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
        for (WindowComponentRuntime runtime : instance.runtimes()) {
            Vec3d worldPosition = this.transformer.toWorld(nextState.anchor(), runtime.localPosition());
            this.entityFactory.moveRuntime(this.server, world, runtime, worldPosition, instance.positionMode());
        }
    }

    private void updateHover(WindowInstance instance, ServerPlayerEntity player) {
        ServerWorld world = player.getWorld();
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(player.getRotationVec(1.0f).multiply(HOVER_DISTANCE));
        WindowComponentRuntime hoveredRuntime = null;
        double closest = Double.MAX_VALUE;

        for (WindowComponentRuntime runtime : instance.runtimes()) {
            if (!(runtime.definition() instanceof ButtonComponentDefinition) || runtime.interactionEntityId() == null) {
                continue;
            }
            Entity entity = world.getEntity(runtime.interactionEntityId());
            if (entity == null) {
                continue;
            }
            Box box = entity.getBoundingBox();
            var hit = box.raycast(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = hit.get().squaredDistanceTo(start);
            if (distance < closest) {
                closest = distance;
                hoveredRuntime = runtime;
            }
        }

        for (WindowComponentRuntime runtime : instance.runtimes()) {
            if (!(runtime.definition() instanceof ButtonComponentDefinition button)) {
                continue;
            }
            boolean shouldHover = runtime == hoveredRuntime;
            if (runtime.hovered() != shouldHover) {
                this.entityFactory.setButtonHover(this.server, world, runtime, button, shouldHover);
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

        ServerWorld world = this.server.getWorld(instance.worldKey());
        if (world != null) {
            for (WindowComponentRuntime runtime : instance.runtimes()) {
                if (runtime.interactionEntityId() != null) {
                    this.interactionRegistry.unregister(runtime.interactionEntityId());
                }
                this.entityFactory.deactivateRuntime(this.server, world, runtime);
                this.displayEntityPool.release(owner, instance.worldKey(), runtime, this.server.getTicks());
            }
        }

        if (playerWindows.isEmpty()) {
            this.activeWindows.remove(owner);
        }

        RemoveWindowResult result = RemoveWindowResult.success(owner, windowId, instance.entityIds().size(), "창 제거 완료");
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

    private void cleanupFailedCreate(UUID owner, String windowId, List<WindowComponentRuntime> runtimes) {
        this.interactionRegistry.unregisterWindow(owner, windowId);
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
        StringBuilder builder = new StringBuilder();
        builder.append(component.id()).append('|').append(component.type()).append('|').append(component.size().width()).append('|').append(component.size().height()).append('|').append(component.opacity());
        if (component instanceof TextComponentDefinition text) {
            builder.append('|').append(text.content()).append('|').append(text.fontSize()).append('|').append(text.color()).append('|').append(text.background()).append('|').append(text.lineWidth());
        } else if (component instanceof ButtonComponentDefinition button) {
            builder.append('|').append(button.label()).append('|').append(button.hoverColor()).append('|').append(button.action().type()).append('|').append(button.action().target());
        } else if (component instanceof ImageComponentDefinition image) {
            builder.append('|').append(image.imageType()).append('|').append(image.value()).append('|').append(image.scale());
            if (image.source() != null) {
                builder.append('|').append(image.source().resolvedPath());
            }
        } else if (component instanceof PanelComponentDefinition panel) {
            builder.append('|').append(panel.backgroundColor()).append('|').append(panel.padding()).append('|').append(panel.layoutMode());
            for (ComponentDefinition child : panel.children()) {
                builder.append('{').append(buildSignature(child)).append('}');
            }
        }
        return builder.toString();
    }

    private static String componentIdFrom(String message) {
        if (message == null) {
            return null;
        }
        int index = message.indexOf("componentId=");
        if (index < 0) {
            return null;
        }
        String tail = message.substring(index + "componentId=".length());
        int end = tail.indexOf(' ');
        return end < 0 ? tail : tail.substring(0, end);
    }
}

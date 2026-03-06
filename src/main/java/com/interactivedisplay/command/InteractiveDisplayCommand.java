package com.interactivedisplay.command;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.interaction.InteractionBinding;
import com.interactivedisplay.core.interaction.InteractionRegistry;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.CreateWindowResult;
import com.interactivedisplay.core.window.ReloadWindowResult;
import com.interactivedisplay.core.window.RemoveWindowResult;
import com.interactivedisplay.core.window.WindowInstance;
import com.interactivedisplay.core.window.WindowManager;
import com.interactivedisplay.debug.DebugEvent;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public final class InteractiveDisplayCommand {
    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND = new SimpleCommandExceptionType(Text.literal("플레이어를 찾을 수 없음"));
    private static final SimpleCommandExceptionType MANAGER_NOT_READY = new SimpleCommandExceptionType(Text.literal("InteractiveDisplay 런타임이 아직 준비되지 않음"));

    private InteractiveDisplayCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                Supplier<WindowManager> managerSupplier,
                                InteractionRegistry interactionRegistry,
                                DebugRecorder debugRecorder) {
        Predicate<ServerCommandSource> canCreate = Permissions.require("interactivedisplay.create", 2);
        Predicate<ServerCommandSource> canRemove = Permissions.require("interactivedisplay.remove", 2);
        Predicate<ServerCommandSource> canReload = Permissions.require("interactivedisplay.reload", 3);
        Predicate<ServerCommandSource> canDebug = Permissions.require("interactivedisplay.debug", 2);

        InteractiveDisplayCommandTree.register(dispatcher,
                new InteractiveDisplayCommandTree.Handlers<>() {
                    @Override
                    public int create(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
                                      String windowId,
                                      String playerName,
                                      PositionMode positionMode,
                                      Vec3d position) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_CREATE, playerName, windowId);
                        ServerPlayerEntity target = getPlayer(context, playerName);
                        CreateWindowResult result = windowManager.createWindow(target, windowId, positionMode, position);
                        if (!result.success()) {
                            InteractiveDisplay.LOGGER.warn("[{}] create command rejected player={} windowId={} mode={} position={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, playerName, windowId, positionMode, position, result.reasonCode(), result.message());
                            context.getSource().sendError(Text.literal("창 생성 실패: " + windowId + " [" + result.reasonCode() + "]"));
                            return 0;
                        }
                        context.getSource().sendFeedback(() -> Text.literal("창 생성 완료: " + windowId), false);
                        return 1;
                    }

                    @Override
                    public int remove(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
                                      String windowId,
                                      String playerName) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_REMOVE, playerName, windowId);
                        ServerPlayerEntity target = getPlayer(context, playerName);
                        RemoveWindowResult result = windowManager.removeWindow(target.getUuid(), windowId);
                        if (!result.success()) {
                            context.getSource().sendError(Text.literal("창 제거 실패: " + windowId + " [" + result.reasonCode() + "]"));
                            return 0;
                        }
                        context.getSource().sendFeedback(() -> Text.literal("창 제거 완료: " + windowId), false);
                        return 1;
                    }

                    @Override
                    public int reload(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
                                      String windowId) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_RELOAD, null, windowId);
                        ReloadWindowResult result = windowId == null ? windowManager.reloadAll() : windowManager.reloadOne(windowId);
                        if (!result.success()) {
                            context.getSource().sendError(Text.literal((windowId == null ? "리로드 완료" : "리로드 실패") + " [" + result.reasonCode() + "] 오류 " + result.errorCount() + "건"));
                            return 0;
                        }
                        context.getSource().sendFeedback(() -> Text.literal(windowId == null ? "전체 창 리로드 완료" : "창 리로드 완료: " + windowId), false);
                        return 1;
                    }

                    @Override
                    public int debugStatus(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_RELOAD, null, null);
                        List<String> lines = buildStatusLines(
                                windowManager.loadedWindowCount(),
                                windowManager.activeWindowCount(),
                                interactionRegistry.size(),
                                windowManager.pooledEntityCount(),
                                windowManager.mapCacheEntryCount(),
                                debugRecorder.recentFailureCount(),
                                debugRecorder.latestFailure(null, null).orElse(null)
                        );
                        sendLines(context, lines, false);
                        return 1;
                    }

                    @Override
                    public int debugRecent(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
                                           String playerName) throws CommandSyntaxException {
                        UUID playerUuid = null;
                        if (playerName != null) {
                            playerUuid = getPlayer(context, playerName).getUuid();
                        }
                        sendLines(context, buildRecentLines(debugRecorder.recentFailures(playerUuid, 5)), false);
                        return 1;
                    }

                    @Override
                    public int debugWindow(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
                                           String windowId,
                                           String playerName) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_RELOAD, playerName, windowId);
                        ServerPlayerEntity player = getPlayer(context, playerName);
                        WindowInstance instance = windowManager.findActiveWindow(player.getUuid(), windowId);
                        sendLines(context, buildWindowLines(windowId, windowManager.hasDefinition(windowId), instance, debugRecorder.latestFailure(player.getUuid(), windowId).orElse(null)), false);
                        return 1;
                    }

                    @Override
                    public int debugBindings(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
                                             String playerName) throws CommandSyntaxException {
                        ServerPlayerEntity player = getPlayer(context, playerName);
                        sendLines(context, buildBindingLines(playerName, interactionRegistry.snapshotOwnerBindings(player.getUuid())), false);
                        return 1;
                    }
                },
                canCreate,
                canRemove,
                canReload,
                canDebug,
                (context, builder) -> CommandSource.suggestMatching(context.getSource().getServer().getPlayerNames(), builder),
                (context, builder) -> {
                    WindowManager manager = managerSupplier.get();
                    if (manager == null) {
                        return builder.buildFuture();
                    }
                    return CommandSource.suggestMatching(manager.loadedWindowIds(), builder);
                }
        );

        InteractiveDisplay.LOGGER.info("[{}] 명령어 트리 등록 완료", InteractiveDisplay.MOD_ID);
    }

    static List<String> buildStatusLines(int loadedWindowCount,
                                         int activeWindowCount,
                                         int bindingCount,
                                         int pooledEntityCount,
                                         int mapCacheEntryCount,
                                         int recentFailureCount,
                                         DebugEvent latestFailure) {
        List<String> lines = new ArrayList<>();
        lines.add("상태 loaded=" + loadedWindowCount + " active=" + activeWindowCount + " bindings=" + bindingCount + " pooled=" + pooledEntityCount + " mapCache=" + mapCacheEntryCount + " recentFailures=" + recentFailureCount);
        if (latestFailure == null) {
            lines.add("최근 실패 없음");
            return lines;
        }
        lines.add("최근 실패 type=" + latestFailure.type() + " reason=" + latestFailure.reasonCode() + " player=" + valueOrDash(latestFailure.playerName()) + " window=" + valueOrDash(latestFailure.windowId()));
        return lines;
    }

    static List<String> buildRecentLines(List<DebugEvent> events) {
        List<String> lines = new ArrayList<>();
        if (events.isEmpty()) {
            lines.add("최근 실패 없음");
            return lines;
        }
        for (DebugEvent event : events) {
            lines.add("[" + event.type() + "] reason=" + event.reasonCode() + " player=" + valueOrDash(event.playerName()) + " window=" + valueOrDash(event.windowId()) + " component=" + valueOrDash(event.componentId()) + " message=" + event.message());
        }
        return lines;
    }

    static List<String> buildWindowLines(String windowId, boolean definitionLoaded, WindowInstance instance, DebugEvent latestFailure) {
        List<String> lines = new ArrayList<>();
        if (!definitionLoaded) {
            lines.add("창 " + windowId + " 정의 없음 [" + DebugReason.WINDOW_DEFINITION_NOT_FOUND + "]");
            return lines;
        }
        if (instance == null) {
            lines.add("창 " + windowId + " 정의 있음, 활성 인스턴스 없음");
        } else {
            lines.add("창 " + windowId + " 정의 있음, 활성 인스턴스 있음, entityCount=" + instance.entityIds().size() + " bindings=" + instance.bindingCount() + " world=" + instance.worldKey().getValue() + " mode=" + instance.positionMode());
        }
        lines.add(latestFailure == null
                ? "최근 실패 없음"
                : "최근 실패 type=" + latestFailure.type() + " reason=" + latestFailure.reasonCode() + " message=" + latestFailure.message());
        return lines;
    }

    static List<String> buildBindingLines(String playerName, Map<UUID, InteractionBinding> bindings) {
        List<String> lines = new ArrayList<>();
        if (bindings.isEmpty()) {
            lines.add("플레이어 " + playerName + " 바인딩 없음");
            return lines;
        }
        lines.add("플레이어 " + playerName + " 바인딩 수=" + bindings.size());
        for (Map.Entry<UUID, InteractionBinding> entry : bindings.entrySet()) {
            InteractionBinding binding = entry.getValue();
            lines.add("entity=" + entry.getKey() + " window=" + binding.windowId() + " component=" + binding.componentId() + " action=" + binding.action().type() + " target=" + valueOrDash(binding.action().target()));
        }
        return lines;
    }

    private static WindowManager getManager(Supplier<WindowManager> managerSupplier,
                                            DebugRecorder debugRecorder,
                                            DebugEventType eventType,
                                            String playerName,
                                            String windowId) throws CommandSyntaxException {
        WindowManager manager = managerSupplier.get();
        if (manager == null) {
            debugRecorder.record(eventType, DebugLevel.WARN, null, playerName, windowId, null, null, DebugReason.MANAGER_NOT_READY, "InteractiveDisplay 런타임이 아직 준비되지 않음", null);
            throw MANAGER_NOT_READY.create();
        }
        return manager;
    }

    private static ServerPlayerEntity getPlayer(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
                                                String playerName) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) {
            throw PLAYER_NOT_FOUND.create();
        }
        return player;
    }

    private static void sendLines(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context,
                                  List<String> lines,
                                  boolean error) {
        for (String line : lines) {
            if (error) {
                context.getSource().sendError(Text.literal(line));
            } else {
                context.getSource().sendFeedback(() -> Text.literal(line), false);
            }
        }
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}

package com.interactivedisplay.command;

import com.interactivedisplay.InteractiveDisplay;
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
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class InteractiveDisplayCommand {
    private static final SimpleCommandExceptionType MANAGER_NOT_READY = new SimpleCommandExceptionType(Component.literal("InteractiveDisplay 런타임이 아직 준비되지 않음"));
    private static final SimpleCommandExceptionType RELATIVE_ROTATION_REQUIRES_ENTITY = new SimpleCommandExceptionType(Component.literal("상대 yaw/pitch(~)는 엔티티 기준 명령에서만 사용 가능"));

    private InteractiveDisplayCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<WindowManager> managerSupplier,
                                DebugRecorder debugRecorder) {
        Predicate<CommandSourceStack> canCreate = Permissions.require("interactivedisplay.create", 2);
        Predicate<CommandSourceStack> canRemove = Permissions.require("interactivedisplay.remove", 2);
        Predicate<CommandSourceStack> canReload = Permissions.require("interactivedisplay.reload", 3);
        Predicate<CommandSourceStack> canDebug = Permissions.require("interactivedisplay.debug", 2);
        Predicate<CommandSourceStack> canList = source -> canCreate.test(source) || canReload.test(source) || canDebug.test(source);

        InteractiveDisplayCommandTree.register(dispatcher,
                new InteractiveDisplayCommandTree.Handlers<>() {
                    @Override
                    public int create(CommandContext<CommandSourceStack> context,
                                      String windowId,
                                      PositionMode positionMode,
                                      Vec3 position,
                                      InteractiveDisplayCommandTree.Rotation rotation) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_CREATE, null, windowId);
                        List<ServerPlayer> targets = resolvePlayers(context);
                        int successCount = 0;
                        List<String> failures = new ArrayList<>();
                        for (ServerPlayer target : targets) {
                            InteractiveDisplayCommandTree.Rotation resolvedRotation = resolveRotationForTarget(
                                    positionMode,
                                    context.getSource(),
                                    rotation
                            );
                            CreateWindowResult result = windowManager.createWindow(
                                    target,
                                    windowId,
                                    positionMode,
                                    position,
                                    resolvedRotation.yaw(),
                                    resolvedRotation.pitch()
                            );
                            if (!result.success()) {
                                InteractiveDisplay.LOGGER.warn("[{}] create command rejected player={} windowId={} mode={} position={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, target.getGameProfile().getName(), windowId, positionMode, position, result.reasonCode(), result.message());
                                failures.add(target.getGameProfile().getName() + " [" + result.reasonCode() + "]");
                                continue;
                            }
                            successCount++;
                        }
                        if (successCount == 0) {
                            context.getSource().sendFailure(Component.literal("창 생성 실패: " + windowId + (failures.isEmpty() ? "" : " " + String.join(", ", failures))));
                            return 0;
                        }
                        if (!failures.isEmpty()) {
                            context.getSource().sendFailure(Component.literal("부분 실패: " + String.join(", ", failures)));
                        }
                        int finalSuccessCount = successCount;
                        context.getSource().sendSuccess(() -> Component.literal(formatSuccessMessage("창 생성 완료", windowId, finalSuccessCount)), false);
                        return successCount;
                    }

                    @Override
                    public int remove(CommandContext<CommandSourceStack> context,
                                      String windowId) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_REMOVE, null, windowId);
                        List<ServerPlayer> targets = resolvePlayers(context);
                        int successCount = 0;
                        List<String> failures = new ArrayList<>();
                        for (ServerPlayer target : targets) {
                            RemoveWindowResult result = windowManager.removeWindow(target.getUUID(), windowId);
                            if (!result.success()) {
                                failures.add(target.getGameProfile().getName() + " [" + result.reasonCode() + "]");
                                continue;
                            }
                            successCount++;
                        }
                        if (successCount == 0) {
                            context.getSource().sendFailure(Component.literal("창 제거 실패: " + windowId + (failures.isEmpty() ? "" : " " + String.join(", ", failures))));
                            return 0;
                        }
                        if (!failures.isEmpty()) {
                            context.getSource().sendFailure(Component.literal("부분 실패: " + String.join(", ", failures)));
                        }
                        int finalSuccessCount = successCount;
                        context.getSource().sendSuccess(() -> Component.literal(formatSuccessMessage("창 제거 완료", windowId, finalSuccessCount)), false);
                        return successCount;
                    }

                    @Override
                    public int reload(CommandContext<CommandSourceStack> context,
                                      String windowId) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_RELOAD, null, windowId);
                        InteractiveDisplay interactiveDisplay = InteractiveDisplay.instance();
                        if (interactiveDisplay != null) {
                            interactiveDisplay.rebuildResourcePack();
                        }
                        ReloadWindowResult result = windowId == null ? windowManager.reloadAll() : windowManager.reloadOne(windowId);
                        if (!result.success()) {
                            context.getSource().sendFailure(Component.literal((windowId == null ? "리로드 완료" : "리로드 실패") + " [" + result.reasonCode() + "] 오류 " + result.errorCount() + "건"));
                            return 0;
                        }
                        context.getSource().sendSuccess(() -> Component.literal(windowId == null ? "전체 창 리로드 완료" : "창 리로드 완료: " + windowId), false);
                        return 1;
                    }

                    @Override
                    public int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_RELOAD, null, null);
                        sendLines(context, buildListLines(windowManager.loadedWindowIds(), windowManager.availableWindowIds(), windowManager.brokenWindowIds()), false);
                        return 1;
                    }

                    @Override
                    public int groupCreate(CommandContext<CommandSourceStack> context,
                                           String groupId,
                                           PositionMode positionMode,
                                           Vec3 position,
                                           InteractiveDisplayCommandTree.Rotation rotation) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_CREATE, null, groupId);
                        List<ServerPlayer> targets = resolvePlayers(context);
                        int successCount = 0;
                        List<String> failures = new ArrayList<>();
                        for (ServerPlayer target : targets) {
                            InteractiveDisplayCommandTree.Rotation resolvedRotation = resolveRotationForTarget(positionMode, context.getSource(), rotation);
                            CreateWindowResult result = windowManager.createGroup(target, groupId, positionMode, position, resolvedRotation.yaw(), resolvedRotation.pitch());
                            if (!result.success()) {
                                failures.add(target.getGameProfile().getName() + " [" + result.reasonCode() + "]");
                                continue;
                            }
                            successCount++;
                        }
                        if (successCount == 0) {
                            context.getSource().sendFailure(Component.literal("그룹 생성 실패: " + groupId + (failures.isEmpty() ? "" : " " + String.join(", ", failures))));
                            return 0;
                        }
                        if (!failures.isEmpty()) {
                            context.getSource().sendFailure(Component.literal("부분 실패: " + String.join(", ", failures)));
                        }
                        int finalSuccessCount = successCount;
                        context.getSource().sendSuccess(() -> Component.literal(formatSuccessMessage("그룹 생성 완료", groupId, finalSuccessCount)), false);
                        return successCount;
                    }

                    @Override
                    public int groupRemove(CommandContext<CommandSourceStack> context, String groupId) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_REMOVE, null, groupId);
                        List<ServerPlayer> targets = resolvePlayers(context);
                        int successCount = 0;
                        List<String> failures = new ArrayList<>();
                        for (ServerPlayer target : targets) {
                            RemoveWindowResult result = windowManager.removeGroup(target.getUUID(), groupId);
                            if (!result.success()) {
                                failures.add(target.getGameProfile().getName() + " [" + result.reasonCode() + "]");
                                continue;
                            }
                            successCount++;
                        }
                        if (successCount == 0) {
                            context.getSource().sendFailure(Component.literal("그룹 제거 실패: " + groupId + (failures.isEmpty() ? "" : " " + String.join(", ", failures))));
                            return 0;
                        }
                        if (!failures.isEmpty()) {
                            context.getSource().sendFailure(Component.literal("부분 실패: " + String.join(", ", failures)));
                        }
                        int finalSuccessCount = successCount;
                        context.getSource().sendSuccess(() -> Component.literal(formatSuccessMessage("그룹 제거 완료", groupId, finalSuccessCount)), false);
                        return successCount;
                    }

                    @Override
                    public int groupList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_RELOAD, null, null);
                        sendLines(context, buildGroupListLines(windowManager.loadedGroupIds(), windowManager.availableGroupIds(), windowManager.brokenGroupIds()), false);
                        return 1;
                    }

                    @Override
                    public int debugStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_RELOAD, null, null);
                        List<String> lines = buildStatusLines(
                                windowManager.loadedWindowCount(),
                                windowManager.activeWindowCount(),
                                windowManager.activeBindingCount(),
                                windowManager.pooledEntityCount(),
                                windowManager.mapCacheEntryCount(),
                                debugRecorder.recentFailureCount(),
                                debugRecorder.latestFailure(null, null).orElse(null)
                        );
                        sendLines(context, lines, false);
                        return 1;
                    }

                    @Override
                    public int debugRecent(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                        UUID playerUuid = hasArgument(context, "player") ? EntityArgument.getPlayer(context, "player").getUUID() : null;
                        sendLines(context, buildRecentLines(debugRecorder.recentFailures(playerUuid, 5)), false);
                        return 1;
                    }

                    @Override
                    public int debugWindow(CommandContext<CommandSourceStack> context,
                                           String windowId) throws CommandSyntaxException {
                        WindowManager windowManager = getManager(managerSupplier, debugRecorder, DebugEventType.WINDOW_RELOAD, null, windowId);
                        ServerPlayer player = EntityArgument.getPlayer(context, "player");
                        WindowInstance instance = windowManager.findWindow(player.getUUID(), windowId);
                        sendLines(context, buildWindowLines(windowId, windowManager.hasDefinition(windowId), instance, debugRecorder.latestFailure(player.getUUID(), windowId).orElse(null)), false);
                        return 1;
                    }

                    @Override
                    public int debugBindings(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                        ServerPlayer player = EntityArgument.getPlayer(context, "player");
                        sendLines(context, buildBindingLines(player.getGameProfile().getName(), windowManager(managerSupplier).bindingSnapshots(player.getUUID())), false);
                        return 1;
                    }
                },
                canCreate,
                canRemove,
                canReload,
                canList,
                canDebug,
                (context, builder) -> {
                    WindowManager manager = managerSupplier.get();
                    if (manager == null) {
                        return builder.buildFuture();
                    }
                    return net.minecraft.commands.SharedSuggestionProvider.suggest(manager.availableWindowIds(), builder);
                },
                (context, builder) -> {
                    WindowManager manager = managerSupplier.get();
                    if (manager == null) {
                        return builder.buildFuture();
                    }
                    return net.minecraft.commands.SharedSuggestionProvider.suggest(manager.availableGroupIds(), builder);
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
            lines.add("창 " + windowId + " 정의 있음, 활성 인스턴스 있음, entityCount=" + instance.entityIds().size() + " bindings=" + instance.bindingCount() + " world=" + instance.worldKey().location() + " mode=" + instance.positionMode() + " fixedYaw=" + instance.fixedYaw() + " fixedPitch=" + instance.fixedPitch());
        }
        lines.add(latestFailure == null
                ? "최근 실패 없음"
                : "최근 실패 type=" + latestFailure.type() + " reason=" + latestFailure.reasonCode() + " message=" + latestFailure.message());
        return lines;
    }

    static List<String> buildBindingLines(String playerName, List<WindowManager.BindingSnapshot> bindings) {
        List<String> lines = new ArrayList<>();
        if (bindings.isEmpty()) {
            lines.add("플레이어 " + playerName + " 바인딩 없음");
            return lines;
        }
        lines.add("플레이어 " + playerName + " 바인딩 수=" + bindings.size());
        for (WindowManager.BindingSnapshot binding : bindings) {
            Vector3f center = binding.localCenter();
            lines.add("window=" + binding.windowId() + " component=" + binding.componentId() + " center=(" + center.x + "," + center.y + "," + center.z + ") size=(" + (binding.halfWidth() * 2.0f) + "," + (binding.halfHeight() * 2.0f) + ") action=" + binding.actionType() + " target=" + valueOrDash(binding.target()));
        }
        return lines;
    }

    static List<String> buildListLines(Set<String> loadedWindowIds, Set<String> availableWindowIds, Set<String> brokenWindowIds) {
        TreeSet<String> sortedLoaded = new TreeSet<>(loadedWindowIds);
        TreeSet<String> sortedAvailable = new TreeSet<>(availableWindowIds);
        TreeSet<String> sortedBroken = new TreeSet<>(brokenWindowIds);
        List<String> lines = new ArrayList<>();
        lines.add("창 목록 loaded=" + sortedLoaded.size() + " configured=" + sortedAvailable.size() + " broken=" + sortedBroken.size());
        if (sortedLoaded.isEmpty()) {
            lines.add("로드된 창 없음");
        } else {
            for (String windowId : sortedLoaded) {
                lines.add("loaded: " + windowId);
            }
        }

        for (String windowId : sortedBroken) {
            lines.add("broken: " + windowId);
        }

        sortedAvailable.removeAll(sortedLoaded);
        sortedAvailable.removeAll(sortedBroken);
        for (String windowId : sortedAvailable) {
            lines.add("configured-only: " + windowId);
        }
        return lines;
    }

    static List<String> buildGroupListLines(Set<String> loadedGroupIds, Set<String> availableGroupIds, Set<String> brokenGroupIds) {
        TreeSet<String> sortedLoaded = new TreeSet<>(loadedGroupIds);
        TreeSet<String> sortedAvailable = new TreeSet<>(availableGroupIds);
        TreeSet<String> sortedBroken = new TreeSet<>(brokenGroupIds);
        List<String> lines = new ArrayList<>();
        lines.add("그룹 목록 loaded=" + sortedLoaded.size() + " configured=" + sortedAvailable.size() + " broken=" + sortedBroken.size());
        if (sortedLoaded.isEmpty()) {
            lines.add("로드된 그룹 없음");
        } else {
            for (String groupId : sortedLoaded) {
                lines.add("loaded: " + groupId);
            }
        }
        for (String groupId : sortedBroken) {
            lines.add("broken: " + groupId);
        }
        sortedAvailable.removeAll(sortedLoaded);
        sortedAvailable.removeAll(sortedBroken);
        for (String groupId : sortedAvailable) {
            lines.add("configured-only: " + groupId);
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

    private static WindowManager windowManager(Supplier<WindowManager> managerSupplier) throws CommandSyntaxException {
        WindowManager manager = managerSupplier.get();
        if (manager == null) {
            throw MANAGER_NOT_READY.create();
        }
        return manager;
    }

    private static List<ServerPlayer> resolvePlayers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return List.copyOf(EntityArgument.getPlayers(context, "player"));
    }

    static InteractiveDisplayCommandTree.Rotation resolveRotationForTarget(PositionMode positionMode,
                                                                           CommandSourceStack source,
                                                                           InteractiveDisplayCommandTree.Rotation requestedRotation) throws CommandSyntaxException {
        return switch (positionMode) {
            case FIXED -> new InteractiveDisplayCommandTree.Rotation(source.getRotation().y, 0.0f);
            case PLAYER_FIXED, PLAYER_VIEW -> {
                if (requestedRotation == null) {
                    yield new InteractiveDisplayCommandTree.Rotation(0.0f, 0.0f);
                }
                validateRelativeRotationSource(source, requestedRotation);
                float yawValue = requestedRotation.yawInput().resolveYaw(source);
                float pitchValue = requestedRotation.pitchInput().resolvePitch(source);
                yield new InteractiveDisplayCommandTree.Rotation(
                        Mth.wrapDegrees(yawValue),
                        Mth.clamp(pitchValue, -90.0f, 90.0f)
                );
            }
        };
    }

    static InteractiveDisplayCommandTree.Rotation resolveRotationForTarget(PositionMode positionMode,
                                                                           float sourceYaw,
                                                                           float targetYaw,
                                                                           float targetPitch,
                                                                           InteractiveDisplayCommandTree.Rotation requestedRotation) {
        return switch (positionMode) {
            case FIXED -> new InteractiveDisplayCommandTree.Rotation(sourceYaw, 0.0f);
            case PLAYER_FIXED, PLAYER_VIEW -> {
                float yawValue = requestedRotation == null ? 0.0f : requestedRotation.yaw();
                float pitchValue = requestedRotation == null ? 0.0f : requestedRotation.pitch();
                yield new InteractiveDisplayCommandTree.Rotation(
                        Mth.wrapDegrees(yawValue),
                        Mth.clamp(pitchValue, -90.0f, 90.0f)
                );
            }
        };
    }

    private static void validateRelativeRotationSource(CommandSourceStack source,
                                                       InteractiveDisplayCommandTree.Rotation requestedRotation) throws CommandSyntaxException {
        if (!requestedRotation.yawInput().relative() && !requestedRotation.pitchInput().relative()) {
            return;
        }
        Entity entity = source.getEntity();
        if (entity == null) {
            throw RELATIVE_ROTATION_REQUIRES_ENTITY.create();
        }
    }

    private static boolean hasArgument(CommandContext<?> context, String name) {
        return context.getNodes().stream().anyMatch(node -> name.equals(node.getNode().getName()));
    }

    private static void sendLines(CommandContext<CommandSourceStack> context, List<String> lines, boolean broadcast) {
        lines.stream()
                .sorted(Comparator.naturalOrder())
                .forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), broadcast));
    }

    private static String formatSuccessMessage(String prefix, String windowId, int successCount) {
        return successCount == 1 ? prefix + ": " + windowId : prefix + ": " + windowId + " x" + successCount;
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}

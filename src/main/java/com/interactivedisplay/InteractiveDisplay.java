package com.interactivedisplay;

import com.interactivedisplay.command.InteractiveDisplayCommand;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.core.interaction.ClickHandleResult;
import com.interactivedisplay.core.interaction.ClickHandler;
import com.interactivedisplay.core.interaction.CommandWhitelist;
import com.interactivedisplay.core.interaction.InteractionRegistry;
import com.interactivedisplay.core.layout.MeditateLayoutEngine;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.WindowPositionTracker;
import com.interactivedisplay.core.window.ReloadWindowResult;
import com.interactivedisplay.core.window.WindowManager;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.DisplayEntityFactory;
import com.interactivedisplay.schema.SchemaLoader;
import com.interactivedisplay.schema.SchemaValidator;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InteractiveDisplay implements DedicatedServerModInitializer {
    public static final String MOD_ID = "interactivedisplay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int DEBUG_BUFFER_CAPACITY = 200;
    private static final CallbackRegistry CALLBACK_REGISTRY = new CallbackRegistry();

    private final InteractionRegistry interactionRegistry = new InteractionRegistry();
    private final DebugRecorder debugRecorder = new DebugRecorder(DEBUG_BUFFER_CAPACITY);

    private volatile WindowManager windowManager;
    private volatile ClickHandler clickHandler;

    public static CallbackRegistry callbackRegistry() {
        return CALLBACK_REGISTRY;
    }

    @Override
    public void onInitializeServer() {
        LOGGER.info("[{}] 초기화 시작", MOD_ID);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                InteractiveDisplayCommand.register(dispatcher, () -> this.windowManager, this.interactionRegistry, this.debugRecorder));

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            ClickHandler handler = this.clickHandler;
            if (handler == null) {
                return ActionResult.PASS;
            }
            ClickHandleResult result = handler.handle(serverPlayer.getUuid(), serverPlayer.getGameProfile().getName(), entity.getUuid(), false);
            return result.consumed() ? ActionResult.CONSUME : ActionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            ClickHandler handler = this.clickHandler;
            if (handler == null) {
                return ActionResult.PASS;
            }
            ClickHandleResult result = handler.handle(serverPlayer.getUuid(), serverPlayer.getGameProfile().getName(), entity.getUuid(), true);
            return result.consumed() ? ActionResult.CONSUME : ActionResult.PASS;
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CoordinateTransformer transformer = new CoordinateTransformer();
            WindowManager manager = new WindowManager(
                    server,
                    new SchemaLoader(FabricLoader.getInstance().getConfigDir(), new SchemaValidator(), this.debugRecorder),
                    new MeditateLayoutEngine(),
                    transformer,
                    new WindowPositionTracker(transformer),
                    new DisplayEntityFactory(this.debugRecorder),
                    this.interactionRegistry,
                    this.debugRecorder,
                    new CommandWhitelist(FabricLoader.getInstance().getConfigDir()),
                    CALLBACK_REGISTRY
            );

            this.windowManager = manager;
            this.clickHandler = new ClickHandler(this.interactionRegistry, manager, this.debugRecorder);

            ReloadWindowResult result = manager.reloadAll();
            if (!result.success()) {
                LOGGER.warn("[{}] 초기 리로드 경고 reasonCode={} errorCount={}", MOD_ID, result.reasonCode(), result.errorCount());
            }
            LOGGER.info("[{}] 런타임 준비 완료", MOD_ID);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            WindowManager manager = this.windowManager;
            if (manager != null) {
                manager.tick();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            WindowManager manager = this.windowManager;
            if (manager != null) {
                manager.handlePlayerJoin(handler.player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            WindowManager manager = this.windowManager;
            if (manager != null) {
                manager.removeAll(handler.player.getUuid());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            WindowManager manager = this.windowManager;
            if (manager != null) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    manager.removeAll(player.getUuid());
                }
            }
            this.clickHandler = null;
            this.windowManager = null;
        });

        LOGGER.info("[{}] 이벤트/명령 등록 완료", MOD_ID);
    }
}

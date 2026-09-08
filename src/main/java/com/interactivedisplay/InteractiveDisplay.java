package com.interactivedisplay;

import com.interactivedisplay.command.InteractiveDisplayCommand;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.core.interaction.ClickHandleResult;
import com.interactivedisplay.core.interaction.ClickHandler;
import com.interactivedisplay.core.interaction.CommandWhitelist;
import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.layout.MeditateLayoutEngine;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.window.ReloadWindowResult;
import com.interactivedisplay.core.window.WindowManager;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.DisplayEntityFactory;
import com.interactivedisplay.item.InteractiveDisplayItems;
import com.interactivedisplay.polymer.PolymerBridge;
import com.interactivedisplay.polymer.PolymerConfigEnsurer;
import com.interactivedisplay.polymer.ResourcePackBootstrap;
import com.interactivedisplay.schema.SchemaLoader;
import com.interactivedisplay.schema.SchemaValidator;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InteractiveDisplay implements DedicatedServerModInitializer {
    public static final String MOD_ID = "interactivedisplay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int DEBUG_BUFFER_CAPACITY = 200;
    private static final CallbackRegistry CALLBACK_REGISTRY = new CallbackRegistry();
    private static volatile InteractiveDisplay INSTANCE;

    private final DebugRecorder debugRecorder = new DebugRecorder(DEBUG_BUFFER_CAPACITY);
    private final Map<UUID, Long> lastLeftClickTicks = new HashMap<>();

    private volatile WindowManager windowManager;
    private volatile ClickHandler clickHandler;
    private volatile ResourcePackBootstrap resourcePackBootstrap;

    public InteractiveDisplay() {
        INSTANCE = this;
    }

    public static CallbackRegistry callbackRegistry() {
        return CALLBACK_REGISTRY;
    }

    public static InteractiveDisplay instance() {
        return INSTANCE;
    }

    @Override
    public void onInitializeServer() {
        LOGGER.info("[{}] 초기화 시작", MOD_ID);
        InteractiveDisplayItems.register();

        Path configDir = FabricLoader.getInstance().getConfigDir();
        PolymerConfigEnsurer configEnsurer = new PolymerConfigEnsurer(configDir);
        this.resourcePackBootstrap = new ResourcePackBootstrap(configEnsurer, new PolymerBridge());
        this.resourcePackBootstrap.prepareFiles();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                InteractiveDisplayCommand.register(dispatcher, () -> this.windowManager, this.debugRecorder));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CoordinateTransformer transformer = new CoordinateTransformer();
            WindowManager manager = new WindowManager(
                    server,
                    new SchemaLoader(FabricLoader.getInstance().getConfigDir(), new SchemaValidator(), this.debugRecorder),
                    new MeditateLayoutEngine(),
                    transformer,
                    new com.interactivedisplay.core.positioning.WindowPositionTracker(transformer),
                    new DisplayEntityFactory(this.debugRecorder),
                    this.debugRecorder,
                    new CommandWhitelist(FabricLoader.getInstance().getConfigDir()),
                    CALLBACK_REGISTRY
            );

            this.windowManager = manager;
            this.clickHandler = new ClickHandler(manager, this.debugRecorder);

            this.rebuildResourcePack();
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
                manager.removeAll(handler.player.getUUID());
            }
            this.lastLeftClickTicks.remove(handler.player.getUUID());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            WindowManager manager = this.windowManager;
            if (manager != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    manager.removeAll(player.getUUID());
                }
                manager.shutdown();
            }
            this.lastLeftClickTicks.clear();
            this.clickHandler = null;
            this.windowManager = null;
        });

        LOGGER.info("[{}] 이벤트/명령 등록 완료", MOD_ID);
    }

    public boolean consumeUiRightClick(ServerPlayer player) {
        return consumeUiClick(player, ClickType.RIGHT);
    }

    public boolean consumeUiLeftClick(ServerPlayer player) {
        return consumeUiClick(player, ClickType.LEFT);
    }

    public boolean consumeUiClick(ServerPlayer player, ClickType clickType) {
        WindowManager manager = this.windowManager;
        ClickHandler handler = this.clickHandler;
        if (manager == null || handler == null || clickType == null) {
            return false;
        }
        if (!InteractiveDisplayItems.isPointer(player.getMainHandItem())) {
            return false;
        }

        UiHitResult hitResult = manager.findUiHit(player);
        if (hitResult == null || !(hitResult.runtime().definition() instanceof ButtonComponentDefinition button)) {
            return false;
        }
        if (!button.clickType().allows(clickType == ClickType.LEFT)) {
            return false;
        }

        if (clickType == ClickType.LEFT && isDuplicateLeftClick(player)) {
            return true;
        }

        ClickHandleResult result = handler.handle(player.getUUID(), player.getGameProfile().getName(), hitResult);
        if (result.consumed()) {
            playButtonSound(player, hitResult);
        }
        return result.consumed();
    }

    public WindowManager windowManager() {
        return this.windowManager;
    }

    public boolean rebuildResourcePack() {
        ResourcePackBootstrap bootstrap = this.resourcePackBootstrap;
        return bootstrap != null && bootstrap.bootstrap(MOD_ID);
    }

    private boolean isDuplicateLeftClick(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        long tick = server.getTickCount();
        Long previous = this.lastLeftClickTicks.put(player.getUUID(), tick);
        return previous != null && previous == tick;
    }

    private static void playButtonSound(ServerPlayer player, UiHitResult hitResult) {
        if (!(hitResult.runtime().definition() instanceof ButtonComponentDefinition button)) {
            return;
        }
        if (button.clickSound() == null || button.clickSound().isBlank()) {
            return;
        }
        ResourceLocation soundId = ResourceLocation.tryParse(button.clickSound());
        if (soundId == null) {
            LOGGER.warn("[{}] invalid clickSound componentId={} value={}", MOD_ID, hitResult.componentId(), button.clickSound());
            return;
        }
        player.playNotifySound(SoundEvent.createVariableRangeEvent(soundId), SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}

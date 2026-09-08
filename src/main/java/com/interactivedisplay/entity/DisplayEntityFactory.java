package com.interactivedisplay.entity;

import com.google.gson.JsonParser;
import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.ImageComponentDefinition;
import com.interactivedisplay.core.component.ImageType;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.mixin.BlockDisplayInvoker;
import com.interactivedisplay.mixin.DisplayInvoker;
import com.interactivedisplay.mixin.TextDisplayInvoker;
import com.mojang.math.Transformation;
import com.mojang.serialization.JsonOps;
import eu.pb4.mapcanvas.api.core.CanvasImage;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.core.PlayerCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.Placeholders;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.BiFunction;
import javax.imageio.ImageIO;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DisplayEntityFactory {
    private static final int INTERPOLATION_DURATION = 3;
    private static final int INTERPOLATION_DELAY = 0;
    private static final int TELEPORT_DURATION = 3;
    private static final float MIN_Z_SCALE = 0.001f;

    private final DebugRecorder debugRecorder;
    private final BiFunction<ServerPlayer, Component, Component> placeholderResolver;

    public DisplayEntityFactory(DebugRecorder debugRecorder) {
        this(debugRecorder, DisplayEntityFactory::applyPlaceholders);
    }

    DisplayEntityFactory(DebugRecorder debugRecorder, BiFunction<ServerPlayer, Component, Component> placeholderResolver) {
        this.debugRecorder = debugRecorder;
        this.placeholderResolver = placeholderResolver;
    }

    public WindowComponentRuntime spawnRuntime(MinecraftServer server,
                                               ServerLevel world,
                                               UUID owner,
                                               String signature,
                                               ComponentDefinition component,
                                               Vec3 position,
                                               PositionMode positionMode,
                                               float yaw,
                                               float pitch,
                                               Collection<ServerPlayer> canvasViewers) {
        try {
            if (component instanceof TextComponentDefinition text) {
                UUID displayId = spawnText(server, world, owner, text, position, positionMode, yaw, pitch);
                return new WindowComponentRuntime(world.dimension(), signature, component, new Vector3f(), displayId, null);
            }

            if (component instanceof PanelComponentDefinition panel) {
                UUID displayId = spawnPanel(world, panel, position, positionMode, yaw, pitch);
                return new WindowComponentRuntime(world.dimension(), signature, component, new Vector3f(), displayId, null);
            }

            if (component instanceof ButtonComponentDefinition button) {
                UUID displayId = spawnButton(server, world, owner, button, position, positionMode, yaw, pitch);
                return new WindowComponentRuntime(world.dimension(), signature, component, new Vector3f(), displayId, null);
            }

            if (component instanceof ImageComponentDefinition image) {
                return spawnImageRuntime(world, signature, image, position, positionMode, yaw, pitch, canvasViewers);
            }

            throw new IllegalArgumentException("지원하지 않는 component type: " + component.type());
        } catch (Exception exception) {
            throw spawnFailure(owner, component.id(), world, position, exception);
        }
    }

    public void reconfigureRuntime(MinecraftServer server,
                                   ServerLevel world,
                                   UUID owner,
                                   WindowComponentRuntime runtime,
                                   Vec3 position,
                                   PositionMode positionMode,
                                   float yaw,
                                   float pitch,
                                   Collection<ServerPlayer> canvasViewers) {
        moveRuntime(world, runtime, position, positionMode, yaw, pitch);
        runtime.setHovered(false);
        applyRuntimeTransform(server, world, owner, runtime, positionMode);
        if (runtime.definition() instanceof ButtonComponentDefinition button) {
            setButtonHover(server, world, owner, runtime, button, false, positionMode);
        }
        if (runtime.mapCanvas() != null) {
            syncMapCanvas(runtime.mapCanvas(), canvasViewers);
        }
    }

    public UUID spawnRoot(MinecraftServer server,
                          ServerLevel world,
                          Vec3 anchor,
                          PositionMode positionMode,
                          float yaw,
                          float pitch) {
        Display.TextDisplay entity = new Display.TextDisplay(EntityType.TEXT_DISPLAY, world);
        entity.setInvisible(false);
        entity.setNoGravity(true);
        entity.setPos(anchor);
        entity.setYRot(displayYaw(positionMode, yaw));
        entity.setXRot(displayPitch(positionMode, pitch));
        world.addFreshEntity(entity);
        applyTextData(entity, Component.empty(), 1, 0, false, 0.0f, Display.BillboardConstraints.FIXED, 0.1f, "center", new Vector3f());
        return entity.getUUID();
    }

    public void moveRoot(ServerLevel world,
                         UUID rootEntityId,
                         Vec3 anchor,
                         PositionMode positionMode,
                         float yaw,
                         float pitch) {
        Entity root = world.getEntity(rootEntityId);
        if (root == null) {
            return;
        }
        root.setPos(anchor);
        root.setYRot(displayYaw(positionMode, yaw));
        root.setXRot(displayPitch(positionMode, pitch));
    }

    public void destroyRoot(MinecraftServer server,
                            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> worldKey,
                            UUID rootEntityId) {
        if (rootEntityId == null) {
            return;
        }
        ServerLevel world = server.getLevel(worldKey);
        if (world == null) {
            return;
        }
        Entity root = world.getEntity(rootEntityId);
        if (root != null) {
            root.ejectPassengers();
            root.discard();
        }
    }

    public void attachRuntime(MinecraftServer server,
                              ServerLevel world,
                              WindowComponentRuntime runtime,
                              UUID rootEntityId,
                              Vec3 anchor,
                              PositionMode positionMode,
                              float yaw,
                              float pitch) {
        Entity root = world.getEntity(rootEntityId);
        if (root == null) {
            return;
        }
        for (UUID entityId : runtime.entityIds()) {
            Entity entity = world.getEntity(entityId);
            if (entity == null) {
                continue;
            }
            entity.stopRiding();
            entity.setInvisible(false);
            entity.setPos(anchor);
            entity.setYRot(displayYaw(positionMode, yaw));
            entity.setXRot(displayPitch(positionMode, pitch));
            entity.startRiding(root, true);
        }
        applyRuntimeTransform(server, world, null, runtime, positionMode);
    }

    public void updateRuntimeOrientation(MinecraftServer server,
                                         ServerLevel world,
                                         WindowComponentRuntime runtime,
                                         PositionMode positionMode,
                                         float yaw,
                                         float pitch) {
        for (UUID entityId : runtime.entityIds()) {
            Entity entity = world.getEntity(entityId);
            if (entity == null) {
                continue;
            }
            entity.setYRot(displayYaw(positionMode, yaw));
            entity.setXRot(displayPitch(positionMode, pitch));
        }
        applyRuntimeTransform(server, world, null, runtime, positionMode);
    }

    public void moveRuntime(ServerLevel world,
                            WindowComponentRuntime runtime,
                            Vec3 position,
                            PositionMode positionMode,
                            float yaw,
                            float pitch) {
        for (UUID entityId : runtime.entityIds()) {
            Entity entity = world.getEntity(entityId);
            if (entity == null) {
                continue;
            }
            entity.stopRiding();
            entity.setInvisible(false);
            entity.setPos(position);
            entity.setYRot(displayYaw(positionMode, yaw));
            entity.setXRot(displayPitch(positionMode, pitch));
        }
    }

    public void deactivateRuntime(MinecraftServer server, ServerLevel world, WindowComponentRuntime runtime) {
        Vec3 hidden = storagePosition(world);
        for (UUID entityId : runtime.entityIds()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                entity.stopRiding();
                entity.setInvisible(true);
                entity.setPos(hidden);
            }
        }
        runtime.setHovered(false);
    }

    public void destroyRuntime(MinecraftServer server, WindowComponentRuntime runtime) {
        ServerLevel world = server.getLevel(runtime.worldKey());
        if (world != null) {
            for (UUID entityId : runtime.entityIds()) {
                Entity entity = world.getEntity(entityId);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
        if (runtime.mapCanvas() != null) {
            runtime.mapCanvas().destroy();
        }
    }

    public void setButtonHover(MinecraftServer server,
                               ServerLevel world,
                               UUID owner,
                               WindowComponentRuntime runtime,
                               ButtonComponentDefinition button,
                               boolean hovered,
                               PositionMode positionMode) {
        if (runtime.displayEntityId() == null) {
            return;
        }
        Entity entity = world.getEntity(runtime.displayEntityId());
        if (!(entity instanceof Display.TextDisplay textDisplay)) {
            return;
        }
        int background = hovered ? parseArgb(button.hoverColor()) : parseArgb(button.backgroundColor());
        applyTextStyle(
                textDisplay,
                renderButtonLabel(button.label(), ownerPlayer(server, owner)),
                buttonLineWidth(button),
                background,
                true,
                button.opacity(),
                "center"
        );
        runtime.setHovered(hovered);
    }

    public void syncMapCanvas(PlayerCanvas canvas, Collection<ServerPlayer> viewers) {
        for (ServerPlayer viewer : viewers) {
            canvas.addPlayer(viewer);
        }
        if (canvas.isDirty()) {
            canvas.sendUpdates();
        }
    }

    private UUID spawnText(MinecraftServer server,
                           ServerLevel world,
                           UUID owner,
                           TextComponentDefinition component,
                           Vec3 position,
                           PositionMode positionMode,
                           float yaw,
                           float pitch) {
        Display.TextDisplay entity = new Display.TextDisplay(EntityType.TEXT_DISPLAY, world);
        entity.setInvisible(false);
        entity.setPos(position);
        entity.setYRot(displayYaw(positionMode, yaw));
        entity.setXRot(displayPitch(positionMode, pitch));
        world.addFreshEntity(entity);
        applyTextData(
                entity,
                renderTextContent(component.content(), component.color(), ownerPlayer(server, owner)),
                component.lineWidth(),
                parseArgb(component.background()),
                component.shadow(),
                component.opacity(),
                billboard(positionMode),
                component.fontSize(),
                component.alignment(),
                new Vector3f()
        );
        return entity.getUUID();
    }

    private UUID spawnPanel(ServerLevel world,
                            PanelComponentDefinition component,
                            Vec3 position,
                            PositionMode positionMode,
                            float yaw,
                            float pitch) {
        Display.TextDisplay entity = new Display.TextDisplay(EntityType.TEXT_DISPLAY, world);
        entity.setInvisible(false);
        entity.setPos(position);
        entity.setYRot(displayYaw(positionMode, yaw));
        entity.setXRot(displayPitch(positionMode, pitch));
        world.addFreshEntity(entity);
        PanelRenderSpec spec = buildPanelRenderSpec(component);
        applyTextData(
                entity,
                spec.text(),
                spec.lineWidth(),
                parseArgb(component.backgroundColor()),
                false,
                spec.textOpacity(),
                billboard(positionMode),
                spec.fontSize(),
                "center",
                new Vector3f()
        );
        return entity.getUUID();
    }

    private UUID spawnButton(MinecraftServer server,
                             ServerLevel world,
                             UUID owner,
                             ButtonComponentDefinition component,
                             Vec3 position,
                             PositionMode positionMode,
                             float yaw,
                             float pitch) {
        Display.TextDisplay entity = new Display.TextDisplay(EntityType.TEXT_DISPLAY, world);
        entity.setInvisible(false);
        entity.setPos(position);
        entity.setYRot(displayYaw(positionMode, yaw));
        entity.setXRot(displayPitch(positionMode, pitch));
        world.addFreshEntity(entity);
        applyTextData(
                entity,
                renderButtonLabel(component.label(), ownerPlayer(server, owner)),
                buttonLineWidth(component),
                parseArgb(component.backgroundColor()),
                true,
                component.opacity(),
                billboard(positionMode),
                component.fontSize(),
                "center",
                new Vector3f()
        );
        return entity.getUUID();
    }

    private WindowComponentRuntime spawnImageRuntime(ServerLevel world,
                                                     String signature,
                                                     ImageComponentDefinition component,
                                                     Vec3 position,
                                                     PositionMode positionMode,
                                                     float yaw,
                                                     float pitch,
                                                     Collection<ServerPlayer> canvasViewers) throws IOException {
        if (component.imageType() == ImageType.ITEM) {
            UUID displayId = spawnItemDisplay(world, buildItemStack(component.value()), component.scale(), position, positionMode, yaw, pitch);
            return new WindowComponentRuntime(world.dimension(), signature, component, new Vector3f(), displayId, null);
        }
        if (component.imageType() == ImageType.BLOCK) {
            UUID displayId = spawnBlockDisplay(world, buildBlockState(component.value()), component.scale(), position, positionMode, yaw, pitch);
            return new WindowComponentRuntime(world.dimension(), signature, component, new Vector3f(), displayId, null);
        }

        PlayerCanvas canvas = DrawableCanvas.create();
        BufferedImage image = ImageIO.read(component.source().resolvedPath().toFile());
        CanvasUtils.draw(canvas, 0, 0, 128, 128, CanvasImage.from(image));
        syncMapCanvas(canvas, canvasViewers);
        UUID displayId = spawnItemDisplay(world, canvas.asStack(), component.scale(), position, positionMode, yaw, pitch);
        return new WindowComponentRuntime(world.dimension(), signature, component, new Vector3f(), displayId, canvas);
    }

    private UUID spawnItemDisplay(ServerLevel world,
                                  ItemStack stack,
                                  float scale,
                                  Vec3 position,
                                  PositionMode positionMode,
                                  float yaw,
                                  float pitch) {
        Display.ItemDisplay entity = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, world);
        entity.setInvisible(false);
        entity.setPos(position);
        entity.setYRot(displayYaw(positionMode, yaw));
        entity.setXRot(displayPitch(positionMode, pitch));
        world.addFreshEntity(entity);
        applyDisplayData(entity, billboard(positionMode), scale, new Vector3f());
        entity.getSlot(0).set(stack);
        return entity.getUUID();
    }

    private UUID spawnBlockDisplay(ServerLevel world,
                                   BlockState state,
                                   float scale,
                                   Vec3 position,
                                   PositionMode positionMode,
                                   float yaw,
                                   float pitch) {
        Display.BlockDisplay entity = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, world);
        entity.setInvisible(false);
        entity.setPos(position);
        entity.setYRot(displayYaw(positionMode, yaw));
        entity.setXRot(displayPitch(positionMode, pitch));
        world.addFreshEntity(entity);
        ((BlockDisplayInvoker) (Object) entity).interactivedisplay$setBlockState(state);
        applyDisplayData(entity, billboard(positionMode), scale, new Vector3f());
        return entity.getUUID();
    }

    private void applyTextData(Display.TextDisplay entity,
                               Component text,
                               int lineWidth,
                               int background,
                               boolean shadow,
                               float opacity,
                               Display.BillboardConstraints billboard,
                               float scale,
                               String alignment,
                               Vector3f translation) {
        applyDisplayData(entity, billboard, scale, translation);
        applyTextStyle(entity, text, lineWidth, background, shadow, opacity, alignment);
    }

    private void applyTextStyle(Display.TextDisplay entity,
                                Component text,
                                int lineWidth,
                                int background,
                                boolean shadow,
                                float opacity,
                                String alignment) {
        TextDisplayInvoker invoker = (TextDisplayInvoker) (Object) entity;
        invoker.interactivedisplay$setText(text);
        invoker.interactivedisplay$setLineWidth(lineWidth);
        invoker.interactivedisplay$setBackgroundColor(background);
        invoker.interactivedisplay$setTextOpacity(toTextOpacity(opacity));
        invoker.interactivedisplay$setFlags(buildTextFlags(shadow, alignment));
    }

    private void applyDisplayData(Display entity,
                                  Display.BillboardConstraints billboard,
                                  float scale,
                                  Vector3f translation) {
        applyDisplayData(entity, billboard, uniformScale(scale), translation);
    }

    private void applyDisplayData(Display entity,
                                  Display.BillboardConstraints billboard,
                                  Vector3f scale,
                                  Vector3f translation) {
        DisplayInvoker invoker = (DisplayInvoker) (Object) entity;
        invoker.interactivedisplay$setTransformationInterpolationDuration(INTERPOLATION_DURATION);
        invoker.interactivedisplay$setTransformationInterpolationDelay(INTERPOLATION_DELAY);
        invoker.interactivedisplay$setPosRotInterpolationDuration(TELEPORT_DURATION);
        invoker.interactivedisplay$setBillboardConstraints(billboard);
        invoker.interactivedisplay$setTransformation(buildTransformation(scale, translation));
    }

    static Transformation buildTransformation(float scale) {
        return buildTransformation(uniformScale(scale), new Vector3f());
    }

    static Transformation buildTransformation(Vector3f scale, Vector3f translation) {
        Vector3f safeScale = new Vector3f(scale.x, scale.y, Math.max(scale.z, MIN_Z_SCALE));
        return new Transformation(
                new Vector3f(translation),
                new Quaternionf(),
                safeScale,
                new Quaternionf()
        );
    }

    static byte buildTextFlags(boolean shadow, String alignment) {
        byte flags = 0;
        if (shadow) {
            flags |= Display.TextDisplay.FLAG_SHADOW;
        }
        switch (normalizeAlignment(alignment)) {
            case "left" -> flags |= Display.TextDisplay.FLAG_ALIGN_LEFT;
            case "right" -> flags |= Display.TextDisplay.FLAG_ALIGN_RIGHT;
            default -> {
            }
        }
        return flags;
    }

    static byte toTextOpacity(float opacity) {
        return (byte) Math.max(0, Math.min(255, Math.round(opacity * 255.0f)));
    }

    private ItemStack buildItemStack(String value) {
        ResourceLocation identifier = ResourceLocation.tryParse(value);
        if (identifier == null) {
            throw new IllegalArgumentException("잘못된 item id: " + value);
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        if (item == Items.AIR) {
            throw new IllegalArgumentException("item id를 찾을 수 없음: " + value);
        }
        return new ItemStack(item);
    }

    private BlockState buildBlockState(String value) {
        ResourceLocation identifier = ResourceLocation.tryParse(value);
        if (identifier == null) {
            throw new IllegalArgumentException("잘못된 block id: " + value);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(identifier);
        if (block.defaultBlockState().isAir()) {
            throw new IllegalArgumentException("block id를 찾을 수 없음: " + value);
        }
        return block.defaultBlockState();
    }

    Component renderTextContent(String content, String color, ServerPlayer owner) {
        return resolvePlaceholders(buildBaseText(content, color), owner);
    }

    Component renderButtonLabel(String label, ServerPlayer owner) {
        return resolvePlaceholders(Component.literal(label), owner);
    }

    private MutableComponent buildBaseText(String content, String color) {
        Component parsed;
        if ((content.startsWith("{") || content.startsWith("["))) {
            parsed = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(content)).result().orElse(Component.literal(content));
        } else {
            parsed = Component.literal(content);
        }
        MutableComponent mutable = parsed.copy();
        TextColor textColor = parseTextColor(color);
        if (textColor != null) {
            mutable.withStyle(style -> style.withColor(textColor));
        }
        return mutable;
    }

    private Component resolvePlaceholders(Component text, ServerPlayer owner) {
        return this.placeholderResolver.apply(owner, text);
    }

    private static Component applyPlaceholders(ServerPlayer player, Component text) {
        if (player == null) {
            return text;
        }
        return Placeholders.parseText(text, PlaceholderContext.of(player));
    }

    private static ServerPlayer ownerPlayer(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(owner);
    }

    private static TextColor parseTextColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var parsed = TextColor.parseColor(value);
        if (parsed.result().isPresent()) {
            return parsed.result().get();
        }
        ChatFormatting formatting = ChatFormatting.getByName(value.toLowerCase(Locale.ROOT));
        return formatting != null ? TextColor.fromLegacyFormat(formatting) : null;
    }

    private static int parseArgb(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        if (normalized.length() == 6) {
            normalized = "FF" + normalized;
        }
        return (int) Long.parseLong(normalized, 16);
    }

    private EntitySpawnException spawnFailure(UUID owner,
                                              String componentId,
                                              ServerLevel world,
                                              Vec3 position,
                                              Exception exception) {
        this.debugRecorder.record(
                DebugEventType.ENTITY_SPAWN,
                DebugLevel.ERROR,
                owner,
                null,
                null,
                componentId,
                null,
                DebugReason.ENTITY_SPAWN_FAILED,
                "엔티티 생성 실패 world=" + world.dimension().location() + " position=" + position,
                exception
        );
        InteractiveDisplay.LOGGER.error(
                "[{}] entity spawn failed world={} componentId={} position={} reasonCode={}",
                InteractiveDisplay.MOD_ID,
                world.dimension().location(),
                componentId,
                position,
                DebugReason.ENTITY_SPAWN_FAILED,
                exception
        );
        return new EntitySpawnException("엔티티 생성 실패 componentId=" + componentId + " position=" + position, exception);
    }

    private static Vec3 storagePosition(ServerLevel world) {
        return new Vec3(0.0, world.getMinY() - 128.0, 0.0);
    }

    private static Display.BillboardConstraints billboard(PositionMode positionMode) {
        return positionMode == PositionMode.PLAYER_VIEW
                ? Display.BillboardConstraints.CENTER
                : Display.BillboardConstraints.FIXED;
    }

    private static float displayYaw(PositionMode positionMode, float yaw) {
        return switch (positionMode) {
            case FIXED, PLAYER_FIXED, PLAYER_VIEW -> yaw + 180.0f;
        };
    }

    private static float displayPitch(PositionMode positionMode, float pitch) {
        return switch (positionMode) {
            case FIXED -> 0.0f;
            case PLAYER_FIXED, PLAYER_VIEW -> pitch;
        };
    }

    private static float componentScale(ComponentDefinition definition) {
        if (definition instanceof ImageComponentDefinition image) {
            return image.scale();
        }
        if (definition instanceof TextComponentDefinition text) {
            return text.fontSize();
        }
        return 1.0f;
    }

    private void applyRuntimeTransform(MinecraftServer server,
                                       ServerLevel world,
                                       UUID owner,
                                       WindowComponentRuntime runtime,
                                       PositionMode positionMode) {
        if (runtime.displayEntityId() == null) {
            return;
        }
        Entity entity = world.getEntity(runtime.displayEntityId());
        if (!(entity instanceof Display display)) {
            return;
        }

        if (runtime.definition() instanceof TextComponentDefinition text && entity instanceof Display.TextDisplay textDisplay) {
            applyTextData(
                    textDisplay,
                    renderTextContent(text.content(), text.color(), ownerPlayer(server, owner)),
                    text.lineWidth(),
                    parseArgb(text.background()),
                    text.shadow(),
                    text.opacity(),
                    billboard(positionMode),
                    text.fontSize(),
                    text.alignment(),
                    new Vector3f()
            );
            return;
        }
        if (runtime.definition() instanceof PanelComponentDefinition panel && entity instanceof Display.TextDisplay textDisplay) {
            PanelRenderSpec spec = buildPanelRenderSpec(panel);
            applyTextData(
                    textDisplay,
                    spec.text(),
                    spec.lineWidth(),
                    parseArgb(panel.backgroundColor()),
                    false,
                    spec.textOpacity(),
                    billboard(positionMode),
                    spec.fontSize(),
                    "center",
                    new Vector3f()
            );
            return;
        }
        if (runtime.definition() instanceof ButtonComponentDefinition button && entity instanceof Display.TextDisplay textDisplay) {
            applyTextData(
                    textDisplay,
                    renderButtonLabel(button.label(), ownerPlayer(server, owner)),
                    buttonLineWidth(button),
                    parseArgb(runtime.hovered() ? button.hoverColor() : button.backgroundColor()),
                    true,
                    button.opacity(),
                    billboard(positionMode),
                    button.fontSize(),
                    "center",
                    new Vector3f()
            );
            return;
        }
        applyDisplayData(display, billboard(positionMode), componentScale(runtime.definition()), new Vector3f());
    }

    private static Vector3f uniformScale(float scale) {
        return new Vector3f(scale, scale, Math.max(scale, MIN_Z_SCALE));
    }

    private static int buttonLineWidth(ButtonComponentDefinition button) {
        float normalizedFontSize = Math.max(button.fontSize(), 0.1f);
        return Math.max(1, Math.round((button.size().width() * 100.0f) / normalizedFontSize));
    }

    private static String normalizeAlignment(String alignment) {
        if (alignment == null || alignment.isBlank()) {
            return "left";
        }
        String normalized = alignment.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "center", "right" -> normalized;
            default -> "left";
        };
    }

    static PanelRenderSpec buildPanelRenderSpec(PanelComponentDefinition panel) {
        int rowCount = Math.max(1, (int) Math.ceil(panel.size().height() / 0.25f));
        float fontSize = Math.max(0.1f, panel.size().height() / rowCount);
        int columnCount = Math.max(1, (int) Math.ceil(panel.size().width() / Math.max(fontSize * 0.6f, 0.05f)));

        String row = "█".repeat(columnCount);
        StringJoiner joiner = new StringJoiner("\n");
        for (int index = 0; index < rowCount; index++) {
            joiner.add(row);
        }

        return new PanelRenderSpec(
                Component.literal(joiner.toString()),
                Math.max(1, columnCount * 6),
                fontSize,
                0.0f
        );
    }

    record PanelRenderSpec(Component text, int lineWidth, float fontSize, float textOpacity) {
    }
}

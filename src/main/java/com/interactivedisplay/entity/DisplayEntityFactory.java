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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.mapcanvas.api.core.CanvasImage;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.core.PlayerCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.text.TextColor;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class DisplayEntityFactory {
    private static final int INTERPOLATION_DURATION = 3;
    private static final int TELEPORT_DURATION = 3;
    private static final float MIN_Z_SCALE = 0.001f;

    private final DebugRecorder debugRecorder;
    private final BiFunction<ServerPlayerEntity, Text, Text> placeholderResolver;

    public DisplayEntityFactory(DebugRecorder debugRecorder) {
        this(debugRecorder, DisplayEntityFactory::applyPlaceholders);
    }

    DisplayEntityFactory(DebugRecorder debugRecorder, BiFunction<ServerPlayerEntity, Text, Text> placeholderResolver) {
        this.debugRecorder = debugRecorder;
        this.placeholderResolver = placeholderResolver;
    }

    public WindowComponentRuntime spawnRuntime(MinecraftServer server,
                                               ServerWorld world,
                                               UUID owner,
                                               String signature,
                                               ComponentDefinition component,
                                               Vec3d position,
                                               PositionMode positionMode,
                                               float yaw,
                                               float pitch,
                                               Collection<ServerPlayerEntity> canvasViewers) {
        try {
            if (component instanceof TextComponentDefinition text) {
                UUID displayId = spawnText(server, world, owner, text, position, positionMode, yaw, pitch);
                return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), displayId, null);
            }

            if (component instanceof PanelComponentDefinition panel) {
                UUID displayId = spawnPanel(server, world, panel, position, positionMode, yaw, pitch);
                return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), displayId, null);
            }

            if (component instanceof ButtonComponentDefinition button) {
                UUID displayId = spawnButton(server, world, owner, button, position, positionMode, yaw, pitch);
                return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), displayId, null);
            }

            if (component instanceof ImageComponentDefinition image) {
                return spawnImageRuntime(server, world, signature, image, position, positionMode, yaw, pitch, canvasViewers);
            }

            throw new IllegalArgumentException("지원하지 않는 component type: " + component.type());
        } catch (Exception exception) {
            throw spawnFailure(owner, component.id(), world, position, exception);
        }
    }

    public void reconfigureRuntime(MinecraftServer server,
                                   ServerWorld world,
                                   UUID owner,
                                   WindowComponentRuntime runtime,
                                   Vec3d position,
                                   PositionMode positionMode,
                                   float yaw,
                                   float pitch,
                                   Collection<ServerPlayerEntity> canvasViewers) {
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
                          ServerWorld world,
                          Vec3d anchor,
                          PositionMode positionMode,
                          float yaw,
                          float pitch) {
        DisplayEntity.TextDisplayEntity entity = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        entity.setInvisible(false);
        entity.setNoGravity(true);
        entity.setPosition(anchor);
        entity.setYaw(displayYaw(positionMode, yaw));
        entity.setPitch(displayPitch(positionMode, pitch));
        world.spawnEntity(entity);
        applyTextData(server, world, entity.getUuid(), Text.empty(), 1, 0, false, 0.0f, "fixed", 0.1f, "center", new org.joml.Vector3f());
        return entity.getUuid();
    }

    public void moveRoot(ServerWorld world,
                         UUID rootEntityId,
                         Vec3d anchor,
                         PositionMode positionMode,
                         float yaw,
                         float pitch) {
        Entity root = world.getEntity(rootEntityId);
        if (root == null) {
            return;
        }
        root.setPosition(anchor);
        root.setYaw(displayYaw(positionMode, yaw));
        root.setPitch(displayPitch(positionMode, pitch));
    }

    public void destroyRoot(MinecraftServer server,
                            net.minecraft.registry.RegistryKey<net.minecraft.world.World> worldKey,
                            UUID rootEntityId) {
        if (rootEntityId == null) {
            return;
        }
        ServerWorld world = server.getWorld(worldKey);
        if (world == null) {
            return;
        }
        Entity root = world.getEntity(rootEntityId);
        if (root != null) {
            root.removeAllPassengers();
            root.discard();
        }
    }

    public void attachRuntime(MinecraftServer server,
                              ServerWorld world,
                              WindowComponentRuntime runtime,
                              UUID rootEntityId,
                              Vec3d anchor,
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
            entity.setPosition(anchor);
            entity.setYaw(displayYaw(positionMode, yaw));
            entity.setPitch(displayPitch(positionMode, pitch));
            entity.startRiding(root, true);
        }
        applyRuntimeTransform(server, world, null, runtime, positionMode);
    }

    public void updateRuntimeOrientation(MinecraftServer server,
                                         ServerWorld world,
                                         WindowComponentRuntime runtime,
                                         PositionMode positionMode,
                                         float yaw,
                                         float pitch) {
        for (UUID entityId : runtime.entityIds()) {
            Entity entity = world.getEntity(entityId);
            if (entity == null) {
                continue;
            }
            entity.setYaw(displayYaw(positionMode, yaw));
            entity.setPitch(displayPitch(positionMode, pitch));
        }
        applyRuntimeTransform(server, world, null, runtime, positionMode);
    }

    public void moveRuntime(ServerWorld world,
                            WindowComponentRuntime runtime,
                            Vec3d position,
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
            entity.setPosition(position);
            entity.setYaw(displayYaw(positionMode, yaw));
            entity.setPitch(displayPitch(positionMode, pitch));
        }
    }

    public void deactivateRuntime(MinecraftServer server, ServerWorld world, WindowComponentRuntime runtime) {
        Vec3d hidden = storagePosition(world);
        for (UUID entityId : runtime.entityIds()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                entity.stopRiding();
                entity.setInvisible(true);
                entity.setPosition(hidden);
            }
        }
        runtime.setHovered(false);
    }

    public void destroyRuntime(MinecraftServer server, WindowComponentRuntime runtime) {
        ServerWorld world = server.getWorld(runtime.worldKey());
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
                               ServerWorld world,
                               UUID owner,
                               WindowComponentRuntime runtime,
                               ButtonComponentDefinition button,
                               boolean hovered,
                               PositionMode positionMode) {
        if (runtime.displayEntityId() == null) {
            return;
        }
        int background = hovered ? parseArgb(button.hoverColor()) : parseArgb(button.backgroundColor());
        applyTextData(server, world, runtime.displayEntityId(), renderButtonLabel(button.label(), ownerPlayer(server, owner)), buttonLineWidth(button), background, true, button.opacity(), billboard(positionMode), button.fontSize(), "center", new org.joml.Vector3f());
        runtime.setHovered(hovered);
    }

    public void syncMapCanvas(PlayerCanvas canvas, Collection<ServerPlayerEntity> viewers) {
        for (ServerPlayerEntity viewer : viewers) {
            canvas.addPlayer(viewer);
        }
        if (canvas.isDirty()) {
            canvas.sendUpdates();
        }
    }

    private UUID spawnText(MinecraftServer server,
                           ServerWorld world,
                           UUID owner,
                           TextComponentDefinition component,
                           Vec3d position,
                           PositionMode positionMode,
                           float yaw,
                           float pitch) {
        DisplayEntity.TextDisplayEntity entity = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        entity.setInvisible(false);
        entity.setPosition(position);
        entity.setYaw(displayYaw(positionMode, yaw));
        entity.setPitch(displayPitch(positionMode, pitch));
        world.spawnEntity(entity);
        applyTextData(
                server,
                world,
                entity.getUuid(),
                renderTextContent(component.content(), component.color(), ownerPlayer(server, owner)),
                component.lineWidth(),
                parseArgb(component.background()),
                component.shadow(),
                component.opacity(),
                billboard(positionMode),
                component.fontSize(),
                component.alignment(),
                new org.joml.Vector3f()
        );
        return entity.getUuid();
    }

    private UUID spawnPanel(MinecraftServer server,
                            ServerWorld world,
                            PanelComponentDefinition component,
                            Vec3d position,
                            PositionMode positionMode,
                            float yaw,
                            float pitch) {
        DisplayEntity.TextDisplayEntity entity = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        entity.setInvisible(false);
        entity.setPosition(position);
        entity.setYaw(displayYaw(positionMode, yaw));
        entity.setPitch(displayPitch(positionMode, pitch));
        world.spawnEntity(entity);
        PanelRenderSpec spec = buildPanelRenderSpec(component);
        applyTextData(
                server,
                world,
                entity.getUuid(),
                spec.text(),
                spec.lineWidth(),
                parseArgb(component.backgroundColor()),
                false,
                spec.textOpacity(),
                billboard(positionMode),
                spec.fontSize(),
                "center",
                new org.joml.Vector3f()
        );
        return entity.getUuid();
    }

    private UUID spawnButton(MinecraftServer server,
                             ServerWorld world,
                             UUID owner,
                             ButtonComponentDefinition component,
                             Vec3d position,
                             PositionMode positionMode,
                             float yaw,
                             float pitch) {
        DisplayEntity.TextDisplayEntity entity = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        entity.setInvisible(false);
        entity.setPosition(position);
        entity.setYaw(displayYaw(positionMode, yaw));
        entity.setPitch(displayPitch(positionMode, pitch));
        world.spawnEntity(entity);
        applyTextData(
                server,
                world,
                entity.getUuid(),
                renderButtonLabel(component.label(), ownerPlayer(server, owner)),
                buttonLineWidth(component),
                parseArgb(component.backgroundColor()),
                true,
                component.opacity(),
                billboard(positionMode),
                component.fontSize(),
                "center",
                new org.joml.Vector3f()
        );
        return entity.getUuid();
    }

    private WindowComponentRuntime spawnImageRuntime(MinecraftServer server,
                                                     ServerWorld world,
                                                     String signature,
                                                     ImageComponentDefinition component,
                                                     Vec3d position,
                                                     PositionMode positionMode,
                                                     float yaw,
                                                     float pitch,
                                                     Collection<ServerPlayerEntity> canvasViewers) throws IOException {
        if (component.imageType() == ImageType.ITEM) {
            UUID displayId = spawnItemDisplay(server, world, buildItemStack(component.value()), component.scale(), position, positionMode, yaw, pitch);
            return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), displayId, null);
        }
        if (component.imageType() == ImageType.BLOCK) {
            UUID displayId = spawnBlockDisplay(server, world, buildBlockState(component.value()), component.scale(), position, positionMode, yaw, pitch);
            return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), displayId, null);
        }

        PlayerCanvas canvas = DrawableCanvas.create();
        BufferedImage image = ImageIO.read(component.source().resolvedPath().toFile());
        CanvasUtils.draw(canvas, 0, 0, 128, 128, CanvasImage.from(image));
        syncMapCanvas(canvas, canvasViewers);
        UUID displayId = spawnItemDisplay(server, world, canvas.asStack(), component.scale(), position, positionMode, yaw, pitch);
        return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), displayId, canvas);
    }

    private UUID spawnItemDisplay(MinecraftServer server,
                                  ServerWorld world,
                                  ItemStack stack,
                                  float scale,
                                  Vec3d position,
                                  PositionMode positionMode,
                                  float yaw,
                                  float pitch) {
        DisplayEntity.ItemDisplayEntity entity = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        entity.setInvisible(false);
        entity.setPosition(position);
        entity.setYaw(displayYaw(positionMode, yaw));
        entity.setPitch(displayPitch(positionMode, pitch));
        world.spawnEntity(entity);
        entity.getStackReference(0).set(stack);
        applyDisplayData(server, world, entity.getUuid(), billboard(positionMode), scale, new org.joml.Vector3f());
        return entity.getUuid();
    }

    private UUID spawnBlockDisplay(MinecraftServer server,
                                   ServerWorld world,
                                   BlockState state,
                                   float scale,
                                   Vec3d position,
                                   PositionMode positionMode,
                                   float yaw,
                                   float pitch) {
        DisplayEntity.BlockDisplayEntity entity = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        entity.setInvisible(false);
        entity.setPosition(position);
        entity.setYaw(displayYaw(positionMode, yaw));
        entity.setPitch(displayPitch(positionMode, pitch));
        world.spawnEntity(entity);
        NbtCompound data = new NbtCompound();
        data.put("block_state", NbtHelper.fromBlockState(state));
        applyEntityData(server, entity, merge(data, buildDisplayData(billboard(positionMode), scale, new org.joml.Vector3f())));
        return entity.getUuid();
    }

    private void applyTextData(MinecraftServer server,
                               ServerWorld world,
                               UUID entityId,
                               Text text,
                               int lineWidth,
                               int background,
                               boolean shadow,
                               float opacity,
                               String billboard,
                               float scale,
                               String alignment,
                               org.joml.Vector3f translation) {
        applyTextData(server, world, entityId, text, lineWidth, background, shadow, opacity, billboard, uniformScale(scale), alignment, translation);
    }

    private void applyTextData(MinecraftServer server,
                               ServerWorld world,
                               UUID entityId,
                               Text text,
                               int lineWidth,
                               int background,
                               boolean shadow,
                               float opacity,
                               String billboard,
                               org.joml.Vector3f scale,
                               String alignment,
                               org.joml.Vector3f translation) {
        Entity entity = world.getEntity(entityId);
        if (!(entity instanceof DisplayEntity.TextDisplayEntity textDisplayEntity)) {
            return;
        }
        NbtCompound data = buildDisplayData(billboard, scale, translation);
        data.put("text", TextCodecs.CODEC.encodeStart(NbtOps.INSTANCE, text).result().orElseThrow());
        data.putInt("line_width", lineWidth);
        data.putInt("background", background);
        data.putBoolean("shadow", shadow);
        data.putByte("text_opacity", (byte) Math.max(0, Math.min(255, Math.round(opacity * 255.0f))));
        data.putString("alignment", normalizeAlignment(alignment));
        applyEntityData(server, textDisplayEntity, data);
    }

    private void applyDisplayData(MinecraftServer server,
                                  ServerWorld world,
                                  UUID entityId,
                                  String billboard,
                                  float scale,
                                  org.joml.Vector3f translation) {
        Entity entity = world.getEntity(entityId);
        if (entity == null) {
            return;
        }
        applyEntityData(server, entity, buildDisplayData(billboard, scale, translation));
    }

    static NbtCompound buildDisplayData(String billboard, float scale) {
        return buildDisplayData(billboard, uniformScale(scale), new org.joml.Vector3f());
    }

    static NbtCompound buildDisplayData(String billboard, float scale, org.joml.Vector3f translation) {
        return buildDisplayData(billboard, uniformScale(scale), translation);
    }

    static NbtCompound buildDisplayData(String billboard, org.joml.Vector3f scale, org.joml.Vector3f translation) {
        try {
            return StringNbtReader.readCompound(buildDisplayDataSnbt(billboard, scale, translation));
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException("display transformation 생성 실패", exception);
        }
    }

    static String buildDisplayDataSnbt(String billboard, float scale) {
        return buildDisplayDataSnbt(billboard, uniformScale(scale), new org.joml.Vector3f());
    }

    static String buildDisplayDataSnbt(String billboard, float scale, org.joml.Vector3f translation) {
        return buildDisplayDataSnbt(billboard, uniformScale(scale), translation);
    }

    static String buildDisplayDataSnbt(String billboard, org.joml.Vector3f scale, org.joml.Vector3f translation) {
        return String.format(Locale.ROOT,
                "{billboard:\"%s\",start_interpolation:0,interpolation_duration:%d,teleport_duration:%d,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[%sf,%sf,%sf],translation:[%sf,%sf,%sf]}}",
                billboard,
                INTERPOLATION_DURATION,
                TELEPORT_DURATION,
                scale.x,
                scale.y,
                Math.max(scale.z, MIN_Z_SCALE),
                translation.x,
                translation.y,
                translation.z
        );
    }

    private static NbtCompound merge(NbtCompound primary, NbtCompound secondary) {
        NbtCompound merged = secondary.copy();
        for (String key : primary.getKeys()) {
            merged.put(key, primary.get(key).copy());
        }
        return merged;
    }

    private void applyEntityData(MinecraftServer server, Entity entity, NbtCompound data) {
        Vec3d position = entity.getPos();
        float yaw = entity.getYaw();
        float pitch = entity.getPitch();
        entity.readData(NbtReadView.create(ErrorReporter.EMPTY, server.getRegistryManager(), data));
        entity.setPosition(position);
        entity.setYaw(yaw);
        entity.setPitch(pitch);
    }

    private ItemStack buildItemStack(String value) {
        Identifier identifier = Identifier.tryParse(value);
        if (identifier == null) {
            throw new IllegalArgumentException("잘못된 item id: " + value);
        }
        Item item = Registries.ITEM.get(identifier);
        if (item == Items.AIR) {
            throw new IllegalArgumentException("item id를 찾을 수 없음: " + value);
        }
        return new ItemStack(item);
    }

    private BlockState buildBlockState(String value) {
        Identifier identifier = Identifier.tryParse(value);
        if (identifier == null) {
            throw new IllegalArgumentException("잘못된 block id: " + value);
        }
        Block block = Registries.BLOCK.get(identifier);
        if (block.getDefaultState().isAir()) {
            throw new IllegalArgumentException("block id를 찾을 수 없음: " + value);
        }
        return block.getDefaultState();
    }

    Text renderTextContent(String content, String color, ServerPlayerEntity owner) {
        return resolvePlaceholders(buildBaseText(content, color), owner);
    }

    Text renderButtonLabel(String label, ServerPlayerEntity owner) {
        return resolvePlaceholders(Text.literal(label), owner);
    }

    private MutableText buildBaseText(String content, String color) {
        Text parsed;
        if ((content.startsWith("{") || content.startsWith("["))) {
            parsed = TextCodecs.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(content)).result().orElse(Text.literal(content));
        } else {
            parsed = Text.literal(content);
        }
        MutableText mutable = parsed.copy();
        TextColor textColor = parseTextColor(color);
        if (textColor != null) {
            mutable.styled(style -> style.withColor(textColor));
        }
        return mutable;
    }

    private Text resolvePlaceholders(Text text, ServerPlayerEntity owner) {
        return this.placeholderResolver.apply(owner, text);
    }

    private static Text applyPlaceholders(ServerPlayerEntity player, Text text) {
        if (player == null) {
            return text;
        }
        return Placeholders.parseText(text, PlaceholderContext.of(player));
    }

    private static ServerPlayerEntity ownerPlayer(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) {
            return null;
        }
        return server.getPlayerManager().getPlayer(owner);
    }

    private static TextColor parseTextColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var parsed = TextColor.parse(value);
        if (parsed.result().isPresent()) {
            return parsed.result().get();
        }
        Formatting formatting = Formatting.byName(value.toLowerCase(Locale.ROOT));
        return formatting != null ? TextColor.fromFormatting(formatting) : null;
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
                                              ServerWorld world,
                                              Vec3d position,
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
                "엔티티 생성 실패 world=" + world.getRegistryKey().getValue() + " position=" + position,
                exception
        );
        InteractiveDisplay.LOGGER.error(
                "[{}] entity spawn failed world={} componentId={} position={} reasonCode={}",
                InteractiveDisplay.MOD_ID,
                world.getRegistryKey().getValue(),
                componentId,
                position,
                DebugReason.ENTITY_SPAWN_FAILED,
                exception
        );
        return new EntitySpawnException("엔티티 생성 실패 componentId=" + componentId + " position=" + position, exception);
    }

    private static Vec3d storagePosition(ServerWorld world) {
        return new Vec3d(0.0, world.getBottomY() - 128.0, 0.0);
    }

    private static String billboard(PositionMode positionMode) {
        return positionMode == PositionMode.PLAYER_VIEW ? "center" : "fixed";
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
                                       ServerWorld world,
                                       UUID owner,
                                       WindowComponentRuntime runtime,
                                       PositionMode positionMode) {
        if (runtime.displayEntityId() == null) {
            return;
        }
        if (runtime.definition() instanceof TextComponentDefinition text) {
            applyTextData(
                    server,
                    world,
                    runtime.displayEntityId(),
                    renderTextContent(text.content(), text.color(), ownerPlayer(server, owner)),
                    text.lineWidth(),
                    parseArgb(text.background()),
                    text.shadow(),
                    text.opacity(),
                    billboard(positionMode),
                    text.fontSize(),
                    text.alignment(),
                    new org.joml.Vector3f()
            );
            return;
        }
        if (runtime.definition() instanceof PanelComponentDefinition panel) {
            PanelRenderSpec spec = buildPanelRenderSpec(panel);
            applyTextData(
                    server,
                    world,
                    runtime.displayEntityId(),
                    spec.text(),
                    spec.lineWidth(),
                    parseArgb(panel.backgroundColor()),
                    false,
                    spec.textOpacity(),
                    billboard(positionMode),
                    spec.fontSize(),
                    "center",
                    new org.joml.Vector3f()
            );
            return;
        }
        if (runtime.definition() instanceof ButtonComponentDefinition button) {
            applyTextData(
                    server,
                    world,
                    runtime.displayEntityId(),
                    renderButtonLabel(button.label(), ownerPlayer(server, owner)),
                    buttonLineWidth(button),
                    parseArgb(runtime.hovered() ? button.hoverColor() : button.backgroundColor()),
                    true,
                    button.opacity(),
                    billboard(positionMode),
                    button.fontSize(),
                    "center",
                    new org.joml.Vector3f()
            );
            return;
        }
        applyDisplayData(server, world, runtime.displayEntityId(), billboard(positionMode), componentScale(runtime.definition()), new org.joml.Vector3f());
    }

    private static org.joml.Vector3f uniformScale(float scale) {
        return new org.joml.Vector3f(scale, scale, Math.max(scale, MIN_Z_SCALE));
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
                Text.literal(joiner.toString()),
                Math.max(1, columnCount * 6),
                fontSize,
                0.0f
        );
    }

    record PanelRenderSpec(Text text, int lineWidth, float fontSize, float textOpacity) {
    }
}

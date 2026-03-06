package com.interactivedisplay.entity;

import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
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
import eu.pb4.mapcanvas.api.core.CanvasImage;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.core.PlayerCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
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
import net.minecraft.text.TextColor;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class DisplayEntityFactory {
    private static final int DEFAULT_TEXT_BACKGROUND = 0x00000000;

    private final DebugRecorder debugRecorder;

    public DisplayEntityFactory(DebugRecorder debugRecorder) {
        this.debugRecorder = debugRecorder;
    }

    public WindowComponentRuntime spawnRuntime(MinecraftServer server,
                                               ServerWorld world,
                                               UUID owner,
                                               String signature,
                                               ComponentDefinition component,
                                               Vec3d position,
                                               PositionMode positionMode,
                                               Collection<ServerPlayerEntity> canvasViewers) {
        try {
            if (component instanceof TextComponentDefinition text) {
                UUID displayId = spawnText(server, world, text, position, positionMode);
                return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), displayId, null, null);
            }

            if (component instanceof PanelComponentDefinition panel) {
                UUID displayId = spawnPanel(server, world, panel, position, positionMode);
                return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), displayId, null, null);
            }

            if (component instanceof ButtonComponentDefinition button) {
                SpawnedButtonEntities entities = spawnButton(server, world, button, position, positionMode);
                return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), entities.textEntityId(), entities.interactionEntityId(), null);
            }

            if (component instanceof ImageComponentDefinition image) {
                return spawnImageRuntime(server, world, signature, image, position, positionMode, canvasViewers);
            }

            throw new IllegalArgumentException("지원하지 않는 component type: " + component.type());
        } catch (Exception exception) {
            throw spawnFailure(owner, component.id(), world, position, exception);
        }
    }

    public void reconfigureRuntime(MinecraftServer server,
                                   ServerWorld world,
                                   WindowComponentRuntime runtime,
                                   Vec3d position,
                                   PositionMode positionMode,
                                   Collection<ServerPlayerEntity> canvasViewers) {
        moveRuntime(server, world, runtime, position, positionMode);
        if (runtime.definition() instanceof ButtonComponentDefinition button) {
            setButtonHover(server, world, runtime, button, false);
            restoreInteraction(server, world, runtime.interactionEntityId(), button);
        }
        if (runtime.mapCanvas() != null) {
            syncMapCanvas(runtime.mapCanvas(), canvasViewers);
        }
    }

    public void moveRuntime(MinecraftServer server,
                            ServerWorld world,
                            WindowComponentRuntime runtime,
                            Vec3d position,
                            PositionMode positionMode) {
        for (UUID entityId : runtime.entityIds()) {
            Entity entity = world.getEntity(entityId);
            if (entity == null) {
                continue;
            }
            entity.setPosition(position);
            entity.setYaw(positionMode == PositionMode.PLAYER_VIEW ? 180.0f : 0.0f);
            entity.setPitch(0.0f);
        }
    }

    public void deactivateRuntime(MinecraftServer server, ServerWorld world, WindowComponentRuntime runtime) {
        Vec3d hidden = storagePosition(world);
        for (UUID entityId : runtime.entityIds()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                entity.setPosition(hidden);
            }
        }
        if (runtime.interactionEntityId() != null) {
            applyInteractionData(server, world, runtime.interactionEntityId(), 0.0f, 0.0f, false);
        }
        if (runtime.definition() instanceof ButtonComponentDefinition button) {
            setButtonHover(server, world, runtime, button, false);
        }
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
                               WindowComponentRuntime runtime,
                               ButtonComponentDefinition button,
                               boolean hovered) {
        if (runtime.displayEntityId() == null) {
            return;
        }
        int background = hovered ? parseArgb(button.hoverColor()) : DEFAULT_TEXT_BACKGROUND;
        applyTextData(server, world, runtime.displayEntityId(), Text.literal(button.label()), Math.max(1, (int) (button.size().width() * 100.0f)), background, true, button.opacity(), "center", 1.0f);
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
                           TextComponentDefinition component,
                           Vec3d position,
                           PositionMode positionMode) {
        DisplayEntity.TextDisplayEntity entity = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        entity.setPosition(position);
        world.spawnEntity(entity);
        applyTextData(
                server,
                world,
                entity.getUuid(),
                buildText(server, component.content(), component.color()),
                component.lineWidth(),
                parseArgb(component.background()),
                component.shadow(),
                component.opacity(),
                positionMode == PositionMode.PLAYER_VIEW ? "center" : "fixed",
                component.fontSize()
        );
        return entity.getUuid();
    }

    private UUID spawnPanel(MinecraftServer server,
                            ServerWorld world,
                            PanelComponentDefinition component,
                            Vec3d position,
                            PositionMode positionMode) {
        DisplayEntity.TextDisplayEntity entity = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        entity.setPosition(position);
        world.spawnEntity(entity);
        applyTextData(
                server,
                world,
                entity.getUuid(),
                Text.empty(),
                Math.max(1, (int) (component.size().width() * 100.0f)),
                parseArgb(component.backgroundColor()),
                false,
                component.opacity(),
                positionMode == PositionMode.PLAYER_VIEW ? "center" : "fixed",
                1.0f
        );
        return entity.getUuid();
    }

    private SpawnedButtonEntities spawnButton(MinecraftServer server,
                                              ServerWorld world,
                                              ButtonComponentDefinition component,
                                              Vec3d position,
                                              PositionMode positionMode) {
        DisplayEntity.TextDisplayEntity textEntity = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        textEntity.setPosition(position);
        world.spawnEntity(textEntity);
        applyTextData(
                server,
                world,
                textEntity.getUuid(),
                Text.literal(component.label()),
                Math.max(1, (int) (component.size().width() * 100.0f)),
                DEFAULT_TEXT_BACKGROUND,
                true,
                component.opacity(),
                positionMode == PositionMode.PLAYER_VIEW ? "center" : "fixed",
                1.0f
        );

        InteractionEntity interactionEntity = new InteractionEntity(EntityType.INTERACTION, world);
        interactionEntity.setPosition(position);
        world.spawnEntity(interactionEntity);
        applyInteractionData(server, world, interactionEntity.getUuid(), component.size().width(), component.size().height(), true);

        return new SpawnedButtonEntities(textEntity.getUuid(), interactionEntity.getUuid());
    }

    private WindowComponentRuntime spawnImageRuntime(MinecraftServer server,
                                                     ServerWorld world,
                                                     String signature,
                                                     ImageComponentDefinition component,
                                                     Vec3d position,
                                                     PositionMode positionMode,
                                                     Collection<ServerPlayerEntity> canvasViewers) throws IOException {
        if (component.imageType() == ImageType.ITEM) {
            UUID displayId = spawnItemDisplay(server, world, buildItemStack(component.value()), component.scale(), position, positionMode);
            return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), displayId, null, null);
        }
        if (component.imageType() == ImageType.BLOCK) {
            UUID displayId = spawnBlockDisplay(server, world, buildBlockState(component.value()), component.scale(), position, positionMode);
            return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), displayId, null, null);
        }

        PlayerCanvas canvas = DrawableCanvas.create();
        BufferedImage image = ImageIO.read(component.source().resolvedPath().toFile());
        CanvasUtils.draw(canvas, 0, 0, 128, 128, CanvasImage.from(image));
        syncMapCanvas(canvas, canvasViewers);
        UUID displayId = spawnItemDisplay(server, world, canvas.asStack(), component.scale(), position, positionMode);
        return new WindowComponentRuntime(world.getRegistryKey(), signature, component, new org.joml.Vector3f(), displayId, null, canvas);
    }

    private UUID spawnItemDisplay(MinecraftServer server,
                                  ServerWorld world,
                                  ItemStack stack,
                                  float scale,
                                  Vec3d position,
                                  PositionMode positionMode) {
        DisplayEntity.ItemDisplayEntity entity = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        entity.setPosition(position);
        world.spawnEntity(entity);
        entity.getStackReference(0).set(stack);
        applyDisplayData(server, world, entity.getUuid(), positionMode == PositionMode.PLAYER_VIEW ? "center" : "fixed", scale);
        return entity.getUuid();
    }

    private UUID spawnBlockDisplay(MinecraftServer server,
                                   ServerWorld world,
                                   BlockState state,
                                   float scale,
                                   Vec3d position,
                                   PositionMode positionMode) {
        DisplayEntity.BlockDisplayEntity entity = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        entity.setPosition(position);
        world.spawnEntity(entity);
        NbtCompound data = new NbtCompound();
        data.put("block_state", NbtHelper.fromBlockState(state));
        applyEntityData(server, entity, merge(data, buildDisplayData(positionMode == PositionMode.PLAYER_VIEW ? "center" : "fixed", scale)));
        return entity.getUuid();
    }

    private void restoreInteraction(MinecraftServer server, ServerWorld world, UUID entityId, ButtonComponentDefinition button) {
        applyInteractionData(server, world, entityId, button.size().width(), button.size().height(), true);
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
                               float scale) {
        Entity entity = world.getEntity(entityId);
        if (!(entity instanceof DisplayEntity.TextDisplayEntity textDisplayEntity)) {
            return;
        }
        NbtCompound data = buildDisplayData(billboard, scale);
        data.put("text", TextCodecs.CODEC.encodeStart(NbtOps.INSTANCE, text).result().orElseThrow());
        data.putInt("line_width", lineWidth);
        data.putInt("background", background);
        data.putBoolean("shadow", shadow);
        data.putByte("text_opacity", (byte) Math.max(0, Math.min(255, Math.round(opacity * 255.0f))));
        applyEntityData(server, textDisplayEntity, data);
    }

    private void applyInteractionData(MinecraftServer server,
                                      ServerWorld world,
                                      UUID entityId,
                                      float width,
                                      float height,
                                      boolean response) {
        Entity entity = world.getEntity(entityId);
        if (!(entity instanceof InteractionEntity interactionEntity)) {
            return;
        }
        NbtCompound data = new NbtCompound();
        data.putFloat("width", width);
        data.putFloat("height", height);
        data.putBoolean("response", response);
        applyEntityData(server, interactionEntity, data);
    }

    private void applyDisplayData(MinecraftServer server,
                                  ServerWorld world,
                                  UUID entityId,
                                  String billboard,
                                  float scale) {
        Entity entity = world.getEntity(entityId);
        if (entity == null) {
            return;
        }
        applyEntityData(server, entity, buildDisplayData(billboard, scale));
    }

    private static NbtCompound buildDisplayData(String billboard, float scale) {
        try {
            return StringNbtReader.readCompound(String.format(Locale.ROOT,
                    "{billboard:\"%s\",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[%sf,%sf,%sf],translation:[0f,0f,0f]}}",
                    billboard,
                    scale,
                    scale,
                    Math.max(scale, 0.001f)
            ));
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException("display transformation 생성 실패", exception);
        }
    }

    private static NbtCompound merge(NbtCompound primary, NbtCompound secondary) {
        NbtCompound merged = secondary.copy();
        for (String key : primary.getKeys()) {
            merged.put(key, primary.get(key).copy());
        }
        return merged;
    }

    private void applyEntityData(MinecraftServer server, Entity entity, NbtCompound data) {
        entity.readData(NbtReadView.create(ErrorReporter.EMPTY, server.getRegistryManager(), data));
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

    private MutableText buildText(MinecraftServer server, String content, String color) {
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
}

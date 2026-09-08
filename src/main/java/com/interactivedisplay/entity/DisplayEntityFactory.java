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
import com.mojang.math.Transformation;
import com.mojang.serialization.JsonOps;
import eu.pb4.mapcanvas.api.core.CanvasImage;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.core.PlayerCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.awt.image.BufferedImage;
import java.io.IOException;
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

    public VirtualWindowHolder createHolder(ServerLevel world,
                                            Vec3 anchor,
                                            ServerPlayer owner,
                                            PositionMode positionMode) {
        return new VirtualWindowHolder(world, anchor, owner, positionMode != PositionMode.FIXED);
    }

    public WindowComponentRuntime spawnRuntime(MinecraftServer server,
                                               ServerLevel world,
                                               UUID owner,
                                               ComponentDefinition component,
                                               Vec3 position,
                                               PositionMode positionMode,
                                               float yaw,
                                               float pitch,
                                               ServerPlayer canvasViewer,
                                               VirtualWindowHolder holder) {
        try {
            DisplayElement element;
            PlayerCanvas canvas = null;

            if (component instanceof TextComponentDefinition text) {
                TextDisplayElement textElement = new TextDisplayElement();
                applyTextData(
                        textElement,
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
                element = textElement;
            } else if (component instanceof PanelComponentDefinition panel) {
                TextDisplayElement textElement = new TextDisplayElement();
                PanelRenderSpec spec = buildPanelRenderSpec(panel);
                applyTextData(
                        textElement,
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
                element = textElement;
            } else if (component instanceof ButtonComponentDefinition button) {
                TextDisplayElement textElement = new TextDisplayElement();
                applyTextData(
                        textElement,
                        renderButtonLabel(button.label(), ownerPlayer(server, owner)),
                        buttonLineWidth(button),
                        parseArgb(button.backgroundColor()),
                        true,
                        button.opacity(),
                        billboard(positionMode),
                        button.fontSize(),
                        "center",
                        new Vector3f()
                );
                element = textElement;
            } else if (component instanceof ImageComponentDefinition image) {
                ImageRuntime imageRuntime = createImageElement(image, canvasViewer);
                element = imageRuntime.element();
                canvas = imageRuntime.canvas();
                applyDisplayData(element, billboard(positionMode), image.scale(), new Vector3f());
            } else {
                throw new IllegalArgumentException("지원하지 않는 component type: " + component.type());
            }

            positionElement(element, holder, position, positionMode, yaw, pitch, false);
            holder.addElement(element);
            return new WindowComponentRuntime(world.dimension(), component, new Vector3f(), element, canvas);
        } catch (Exception exception) {
            throw spawnFailure(owner, component.id(), world, position, exception);
        }
    }

    public void moveRuntime(WindowComponentRuntime runtime,
                            VirtualWindowHolder holder,
                            Vec3 position,
                            PositionMode positionMode,
                            float yaw,
                            float pitch) {
        DisplayElement element = runtime.displayElement();
        if (element == null) {
            return;
        }
        positionElement(element, holder, position, positionMode, yaw, pitch, true);
    }

    public void destroyRuntime(WindowComponentRuntime runtime) {
        if (runtime.mapCanvas() != null) {
            runtime.mapCanvas().destroy();
        }
    }

    public void setButtonHover(MinecraftServer server,
                               UUID owner,
                               WindowComponentRuntime runtime,
                               ButtonComponentDefinition button,
                               boolean hovered) {
        if (!(runtime.displayElement() instanceof TextDisplayElement textElement)) {
            return;
        }
        int background = hovered ? parseArgb(button.hoverColor()) : parseArgb(button.backgroundColor());
        applyTextStyle(
                textElement,
                renderButtonLabel(button.label(), ownerPlayer(server, owner)),
                buttonLineWidth(button),
                background,
                true,
                button.opacity(),
                "center"
        );
        runtime.setHovered(hovered);
    }

    public void syncMapCanvas(PlayerCanvas canvas, ServerPlayer viewer) {
        if (canvas == null || viewer == null) {
            return;
        }
        canvas.addPlayer(viewer);
        if (canvas.isDirty()) {
            canvas.sendUpdates();
        }
    }

    private ImageRuntime createImageElement(ImageComponentDefinition component, ServerPlayer canvasViewer) throws IOException {
        if (component.imageType() == ImageType.ITEM) {
            return new ImageRuntime(new ItemDisplayElement(buildItemStack(component.value())), null);
        }
        if (component.imageType() == ImageType.BLOCK) {
            return new ImageRuntime(new BlockDisplayElement(buildBlockState(component.value())), null);
        }

        PlayerCanvas canvas = DrawableCanvas.create();
        BufferedImage image = ImageIO.read(component.source().resolvedPath().toFile());
        if (image == null) {
            canvas.destroy();
            throw new IOException("MAP 이미지 디코딩 실패: " + component.source().resolvedPath());
        }
        CanvasUtils.draw(canvas, 0, 0, 128, 128, CanvasImage.from(image));
        syncMapCanvas(canvas, canvasViewer);
        return new ImageRuntime(new ItemDisplayElement(canvas.asStack()), canvas);
    }

    private void positionElement(DisplayElement element,
                                 VirtualWindowHolder holder,
                                 Vec3 worldPosition,
                                 PositionMode positionMode,
                                 float yaw,
                                 float pitch,
                                 boolean interpolate) {
        if (!holder.playerAttached()) {
            element.setOffset(worldPosition.subtract(holder.anchor()));
            element.setYaw(displayYaw(positionMode, yaw));
            element.setPitch(displayPitch(positionMode, pitch));
            element.setBillboardMode(billboard(positionMode));
            return;
        }

        // Spawn at the correct world position, then let the ride relationship carry the entity with the player.
        // Rendering displacement/orientation lives in Display transformation data so no entity move packet is needed.
        element.setOffset(worldPosition.subtract(holder.attachmentPosition()));
        Vec3 relative = worldPosition.subtract(holder.passengerRenderOrigin());
        element.setYaw(0.0f);
        element.setPitch(0.0f);
        element.setBillboardMode(Display.BillboardConstraints.FIXED);
        element.setTranslation(new Vector3f((float) relative.x, (float) relative.y, (float) relative.z));
        element.setLeftRotation(attachedRotation(positionMode, yaw, pitch));
        if (interpolate) {
            element.startInterpolationIfDirty();
        }
    }

    private void applyTextData(TextDisplayElement element,
                               Component text,
                               int lineWidth,
                               int background,
                               boolean shadow,
                               float opacity,
                               Display.BillboardConstraints billboard,
                               float scale,
                               String alignment,
                               Vector3f translation) {
        applyDisplayData(element, billboard, scale, translation);
        applyTextStyle(element, text, lineWidth, background, shadow, opacity, alignment);
    }

    private void applyTextStyle(TextDisplayElement element,
                                Component text,
                                int lineWidth,
                                int background,
                                boolean shadow,
                                float opacity,
                                String alignment) {
        element.setText(text);
        element.setLineWidth(lineWidth);
        element.setBackground(background);
        element.setTextOpacity(toTextOpacity(opacity));
        element.setDisplayFlags(buildTextFlags(shadow, alignment));
    }

    private void applyDisplayData(DisplayElement element,
                                  Display.BillboardConstraints billboard,
                                  float scale,
                                  Vector3f translation) {
        applyDisplayData(element, billboard, uniformScale(scale), translation);
    }

    private void applyDisplayData(DisplayElement element,
                                  Display.BillboardConstraints billboard,
                                  Vector3f scale,
                                  Vector3f translation) {
        element.setInterpolationDuration(INTERPOLATION_DURATION);
        element.setStartInterpolation(INTERPOLATION_DELAY);
        element.setTeleportDuration(TELEPORT_DURATION);
        element.setBillboardMode(billboard);
        element.setTransformation(buildTransformation(scale, translation));
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
                "가상 엔티티 생성 실패 world=" + world.dimension().location() + " position=" + position,
                exception
        );
        InteractiveDisplay.LOGGER.error(
                "[{}] virtual entity spawn failed world={} componentId={} position={} reasonCode={}",
                InteractiveDisplay.MOD_ID,
                world.dimension().location(),
                componentId,
                position,
                DebugReason.ENTITY_SPAWN_FAILED,
                exception
        );
        return new EntitySpawnException("가상 엔티티 생성 실패 componentId=" + componentId + " position=" + position, exception);
    }

    private static Display.BillboardConstraints billboard(PositionMode positionMode) {
        return positionMode == PositionMode.PLAYER_VIEW
                ? Display.BillboardConstraints.CENTER
                : Display.BillboardConstraints.FIXED;
    }

    private static Quaternionf attachedRotation(PositionMode positionMode, float yaw, float pitch) {
        float renderedYaw = displayYaw(positionMode, yaw);
        float renderedPitch = displayPitch(positionMode, pitch);
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-renderedYaw),
                (float) Math.toRadians(renderedPitch),
                0.0f
        );
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

    private record ImageRuntime(DisplayElement element, PlayerCanvas canvas) {
    }
}

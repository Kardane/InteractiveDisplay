package com.interactivedisplay.entity;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ButtonBoxModel;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ResolvedButtonBox;
import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.ImageComponentDefinition;
import com.interactivedisplay.core.component.ImageType;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.component.TextInputComponentDefinition;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
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
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.BiFunction;
import javax.imageio.ImageIO;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.ItemDisplayContext;
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
    private static final float TEXT_PIXEL_SCALE = 0.025f;
    private static final float TEXT_LINE_HEIGHT_PIXELS = 10.0f;
    private static final float TEXT_SPACE_ADVANCE_PIXELS = 4.0f;
    private static final float TEXT_BACKGROUND_WIDTH_PADDING_PIXELS = 1.0f;
    private static final float BUTTON_LABEL_Z_OFFSET = 0.001f;
    private static final CoordinateTransformer COORDINATE_TRANSFORMER = new CoordinateTransformer();

    private final DebugRecorder debugRecorder;
    private final BiFunction<ServerPlayer, Component, Component> placeholderResolver;

    public DisplayEntityFactory(DebugRecorder debugRecorder) {
        this(debugRecorder, DisplayEntityFactory::applyPlaceholders);
    }

    DisplayEntityFactory(DebugRecorder debugRecorder, BiFunction<ServerPlayer, Component, Component> placeholderResolver) {
        this.debugRecorder = debugRecorder;
        this.placeholderResolver = placeholderResolver;
    }

    public VirtualWindowHolder createHolder(ServerLevel world, Vec3 anchor) {
        return new VirtualWindowHolder(world, anchor);
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
            validateImagePositionMode(component, positionMode);
            holder.configure(positionMode, canvasViewer);

            DisplayElement element = null;
            DisplayElement backgroundElement = null;
            VirtualElement virtualElement;
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
                virtualElement = element;
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
                virtualElement = element;
            } else if (component instanceof ButtonComponentDefinition button) {
                TextDisplayElement buttonBackground = new TextDisplayElement();
                ButtonBackgroundRenderSpec backgroundSpec = buildButtonBackgroundRenderSpec(button);
                applyDisplayData(buttonBackground, billboard(positionMode), backgroundSpec.scale(), new Vector3f());
                applyTextStyle(
                        buttonBackground,
                        backgroundSpec.text(),
                        backgroundSpec.lineWidth(),
                        parseArgb(button.backgroundColor()),
                        false,
                        backgroundSpec.textOpacity(),
                        "center"
                );

                TextDisplayElement labelElement = new TextDisplayElement();
                applyTextData(
                        labelElement,
                        renderButtonLabel(button.label(), ownerPlayer(server, owner)),
                        buttonLineWidth(button),
                        0x00000000,
                        true,
                        button.opacity(),
                        billboard(positionMode),
                        button.fontSize(),
                        button.horizontalAlignment().serializedName(),
                        new Vector3f()
                );
                backgroundElement = buttonBackground;
                element = labelElement;
                virtualElement = element;
            } else if (component instanceof TextInputComponentDefinition input) {
                TextDisplayElement textElement = new TextDisplayElement();
                applyTextData(
                        textElement,
                        renderTextInputValue(input, input.initialValue(), ownerPlayer(server, owner)),
                        textInputLineWidth(input),
                        parseArgb(input.backgroundColor()),
                        true,
                        input.opacity(),
                        billboard(positionMode),
                        input.fontSize(),
                        "left",
                        new Vector3f()
                );
                element = textElement;
                virtualElement = element;
            } else if (component instanceof ImageComponentDefinition image) {
                ImageRuntime imageRuntime = createImageElement(image, canvasViewer, positionMode, yaw, pitch);
                element = imageRuntime.element();
                virtualElement = imageRuntime.virtualElement();
                canvas = imageRuntime.canvas();
                if (element != null) {
                    configureImageElement(element);
                    applyDisplayData(element, billboard(positionMode), flatScale(image.scale()), new Vector3f());
                }
            } else {
                throw new IllegalArgumentException("지원하지 않는 component type: " + component.type());
            }

            if (backgroundElement != null) {
                positionElement(backgroundElement, holder, position, positionMode, yaw, pitch, false);
            }
            if (element != null) {
                Vector3f localOffset = component instanceof ButtonComponentDefinition button
                        ? buttonLabelLocalOffset(button)
                        : new Vector3f();
                positionElement(element, holder, position, positionMode, yaw, pitch, false, localOffset);
            } else if (virtualElement instanceof MapDisplayElement mapElement) {
                positionMapElement(mapElement, position, positionMode, yaw, pitch);
            }
            RenderedComponent renderedComponent = backgroundElement != null
                    ? RenderedComponent.composite(element, backgroundElement)
                    : element != null
                    ? RenderedComponent.single(element)
                    : RenderedComponent.virtualOnly(virtualElement);
            for (VirtualElement renderedElement : renderedComponent.virtualElements()) {
                holder.addElement(renderedElement);
            }
            return WindowComponentRuntime.fromRenderedComponent(
                    world.dimension(),
                    component,
                    new Vector3f(),
                    renderedComponent,
                    canvas
            );
        } catch (Exception exception) {
            throw spawnFailure(owner, component.id(), world, position, exception);
        }
    }

    private static void validateImagePositionMode(ComponentDefinition component, PositionMode positionMode) {
        if (component instanceof ImageComponentDefinition image
                && image.imageType() == ImageType.MAP
                && positionMode != PositionMode.FIXED) {
            throw new IllegalArgumentException(
                    "MAP 이미지 디스플레이는 FIXED 모드에서만 지원됩니다 (현재 모드: " + positionMode + ")"
            );
        }
    }

    public void moveRuntime(WindowComponentRuntime runtime,
                            VirtualWindowHolder holder,
                            Vec3 position,
                            PositionMode positionMode,
                            float yaw,
                            float pitch) {
        DisplayElement backgroundElement = runtime.backgroundElement();
        if (backgroundElement != null) {
            positionElement(backgroundElement, holder, position, positionMode, yaw, pitch, true);
        }

        DisplayElement element = runtime.displayElement();
        if (element != null) {
            Vector3f localOffset = runtime.definition() instanceof ButtonComponentDefinition button
                    ? buttonLabelLocalOffset(button)
                    : new Vector3f();
            positionElement(element, holder, position, positionMode, yaw, pitch, true, localOffset);
        }
        if (runtime.virtualElement() instanceof MapDisplayElement mapElement) {
            positionMapElement(mapElement, position, positionMode, yaw, pitch);
        }
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
        if (!(runtime.displayElement() instanceof TextDisplayElement labelElement)
                || !(runtime.backgroundElement() instanceof TextDisplayElement backgroundElement)) {
            return;
        }

        backgroundElement.setBackground(hovered ? parseArgb(button.hoverColor()) : parseArgb(button.backgroundColor()));
        labelElement.setText(renderButtonLabel(button.label(), ownerPlayer(server, owner)));

        applyButtonHoverScale(runtime, backgroundElement, button.hoverScale(), hovered);
        applyButtonHoverScale(runtime, labelElement, button.hoverScale(), hovered);
        runtime.setHovered(hovered);
    }

    public void setTextInputHover(MinecraftServer server,
                                  UUID owner,
                                  WindowComponentRuntime runtime,
                                  TextInputComponentDefinition input,
                                  boolean hovered) {
        if (!(runtime.displayElement() instanceof TextDisplayElement textElement)) {
            return;
        }
        int background = hovered ? parseArgb(input.hoverColor()) : parseArgb(input.backgroundColor());
        applyTextStyle(
                textElement,
                renderTextInputValue(input, runtime.inputValue(), ownerPlayer(server, owner)),
                textInputLineWidth(input),
                background,
                true,
                input.opacity(),
                "left"
        );
        runtime.setHovered(hovered);
    }

    public boolean updateTextInputValue(MinecraftServer server,
                                        UUID owner,
                                        WindowComponentRuntime runtime,
                                        TextInputComponentDefinition input,
                                        String value) {
        if (!(runtime.displayElement() instanceof TextDisplayElement textElement)) {
            return false;
        }
        runtime.setInputValue(value);
        int background = runtime.hovered() ? parseArgb(input.hoverColor()) : parseArgb(input.backgroundColor());
        Component rendered = renderTextInputValue(input, runtime.inputValue(), ownerPlayer(server, owner));
        applyTextStyle(
                textElement,
                rendered,
                textInputLineWidth(input),
                background,
                true,
                input.opacity(),
                "left"
        );
        return true;
    }

    public boolean refreshText(MinecraftServer server,
                               UUID owner,
                               WindowComponentRuntime runtime,
                               TextComponentDefinition text) {
        if (!(runtime.displayElement() instanceof TextDisplayElement textElement)) {
            return false;
        }
        Component rendered = renderTextContent(text.content(), text.color(), ownerPlayer(server, owner));
        if (rendered.equals(textElement.getText())) {
            return false;
        }
        textElement.setText(rendered);
        return true;
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

    private ImageRuntime createImageElement(ImageComponentDefinition component,
                                            ServerPlayer canvasViewer,
                                            PositionMode positionMode,
                                            float yaw,
                                            float pitch) throws IOException {
        if (component.imageType() == ImageType.ITEM) {
            ItemDisplayElement element = new ItemDisplayElement(buildItemStack(component.value()));
            return new ImageRuntime(element, element, null);
        }
        if (component.imageType() == ImageType.BLOCK) {
            BlockDisplayElement element = new BlockDisplayElement(buildBlockState(component.value()));
            return new ImageRuntime(element, element, null);
        }

        PlayerCanvas canvas = DrawableCanvas.create();
        BufferedImage image = ImageIO.read(component.source().resolvedPath().toFile());
        if (image == null) {
            canvas.destroy();
            throw new IOException("MAP 이미지 디코딩 실패: " + component.source().resolvedPath());
        }
        CanvasUtils.draw(canvas, 0, 0, 128, 128, CanvasImage.from(image));
        syncMapCanvas(canvas, canvasViewer);
        MapDisplayElement element = new MapDisplayElement(canvas.asStack(), mapDirection(positionMode, yaw, pitch));
        return new ImageRuntime(null, element, canvas);
    }

    private static void configureImageElement(DisplayElement element) {
        if (element instanceof ItemDisplayElement itemDisplay) {
            // Keep item images in a world-facing display context instead of an
            // inventory/held-item presentation.
            itemDisplay.setItemDisplayContext(ItemDisplayContext.FIXED);
        }
    }

    private void positionElement(DisplayElement element,
                                 VirtualWindowHolder holder,
                                 Vec3 worldPosition,
                                 PositionMode positionMode,
                                 float yaw,
                                 float pitch,
                                 boolean interpolate) {
        positionElement(element, holder, worldPosition, positionMode, yaw, pitch, interpolate, new Vector3f());
    }

    private void positionElement(DisplayElement element,
                                 VirtualWindowHolder holder,
                                 Vec3 worldPosition,
                                 PositionMode positionMode,
                                 float yaw,
                                 float pitch,
                                 boolean interpolate,
                                 Vector3f localOffset) {
        Vec3 logicalPosition = COORDINATE_TRANSFORMER.toWorld(
                worldPosition,
                localOffset == null ? new Vector3f() : localOffset,
                positionMode,
                yaw,
                pitch
        );
        Vec3 renderPosition = displayRenderPosition(element, logicalPosition, positionMode, yaw, pitch);
        if (!holder.playerAttached()) {
            element.setOffset(renderPosition.subtract(holder.anchor()));
            element.setYaw(displayYaw(positionMode, yaw));
            element.setPitch(displayPitch(positionMode, pitch));
            element.setBillboardMode(billboard(positionMode));
            return;
        }

        element.setOffset(renderPosition.subtract(holder.attachmentPosition()));
        Vec3 relative = renderPosition.subtract(holder.passengerRenderOrigin());
        element.setYaw(0.0f);
        element.setPitch(0.0f);
        element.setBillboardMode(Display.BillboardConstraints.FIXED);
        element.setTranslation(new Vector3f((float) relative.x, (float) relative.y, (float) relative.z));
        element.setLeftRotation(attachedRotation(positionMode, yaw, pitch));
        if (interpolate) {
            element.startInterpolationIfDirty();
        }
    }

    private static Vec3 displayRenderPosition(DisplayElement element,
                                              Vec3 logicalPosition,
                                              PositionMode positionMode,
                                              float yaw,
                                              float pitch) {
        if (!(element instanceof BlockDisplayElement)) {
            return logicalPosition;
        }

        // A block model occupies [0, 1] in each local axis, while an item
        // display is centered by its item model transform. Move the block
        // display entity origin back by half of its flattened model so both
        // display types share the same logical center.
        double halfWidth = 0.5D;
        double halfDepth = MIN_Z_SCALE * 0.5D;
        if (positionMode == PositionMode.FIXED) {
            return logicalPosition.add(-halfWidth, -halfWidth, -halfDepth);
        }

        Vec3 look = Vec3.directionFromRotation(pitch, yaw).normalize();
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        if (right.lengthSqr() < 1.0E-12D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 up = right.cross(look).normalize();
        Vec3 normal = look.scale(-1.0D);
        return logicalPosition
                .subtract(right.scale(halfWidth))
                .subtract(up.scale(halfWidth))
                .subtract(normal.scale(halfDepth));
    }

    private void positionMapElement(MapDisplayElement element,
                                    Vec3 worldPosition,
                                    PositionMode positionMode,
                                    float yaw,
                                    float pitch) {
        // The item-frame direction is fixed when the map entity is spawned.
        // Its physical movement is delegated to MapAnchorElement for
        // player-attached windows; the map's own world position remains the
        // logical position used by server-side hit testing and diagnostics.
        element.setRenderPosition(worldPosition);
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

    Component renderTextInputValue(TextInputComponentDefinition input, String value, ServerPlayer owner) {
        String display = value == null || value.isEmpty() ? input.placeholder() : value;
        return renderTextContent(display, input.color(), owner);
    }

    private MutableComponent buildBaseText(String content, String color) {
        Component parsed;
        if (content.startsWith("{") || content.startsWith("[")) {
            try {
                parsed = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(content))
                        .result().orElse(Component.literal(content));
            } catch (JsonParseException exception) {
                parsed = Component.literal(content);
            }
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

    static Quaternionf attachedRotation(PositionMode positionMode, float yaw, float pitch) {
        float renderedPitch = displayPitch(positionMode, pitch);
        return COORDINATE_TRANSFORMER.basisRotation(positionMode, yaw, renderedPitch);
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

    private static Vector3f flatScale(float scale) {
        return new Vector3f(scale, scale, MIN_Z_SCALE);
    }

    private static Direction mapDirection(PositionMode positionMode, float yaw, float pitch) {
        Vec3 normal = Vec3.directionFromRotation(
                displayPitch(positionMode, pitch),
                displayYaw(positionMode, yaw)
        );
        return Direction.getApproximateNearest(normal);
    }

    private static void applyButtonHoverScale(WindowComponentRuntime runtime,
                                              DisplayElement element,
                                              float hoverScale,
                                              boolean hovered) {
        Vector3f targetScale = runtime.baseScale(element);
        if (hovered && hoverScale != 1.0f) {
            targetScale.mul(hoverScale);
        }
        element.setInterpolationDuration(INTERPOLATION_DURATION);
        element.setScale(targetScale);
        element.startInterpolationIfDirty();
    }

    static Vector3f buttonLabelLocalOffset(ButtonComponentDefinition button) {
        ResolvedButtonBox box = ButtonBoxModel.resolve(button);
        float verticalOffset = switch (button.verticalAlignment()) {
            case BOTTOM -> button.padding().vertical();
            case TOP -> box.height() - button.padding().vertical() - box.labelHeight();
            case CENTER -> button.padding().vertical()
                    + Math.max(0.0f, (box.contentHeight() - box.labelHeight()) / 2.0f);
        };
        verticalOffset = Math.max(button.padding().vertical(), verticalOffset);
        return new Vector3f(0.0f, verticalOffset, BUTTON_LABEL_Z_OFFSET);
    }

    static float buttonContentWidth(ButtonComponentDefinition button) {
        return ButtonBoxModel.resolve(button).contentWidth();
    }

    static int buttonLineWidth(ButtonComponentDefinition button) {
        return ButtonBoxModel.lineWidthPixels(button);
    }

    static int textInputLineWidth(TextInputComponentDefinition input) {
        float normalizedFontSize = Math.max(input.fontSize(), 0.1f);
        return Math.max(1, Math.round(input.size().width() / (TEXT_PIXEL_SCALE * normalizedFontSize)));
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

    static ButtonBackgroundRenderSpec buildButtonBackgroundRenderSpec(ButtonComponentDefinition button) {
        ResolvedButtonBox box = ButtonBoxModel.resolve(button);
        float targetWidth = box.width();
        float targetHeight = box.height();
        float baseLineHeight = TEXT_PIXEL_SCALE * TEXT_LINE_HEIGHT_PIXELS;
        int rowCount = Math.max(1, (int) Math.ceil(targetHeight / baseLineHeight));
        float scaleY = targetHeight / (rowCount * baseLineHeight);

        float spaceWidthAtScaleY = TEXT_PIXEL_SCALE * TEXT_SPACE_ADVANCE_PIXELS * scaleY;
        int spaceCount = Math.max(1, (int) Math.ceil(targetWidth / spaceWidthAtScaleY));
        int lineWidth = Math.max(1, Math.round(spaceCount * TEXT_SPACE_ADVANCE_PIXELS));
        float backgroundPixelWidth = lineWidth + TEXT_BACKGROUND_WIDTH_PADDING_PIXELS;
        float scaleX = targetWidth / (backgroundPixelWidth * TEXT_PIXEL_SCALE);

        String row = " ".repeat(spaceCount);
        StringJoiner joiner = new StringJoiner("\n");
        for (int index = 0; index < rowCount; index++) {
            joiner.add(row);
        }

        return new ButtonBackgroundRenderSpec(
                Component.literal(joiner.toString()),
                lineWidth,
                new Vector3f(scaleX, scaleY, MIN_Z_SCALE),
                0.0f
        );
    }

    static PanelRenderSpec buildPanelRenderSpec(PanelComponentDefinition panel) {
        float baseLineHeight = TEXT_PIXEL_SCALE * TEXT_LINE_HEIGHT_PIXELS;
        int rowCount = Math.max(1, (int) Math.ceil(panel.size().height() / baseLineHeight));
        float fontSize = Math.max(0.1f, panel.size().height() / (rowCount * baseLineHeight));
        float spaceWidth = TEXT_PIXEL_SCALE * TEXT_SPACE_ADVANCE_PIXELS * fontSize;
        int spaceCount = Math.max(1, (int) Math.ceil(panel.size().width() / spaceWidth));
        int lineWidth = Math.max(1, Math.round(spaceCount * TEXT_SPACE_ADVANCE_PIXELS));

        String row = " ".repeat(spaceCount);
        StringJoiner joiner = new StringJoiner("\n");
        for (int index = 0; index < rowCount; index++) {
            joiner.add(row);
        }

        return new PanelRenderSpec(
                Component.literal(joiner.toString()),
                lineWidth,
                fontSize,
                0.0f
        );
    }

    record ButtonBackgroundRenderSpec(Component text, int lineWidth, Vector3f scale, float textOpacity) {
        ButtonBackgroundRenderSpec {
            scale = new Vector3f(scale);
        }

        @Override
        public Vector3f scale() {
            return new Vector3f(this.scale);
        }
    }

    record PanelRenderSpec(Component text, int lineWidth, float fontSize, float textOpacity) {
    }

    private record ImageRuntime(DisplayElement element, VirtualElement virtualElement, PlayerCanvas canvas) {
    }
}

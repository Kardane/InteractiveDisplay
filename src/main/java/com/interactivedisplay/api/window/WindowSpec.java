package com.interactivedisplay.api.window;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

public final class WindowSpec {
    private final ResourceLocation id;
    private final Size size;
    private final Offset offset;
    private final Layout layout;
    private final List<ComponentSpec> components;
    private final Transition transition;

    private WindowSpec(Builder builder) {
        this.id = builder.id;
        this.size = builder.size;
        this.offset = builder.offset;
        this.layout = builder.layout;
        this.components = List.copyOf(builder.components);
        this.transition = builder.transition;
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public ResourceLocation id() {
        return this.id;
    }

    public Size size() {
        return this.size;
    }

    public Offset offset() {
        return this.offset;
    }

    public Layout layout() {
        return this.layout;
    }

    public List<ComponentSpec> components() {
        return this.components;
    }

    public Transition transition() {
        return this.transition;
    }

    public enum Layout {
        ABSOLUTE,
        VERTICAL,
        HORIZONTAL
    }

    public enum Click {
        LEFT,
        RIGHT,
        BOTH
    }

    public enum HorizontalAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum VerticalAlignment {
        BOTTOM,
        CENTER,
        TOP
    }

    public enum ButtonSizeMode {
        FIXED,
        CONTENT
    }

    public enum TransitionType {
        NONE,
        SCALE,
        SLIDE_UP,
        SLIDE_DOWN
    }

    public enum ImageKind {
        ITEM,
        BLOCK
    }

    public record Position(float x, float y, float z) {
        public static Position origin() {
            return new Position(0.0f, 0.0f, 0.0f);
        }
    }

    public record Size(float width, float height) {
        public Size {
            if (width <= 0.0f || height <= 0.0f) {
                throw new IllegalArgumentException("size must be positive");
            }
        }
    }

    public record ButtonSizing(ButtonSizeMode width, ButtonSizeMode height) {
        public ButtonSizing {
            width = width == null ? ButtonSizeMode.FIXED : width;
            height = height == null ? ButtonSizeMode.FIXED : height;
        }

        public static ButtonSizing fixed() {
            return new ButtonSizing(ButtonSizeMode.FIXED, ButtonSizeMode.FIXED);
        }
    }

    public record ButtonPadding(float horizontal, float vertical) {
        public ButtonPadding {
            if (horizontal < 0.0f || vertical < 0.0f) {
                throw new IllegalArgumentException("button padding must be >= 0");
            }
        }

        public static ButtonPadding zero() {
            return new ButtonPadding(0.0f, 0.0f);
        }
    }

    public record Offset(float forward, float horizontal, float vertical) {
        public static Offset defaults() {
            return new Offset(2.0f, 0.0f, 0.5f);
        }
    }

    public record Transition(int duration, TransitionType enter, TransitionType exit) {
        public Transition {
            duration = Math.max(0, duration);
            enter = enter == null ? TransitionType.NONE : enter;
            exit = exit == null ? TransitionType.NONE : exit;
        }

        public static Transition none() {
            return new Transition(0, TransitionType.NONE, TransitionType.NONE);
        }
    }

    public sealed interface ComponentSpec permits TextSpec, ButtonSpec, TextInputSpec, PanelSpec, ImageSpec {
        String id();

        Position position();

        Size size();

        boolean visible();

        float opacity();
    }

    public record TextSpec(
            String id,
            Position position,
            Size size,
            boolean visible,
            float opacity,
            String content,
            float fontSize,
            String color,
            String alignment,
            int lineWidth,
            boolean shadow,
            String background,
            int refreshInterval
    ) implements ComponentSpec {
        public TextSpec {
            requireComponentId(id);
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(size, "size");
            content = content == null ? "" : content;
            fontSize = positive(fontSize, 0.5f);
            alignment = alignment == null ? "center" : alignment;
            lineWidth = Math.max(1, lineWidth);
            background = background == null ? "#00000000" : background;
            refreshInterval = Math.max(0, refreshInterval);
            opacity = clampOpacity(opacity);
        }
    }

    public record ButtonSpec(
            String id,
            Position position,
            Size size,
            boolean visible,
            float opacity,
            String label,
            float fontSize,
            String backgroundColor,
            String hoverColor,
            String clickSound,
            Click click,
            ButtonAction action,
            float hoverScale,
            ButtonPadding padding,
            HorizontalAlignment horizontalAlignment,
            VerticalAlignment verticalAlignment,
            ButtonSizing sizing
    ) implements ComponentSpec {
        public ButtonSpec(
                String id,
                Position position,
                Size size,
                boolean visible,
                float opacity,
                String label,
                float fontSize,
                String backgroundColor,
                String hoverColor,
                String clickSound,
                Click click,
                ButtonAction action,
                float hoverScale
        ) {
            this(
                    id, position, size, visible, opacity, label, fontSize, backgroundColor, hoverColor, clickSound,
                    click, action, hoverScale, ButtonPadding.zero(), HorizontalAlignment.CENTER, VerticalAlignment.CENTER,
                    ButtonSizing.fixed()
            );
        }

        public ButtonSpec(
                String id,
                Position position,
                Size size,
                boolean visible,
                float opacity,
                String label,
                float fontSize,
                String backgroundColor,
                String hoverColor,
                String clickSound,
                Click click,
                ButtonAction action,
                float hoverScale,
                ButtonPadding padding,
                HorizontalAlignment horizontalAlignment,
                VerticalAlignment verticalAlignment
        ) {
            this(
                    id, position, size, visible, opacity, label, fontSize, backgroundColor, hoverColor, clickSound,
                    click, action, hoverScale, padding, horizontalAlignment, verticalAlignment, ButtonSizing.fixed()
            );
        }

        public ButtonSpec {
            requireComponentId(id);
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(size, "size");
            label = label == null ? id : label;
            fontSize = positive(fontSize, 0.4f);
            backgroundColor = backgroundColor == null ? "#CC222222" : backgroundColor;
            hoverColor = hoverColor == null ? "#EE444444" : hoverColor;
            click = click == null ? Click.RIGHT : click;
            Objects.requireNonNull(action, "action");
            hoverScale = positive(hoverScale, 1.0f);
            padding = padding == null ? ButtonPadding.zero() : padding;
            horizontalAlignment = horizontalAlignment == null ? HorizontalAlignment.CENTER : horizontalAlignment;
            verticalAlignment = verticalAlignment == null ? VerticalAlignment.CENTER : verticalAlignment;
            sizing = sizing == null ? ButtonSizing.fixed() : sizing;
            if (sizing.width() == ButtonSizeMode.FIXED && size.width() <= padding.horizontal() * 2.0f) {
                throw new IllegalArgumentException("button horizontal padding must leave positive content width");
            }
            if (sizing.height() == ButtonSizeMode.FIXED && size.height() <= padding.vertical() * 2.0f) {
                throw new IllegalArgumentException("button vertical padding must leave positive content height");
            }
            opacity = clampOpacity(opacity);
        }
    }

    public record TextInputSpec(
            String id,
            Position position,
            Size size,
            boolean visible,
            float opacity,
            String initialValue,
            String placeholder,
            int maxLength,
            float fontSize,
            String color,
            String backgroundColor,
            String hoverColor,
            String clickSound,
            Click click,
            String dialogTitle,
            String dialogLabel,
            String confirmLabel,
            String cancelLabel
    ) implements ComponentSpec {
        public TextInputSpec {
            requireComponentId(id);
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(size, "size");
            initialValue = initialValue == null ? "" : initialValue;
            placeholder = placeholder == null ? "" : placeholder;
            maxLength = Math.max(1, maxLength);
            fontSize = positive(fontSize, 0.4f);
            color = color == null ? "#FFFFFF" : color;
            backgroundColor = backgroundColor == null ? "#CC222222" : backgroundColor;
            hoverColor = hoverColor == null ? "#EE444444" : hoverColor;
            click = click == null ? Click.RIGHT : click;
            dialogTitle = dialogTitle == null || dialogTitle.isBlank() ? "Text Input" : dialogTitle;
            dialogLabel = dialogLabel == null || dialogLabel.isBlank()
                    ? (placeholder.isBlank() ? "Value" : placeholder)
                    : dialogLabel;
            confirmLabel = confirmLabel == null || confirmLabel.isBlank() ? "Done" : confirmLabel;
            cancelLabel = cancelLabel == null || cancelLabel.isBlank() ? "Cancel" : cancelLabel;
            opacity = clampOpacity(opacity);
            if (initialValue.length() > maxLength) {
                initialValue = initialValue.substring(0, maxLength);
            }
        }
    }

    public record PanelSpec(
            String id,
            Position position,
            Size size,
            boolean visible,
            float opacity,
            String backgroundColor,
            float padding,
            Layout layout
    ) implements ComponentSpec {
        public PanelSpec {
            requireComponentId(id);
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(size, "size");
            backgroundColor = backgroundColor == null ? "#88000000" : backgroundColor;
            padding = Math.max(0.0f, padding);
            layout = layout == null ? Layout.ABSOLUTE : layout;
            opacity = clampOpacity(opacity);
        }
    }

    public record ImageSpec(
            String id,
            Position position,
            Size size,
            boolean visible,
            float opacity,
            ImageKind kind,
            ResourceLocation value,
            float scale
    ) implements ComponentSpec {
        public ImageSpec {
            requireComponentId(id);
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(value, "value");
            scale = positive(scale, 1.0f);
            opacity = clampOpacity(opacity);
        }
    }

    public sealed interface ButtonAction permits CloseAction, OpenAction, CallbackAction, RunCommandAction {
    }

    public record CloseAction() implements ButtonAction {
    }

    public record OpenAction(ResourceLocation target) implements ButtonAction {
        public OpenAction {
            Objects.requireNonNull(target, "target");
        }
    }

    public record CallbackAction(ResourceLocation callbackId) implements ButtonAction {
        public CallbackAction {
            Objects.requireNonNull(callbackId, "callbackId");
        }
    }

    public record RunCommandAction(String command, Integer permissionLevel) implements ButtonAction {
        public RunCommandAction {
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("command must not be blank");
            }
        }
    }

    public static final class Actions {
        private Actions() {
        }

        public static ButtonAction close() {
            return new CloseAction();
        }

        public static ButtonAction open(ResourceLocation target) {
            return new OpenAction(target);
        }

        public static ButtonAction callback(ResourceLocation callbackId) {
            return new CallbackAction(callbackId);
        }

        public static ButtonAction runCommand(String command) {
            return new RunCommandAction(command, null);
        }

        public static ButtonAction runCommand(String command, int permissionLevel) {
            return new RunCommandAction(command, permissionLevel);
        }
    }

    public static final class Builder {
        private final ResourceLocation id;
        private Size size = new Size(3.0f, 2.0f);
        private Offset offset = Offset.defaults();
        private Layout layout = Layout.ABSOLUTE;
        private final List<ComponentSpec> components = new ArrayList<>();
        private Transition transition = Transition.none();

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder size(float width, float height) {
            this.size = new Size(width, height);
            return this;
        }

        public Builder offset(float forward, float horizontal, float vertical) {
            this.offset = new Offset(forward, horizontal, vertical);
            return this;
        }

        public Builder layout(Layout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            return this;
        }

        public Builder transition(int duration, TransitionType enter, TransitionType exit) {
            this.transition = new Transition(duration, enter, exit);
            return this;
        }

        public Builder text(String id, Consumer<TextBuilder> consumer) {
            TextBuilder builder = new TextBuilder(id);
            consumer.accept(builder);
            this.components.add(builder.build());
            return this;
        }

        public Builder button(String id, Consumer<ButtonBuilder> consumer) {
            ButtonBuilder builder = new ButtonBuilder(id);
            consumer.accept(builder);
            this.components.add(builder.build());
            return this;
        }

        public Builder textInput(String id, Consumer<TextInputBuilder> consumer) {
            TextInputBuilder builder = new TextInputBuilder(id);
            consumer.accept(builder);
            this.components.add(builder.build());
            return this;
        }

        public Builder panel(String id, Consumer<PanelBuilder> consumer) {
            PanelBuilder builder = new PanelBuilder(id);
            consumer.accept(builder);
            this.components.add(builder.build());
            return this;
        }

        public Builder item(String id, ResourceLocation itemId, Consumer<ImageBuilder> consumer) {
            ImageBuilder builder = new ImageBuilder(id, ImageKind.ITEM, itemId);
            consumer.accept(builder);
            this.components.add(builder.build());
            return this;
        }

        public Builder block(String id, ResourceLocation blockId, Consumer<ImageBuilder> consumer) {
            ImageBuilder builder = new ImageBuilder(id, ImageKind.BLOCK, blockId);
            consumer.accept(builder);
            this.components.add(builder.build());
            return this;
        }

        public WindowSpec build() {
            return new WindowSpec(this);
        }
    }

    public static final class TextBuilder {
        private final String id;
        private Position position = Position.origin();
        private Size size = new Size(1.0f, 0.3f);
        private boolean visible = true;
        private float opacity = 1.0f;
        private String content = "";
        private float fontSize = 0.5f;
        private String color;
        private String alignment = "center";
        private int lineWidth = 200;
        private boolean shadow;
        private String background = "#00000000";
        private int refreshInterval;

        private TextBuilder(String id) {
            this.id = id;
        }

        public TextBuilder position(float x, float y, float z) {
            this.position = new Position(x, y, z);
            return this;
        }

        public TextBuilder size(float width, float height) {
            this.size = new Size(width, height);
            return this;
        }

        public TextBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public TextBuilder opacity(float opacity) {
            this.opacity = opacity;
            return this;
        }

        public TextBuilder content(String content) {
            this.content = content;
            return this;
        }

        public TextBuilder fontSize(float fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public TextBuilder color(String color) {
            this.color = color;
            return this;
        }

        public TextBuilder alignment(String alignment) {
            this.alignment = alignment;
            return this;
        }

        public TextBuilder lineWidth(int lineWidth) {
            this.lineWidth = lineWidth;
            return this;
        }

        public TextBuilder shadow(boolean shadow) {
            this.shadow = shadow;
            return this;
        }

        public TextBuilder background(String background) {
            this.background = background;
            return this;
        }

        public TextBuilder refreshInterval(int ticks) {
            this.refreshInterval = ticks;
            return this;
        }

        private TextSpec build() {
            return new TextSpec(this.id, this.position, this.size, this.visible, this.opacity, this.content, this.fontSize,
                    this.color, this.alignment, this.lineWidth, this.shadow, this.background, this.refreshInterval);
        }
    }

    public static final class ButtonBuilder {
        private final String id;
        private Position position = Position.origin();
        private Size size = new Size(1.0f, 0.35f);
        private boolean visible = true;
        private float opacity = 1.0f;
        private String label;
        private float fontSize = 0.4f;
        private String backgroundColor = "#CC222222";
        private String hoverColor = "#EE444444";
        private String clickSound = "minecraft:ui.button.click";
        private Click click = Click.RIGHT;
        private ButtonAction action;
        private float hoverScale = 1.0f;
        private ButtonPadding padding = ButtonPadding.zero();
        private HorizontalAlignment horizontalAlignment = HorizontalAlignment.CENTER;
        private VerticalAlignment verticalAlignment = VerticalAlignment.CENTER;
        private ButtonSizing sizing = ButtonSizing.fixed();

        private ButtonBuilder(String id) {
            this.id = id;
        }

        public ButtonBuilder position(float x, float y, float z) {
            this.position = new Position(x, y, z);
            return this;
        }

        public ButtonBuilder size(float width, float height) {
            this.size = new Size(width, height);
            return this;
        }

        public ButtonBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public ButtonBuilder opacity(float opacity) {
            this.opacity = opacity;
            return this;
        }

        public ButtonBuilder label(String label) {
            this.label = label;
            return this;
        }

        public ButtonBuilder fontSize(float fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public ButtonBuilder background(String normal, String hover) {
            this.backgroundColor = normal;
            this.hoverColor = hover;
            return this;
        }

        public ButtonBuilder clickSound(String clickSound) {
            this.clickSound = clickSound;
            return this;
        }

        public ButtonBuilder click(Click click) {
            this.click = click;
            return this;
        }

        public ButtonBuilder action(ButtonAction action) {
            this.action = action;
            return this;
        }

        public ButtonBuilder hoverScale(float hoverScale) {
            this.hoverScale = hoverScale;
            return this;
        }

        public ButtonBuilder padding(float horizontal, float vertical) {
            this.padding = new ButtonPadding(horizontal, vertical);
            return this;
        }

        public ButtonBuilder alignment(HorizontalAlignment horizontal, VerticalAlignment vertical) {
            this.horizontalAlignment = Objects.requireNonNull(horizontal, "horizontal");
            this.verticalAlignment = Objects.requireNonNull(vertical, "vertical");
            return this;
        }

        public ButtonBuilder sizing(ButtonSizeMode width, ButtonSizeMode height) {
            this.sizing = new ButtonSizing(width, height);
            return this;
        }

        private ButtonSpec build() {
            if (this.action == null) {
                throw new IllegalStateException("button action is required: " + this.id);
            }
            return new ButtonSpec(this.id, this.position, this.size, this.visible, this.opacity, this.label, this.fontSize,
                    this.backgroundColor, this.hoverColor, this.clickSound, this.click, this.action, this.hoverScale,
                    this.padding, this.horizontalAlignment, this.verticalAlignment, this.sizing);
        }
    }

    public static final class TextInputBuilder {
        private final String id;
        private Position position = Position.origin();
        private Size size = new Size(1.0f, 0.35f);
        private boolean visible = true;
        private float opacity = 1.0f;
        private String initialValue = "";
        private String placeholder = "";
        private int maxLength = 64;
        private float fontSize = 0.4f;
        private String color = "#FFFFFF";
        private String backgroundColor = "#CC222222";
        private String hoverColor = "#EE444444";
        private String clickSound = "minecraft:ui.button.click";
        private Click click = Click.RIGHT;
        private String dialogTitle = "Text Input";
        private String dialogLabel;
        private String confirmLabel = "Done";
        private String cancelLabel = "Cancel";

        private TextInputBuilder(String id) {
            this.id = id;
        }

        public TextInputBuilder position(float x, float y, float z) {
            this.position = new Position(x, y, z);
            return this;
        }

        public TextInputBuilder size(float width, float height) {
            this.size = new Size(width, height);
            return this;
        }

        public TextInputBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public TextInputBuilder opacity(float opacity) {
            this.opacity = opacity;
            return this;
        }

        public TextInputBuilder initialValue(String initialValue) {
            this.initialValue = initialValue;
            return this;
        }

        public TextInputBuilder placeholder(String placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        public TextInputBuilder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public TextInputBuilder fontSize(float fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public TextInputBuilder color(String color) {
            this.color = color;
            return this;
        }

        public TextInputBuilder background(String normal, String hover) {
            this.backgroundColor = normal;
            this.hoverColor = hover;
            return this;
        }

        public TextInputBuilder clickSound(String clickSound) {
            this.clickSound = clickSound;
            return this;
        }

        public TextInputBuilder click(Click click) {
            this.click = click;
            return this;
        }

        public TextInputBuilder dialog(String title, String label) {
            this.dialogTitle = title;
            this.dialogLabel = label;
            return this;
        }

        public TextInputBuilder buttons(String confirmLabel, String cancelLabel) {
            this.confirmLabel = confirmLabel;
            this.cancelLabel = cancelLabel;
            return this;
        }

        private TextInputSpec build() {
            return new TextInputSpec(
                    this.id, this.position, this.size, this.visible, this.opacity,
                    this.initialValue, this.placeholder, this.maxLength, this.fontSize, this.color,
                    this.backgroundColor, this.hoverColor, this.clickSound, this.click,
                    this.dialogTitle, this.dialogLabel, this.confirmLabel, this.cancelLabel
            );
        }
    }

    public static final class PanelBuilder {
        private final String id;
        private Position position = Position.origin();
        private Size size = new Size(1.0f, 1.0f);
        private boolean visible = true;
        private float opacity = 1.0f;
        private String backgroundColor = "#88000000";
        private float padding;
        private Layout layout = Layout.ABSOLUTE;

        private PanelBuilder(String id) {
            this.id = id;
        }

        public PanelBuilder position(float x, float y, float z) {
            this.position = new Position(x, y, z);
            return this;
        }

        public PanelBuilder size(float width, float height) {
            this.size = new Size(width, height);
            return this;
        }

        public PanelBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public PanelBuilder opacity(float opacity) {
            this.opacity = opacity;
            return this;
        }

        public PanelBuilder background(String backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public PanelBuilder padding(float padding) {
            this.padding = padding;
            return this;
        }

        public PanelBuilder layout(Layout layout) {
            this.layout = layout;
            return this;
        }

        private PanelSpec build() {
            return new PanelSpec(this.id, this.position, this.size, this.visible, this.opacity,
                    this.backgroundColor, this.padding, this.layout);
        }
    }

    public static final class ImageBuilder {
        private final String id;
        private final ImageKind kind;
        private final ResourceLocation value;
        private Position position = Position.origin();
        private Size size = new Size(1.0f, 1.0f);
        private boolean visible = true;
        private float opacity = 1.0f;
        private float scale = 1.0f;

        private ImageBuilder(String id, ImageKind kind, ResourceLocation value) {
            this.id = id;
            this.kind = Objects.requireNonNull(kind, "kind");
            this.value = Objects.requireNonNull(value, "value");
        }

        public ImageBuilder position(float x, float y, float z) {
            this.position = new Position(x, y, z);
            return this;
        }

        public ImageBuilder size(float width, float height) {
            this.size = new Size(width, height);
            return this;
        }

        public ImageBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public ImageBuilder opacity(float opacity) {
            this.opacity = opacity;
            return this;
        }

        public ImageBuilder scale(float scale) {
            this.scale = scale;
            return this;
        }

        private ImageSpec build() {
            return new ImageSpec(this.id, this.position, this.size, this.visible, this.opacity, this.kind, this.value, this.scale);
        }
    }

    private static void requireComponentId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("component id must not be blank");
        }
    }

    private static float positive(float value, float fallback) {
        return value > 0.0f ? value : fallback;
    }

    private static float clampOpacity(float opacity) {
        return Math.max(0.0f, Math.min(1.0f, opacity));
    }
}

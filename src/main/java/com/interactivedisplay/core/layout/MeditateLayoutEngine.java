package com.interactivedisplay.core.layout;

import com.interactivedisplay.core.component.ButtonBoxModel;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ButtonSizeMode;
import com.interactivedisplay.core.component.ButtonSizing;
import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.ComponentMargin;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.ComponentSizeMode;
import com.interactivedisplay.core.component.ImageComponentDefinition;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.component.TextInputComponentDefinition;
import com.interactivedisplay.core.window.WindowDefinition;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

public final class MeditateLayoutEngine implements LayoutEngine {
    private static final float PANEL_CHILD_Z_OFFSET = 0.01f;
    private static final float MIN_LAYOUT_SIZE = 0.0001f;
    private static final float BOUNDS_EPSILON = 0.0001f;

    @Override
    public List<LayoutComponent> calculate(WindowDefinition definition) {
        MeasuredSize measuredContent = measureContainer(definition.components(), definition.layoutOptions());
        ComponentSize configuredWindowSize = definition.size();
        float windowWidth = resolveContainerAxis(
                configuredWindowSize.widthMode(),
                configuredWindowSize.width(),
                measuredContent.width(),
                configuredWindowSize.minWidth(),
                configuredWindowSize.maxWidth()
        );
        float windowHeight = resolveContainerAxis(
                configuredWindowSize.heightMode(),
                configuredWindowSize.height(),
                measuredContent.height(),
                configuredWindowSize.minHeight(),
                configuredWindowSize.maxHeight()
        );

        List<LayoutComponent> layout = new ArrayList<>();
        LayoutBounds windowBounds = LayoutBounds.centered(windowWidth, windowHeight);
        layoutComponents(
                definition.components(),
                definition.layoutOptions(),
                new Vector3f(),
                windowBounds,
                layout
        );
        return layout;
    }

    private static void layoutComponents(List<ComponentDefinition> components,
                                         LayoutOptions layoutOptions,
                                         Vector3f legacyOrigin,
                                         LayoutBounds parentBounds,
                                         List<LayoutComponent> out) {
        LayoutMode layoutMode = layoutOptions.mode();
        List<ComponentDefinition> resolvedComponents = new ArrayList<>(components.size());
        for (ComponentDefinition component : components) {
            resolvedComponents.add(resolveComponent(component, parentBounds));
        }

        float[] columnWidths = layoutMode == LayoutMode.GRID
                ? columnWidths(resolvedComponents, layoutOptions.columns())
                : null;
        float[] rowHeights = layoutMode == LayoutMode.GRID
                ? rowHeights(resolvedComponents, layoutOptions.columns())
                : null;

        float cursor = 0.0f;
        for (int index = 0; index < components.size(); index++) {
            ComponentDefinition original = components.get(index);
            ComponentDefinition resolved = resolvedComponents.get(index);

            Vector3f legacyPosition = switch (layoutMode) {
                case VERTICAL -> new Vector3f(
                        legacyOrigin.x + original.position().x(),
                        legacyOrigin.y + cursor + original.position().y(),
                        legacyOrigin.z + original.position().z()
                );
                case HORIZONTAL -> new Vector3f(
                        legacyOrigin.x + cursor + original.position().x(),
                        legacyOrigin.y + original.position().y(),
                        legacyOrigin.z + original.position().z()
                );
                case ABSOLUTE -> new Vector3f(
                        legacyOrigin.x + original.position().x(),
                        legacyOrigin.y + original.position().y(),
                        legacyOrigin.z + original.position().z()
                );
                case GRID -> gridPosition(
                        original,
                        resolved,
                        index,
                        layoutOptions,
                        columnWidths,
                        rowHeights,
                        legacyOrigin
                );
            };

            Vector3f position = applyParentPlacement(original, resolved, legacyPosition, parentBounds);
            validateOverflow(layoutOptions, resolved, position, parentBounds);
            out.add(new LayoutComponent(resolved, position));

            if (resolved instanceof PanelComponentDefinition panel) {
                LayoutBounds panelBounds = new LayoutBounds(
                        position.x,
                        position.y,
                        panel.size().width(),
                        panel.size().height()
                );
                LayoutBounds contentBounds = panelBounds.inset(panel.padding());
                Vector3f childLegacyOrigin = new Vector3f(
                        position.x + panel.padding(),
                        position.y + panel.padding(),
                        position.z + PANEL_CHILD_Z_OFFSET
                );
                layoutComponents(
                        panel.children(),
                        panel.layoutOptions(),
                        childLegacyOrigin,
                        contentBounds,
                        out
                );
            }

            if (layoutMode == LayoutMode.VERTICAL) {
                cursor += resolvedHeight(resolved) + layoutOptions.gap();
            } else if (layoutMode == LayoutMode.HORIZONTAL) {
                cursor += resolvedWidth(resolved) + layoutOptions.gap();
            }
        }
    }

    private static MeasuredSize measureContainer(List<ComponentDefinition> components, LayoutOptions layoutOptions) {
        if (components.isEmpty()) {
            return new MeasuredSize(MIN_LAYOUT_SIZE, MIN_LAYOUT_SIZE);
        }

        List<MeasuredSize> measured = new ArrayList<>(components.size());
        for (ComponentDefinition component : components) {
            measured.add(measureComponent(component));
        }

        return switch (layoutOptions.mode()) {
            case VERTICAL -> measureVertical(components, measured, layoutOptions.gap());
            case HORIZONTAL -> measureHorizontal(components, measured, layoutOptions.gap());
            case GRID -> measureGrid(components, measured, layoutOptions);
            case ABSOLUTE -> measureAbsolute(components, measured);
        };
    }

    private static MeasuredSize measureComponent(ComponentDefinition component) {
        if (component instanceof PanelComponentDefinition panel) {
            MeasuredSize content = measureContainer(panel.children(), panel.layoutOptions());
            float desiredWidth = content.width() + panel.padding() * 2.0f;
            float desiredHeight = content.height() + panel.padding() * 2.0f;
            return new MeasuredSize(
                    resolveIntrinsicAxis(panel.size().widthMode(), panel.size().width(), desiredWidth,
                            panel.size().minWidth(), panel.size().maxWidth()),
                    resolveIntrinsicAxis(panel.size().heightMode(), panel.size().height(), desiredHeight,
                            panel.size().minHeight(), panel.size().maxHeight())
            );
        }

        if (component instanceof ButtonComponentDefinition button) {
            return measureButton(button);
        }

        ComponentSize configured = component.size();
        return new MeasuredSize(
                resolveIntrinsicAxis(configured.widthMode(), configured.width(), configured.width(),
                        configured.minWidth(), configured.maxWidth()),
                resolveIntrinsicAxis(configured.heightMode(), configured.height(), configured.height(),
                        configured.minHeight(), configured.maxHeight())
        );
    }

    private static MeasuredSize measureButton(ButtonComponentDefinition button) {
        ComponentSize configured = button.size();
        ButtonSizeMode widthSizing = switch (configured.widthMode()) {
            case AUTO -> ButtonSizeMode.CONTENT;
            case FILL -> ButtonSizeMode.FIXED;
            case FIXED -> button.sizing().width();
        };
        ButtonSizeMode heightSizing = switch (configured.heightMode()) {
            case AUTO -> ButtonSizeMode.CONTENT;
            case FILL -> ButtonSizeMode.FIXED;
            case FIXED -> button.sizing().height();
        };
        ButtonComponentDefinition intrinsic = copyButton(
                button,
                configured.resolved(configured.width(), configured.height()),
                new ButtonSizing(widthSizing, heightSizing)
        );
        var box = ButtonBoxModel.resolve(intrinsic);
        return new MeasuredSize(
                clamp(box.width(), configured.minWidth(), configured.maxWidth()),
                clamp(box.height(), configured.minHeight(), configured.maxHeight())
        );
    }

    private static MeasuredSize measureVertical(List<ComponentDefinition> components,
                                                List<MeasuredSize> measured,
                                                float gap) {
        float width = MIN_LAYOUT_SIZE;
        float height = 0.0f;
        for (int index = 0; index < components.size(); index++) {
            ComponentMargin margin = components.get(index).position().margin();
            MeasuredSize child = measured.get(index);
            width = Math.max(width, child.width() + margin.horizontal());
            height += child.height() + margin.vertical();
            if (index > 0) {
                height += gap;
            }
        }
        return new MeasuredSize(width, Math.max(MIN_LAYOUT_SIZE, height));
    }

    private static MeasuredSize measureHorizontal(List<ComponentDefinition> components,
                                                  List<MeasuredSize> measured,
                                                  float gap) {
        float width = 0.0f;
        float height = MIN_LAYOUT_SIZE;
        for (int index = 0; index < components.size(); index++) {
            ComponentMargin margin = components.get(index).position().margin();
            MeasuredSize child = measured.get(index);
            width += child.width() + margin.horizontal();
            height = Math.max(height, child.height() + margin.vertical());
            if (index > 0) {
                width += gap;
            }
        }
        return new MeasuredSize(Math.max(MIN_LAYOUT_SIZE, width), height);
    }

    private static MeasuredSize measureGrid(List<ComponentDefinition> components,
                                            List<MeasuredSize> measured,
                                            LayoutOptions options) {
        int columns = options.columns();
        int rows = (int) ((components.size() + (long) columns - 1L) / columns);
        float[] widths = new float[Math.min(columns, components.size())];
        float[] heights = new float[rows];
        for (int index = 0; index < components.size(); index++) {
            ComponentMargin margin = components.get(index).position().margin();
            MeasuredSize child = measured.get(index);
            int column = index % columns;
            int row = index / columns;
            widths[column] = Math.max(widths[column], child.width() + margin.horizontal());
            heights[row] = Math.max(heights[row], child.height() + margin.vertical());
        }

        float width = sum(widths) + Math.max(0, widths.length - 1) * options.columnGap();
        float height = sum(heights) + Math.max(0, heights.length - 1) * options.rowGap();
        return new MeasuredSize(Math.max(MIN_LAYOUT_SIZE, width), Math.max(MIN_LAYOUT_SIZE, height));
    }

    private static MeasuredSize measureAbsolute(List<ComponentDefinition> components,
                                                List<MeasuredSize> measured) {
        float width = MIN_LAYOUT_SIZE;
        float height = MIN_LAYOUT_SIZE;
        for (int index = 0; index < components.size(); index++) {
            ComponentDefinition component = components.get(index);
            MeasuredSize child = measured.get(index);
            ComponentMargin margin = component.position().margin();
            if (component.position().anchored()) {
                width = Math.max(width, child.width() + margin.horizontal());
                height = Math.max(height, child.height() + margin.vertical());
                continue;
            }

            float halfWidthExtent = Math.abs(component.position().x())
                    + child.width() / 2.0f
                    + margin.horizontal() / 2.0f;
            float lower = component.position().y() - margin.bottom();
            float upper = component.position().y() + child.height() + margin.top();
            float halfHeightExtent = Math.max(Math.abs(lower), Math.abs(upper));
            width = Math.max(width, halfWidthExtent * 2.0f);
            height = Math.max(height, halfHeightExtent * 2.0f);
        }
        return new MeasuredSize(width, height);
    }

    private static ComponentDefinition resolveComponent(ComponentDefinition component, LayoutBounds parentBounds) {
        ComponentMargin margin = component.position().margin();
        float availableWidth = Math.max(MIN_LAYOUT_SIZE, parentBounds.width() - margin.horizontal());
        float availableHeight = Math.max(MIN_LAYOUT_SIZE, parentBounds.height() - margin.vertical());
        ComponentSize configured = component.size();

        if (component instanceof ButtonComponentDefinition button) {
            return resolveButton(button, availableWidth, availableHeight);
        }

        if (component instanceof PanelComponentDefinition panel) {
            MeasuredSize measuredContent = measureContainer(panel.children(), panel.layoutOptions());
            float autoWidth = measuredContent.width() + panel.padding() * 2.0f;
            float autoHeight = measuredContent.height() + panel.padding() * 2.0f;
            float width = resolveArrangedAxis(configured.widthMode(), configured.width(), autoWidth, availableWidth,
                    configured.minWidth(), configured.maxWidth());
            float height = resolveArrangedAxis(configured.heightMode(), configured.height(), autoHeight, availableHeight,
                    configured.minHeight(), configured.maxHeight());
            return withSize(panel, configured.resolved(width, height));
        }

        float width = resolveArrangedAxis(configured.widthMode(), configured.width(), configured.width(), availableWidth,
                configured.minWidth(), configured.maxWidth());
        float height = resolveArrangedAxis(configured.heightMode(), configured.height(), configured.height(), availableHeight,
                configured.minHeight(), configured.maxHeight());
        return withSize(component, configured.resolved(width, height));
    }

    private static ButtonComponentDefinition resolveButton(ButtonComponentDefinition button,
                                                           float availableWidth,
                                                           float availableHeight) {
        ComponentSize configured = button.size();

        float width;
        if (configured.widthMode() == ComponentSizeMode.FILL) {
            width = clamp(availableWidth, configured.minWidth(), configured.maxWidth());
        } else {
            ButtonSizeMode widthSizing = configured.widthMode() == ComponentSizeMode.AUTO
                    ? ButtonSizeMode.CONTENT
                    : button.sizing().width();
            ButtonComponentDefinition widthMeasure = copyButton(
                    button,
                    configured.resolved(configured.width(), configured.height()),
                    new ButtonSizing(widthSizing, button.sizing().height())
            );
            width = clamp(ButtonBoxModel.resolve(widthMeasure).width(), configured.minWidth(), configured.maxWidth());
        }

        ButtonSizeMode heightSizing = configured.heightMode() == ComponentSizeMode.AUTO
                ? ButtonSizeMode.CONTENT
                : button.sizing().height();
        ButtonComponentDefinition widthResolved = copyButton(
                button,
                configured.resolved(width, configured.height()),
                new ButtonSizing(ButtonSizeMode.FIXED, heightSizing)
        );

        float height = configured.heightMode() == ComponentSizeMode.FILL
                ? clamp(availableHeight, configured.minHeight(), configured.maxHeight())
                : clamp(ButtonBoxModel.resolve(widthResolved).height(), configured.minHeight(), configured.maxHeight());

        return copyButton(button, configured.resolved(width, height), ButtonSizing.fixed());
    }

    private static float resolveContainerAxis(ComponentSizeMode mode,
                                              float configured,
                                              float measured,
                                              float minimum,
                                              float maximum) {
        return switch (mode) {
            case AUTO -> clamp(measured, minimum, maximum);
            case FIXED, FILL -> clamp(configured, minimum, maximum);
        };
    }

    private static float resolveIntrinsicAxis(ComponentSizeMode mode,
                                              float configured,
                                              float measured,
                                              float minimum,
                                              float maximum) {
        return switch (mode) {
            case AUTO -> clamp(measured, minimum, maximum);
            case FIXED, FILL -> clamp(configured, minimum, maximum);
        };
    }

    private static float resolveArrangedAxis(ComponentSizeMode mode,
                                             float configured,
                                             float measured,
                                             float available,
                                             float minimum,
                                             float maximum) {
        return switch (mode) {
            case AUTO -> clamp(measured, minimum, maximum);
            case FILL -> clamp(available, minimum, maximum);
            case FIXED -> clamp(configured, minimum, maximum);
        };
    }

    private static Vector3f applyParentPlacement(ComponentDefinition original,
                                                 ComponentDefinition resolved,
                                                 Vector3f legacyPosition,
                                                 LayoutBounds parentBounds) {
        ComponentPosition position = original.position();
        ComponentMargin margin = position.margin();
        float width = resolvedWidth(resolved);
        float height = resolvedHeight(resolved);

        if (position.anchored()) {
            float x = switch (position.anchor()) {
                case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT ->
                        parentBounds.left() + margin.left() + width / 2.0f;
                case TOP_CENTER, CENTER, BOTTOM_CENTER ->
                        parentBounds.centerX() + (margin.left() - margin.right()) / 2.0f;
                case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT ->
                        parentBounds.right() - margin.right() - width / 2.0f;
                case NONE -> legacyPosition.x;
            };
            float y = switch (position.anchor()) {
                case TOP_LEFT, TOP_CENTER, TOP_RIGHT ->
                        parentBounds.top() - margin.top() - height;
                case CENTER_LEFT, CENTER, CENTER_RIGHT ->
                        parentBounds.centerY() - height / 2.0f + (margin.bottom() - margin.top()) / 2.0f;
                case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT ->
                        parentBounds.bottom() + margin.bottom();
                case NONE -> legacyPosition.y;
            };
            return new Vector3f(
                    x + position.x(),
                    y + position.y(),
                    legacyPosition.z
            );
        }

        float x = configuredFillWidth(original)
                ? parentBounds.centerX() + (margin.left() - margin.right()) / 2.0f + position.x()
                : legacyPosition.x;
        float y = configuredFillHeight(original)
                ? parentBounds.bottom() + margin.bottom() + position.y()
                : legacyPosition.y;
        return new Vector3f(x, y, legacyPosition.z);
    }

    private static void validateOverflow(LayoutOptions options,
                                         ComponentDefinition component,
                                         Vector3f position,
                                         LayoutBounds parentBounds) {
        if (options.overflow() != OverflowPolicy.ERROR) {
            return;
        }
        LayoutBounds componentBounds = new LayoutBounds(
                position.x,
                position.y,
                resolvedWidth(component),
                resolvedHeight(component)
        );
        if (!parentBounds.contains(componentBounds, BOUNDS_EPSILON)) {
            throw new IllegalArgumentException(
                    "layout overflow: component " + component.id()
                            + " bounds=" + componentBounds
                            + " parent=" + parentBounds
            );
        }
    }

    private static boolean configuredFillWidth(ComponentDefinition component) {
        return component.size().widthMode() == ComponentSizeMode.FILL;
    }

    private static boolean configuredFillHeight(ComponentDefinition component) {
        return component.size().heightMode() == ComponentSizeMode.FILL;
    }

    private static Vector3f gridPosition(ComponentDefinition original,
                                         ComponentDefinition resolved,
                                         int index,
                                         LayoutOptions layoutOptions,
                                         float[] columnWidths,
                                         float[] rowHeights,
                                         Vector3f origin) {
        int row = index / layoutOptions.columns();
        int column = index % layoutOptions.columns();
        float x = layoutOptions.justifyItems().offset(columnWidths[column], resolvedWidth(resolved));
        for (int priorColumn = 0; priorColumn < column; priorColumn++) {
            x += columnWidths[priorColumn] + layoutOptions.columnGap();
        }
        float y = layoutOptions.alignItems().offset(rowHeights[row], resolvedHeight(resolved));
        for (int priorRow = 0; priorRow < row; priorRow++) {
            y += rowHeights[priorRow] + layoutOptions.rowGap();
        }
        return new Vector3f(
                origin.x + x + original.position().x(),
                origin.y + y + original.position().y(),
                origin.z + original.position().z()
        );
    }

    private static float[] columnWidths(List<ComponentDefinition> components, int columns) {
        float[] widths = new float[Math.min(columns, components.size())];
        for (int index = 0; index < components.size(); index++) {
            int column = index % columns;
            widths[column] = Math.max(widths[column], resolvedWidth(components.get(index)));
        }
        return widths;
    }

    private static float[] rowHeights(List<ComponentDefinition> components, int columns) {
        int rows = (int) ((components.size() + (long) columns - 1L) / columns);
        float[] heights = new float[rows];
        for (int index = 0; index < components.size(); index++) {
            int row = index / columns;
            heights[row] = Math.max(heights[row], resolvedHeight(components.get(index)));
        }
        return heights;
    }

    private static float sum(float[] values) {
        float total = 0.0f;
        for (float value : values) {
            total += value;
        }
        return total;
    }

    private static float resolvedWidth(ComponentDefinition component) {
        return component.size().width();
    }

    private static float resolvedHeight(ComponentDefinition component) {
        return component.size().height();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(MIN_LAYOUT_SIZE, Math.max(minimum, Math.min(maximum, value)));
    }

    private static ComponentDefinition withSize(ComponentDefinition component, ComponentSize size) {
        if (component instanceof TextComponentDefinition text) {
            return new TextComponentDefinition(
                    text.id(), text.position(), size, text.visible(), text.opacity(), text.content(), text.fontSize(),
                    text.color(), text.alignment(), text.lineWidth(), text.shadow(), text.background(),
                    text.refreshInterval(), text.animations()
            );
        }
        if (component instanceof ButtonComponentDefinition button) {
            return copyButton(button, size, button.sizing());
        }
        if (component instanceof TextInputComponentDefinition input) {
            return new TextInputComponentDefinition(
                    input.id(), input.position(), size, input.visible(), input.opacity(),
                    input.initialValue(), input.placeholder(), input.maxLength(), input.fontSize(), input.color(),
                    input.backgroundColor(), input.hoverColor(), input.clickSound(), input.clickType(),
                    input.dialogTitle(), input.dialogLabel(), input.confirmLabel(), input.cancelLabel()
            );
        }
        if (component instanceof PanelComponentDefinition panel) {
            return new PanelComponentDefinition(
                    panel.id(), panel.position(), size, panel.visible(), panel.opacity(), panel.backgroundColor(),
                    panel.padding(), panel.layoutOptions(), panel.children()
            );
        }
        if (component instanceof ImageComponentDefinition image) {
            return new ImageComponentDefinition(
                    image.id(), image.position(), size, image.visible(), image.opacity(),
                    image.imageType(), image.value(), image.scale(), image.source()
            );
        }
        throw new IllegalArgumentException("unsupported component type: " + component.getClass().getName());
    }

    private static ButtonComponentDefinition copyButton(ButtonComponentDefinition button,
                                                        ComponentSize size,
                                                        ButtonSizing sizing) {
        return new ButtonComponentDefinition(
                button.id(), button.position(), size, button.visible(), button.opacity(), button.label(),
                button.fontSize(), button.backgroundColor(), button.hoverColor(), button.clickSound(),
                button.clickType(), button.action(), button.hoverScale(), button.padding(),
                button.horizontalAlignment(), button.verticalAlignment(), sizing
        );
    }

    private record MeasuredSize(float width, float height) {
        private MeasuredSize {
            width = Math.max(MIN_LAYOUT_SIZE, width);
            height = Math.max(MIN_LAYOUT_SIZE, height);
        }
    }
}

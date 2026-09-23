package com.interactivedisplay.core.layout;

import com.interactivedisplay.core.component.ButtonBoxModel;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ButtonSizing;
import com.interactivedisplay.core.component.ComponentAnchor;
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

    @Override
    public List<LayoutComponent> calculate(WindowDefinition definition) {
        List<LayoutComponent> layout = new ArrayList<>();
        LayoutBounds windowBounds = LayoutBounds.centered(definition.size().width(), definition.size().height());
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
            ComponentDefinition placed = withPosition(resolved, new ComponentPosition(
                    position.x,
                    position.y,
                    position.z,
                    original.position().anchor(),
                    original.position().margin()
            ));
            out.add(new LayoutComponent(placed, position));

            if (placed instanceof PanelComponentDefinition panel) {
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

    private static ComponentDefinition resolveComponent(ComponentDefinition component, LayoutBounds parentBounds) {
        ComponentMargin margin = component.position().margin();
        float availableWidth = Math.max(MIN_LAYOUT_SIZE, parentBounds.width() - margin.horizontal());
        float availableHeight = Math.max(MIN_LAYOUT_SIZE, parentBounds.height() - margin.vertical());
        ComponentSize configured = component.size();

        if (component instanceof ButtonComponentDefinition button) {
            float width;
            if (configured.widthMode() == ComponentSizeMode.FILL) {
                width = configured.resolveWidth(availableWidth);
            } else {
                width = clamp(
                        ButtonBoxModel.resolve(button).width(),
                        configured.minWidth(),
                        configured.maxWidth()
                );
            }

            ButtonComponentDefinition widthResolved = copyButton(
                    button,
                    configured.resolved(width, configured.height()),
                    new ButtonSizing(
                            com.interactivedisplay.core.component.ButtonSizeMode.FIXED,
                            button.sizing().height()
                    )
            );

            float height;
            if (configured.heightMode() == ComponentSizeMode.FILL) {
                height = configured.resolveHeight(availableHeight);
            } else {
                height = clamp(
                        ButtonBoxModel.resolve(widthResolved).height(),
                        configured.minHeight(),
                        configured.maxHeight()
                );
            }

            return copyButton(
                    button,
                    configured.resolved(width, height),
                    ButtonSizing.fixed()
            );
        }

        float width = configured.resolveWidth(availableWidth);
        float height = configured.resolveHeight(availableHeight);
        return withSize(component, configured.resolved(width, height));
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

    private static ComponentDefinition withPosition(ComponentDefinition component, ComponentPosition position) {
        if (component instanceof TextComponentDefinition text) {
            return new TextComponentDefinition(
                    text.id(), position, text.size(), text.visible(), text.opacity(), text.content(), text.fontSize(),
                    text.color(), text.alignment(), text.lineWidth(), text.shadow(), text.background(),
                    text.refreshInterval(), text.animations()
            );
        }
        if (component instanceof ButtonComponentDefinition button) {
            return new ButtonComponentDefinition(
                    button.id(), position, button.size(), button.visible(), button.opacity(), button.label(),
                    button.fontSize(), button.backgroundColor(), button.hoverColor(), button.clickSound(),
                    button.clickType(), button.action(), button.hoverScale(), button.padding(),
                    button.horizontalAlignment(), button.verticalAlignment(), button.sizing()
            );
        }
        if (component instanceof TextInputComponentDefinition input) {
            return new TextInputComponentDefinition(
                    input.id(), position, input.size(), input.visible(), input.opacity(),
                    input.initialValue(), input.placeholder(), input.maxLength(), input.fontSize(), input.color(),
                    input.backgroundColor(), input.hoverColor(), input.clickSound(), input.clickType(),
                    input.dialogTitle(), input.dialogLabel(), input.confirmLabel(), input.cancelLabel()
            );
        }
        if (component instanceof PanelComponentDefinition panel) {
            return new PanelComponentDefinition(
                    panel.id(), position, panel.size(), panel.visible(), panel.opacity(), panel.backgroundColor(),
                    panel.padding(), panel.layoutOptions(), panel.children()
            );
        }
        if (component instanceof ImageComponentDefinition image) {
            return new ImageComponentDefinition(
                    image.id(), position, image.size(), image.visible(), image.opacity(),
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
}

package com.interactivedisplay.core.layout;

import com.interactivedisplay.core.component.ButtonBoxModel;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.window.WindowDefinition;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

public final class MeditateLayoutEngine implements LayoutEngine {
    private static final float PANEL_CHILD_Z_OFFSET = 0.01f;

    @Override
    public List<LayoutComponent> calculate(WindowDefinition definition) {
        List<LayoutComponent> layout = new ArrayList<>();
        layoutComponents(definition.components(), definition.layoutOptions(), new Vector3f(), layout);
        return layout;
    }

    private static void layoutComponents(List<ComponentDefinition> components,
                                         LayoutOptions layoutOptions,
                                         Vector3f origin,
                                         List<LayoutComponent> out) {
        LayoutMode layoutMode = layoutOptions.mode();
        float[] columnWidths = layoutMode == LayoutMode.GRID ? columnWidths(components, layoutOptions.columns()) : null;
        float[] rowHeights = layoutMode == LayoutMode.GRID ? rowHeights(components, layoutOptions.columns()) : null;
        float cursor = 0.0f;
        for (int index = 0; index < components.size(); index++) {
            ComponentDefinition component = components.get(index);
            Vector3f position = switch (layoutMode) {
                case VERTICAL -> new Vector3f(
                        origin.x + component.position().x(),
                        origin.y + cursor + component.position().y(),
                        origin.z + component.position().z()
                );
                case HORIZONTAL -> new Vector3f(
                        origin.x + cursor + component.position().x(),
                        origin.y + component.position().y(),
                        origin.z + component.position().z()
                );
                case ABSOLUTE -> new Vector3f(
                        origin.x + component.position().x(),
                        origin.y + component.position().y(),
                        origin.z + component.position().z()
                );
                case GRID -> gridPosition(
                        component,
                        index,
                        layoutOptions,
                        columnWidths,
                        rowHeights,
                        origin
                );
            };

            out.add(new LayoutComponent(component, position));

            if (component instanceof PanelComponentDefinition panel) {
                Vector3f childOrigin = new Vector3f(
                        position.x + panel.padding(),
                        position.y + panel.padding(),
                        position.z + PANEL_CHILD_Z_OFFSET
                );
                layoutComponents(panel.children(), panel.layoutOptions(), childOrigin, out);
            }

            if (layoutMode == LayoutMode.VERTICAL) {
                cursor += resolvedHeight(component) + layoutOptions.gap();
            } else if (layoutMode == LayoutMode.HORIZONTAL) {
                cursor += resolvedWidth(component) + layoutOptions.gap();
            }
        }
    }

    private static Vector3f gridPosition(ComponentDefinition component,
                                         int index,
                                         LayoutOptions layoutOptions,
                                         float[] columnWidths,
                                         float[] rowHeights,
                                         Vector3f origin) {
        int row = index / layoutOptions.columns();
        int column = index % layoutOptions.columns();
        float x = layoutOptions.justifyItems().offset(columnWidths[column], resolvedWidth(component));
        for (int priorColumn = 0; priorColumn < column; priorColumn++) {
            x += columnWidths[priorColumn] + layoutOptions.columnGap();
        }
        float y = layoutOptions.alignItems().offset(rowHeights[row], resolvedHeight(component));
        for (int priorRow = 0; priorRow < row; priorRow++) {
            y += rowHeights[priorRow] + layoutOptions.rowGap();
        }
        return new Vector3f(
                origin.x + x + component.position().x(),
                origin.y + y + component.position().y(),
                origin.z + component.position().z()
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
        if (component instanceof ButtonComponentDefinition button) {
            return ButtonBoxModel.resolve(button).width();
        }
        return component.size().width();
    }

    private static float resolvedHeight(ComponentDefinition component) {
        if (component instanceof ButtonComponentDefinition button) {
            return ButtonBoxModel.resolve(button).height();
        }
        return component.size().height();
    }

}

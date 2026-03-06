package com.interactivedisplay.core.layout;

import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.window.WindowDefinition;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

public final class MeditateLayoutEngine implements LayoutEngine {
    private static final float FLOW_GAP = 0.05f;
    private static final float PANEL_CHILD_Z_OFFSET = 0.01f;

    @Override
    public List<LayoutComponent> calculate(WindowDefinition definition) {
        List<LayoutComponent> layout = new ArrayList<>();
        layoutComponents(definition.components(), definition.layoutMode(), new Vector3f(), layout);
        return layout;
    }

    private static void layoutComponents(List<ComponentDefinition> components,
                                         LayoutMode layoutMode,
                                         Vector3f origin,
                                         List<LayoutComponent> out) {
        float cursor = 0.0f;
        for (ComponentDefinition component : components) {
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
            };

            out.add(new LayoutComponent(component, position));

            if (component instanceof PanelComponentDefinition panel) {
                Vector3f childOrigin = new Vector3f(
                        position.x + panel.padding(),
                        position.y + panel.padding(),
                        position.z + PANEL_CHILD_Z_OFFSET
                );
                layoutComponents(panel.children(), panel.layoutMode(), childOrigin, out);
            }

            if (layoutMode == LayoutMode.VERTICAL) {
                cursor += component.size().height() + FLOW_GAP;
            } else if (layoutMode == LayoutMode.HORIZONTAL) {
                cursor += component.size().width() + FLOW_GAP;
            }
        }
    }
}

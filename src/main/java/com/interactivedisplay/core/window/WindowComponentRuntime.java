package com.interactivedisplay.core.window;

import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentDefinition;
import eu.pb4.mapcanvas.api.core.PlayerCanvas;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.joml.Vector3f;

public final class WindowComponentRuntime {
    private static final double DEFAULT_MAX_DISTANCE = 6.0D;

    private final RegistryKey<World> worldKey;
    private final String signature;
    private ComponentDefinition definition;
    private Vector3f localPosition;
    private UUID displayEntityId;
    private PlayerCanvas mapCanvas;
    private boolean hovered;

    public WindowComponentRuntime(RegistryKey<World> worldKey,
                                  String signature,
                                  ComponentDefinition definition,
                                  Vector3f localPosition,
                                  UUID displayEntityId,
                                  PlayerCanvas mapCanvas) {
        this.worldKey = worldKey;
        this.signature = signature;
        this.definition = definition;
        this.localPosition = new Vector3f(localPosition);
        this.displayEntityId = displayEntityId;
        this.mapCanvas = mapCanvas;
    }

    public RegistryKey<World> worldKey() {
        return this.worldKey;
    }

    public String signature() {
        return this.signature;
    }

    public ComponentDefinition definition() {
        return this.definition;
    }

    public void redefine(ComponentDefinition definition, Vector3f localPosition) {
        this.definition = definition;
        this.localPosition = new Vector3f(localPosition);
        this.hovered = false;
    }

    public Vector3f localPosition() {
        return new Vector3f(this.localPosition);
    }

    public UUID displayEntityId() {
        return this.displayEntityId;
    }

    public PlayerCanvas mapCanvas() {
        return this.mapCanvas;
    }

    public void setMapCanvas(PlayerCanvas mapCanvas) {
        this.mapCanvas = mapCanvas;
    }

    public boolean hovered() {
        return this.hovered;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public boolean interactive() {
        return this.definition instanceof ButtonComponentDefinition;
    }

    public float hitHalfWidth() {
        if (this.definition instanceof ButtonComponentDefinition button) {
            return button.size().width() / 2.0f;
        }
        return 0.0f;
    }

    public float hitHalfHeight() {
        if (this.definition instanceof ButtonComponentDefinition button) {
            return button.size().height() / 2.0f;
        }
        return 0.0f;
    }

    public double maxDistance() {
        return DEFAULT_MAX_DISTANCE;
    }

    public ComponentAction action() {
        if (this.definition instanceof ButtonComponentDefinition button) {
            return button.action();
        }
        return null;
    }

    public List<UUID> entityIds() {
        List<UUID> ids = new ArrayList<>();
        if (this.displayEntityId != null) {
            ids.add(this.displayEntityId);
        }
        return ids;
    }
}

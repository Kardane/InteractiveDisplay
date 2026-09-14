package com.interactivedisplay.entity;

import eu.pb4.mapcanvas.mixin.ItemFrameEntityAccessor;
import eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A map canvas rendered through Minecraft's item-frame renderer.
 *
 * <p>An {@code ItemDisplayElement} renders a filled map as an item model. RGBMapUtils
 * uses the item-frame packet path instead: the client receives an ITEM_FRAME entity,
 * then the map stack is supplied through the item-frame tracked data. Keeping that
 * distinction here is what makes the canvas render as a map surface.</p>
 */
public final class MapDisplayElement extends GenericEntityElement {
    private final Direction direction;
    private MapAnchorElement passengerAnchor;

    public MapDisplayElement(ItemStack mapStack, Direction direction) {
        this.direction = direction;
        this.getDataTracker().set(ItemFrameEntityAccessor.getItemStack(), mapStack);
        this.getDataTracker().set(ItemFrameEntityAccessor.getRotation(), 0);
        this.setInvisible(true);
    }

    public Direction direction() {
        return this.direction;
    }

    MapAnchorElement ensurePassengerAnchor() {
        if (this.passengerAnchor == null) {
            this.passengerAnchor = new MapAnchorElement();
        }
        return this.passengerAnchor;
    }

    @Nullable
    MapAnchorElement passengerAnchor() {
        return this.passengerAnchor;
    }

    void setRenderPosition(Vec3 position) {
        this.setOverridePos(position);
        this.setOffset(Vec3.ZERO);
        if (this.passengerAnchor != null) {
            this.passengerAnchor.setOverridePos(position);
            this.passengerAnchor.setOffset(Vec3.ZERO);
        }
    }

    /**
     * Item-frame entities are block-attached on the client. Rotation packets
     * therefore have the same snapping problem as movement packets, so the
     * frame's direction is fixed in the spawn packet and its entity rotation
     * is intentionally left at zero.
     */
    @Override
    public void setYaw(float yaw) {
    }

    @Override
    public void setPitch(float pitch) {
    }

    @Override
    protected EntityType<? extends Entity> getEntityType() {
        return EntityType.ITEM_FRAME;
    }

    @Override
    protected ClientboundAddEntityPacket createSpawnPacket(@Nullable ServerPlayer player) {
        Vec3 position = this.getCurrentPos();
        return new ClientboundAddEntityPacket(
                this.getEntityId(),
                this.getUuid(),
                position.x,
                position.y,
                position.z,
                0.0f,
                0.0f,
                EntityType.ITEM_FRAME,
                this.direction.get3DDataValue(),
                Vec3.ZERO,
                0.0f
        );
    }

    /**
     * A client-side, non-rendered carrier whose position can move normally.
     * The map frame rides this entity because vanilla's block-attached item
     * frame implementation snaps whenever it receives a position packet.
     */
    static final class MapAnchorElement extends GenericEntityElement {
        private MapAnchorElement() {
            this.setInvisible(true);
        }

        @Override
        protected EntityType<? extends Entity> getEntityType() {
            return EntityType.INTERACTION;
        }
    }
}

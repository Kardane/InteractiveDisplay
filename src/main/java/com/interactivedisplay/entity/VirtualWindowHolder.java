package com.interactivedisplay.entity;

import com.interactivedisplay.core.positioning.PositionMode;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class VirtualWindowHolder {
    private final OwnerOnlyElementHolder holder = new OwnerOnlyElementHolder();
    private final ServerLevel world;
    private HolderAttachment attachment;
    private ServerPlayer owner;
    private boolean playerAttached;
    private Vec3 anchor;
    private Vec3 ownerPositionAtAnchor;
    private boolean watching;

    public VirtualWindowHolder(ServerLevel world, Vec3 anchor) {
        this.world = world;
        this.anchor = anchor;
    }

    public void configure(PositionMode positionMode, ServerPlayer owner) {
        boolean shouldAttachToPlayer = positionMode != PositionMode.FIXED;
        if (this.attachment != null) {
            if (this.playerAttached != shouldAttachToPlayer) {
                throw new IllegalStateException("virtual holder attachment mode cannot change after configuration");
            }
            return;
        }

        this.owner = owner;
        this.holder.setOwner(owner == null ? null : owner.getUUID());
        this.playerAttached = shouldAttachToPlayer;
        if (shouldAttachToPlayer) {
            if (owner == null) {
                throw new IllegalArgumentException("player-attached virtual window requires an owner");
            }
            this.ownerPositionAtAnchor = owner.position();
            this.attachment = new EntityAttachment(this.holder, owner, false);
        } else {
            this.attachment = new ManualAttachment(this.holder, this.world, () -> this.anchor);
        }
    }

    public <T extends VirtualElement> T addElement(T element) {
        if (!this.playerAttached) {
            return this.holder.addElement(element);
        }
        if (element instanceof GenericEntityElement genericEntityElement) {
            genericEntityElement.ignorePositionUpdates();
        }
        return this.holder.addPassengerElement(element);
    }

    public void startWatching(ServerPlayer player) {
        if (!isOwner(player)) {
            return;
        }
        this.holder.startWatching(player);
        this.watching = true;
        if (this.playerAttached) {
            sendRidePacket(true);
        }
    }

    public void stopWatching(ServerPlayer player) {
        if (!isOwner(player) || !this.watching) {
            return;
        }
        if (this.playerAttached) {
            sendRidePacket(false);
        }
        this.holder.stopWatching(player);
        this.watching = false;
    }

    public void setAnchor(Vec3 anchor) {
        this.anchor = anchor;
        if (this.playerAttached && this.owner != null) {
            this.ownerPositionAtAnchor = this.owner.position();
        }
    }

    public Vec3 anchor() {
        return this.anchor;
    }

    /**
     * Returns the server-side world anchor translated by the owner's movement since the last transform update.
     * This keeps raycasts aligned with client-side passenger movement between server transform ticks.
     */
    public Vec3 worldAnchor() {
        if (!this.playerAttached || this.owner == null || this.ownerPositionAtAnchor == null) {
            return this.anchor;
        }
        return this.anchor.add(this.owner.position().subtract(this.ownerPositionAtAnchor));
    }

    /**
     * World-space position used by Polymer when spawning the virtual entity before the ride packet is applied.
     */
    public Vec3 attachmentPosition() {
        if (this.playerAttached && this.owner != null) {
            return this.owner.position();
        }
        return this.anchor;
    }

    /**
     * Approximate vanilla passenger attachment origin for a display riding a player.
     * Player attachment points track the current pose height, so this also follows crouching/swimming changes.
     */
    public Vec3 passengerRenderOrigin() {
        if (!this.playerAttached || this.owner == null) {
            return this.anchor;
        }
        return this.owner.position().add(0.0D, this.owner.getBbHeight(), 0.0D);
    }

    public boolean playerAttached() {
        return this.playerAttached;
    }

    public void tick() {
        this.holder.tick();
    }

    public int entityCount() {
        return this.holder.getEntityIds().size();
    }

    public boolean isWatching() {
        return this.watching;
    }

    public void destroy() {
        if (this.playerAttached && this.watching) {
            sendRidePacket(false);
        }
        this.holder.destroy();
        this.watching = false;
    }

    private boolean isOwner(ServerPlayer player) {
        return player != null && this.owner != null && player.getUUID().equals(this.owner.getUUID());
    }

    private void sendRidePacket(boolean includeVirtualPassengers) {
        if (this.owner == null || this.owner.connection == null) {
            return;
        }
        List<Entity> realPassengers = this.owner.getPassengers();
        int virtualCount = includeVirtualPassengers ? this.holder.getAttachedPassengerEntityIds().size() : 0;
        int[] passengerIds = new int[realPassengers.size() + virtualCount];
        int index = 0;
        for (Entity passenger : realPassengers) {
            passengerIds[index++] = passenger.getId();
        }
        if (includeVirtualPassengers) {
            for (int virtualIndex = 0; virtualIndex < virtualCount; virtualIndex++) {
                passengerIds[index++] = this.holder.getAttachedPassengerEntityIds().getInt(virtualIndex);
            }
        }
        this.owner.connection.send(VirtualEntityUtils.createRidePacket(this.owner.getId(), passengerIds));
    }

    private static final class OwnerOnlyElementHolder extends ElementHolder {
        private UUID owner;

        private void setOwner(UUID owner) {
            this.owner = owner;
        }

        @Override
        public boolean startWatching(ServerGamePacketListenerImpl player) {
            if (this.owner != null && !this.owner.equals(player.player.getUUID())) {
                return false;
            }
            return super.startWatching(player);
        }
    }
}

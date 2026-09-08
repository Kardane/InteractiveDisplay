package com.interactivedisplay.entity;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class VirtualWindowHolder {
    private final ElementHolder holder;
    private final ManualAttachment attachment;
    private Vec3 anchor;
    private boolean watching;

    public VirtualWindowHolder(ServerLevel world, Vec3 anchor) {
        this.anchor = anchor;
        this.holder = new ElementHolder();
        this.attachment = new ManualAttachment(this.holder, world, () -> this.anchor);
    }

    public <T extends VirtualElement> T addElement(T element) {
        return this.holder.addElement(element);
    }

    public void startWatching(ServerPlayer player) {
        if (!this.watching) {
            this.holder.startWatching(player);
            this.watching = true;
        }
    }

    public void stopWatching(ServerPlayer player) {
        if (this.watching) {
            this.holder.stopWatching(player);
            this.watching = false;
        }
    }

    public void setAnchor(Vec3 anchor) {
        this.anchor = anchor;
    }

    public Vec3 anchor() {
        return this.anchor;
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
        this.holder.destroy();
        this.watching = false;
    }
}

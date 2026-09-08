package com.interactivedisplay.entity;

import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.WindowTransition;
import com.interactivedisplay.core.window.WindowTransitionType;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class VirtualWindowHolder {
    private static final float MIN_TRANSITION_SCALE = 0.01f;
    private static final float TRANSITION_SLIDE_DISTANCE = 0.35f;
    private static final List<VirtualWindowHolder> PENDING_DESTROYS = new ArrayList<>();

    private final OwnerOnlyElementHolder holder = new OwnerOnlyElementHolder();
    private final ServerLevel world;
    private final Map<DisplayElement, TransformSnapshot> baseTransforms = new IdentityHashMap<>();
    private HolderAttachment attachment;
    private ServerPlayer owner;
    private boolean playerAttached;
    private Vec3 anchor;
    private Vec3 ownerPositionAtAnchor;
    private boolean watching;
    private boolean destroyed;
    private boolean pendingDestroy;
    private long destroyAtTick;
    private WindowTransition transition = WindowTransition.none();

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

    public void setTransition(WindowTransition transition) {
        this.transition = transition == null ? WindowTransition.none() : transition;
        this.baseTransforms.clear();
        for (VirtualElement element : this.holder.getElements()) {
            if (element instanceof DisplayElement display) {
                this.baseTransforms.put(display, TransformSnapshot.capture(display));
            }
        }
    }

    public void playEnterTransition() {
        if (this.destroyed || !this.transition.hasEnter() || this.baseTransforms.isEmpty()) {
            return;
        }

        for (Map.Entry<DisplayElement, TransformSnapshot> entry : this.baseTransforms.entrySet()) {
            applyTransition(entry.getKey(), entry.getValue(), this.transition.enter(), true, this.transition.duration());
        }
        this.holder.tick();

        for (Map.Entry<DisplayElement, TransformSnapshot> entry : this.baseTransforms.entrySet()) {
            DisplayElement display = entry.getKey();
            TransformSnapshot base = entry.getValue();
            display.setInterpolationDuration(this.transition.duration());
            display.setScale(base.scale());
            display.setTranslation(base.translation());
            display.startInterpolationIfDirty();
        }
        this.holder.tick();
    }

    public void startWatching(ServerPlayer player) {
        if (!isOwner(player) || this.destroyed) {
            return;
        }
        this.holder.startWatching(player);
        this.watching = true;
    }

    public void stopWatching(ServerPlayer player) {
        if (!isOwner(player) || !this.watching) {
            return;
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

    public Vec3 worldAnchor() {
        if (!this.playerAttached || this.owner == null || this.ownerPositionAtAnchor == null) {
            return this.anchor;
        }
        return this.anchor.add(this.owner.position().subtract(this.ownerPositionAtAnchor));
    }

    public Vec3 attachmentPosition() {
        if (this.playerAttached && this.owner != null) {
            return this.owner.position();
        }
        return this.anchor;
    }

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
        if (!this.destroyed) {
            this.holder.tick();
        }
    }

    public int entityCount() {
        return this.destroyed ? 0 : this.holder.getEntityIds().size();
    }

    public boolean isWatching() {
        return this.watching;
    }

    public void destroy() {
        if (this.destroyed || this.pendingDestroy) {
            return;
        }
        if (!this.watching || !this.transition.hasExit() || this.baseTransforms.isEmpty()) {
            destroyNow();
            return;
        }

        for (Map.Entry<DisplayElement, TransformSnapshot> entry : this.baseTransforms.entrySet()) {
            applyTransition(entry.getKey(), entry.getValue(), this.transition.exit(), false, this.transition.duration());
        }
        this.holder.tick();
        this.pendingDestroy = true;
        this.destroyAtTick = this.world.getServer().getTickCount() + this.transition.duration();
        PENDING_DESTROYS.add(this);
    }

    public static void tickPendingDestroys(MinecraftServer server) {
        if (PENDING_DESTROYS.isEmpty()) {
            return;
        }
        long tick = server.getTickCount();
        for (int index = PENDING_DESTROYS.size() - 1; index >= 0; index--) {
            VirtualWindowHolder pending = PENDING_DESTROYS.get(index);
            if (pending.world.getServer() != server || pending.destroyAtTick > tick) {
                continue;
            }
            PENDING_DESTROYS.remove(index);
            pending.pendingDestroy = false;
            pending.destroyNow();
        }
    }

    public static void destroyAllPending(MinecraftServer server) {
        for (int index = PENDING_DESTROYS.size() - 1; index >= 0; index--) {
            VirtualWindowHolder pending = PENDING_DESTROYS.get(index);
            if (pending.world.getServer() != server) {
                continue;
            }
            PENDING_DESTROYS.remove(index);
            pending.pendingDestroy = false;
            pending.destroyNow();
        }
    }

    private void destroyNow() {
        if (this.destroyed) {
            return;
        }
        this.holder.destroy();
        this.watching = false;
        this.pendingDestroy = false;
        this.destroyed = true;
        this.baseTransforms.clear();
    }

    private static void applyTransition(DisplayElement display,
                                        TransformSnapshot base,
                                        WindowTransitionType type,
                                        boolean enterInitial,
                                        int duration) {
        display.setInterpolationDuration(duration);
        switch (type) {
            case SCALE -> display.setScale(base.scale().mul(MIN_TRANSITION_SCALE));
            case SLIDE_UP -> display.setTranslation(base.translation().add(0.0f, enterInitial ? -TRANSITION_SLIDE_DISTANCE : TRANSITION_SLIDE_DISTANCE, 0.0f));
            case SLIDE_DOWN -> display.setTranslation(base.translation().add(0.0f, enterInitial ? TRANSITION_SLIDE_DISTANCE : -TRANSITION_SLIDE_DISTANCE, 0.0f));
            case NONE -> {
            }
        }
        if (!enterInitial) {
            display.startInterpolationIfDirty();
        }
    }

    private boolean isOwner(ServerPlayer player) {
        return player != null && this.owner != null && player.getUUID().equals(this.owner.getUUID());
    }

    private record TransformSnapshot(Vector3f scale, Vector3f translation) {
        private TransformSnapshot {
            scale = new Vector3f(scale);
            translation = new Vector3f(translation);
        }

        private static TransformSnapshot capture(DisplayElement display) {
            return new TransformSnapshot(new Vector3f(display.getScale()), new Vector3f(display.getTranslation()));
        }

        @Override
        public Vector3f scale() {
            return new Vector3f(this.scale);
        }

        @Override
        public Vector3f translation() {
            return new Vector3f(this.translation);
        }
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

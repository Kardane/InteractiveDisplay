package com.interactivedisplay.mixin;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ClickType;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public net.minecraft.server.level.ServerPlayer player;

    @Shadow
    public abstract void ackBlockChangesUpTo(int sequence);

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptBlockUse(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (packet.hand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (this.interactivedisplay$deferToServerThread(() -> ((ServerGamePacketListenerImpl) (Object) this).handleUseItemOn(packet))) {
            ci.cancel();
            return;
        }
        if (interactivedisplay$consume(ClickType.RIGHT)) {
            this.ackBlockChangesUpTo(packet.sequence());
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptItemUse(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (packet.hand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (this.interactivedisplay$deferToServerThread(() -> ((ServerGamePacketListenerImpl) (Object) this).handleUseItem(packet))) {
            ci.cancel();
            return;
        }
        if (interactivedisplay$consume(ClickType.RIGHT)) {
            this.ackBlockChangesUpTo(packet.sequence());
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptBlockAttack(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            return;
        }
        if (this.interactivedisplay$deferToServerThread(() -> ((ServerGamePacketListenerImpl) (Object) this).handlePlayerAction(packet))) {
            ci.cancel();
            return;
        }
        if (interactivedisplay$consume(ClickType.LEFT)) {
            this.ackBlockChangesUpTo(packet.getSequence());
            ci.cancel();
        }
    }

    @Inject(method = "handlePunch", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptPunch(ServerboundPunchPacket packet, CallbackInfo ci) {
        if (this.interactivedisplay$deferToServerThread(() -> ((ServerGamePacketListenerImpl) (Object) this).handlePunch(packet))) {
            ci.cancel();
            return;
        }
        if (interactivedisplay$consume(ClickType.LEFT)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptEntityUse(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (this.interactivedisplay$deferToServerThread(() -> ((ServerGamePacketListenerImpl) (Object) this).handleInteract(packet))) {
            ci.cancel();
            return;
        }
        if (packet.hand() == InteractionHand.MAIN_HAND && interactivedisplay$consume(ClickType.RIGHT)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleAttack", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptEntityAttack(ServerboundAttackPacket packet, CallbackInfo ci) {
        if (this.interactivedisplay$deferToServerThread(() -> ((ServerGamePacketListenerImpl) (Object) this).handleAttack(packet))) {
            ci.cancel();
            return;
        }
        if (interactivedisplay$consume(ClickType.LEFT)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean interactivedisplay$consume(ClickType clickType) {
        InteractiveDisplay mod = InteractiveDisplay.instance();
        return mod != null && mod.consumeUiClick(this.player, clickType);
    }

    @Unique
    private boolean interactivedisplay$deferToServerThread(Runnable action) {
        net.minecraft.server.MinecraftServer server = this.player.level().getServer();
        if (server == null || server.isSameThread()) {
            return false;
        }
        server.execute(action);
        return true;
    }
}

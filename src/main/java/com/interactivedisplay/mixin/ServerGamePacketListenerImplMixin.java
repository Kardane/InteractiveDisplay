package com.interactivedisplay.mixin;

import com.interactivedisplay.InteractiveDisplay;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
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
        if (packet.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (this.interactivedisplay$deferToServerThread(() -> ((ServerGamePacketListenerImpl) (Object) this).handleUseItemOn(packet))) {
            ci.cancel();
            return;
        }
        InteractiveDisplay mod = InteractiveDisplay.instance();
        if (mod != null && mod.consumeUiRightClick(this.player)) {
            this.ackBlockChangesUpTo(packet.getSequence());
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptItemUse(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (packet.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (this.interactivedisplay$deferToServerThread(() -> ((ServerGamePacketListenerImpl) (Object) this).handleUseItem(packet))) {
            ci.cancel();
            return;
        }
        InteractiveDisplay mod = InteractiveDisplay.instance();
        if (mod != null && mod.consumeUiRightClick(this.player)) {
            this.ackBlockChangesUpTo(packet.getSequence());
            ci.cancel();
        }
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptEntityUse(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (this.interactivedisplay$deferToServerThread(() -> ((ServerGamePacketListenerImpl) (Object) this).handleInteract(packet))) {
            ci.cancel();
            return;
        }
        boolean[] shouldHandle = {false};
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
                shouldHandle[0] = hand == InteractionHand.MAIN_HAND;
            }

            @Override
            public void onInteraction(InteractionHand hand, net.minecraft.world.phys.Vec3 hitPos) {
                shouldHandle[0] = hand == InteractionHand.MAIN_HAND;
            }

            @Override
            public void onAttack() {
                shouldHandle[0] = false;
            }
        });
        if (!shouldHandle[0]) {
            return;
        }
        InteractiveDisplay mod = InteractiveDisplay.instance();
        if (mod != null && mod.consumeUiRightClick(this.player)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean interactivedisplay$deferToServerThread(Runnable action) {
        net.minecraft.server.MinecraftServer server = this.player.getServer();
        if (server == null || server.isSameThread()) {
            return false;
        }
        server.execute(action);
        return true;
    }
}

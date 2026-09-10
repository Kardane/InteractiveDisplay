package com.interactivedisplay.mixin;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ClickType;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
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
        if (interactivedisplay$consume(ClickType.RIGHT)) {
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
        if (interactivedisplay$consume(ClickType.RIGHT)) {
            this.ackBlockChangesUpTo(packet.getSequence());
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

    @Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (packet.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (this.interactivedisplay$deferToServerThread(() -> ((ServerGamePacketListenerImpl) (Object) this).handleAnimate(packet))) {
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
        ClickType[] clickType = {null};
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
                if (hand == InteractionHand.MAIN_HAND) {
                    clickType[0] = ClickType.RIGHT;
                }
            }

            @Override
            public void onInteraction(InteractionHand hand, net.minecraft.world.phys.Vec3 hitPos) {
                if (hand == InteractionHand.MAIN_HAND) {
                    clickType[0] = ClickType.RIGHT;
                }
            }

            @Override
            public void onAttack() {
                clickType[0] = ClickType.LEFT;
            }
        });
        if (clickType[0] != null && interactivedisplay$consume(clickType[0])) {
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
        net.minecraft.server.MinecraftServer server = this.player.getServer();
        if (server == null || server.isSameThread()) {
            return false;
        }
        server.execute(action);
        return true;
    }
}

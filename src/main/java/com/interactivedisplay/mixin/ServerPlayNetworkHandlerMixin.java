package com.interactivedisplay.mixin;

import com.interactivedisplay.InteractiveDisplay;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public net.minecraft.server.network.ServerPlayerEntity player;

    @Shadow
    public abstract void updateSequence(int sequence);

    @Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptBlockUse(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        if (packet.getHand() != Hand.MAIN_HAND) {
            return;
        }
        InteractiveDisplay mod = InteractiveDisplay.instance();
        if (mod != null && mod.consumeUiRightClick(this.player)) {
            this.updateSequence(packet.getSequence());
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptItemUse(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        if (packet.getHand() != Hand.MAIN_HAND) {
            return;
        }
        InteractiveDisplay mod = InteractiveDisplay.instance();
        if (mod != null && mod.consumeUiRightClick(this.player)) {
            this.updateSequence(packet.getSequence());
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$interceptEntityUse(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        boolean[] shouldHandle = {false};
        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override
            public void interact(Hand hand) {
                shouldHandle[0] = hand == Hand.MAIN_HAND;
            }

            @Override
            public void interactAt(Hand hand, net.minecraft.util.math.Vec3d hitPos) {
                shouldHandle[0] = hand == Hand.MAIN_HAND;
            }

            @Override
            public void attack() {
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
}

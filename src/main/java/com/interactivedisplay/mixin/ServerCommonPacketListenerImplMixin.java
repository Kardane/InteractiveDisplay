package com.interactivedisplay.mixin;

import com.interactivedisplay.InteractiveDisplay;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Inject(method = "handleCustomClickAction", at = @At("HEAD"), cancellable = true)
    private void interactivedisplay$handleCustomClickAction(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl gameListener)) {
            return;
        }

        MinecraftServer server = gameListener.player.getServer();
        if (server != null && !server.isSameThread()) {
            server.execute(() -> ((ServerCommonPacketListenerImpl) (Object) this).handleCustomClickAction(packet));
            ci.cancel();
            return;
        }

        InteractiveDisplay mod = InteractiveDisplay.instance();
        if (mod != null && mod.consumeCustomClickAction(gameListener.player, packet)) {
            ci.cancel();
        }
    }
}

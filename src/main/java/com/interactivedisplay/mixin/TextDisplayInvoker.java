package com.interactivedisplay.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayInvoker {
    @Invoker("setText")
    void interactivedisplay$setText(Component text);

    @Invoker("setLineWidth")
    void interactivedisplay$setLineWidth(int lineWidth);

    @Invoker("setTextOpacity")
    void interactivedisplay$setTextOpacity(byte opacity);

    @Invoker("setBackgroundColor")
    void interactivedisplay$setBackgroundColor(int backgroundColor);

    @Invoker("setFlags")
    void interactivedisplay$setFlags(byte flags);
}

package com.interactivedisplay.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayInvoker {
    @Invoker("setTransformation")
    void interactivedisplay$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void interactivedisplay$setTransformationInterpolationDuration(int duration);

    @Invoker("setTransformationInterpolationDelay")
    void interactivedisplay$setTransformationInterpolationDelay(int delay);

    @Invoker("setPosRotInterpolationDuration")
    void interactivedisplay$setPosRotInterpolationDuration(int duration);

    @Invoker("setBillboardConstraints")
    void interactivedisplay$setBillboardConstraints(Display.BillboardConstraints constraints);
}

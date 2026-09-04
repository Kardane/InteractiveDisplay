package com.interactivedisplay.command;

import java.lang.reflect.Field;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.AngleArgument;
import net.minecraft.util.Mth;

public record AngleInput(float value, boolean relative) {
    private static final Field ANGLE_FIELD = resolveField("angle");
    private static final Field RELATIVE_FIELD = resolveField("isRelative");

    public static AngleInput absolute(float value) {
        return new AngleInput(value, false);
    }

    public static AngleInput fromParsed(AngleArgument.SingleAngle angle) {
        try {
            return new AngleInput(ANGLE_FIELD.getFloat(angle), RELATIVE_FIELD.getBoolean(angle));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("AngleArgument.SingleAngle reflection 실패", exception);
        }
    }

    public float resolveYaw(CommandSourceStack source) {
        return Mth.wrapDegrees(this.relative ? source.getRotation().y + this.value : this.value);
    }

    public float resolvePitch(CommandSourceStack source) {
        return Mth.clamp(this.relative ? source.getRotation().x + this.value : this.value, -90.0f, 90.0f);
    }

    private static Field resolveField(String name) {
        try {
            Field field = AngleArgument.SingleAngle.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}

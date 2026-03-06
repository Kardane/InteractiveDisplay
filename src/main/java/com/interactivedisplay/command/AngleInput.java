package com.interactivedisplay.command;

import java.lang.reflect.Field;
import net.minecraft.command.argument.AngleArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.MathHelper;

public record AngleInput(float value, boolean relative) {
    private static final Field ANGLE_FIELD = resolveField("angle");
    private static final Field RELATIVE_FIELD = resolveField("relative");

    public static AngleInput absolute(float value) {
        return new AngleInput(value, false);
    }

    public static AngleInput fromParsed(AngleArgumentType.Angle angle) {
        try {
            return new AngleInput(ANGLE_FIELD.getFloat(angle), RELATIVE_FIELD.getBoolean(angle));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("AngleArgumentType.Angle reflection 실패", exception);
        }
    }

    public float resolveYaw(ServerCommandSource source) {
        return MathHelper.wrapDegrees(this.relative ? source.getRotation().y + this.value : this.value);
    }

    public float resolvePitch(ServerCommandSource source) {
        return MathHelper.clamp(this.relative ? source.getRotation().x + this.value : this.value, -90.0f, 90.0f);
    }

    private static Field resolveField(String name) {
        try {
            Field field = AngleArgumentType.Angle.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}

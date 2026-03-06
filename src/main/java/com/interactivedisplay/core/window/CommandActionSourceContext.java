package com.interactivedisplay.core.window;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

record CommandActionSourceContext(
        RegistryKey<World> worldKey,
        Vec3d position,
        float yaw,
        float pitch,
        Integer permissionLevel,
        String normalizedCommand
) {
    static CommandActionSourceContext of(RegistryKey<World> worldKey,
                                         Vec3d position,
                                         float yaw,
                                         float pitch,
                                         Integer permissionLevel,
                                         String command) {
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        return new CommandActionSourceContext(worldKey, position, yaw, pitch, permissionLevel, normalized);
    }

    boolean hasPermissionOverride() {
        return this.permissionLevel != null;
    }
}

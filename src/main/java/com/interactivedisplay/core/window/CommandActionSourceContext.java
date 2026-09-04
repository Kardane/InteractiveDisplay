package com.interactivedisplay.core.window;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

record CommandActionSourceContext(
        ResourceKey<Level> worldKey,
        Vec3 position,
        float yaw,
        float pitch,
        Integer permissionLevel,
        String normalizedCommand
) {
    static CommandActionSourceContext of(ResourceKey<Level> worldKey,
                                         Vec3 position,
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

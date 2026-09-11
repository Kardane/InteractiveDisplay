package com.interactivedisplay.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.minecraft.world.entity.Display;

public final class InteractiveDisplayClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestDedicatedServerContext server = context.worldBuilder().createServer();
             TestServerConnection connection = server.connect()) {
            connection.getClientWorld().waitForChunksDownload();
            context.waitTicks(10);

            String playerName = context.computeOnClient(client -> client.player.getGameProfile().getName());
            int baselinePassengers = displayPassengerCount(context);

            server.runCommand("interactivedisplay create main_menu " + playerName + " player_fixed");
            context.waitTicks(10);

            int openedPassengers = displayPassengerCount(context);
            if (openedPassengers <= baselinePassengers) {
                throw new AssertionError("PLAYER_FIXED window did not arrive as virtual display passengers: baseline="
                        + baselinePassengers + " opened=" + openedPassengers);
            }

            server.runCommand("interactivedisplay remove main_menu " + playerName);
            context.waitTicks(20);

            int remainingPassengers = displayPassengerCount(context);
            if (remainingPassengers != baselinePassengers) {
                throw new AssertionError("virtual display passengers were not cleaned up after remove: baseline="
                        + baselinePassengers + " remaining=" + remainingPassengers);
            }
        }
    }

    private static int displayPassengerCount(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            if (client.player == null) {
                throw new AssertionError("client player is not available");
            }
            return (int) client.player.getPassengers().stream()
                    .filter(entity -> entity instanceof Display)
                    .count();
        });
    }
}

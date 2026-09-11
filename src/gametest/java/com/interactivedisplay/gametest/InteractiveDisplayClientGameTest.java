package com.interactivedisplay.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;

public final class InteractiveDisplayClientGameTest implements FabricClientGameTest {
    private static final String EXTERNAL_SERVER_PROPERTY = "interactivedisplay.qa.externalServer";
    private static final String STATE_DIR_PROPERTY = "interactivedisplay.qa.stateDir";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean(EXTERNAL_SERVER_PROPERTY)) {
            runExternalServerTest(context);
            return;
        }

        runInProcessServerTest(context);
    }

    private static void runExternalServerTest(ClientGameTestContext context) {
        try {
            context.waitFor(client -> client.player != null && client.level != null, 1_200);
        } catch (AssertionError error) {
            String diagnostic = context.computeOnClient(client -> "screen="
                    + (client.screen == null ? "null" : client.screen.getClass().getName())
                    + ", player=" + (client.player != null)
                    + ", level=" + (client.level != null)
                    + ", connection=" + (client.getConnection() != null));
            writeState("client-join-diagnostic", diagnostic);
            throw new AssertionError("external client did not finish world join: " + diagnostic, error);
        }
        context.waitTicks(20);

        String playerName = context.computeOnClient(client -> client.player.getGameProfile().getName());
        int baselinePassengers = displayPassengerCount(context);
        double baselineX = context.computeOnClient(client -> client.player.getX());
        writeState("client-ready", playerName);

        context.waitFor(client -> displayPassengerCount(client) > baselinePassengers, 1_200);
        int openedPassengers = displayPassengerCount(context);
        if (openedPassengers <= baselinePassengers) {
            throw new AssertionError("PLAYER_FIXED window did not arrive as virtual display passengers: baseline="
                    + baselinePassengers + " opened=" + openedPassengers);
        }
        writeState("client-opened", Integer.toString(openedPassengers));

        context.waitFor(client -> Math.abs(client.player.getX() - baselineX) >= 5.0, 1_200);
        context.waitTicks(10);
        int movedPassengers = displayPassengerCount(context);
        if (movedPassengers != openedPassengers) {
            throw new AssertionError("PLAYER_FIXED virtual display passenger count changed after player movement: opened="
                    + openedPassengers + " moved=" + movedPassengers);
        }
        writeState("client-moved", Integer.toString(movedPassengers));

        context.waitFor(client -> displayPassengerCount(client) == baselinePassengers, 1_200);
        context.waitTicks(10);
        int remainingPassengers = displayPassengerCount(context);
        if (remainingPassengers != baselinePassengers) {
            throw new AssertionError("virtual display passengers were not cleaned up after remove: baseline="
                    + baselinePassengers + " remaining=" + remainingPassengers);
        }
        writeState("client-clean", Integer.toString(remainingPassengers));
    }

    private static void runInProcessServerTest(ClientGameTestContext context) {
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
        return context.computeOnClient(InteractiveDisplayClientGameTest::displayPassengerCount);
    }

    private static int displayPassengerCount(Minecraft client) {
        if (client.player == null) {
            return 0;
        }
        return (int) client.player.getPassengers().stream()
                .filter(entity -> entity instanceof Display)
                .count();
    }

    private static void writeState(String fileName, String content) {
        String stateDir = System.getProperty(STATE_DIR_PROPERTY);
        if (stateDir == null || stateDir.isBlank()) {
            throw new AssertionError("missing client E2E state directory system property");
        }

        try {
            Path directory = Path.of(stateDir);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(fileName), content);
        } catch (IOException exception) {
            throw new AssertionError("failed to write client E2E state marker " + fileName, exception);
        }
    }
}

package com.interactivedisplay.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.world.entity.Display;

public final class InteractiveDisplayClientGameTest implements FabricClientGameTest {
    private static final String EXTERNAL_SERVER_PROPERTY = "interactivedisplay.qa.externalServer";
    private static final String STATE_DIR_PROPERTY = "interactivedisplay.qa.stateDir";
    private static final String PACK_CONFIRM_SCREEN = "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl$PackConfirmScreen";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean(EXTERNAL_SERVER_PROPERTY)) {
            runExternalServerTest(context);
            return;
        }

        runInProcessServerTest(context);
    }

    private static void runExternalServerTest(ClientGameTestContext context) {
        acceptServerPackPromptIfPresent(context);
        waitForExternalWorldOrReportDisconnect(context);
        context.waitTicks(20);

        String playerName = context.computeOnClient(client -> client.player.getGameProfile().getName());
        Set<Integer> baselineDisplayIds = displayPassengerIds(context);
        int baselinePassengers = baselineDisplayIds.size();
        int baselineWorldDisplays = worldDisplayCount(context);
        writeState("client-ready", playerName);

        context.waitFor(client -> displayPassengerCount(client) > baselinePassengers, 1_200);
        int openedPassengers = displayPassengerCount(context);
        if (openedPassengers <= baselinePassengers) {
            throw new AssertionError("PLAYER_FIXED window did not arrive as virtual display passengers: baseline="
                    + baselinePassengers + " opened=" + openedPassengers);
        }
        Set<Integer> openedDisplayIds = displayPassengerIds(context);
        if (!openedDisplayIds.containsAll(baselineDisplayIds)) {
            throw new AssertionError("baseline display passengers changed while opening PLAYER_FIXED window: baseline="
                    + baselineDisplayIds + " opened=" + openedDisplayIds);
        }
        int openedWorldDisplays = worldDisplayCount(context);
        if (openedWorldDisplays <= baselineWorldDisplays) {
            throw new AssertionError("PLAYER_FIXED window did not add Display entities to the client world: baseline="
                    + baselineWorldDisplays + " opened=" + openedWorldDisplays);
        }
        double openedPlayerX = context.computeOnClient(client -> client.player.getX());
        double openedDisplayX = newDisplayPassengerAverageX(context, baselineDisplayIds);
        writeState("client-opened", openedPassengers + ",worldDisplays=" + openedWorldDisplays);

        context.waitFor(client -> Math.abs(client.player.getX() - openedPlayerX) >= 5.0, 1_200);
        context.waitTicks(10);
        int movedPassengers = displayPassengerCount(context);
        if (movedPassengers != openedPassengers) {
            throw new AssertionError("PLAYER_FIXED virtual display passenger count changed after player movement: opened="
                    + openedPassengers + " moved=" + movedPassengers);
        }
        Set<Integer> movedDisplayIds = displayPassengerIds(context);
        if (!movedDisplayIds.equals(openedDisplayIds)) {
            throw new AssertionError("PLAYER_FIXED virtual display passenger identity changed after movement: opened="
                    + openedDisplayIds + " moved=" + movedDisplayIds);
        }
        int movedWorldDisplays = worldDisplayCount(context);
        if (movedWorldDisplays != openedWorldDisplays) {
            throw new AssertionError("client-world Display count changed during PLAYER_FIXED movement: opened="
                    + openedWorldDisplays + " moved=" + movedWorldDisplays);
        }
        double movedPlayerX = context.computeOnClient(client -> client.player.getX());
        double movedDisplayX = newDisplayPassengerAverageX(context, baselineDisplayIds);
        double playerDeltaX = movedPlayerX - openedPlayerX;
        double displayDeltaX = movedDisplayX - openedDisplayX;
        if (Math.abs(playerDeltaX - displayDeltaX) > 0.25) {
            throw new AssertionError("PLAYER_FIXED virtual displays did not follow player movement: playerDeltaX="
                    + playerDeltaX + " displayDeltaX=" + displayDeltaX);
        }
        writeState("client-moved", movedPassengers + ",deltaX=" + displayDeltaX + ",worldDisplays=" + movedWorldDisplays);

        context.waitFor(client -> displayPassengerCount(client) == baselinePassengers
                && worldDisplayCount(client) == baselineWorldDisplays, 1_200);
        context.waitTicks(10);
        int remainingPassengers = displayPassengerCount(context);
        if (remainingPassengers != baselinePassengers) {
            throw new AssertionError("virtual display passengers were not cleaned up after remove: baseline="
                    + baselinePassengers + " remaining=" + remainingPassengers);
        }
        Set<Integer> remainingDisplayIds = displayPassengerIds(context);
        if (!remainingDisplayIds.equals(baselineDisplayIds)) {
            throw new AssertionError("virtual display passenger identities did not return to baseline after remove: baseline="
                    + baselineDisplayIds + " remaining=" + remainingDisplayIds);
        }
        int remainingWorldDisplays = worldDisplayCount(context);
        if (remainingWorldDisplays != baselineWorldDisplays) {
            throw new AssertionError("ghost Display entities remained in the client world after remove: baseline="
                    + baselineWorldDisplays + " remaining=" + remainingWorldDisplays);
        }
        writeState("client-clean", remainingPassengers + ",worldDisplays=" + remainingWorldDisplays);

        // Fabric Client GameTest requires each test to return in a disconnected state. Use the
        // normal Minecraft client teardown path so the world, connection, and server-pack state
        // are released exactly as they are when a player leaves a multiplayer server.
        context.computeOnClient(client -> {
            client.disconnect(new TitleScreen(), false);
            return true;
        });
        context.waitFor(client -> client.getConnection() == null && client.player == null && client.level == null, 200);
        writeState("client-disconnected", "true");
    }

    private static void waitForExternalWorldOrReportDisconnect(ClientGameTestContext context) {
        AtomicReference<String> lastScreen = new AtomicReference<>("");
        AtomicReference<String> screenTrace = new AtomicReference<>("");
        AtomicReference<String> disconnectReason = new AtomicReference<>();

        try {
            context.waitFor(client -> {
                recordScreenTransition(client, lastScreen, screenTrace);
                if (client.screen instanceof DisconnectedScreen disconnected) {
                    disconnectReason.compareAndSet(null, disconnected.getNarrationMessage().getString());
                    return true;
                }
                return client.player != null && client.level != null;
            }, 1_200);
        } catch (AssertionError error) {
            String diagnostic = externalJoinDiagnostic(context, screenTrace.get(), disconnectReason.get());
            writeState("client-join-diagnostic", diagnostic);
            throw new AssertionError("external client did not finish world join: " + diagnostic, error);
        }

        if (disconnectReason.get() != null) {
            String diagnostic = externalJoinDiagnostic(context, screenTrace.get(), disconnectReason.get());
            writeState("client-disconnect", diagnostic);
            throw new AssertionError("external client disconnected before world join: " + diagnostic);
        }
    }

    private static String externalJoinDiagnostic(
            ClientGameTestContext context,
            String screenTrace,
            String disconnectReason
    ) {
        return context.computeOnClient(client -> "screen="
                + screenName(client)
                + ", player=" + (client.player != null)
                + ", level=" + (client.level != null)
                + ", connection=" + (client.getConnection() != null)
                + ", serverData=" + (client.getCurrentServer() != null)
                + ", trace=" + screenTrace
                + ", disconnect=" + (disconnectReason == null ? "-" : disconnectReason));
    }

    private static void recordScreenTransition(
            Minecraft client,
            AtomicReference<String> lastScreen,
            AtomicReference<String> screenTrace
    ) {
        String current = screenName(client);
        String previous = lastScreen.getAndSet(current);
        if (current.equals(previous)) {
            return;
        }
        screenTrace.updateAndGet(existing -> existing == null || existing.isEmpty() ? current : existing + " -> " + current);
    }

    private static String screenName(Minecraft client) {
        return client.screen == null ? "null" : client.screen.getClass().getName();
    }

    private static void acceptServerPackPromptIfPresent(ClientGameTestContext context) {
        context.waitFor(client -> client.player != null || isPackConfirmScreen(client), 1_200);
        boolean accepted = context.computeOnClient(client -> {
            if (!isPackConfirmScreen(client)) {
                return false;
            }

            // Invoke the actual affirmative widget rather than duplicating part of its callback.
            // Vanilla's PackConfirmScreen callback restores its parent screen, allows queued server
            // packs, pushes each pending pack, persists ServerData when present, and continues login.
            Button affirmative = client.screen.children().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> CommonComponents.GUI_PROCEED.equals(button.getMessage())
                            || CommonComponents.GUI_YES.equals(button.getMessage()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("PackConfirmScreen affirmative button not found"));
            affirmative.onPress();
            return true;
        });
        if (accepted) {
            writeState("client-pack-accepted", "true");
        }
    }

    private static boolean isPackConfirmScreen(Minecraft client) {
        return client.screen != null && PACK_CONFIRM_SCREEN.equals(client.screen.getClass().getName());
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

    private static Set<Integer> displayPassengerIds(ClientGameTestContext context) {
        return context.computeOnClient(InteractiveDisplayClientGameTest::displayPassengerIds);
    }

    private static Set<Integer> displayPassengerIds(Minecraft client) {
        if (client.player == null) {
            return Set.of();
        }
        Set<Integer> ids = new HashSet<>();
        client.player.getPassengers().stream()
                .filter(entity -> entity instanceof Display)
                .forEach(entity -> ids.add(entity.getId()));
        return Set.copyOf(ids);
    }

    private static int worldDisplayCount(ClientGameTestContext context) {
        return context.computeOnClient(InteractiveDisplayClientGameTest::worldDisplayCount);
    }

    private static int worldDisplayCount(Minecraft client) {
        if (client.level == null) {
            return 0;
        }
        int count = 0;
        for (var entity : client.level.entitiesForRendering()) {
            if (entity instanceof Display) {
                count++;
            }
        }
        return count;
    }

    private static double newDisplayPassengerAverageX(ClientGameTestContext context, Set<Integer> baselineIds) {
        return context.computeOnClient(client -> newDisplayPassengerAverageX(client, baselineIds));
    }

    private static double newDisplayPassengerAverageX(Minecraft client, Set<Integer> baselineIds) {
        if (client.player == null) {
            throw new AssertionError("client player missing while measuring PLAYER_FIXED display positions");
        }
        double sum = 0.0;
        int count = 0;
        for (var entity : client.player.getPassengers()) {
            if (entity instanceof Display && !baselineIds.contains(entity.getId())) {
                sum += entity.getX();
                count++;
            }
        }
        if (count == 0) {
            throw new AssertionError("no new PLAYER_FIXED display passengers available for position measurement");
        }
        return sum / count;
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

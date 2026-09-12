package com.interactivedisplay.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Split-process client coverage used by the QA dedicated-server workflow.
 * Non-external runs delegate to the original in-process smoke test.
 */
public final class InteractiveDisplayClientModesGameTest implements FabricClientGameTest {
    private static final String EXTERNAL_SERVER_PROPERTY = "interactivedisplay.qa.externalServer";
    private static final String STATE_DIR_PROPERTY = "interactivedisplay.qa.stateDir";
    private static final String PACK_CONFIRM_SCREEN = "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl$PackConfirmScreen";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean(EXTERNAL_SERVER_PROPERTY)) {
            new InteractiveDisplayClientGameTest().runTest(context);
            return;
        }
        runExternalServerTest(context);
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

        // Existing PLAYER_FIXED packet/movement/cleanup boundary.
        context.waitFor(client -> displayPassengerCount(client) > baselinePassengers, 1_200);
        context.waitTicks(10);
        int openedPassengers = displayPassengerCount(context);
        Set<Integer> openedDisplayIds = displayPassengerIds(context);
        int openedWorldDisplays = worldDisplayCount(context);
        if (openedPassengers <= baselinePassengers || openedWorldDisplays <= baselineWorldDisplays) {
            throw new AssertionError("PLAYER_FIXED window did not arrive on client: passengers="
                    + openedPassengers + " worldDisplays=" + openedWorldDisplays);
        }
        if (!openedDisplayIds.containsAll(baselineDisplayIds)) {
            throw new AssertionError("baseline passenger IDs changed while opening PLAYER_FIXED window");
        }
        double openedPlayerX = context.computeOnClient(client -> client.player.getX());
        double openedDisplayX = newDisplayPassengerAverageX(context, baselineDisplayIds);
        writeState("client-opened", openedPassengers + ",worldDisplays=" + openedWorldDisplays);

        context.waitFor(client -> Math.abs(client.player.getX() - openedPlayerX) >= 5.0, 1_200);
        context.waitTicks(10);
        int movedPassengers = displayPassengerCount(context);
        Set<Integer> movedDisplayIds = displayPassengerIds(context);
        int movedWorldDisplays = worldDisplayCount(context);
        if (movedPassengers != openedPassengers || !movedDisplayIds.equals(openedDisplayIds)
                || movedWorldDisplays != openedWorldDisplays) {
            throw new AssertionError("PLAYER_FIXED identity/count changed during movement");
        }
        double movedPlayerX = context.computeOnClient(client -> client.player.getX());
        double movedDisplayX = newDisplayPassengerAverageX(context, baselineDisplayIds);
        double playerDeltaX = movedPlayerX - openedPlayerX;
        double displayDeltaX = movedDisplayX - openedDisplayX;
        if (Math.abs(playerDeltaX - displayDeltaX) > 0.25) {
            throw new AssertionError("PLAYER_FIXED displays did not follow player movement: playerDeltaX="
                    + playerDeltaX + " displayDeltaX=" + displayDeltaX);
        }
        writeState("client-moved", movedPassengers + ",deltaX=" + displayDeltaX + ",worldDisplays=" + movedWorldDisplays);

        waitForBaseline(context, baselinePassengers, baselineWorldDisplays, baselineDisplayIds, "PLAYER_FIXED remove");
        writeState("client-clean", baselinePassengers + ",worldDisplays=" + baselineWorldDisplays);

        // PLAYER_VIEW: workflow resets view to yaw=0/pitch=0, opens the window, then rotates to 90/30.
        writeState("client-view-ready", playerName);
        context.waitFor(client -> displayPassengerCount(client) > baselinePassengers, 1_200);
        context.waitTicks(10);

        int viewPassengers = displayPassengerCount(context);
        int viewWorldDisplays = worldDisplayCount(context);
        Set<Integer> viewIds = displayPassengerIds(context);
        if (viewPassengers <= baselinePassengers || viewWorldDisplays <= baselineWorldDisplays) {
            throw new AssertionError("PLAYER_VIEW window did not arrive on client");
        }
        ViewState initialView = viewState(context);
        if (Math.abs(Mth.wrapDegrees(initialView.yaw())) > 5.0f || Math.abs(initialView.pitch()) > 5.0f) {
            throw new AssertionError("PLAYER_VIEW fixture did not start from yaw=0/pitch=0: " + initialView);
        }
        TransformSnapshot initialTransform = newDisplayTransform(context, baselineDisplayIds);
        assertRotationMatchesView(initialTransform.rotation(), initialView, "PLAYER_VIEW initial transform");
        writeState("client-view-opened", "yaw=" + initialView.yaw() + ",pitch=" + initialView.pitch());

        context.waitFor(client -> Math.abs(Mth.wrapDegrees(client.player.getYRot() - initialView.yaw())) >= 45.0f
                && Math.abs(client.player.getXRot() - initialView.pitch()) >= 20.0f, 1_200);
        context.waitTicks(10);

        ViewState rotatedView = viewState(context);
        Set<Integer> rotatedIds = displayPassengerIds(context);
        int rotatedWorldDisplays = worldDisplayCount(context);
        if (!rotatedIds.equals(viewIds)) {
            throw new AssertionError("PLAYER_VIEW passenger identity changed while rotating: opened="
                    + viewIds + " rotated=" + rotatedIds);
        }
        if (rotatedWorldDisplays != viewWorldDisplays) {
            throw new AssertionError("PLAYER_VIEW client-world Display count changed while rotating: opened="
                    + viewWorldDisplays + " rotated=" + rotatedWorldDisplays);
        }

        TransformSnapshot rotatedTransform = newDisplayTransform(context, baselineDisplayIds);
        assertRotationMatchesView(rotatedTransform.rotation(), rotatedView, "PLAYER_VIEW rotated transform");
        float quaternionDot = Math.abs(initialTransform.rotation().dot(rotatedTransform.rotation()));
        if (quaternionDot > 0.95f) {
            throw new AssertionError("PLAYER_VIEW rotation quaternion did not materially change: absDot=" + quaternionDot);
        }
        float translationDelta = initialTransform.translation().distance(rotatedTransform.translation());
        if (translationDelta < 0.25f) {
            throw new AssertionError("PLAYER_VIEW translation did not follow view rotation: delta=" + translationDelta);
        }
        writeState("client-view-rotated", "yaw=" + rotatedView.yaw()
                + ",pitch=" + rotatedView.pitch()
                + ",translationDelta=" + translationDelta
                + ",quaternionDot=" + quaternionDot);

        waitForBaseline(context, baselinePassengers, baselineWorldDisplays, baselineDisplayIds, "PLAYER_VIEW remove");
        writeState("client-view-clean", baselinePassengers + ",worldDisplays=" + baselineWorldDisplays);

        context.computeOnClient(client -> {
            client.disconnect(new TitleScreen(), false);
            return true;
        });
        context.waitFor(client -> client.getConnection() == null && client.player == null && client.level == null, 200);
        writeState("client-disconnected", "true");
    }

    private static void waitForBaseline(
            ClientGameTestContext context,
            int baselinePassengers,
            int baselineWorldDisplays,
            Set<Integer> baselineDisplayIds,
            String label
    ) {
        context.waitFor(client -> displayPassengerCount(client) == baselinePassengers
                && worldDisplayCount(client) == baselineWorldDisplays, 1_200);
        context.waitTicks(10);
        Set<Integer> remainingIds = displayPassengerIds(context);
        if (!remainingIds.equals(baselineDisplayIds)) {
            throw new AssertionError(label + " did not restore baseline passenger IDs: baseline="
                    + baselineDisplayIds + " remaining=" + remainingIds);
        }
        if (worldDisplayCount(context) != baselineWorldDisplays) {
            throw new AssertionError(label + " left ghost Display entities in client world");
        }
    }

    private static ViewState viewState(ClientGameTestContext context) {
        return context.computeOnClient(client -> new ViewState(client.player.getYRot(), client.player.getXRot()));
    }

    private static TransformSnapshot newDisplayTransform(ClientGameTestContext context, Set<Integer> baselineIds) {
        return context.computeOnClient(client -> {
            Vector3f translationSum = new Vector3f();
            Quaternionf rotation = null;
            int count = 0;
            for (var entity : client.player.getPassengers()) {
                if (!(entity instanceof Display display) || baselineIds.contains(entity.getId())) {
                    continue;
                }
                var renderState = display.renderState();
                if (renderState == null || renderState.transformation() == null) {
                    throw new AssertionError("Display render state missing for PLAYER_VIEW passenger id=" + entity.getId());
                }
                var transformation = renderState.transformation().get(1.0f);
                translationSum.add(transformation.getTranslation());
                if (rotation == null) {
                    rotation = new Quaternionf(transformation.getLeftRotation());
                }
                count++;
            }
            if (count == 0 || rotation == null) {
                throw new AssertionError("no new Display passengers available for PLAYER_VIEW transform measurement");
            }
            translationSum.div((float) count);
            return new TransformSnapshot(translationSum, rotation);
        });
    }

    private static void assertRotationMatchesView(Quaternionf actual, ViewState view, String label) {
        Quaternionf expected = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-(view.yaw() + 180.0f)),
                (float) Math.toRadians(view.pitch()),
                0.0f
        );
        float dot = Math.abs(actual.dot(expected));
        if (dot < 0.98f) {
            throw new AssertionError(label + " quaternion mismatch: absDot=" + dot
                    + " yaw=" + view.yaw() + " pitch=" + view.pitch());
        }
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

    private static String externalJoinDiagnostic(ClientGameTestContext context, String trace, String disconnectReason) {
        return context.computeOnClient(client -> "screen=" + screenName(client)
                + ", player=" + (client.player != null)
                + ", level=" + (client.level != null)
                + ", connection=" + (client.getConnection() != null)
                + ", serverData=" + (client.getCurrentServer() != null)
                + ", trace=" + trace
                + ", disconnect=" + (disconnectReason == null ? "-" : disconnectReason));
    }

    private static void recordScreenTransition(Minecraft client, AtomicReference<String> last, AtomicReference<String> trace) {
        String current = screenName(client);
        String previous = last.getAndSet(current);
        if (!current.equals(previous)) {
            trace.updateAndGet(existing -> existing == null || existing.isEmpty() ? current : existing + " -> " + current);
        }
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

    private static int displayPassengerCount(ClientGameTestContext context) {
        return context.computeOnClient(InteractiveDisplayClientModesGameTest::displayPassengerCount);
    }

    private static int displayPassengerCount(Minecraft client) {
        if (client.player == null) {
            return 0;
        }
        return (int) client.player.getPassengers().stream().filter(entity -> entity instanceof Display).count();
    }

    private static Set<Integer> displayPassengerIds(ClientGameTestContext context) {
        return context.computeOnClient(InteractiveDisplayClientModesGameTest::displayPassengerIds);
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
        return context.computeOnClient(InteractiveDisplayClientModesGameTest::worldDisplayCount);
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
        return context.computeOnClient(client -> {
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
        });
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

    private record ViewState(float yaw, float pitch) {
    }

    private record TransformSnapshot(Vector3f translation, Quaternionf rotation) {
    }
}

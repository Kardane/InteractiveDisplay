package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.api.window.WindowSpec;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.core.interaction.CommandWhitelist;
import com.interactivedisplay.core.layout.MeditateLayoutEngine;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.WindowPositionTracker;
import com.interactivedisplay.core.window.WindowManager;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.DisplayEntityFactory;
import com.interactivedisplay.schema.SchemaLoader;
import com.interactivedisplay.schema.SchemaValidator;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttachedPublicWindowRegistrationTest {
    @Test
    void registrationAfterManagerAttachShouldImmediatelyReachRuntimeDefinitions(@TempDir Path tempDir) {
        WindowManager manager = manager(tempDir);
        InteractiveDisplayApiImpl api = new InteractiveDisplayApiImpl(new CallbackRegistry());
        api.attach(manager);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "after_attach");

        var first = api.windows().register(WindowSpec.builder(id).size(2.0f, 1.0f).build());
        var duplicate = api.windows().register(WindowSpec.builder(id).size(4.0f, 2.0f).build());

        assertTrue(first.success());
        assertTrue(api.windows().registeredIds().contains(id));
        assertTrue(manager.hasDefinition(PublicIdCodec.toInternalWindowId(id)));
        assertFalse(duplicate.success());
    }

    @Test
    void newRuntimeInstanceShouldAllowConsumerToRegisterAgainAfterRestart(@TempDir Path tempDir) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "restart_registration");

        InteractiveDisplayApiImpl firstApi = new InteractiveDisplayApiImpl(new CallbackRegistry());
        firstApi.attach(manager(tempDir.resolve("first")));
        assertTrue(firstApi.windows().register(WindowSpec.builder(id).size(2.0f, 1.0f).build()).success());
        firstApi.detach();

        WindowManager secondManager = manager(tempDir.resolve("second"));
        InteractiveDisplayApiImpl secondApi = new InteractiveDisplayApiImpl(new CallbackRegistry());
        secondApi.attach(secondManager);

        assertTrue(secondApi.windows().register(WindowSpec.builder(id).size(2.0f, 1.0f).build()).success());
        assertTrue(secondManager.hasDefinition(PublicIdCodec.toInternalWindowId(id)));
    }

    private static WindowManager manager(Path tempDir) {
        DebugRecorder debugRecorder = new DebugRecorder(20);
        CoordinateTransformer transformer = new CoordinateTransformer();
        return new WindowManager(
                null,
                new SchemaLoader(tempDir, new SchemaValidator(), debugRecorder),
                new MeditateLayoutEngine(),
                transformer,
                new WindowPositionTracker(transformer),
                new DisplayEntityFactory(debugRecorder),
                debugRecorder,
                new CommandWhitelist(tempDir),
                new CallbackRegistry()
        );
    }
}

package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.api.window.WindowSpec;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollisionAwareInteractiveDisplayApiTest {
    @Test
    void preRuntimeRegistrationShouldRejectConfiguredYamlCollision(@TempDir Path tempDir) throws Exception {
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);
        Files.writeString(windows.resolve("operator-shop.yaml"), """
                id: economy:shop
                size: { width: 3.0, height: 2.0 }
                components: []
                """, StandardCharsets.UTF_8);

        PreRuntimeWindowCollisionIndex index = new PreRuntimeWindowCollisionIndex(tempDir);
        InteractiveDisplayApiImpl delegate = new InteractiveDisplayApiImpl(new CallbackRegistry());
        CollisionAwareInteractiveDisplayApi api = new CollisionAwareInteractiveDisplayApi(delegate, index::contains);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("economy", "shop");

        var result = api.windows().register(WindowSpec.builder(id).size(2.0f, 1.0f).build());

        assertFalse(result.success());
        assertFalse(api.windows().registeredIds().contains(id));
    }

    @Test
    void preRuntimeRegistrationShouldRejectBuiltInCollision(@TempDir Path tempDir) {
        PreRuntimeWindowCollisionIndex index = new PreRuntimeWindowCollisionIndex(tempDir);
        InteractiveDisplayApiImpl delegate = new InteractiveDisplayApiImpl(new CallbackRegistry());
        CollisionAwareInteractiveDisplayApi api = new CollisionAwareInteractiveDisplayApi(delegate, index::contains);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("interactivedisplay", "main_menu");

        var result = api.windows().register(WindowSpec.builder(id).size(2.0f, 1.0f).build());

        assertFalse(result.success());
        assertFalse(api.windows().registeredIds().contains(id));
    }

    @Test
    void preRuntimeRegistrationShouldStillAcceptNonCollidingWindow(@TempDir Path tempDir) {
        PreRuntimeWindowCollisionIndex index = new PreRuntimeWindowCollisionIndex(tempDir);
        InteractiveDisplayApiImpl delegate = new InteractiveDisplayApiImpl(new CallbackRegistry());
        CollisionAwareInteractiveDisplayApi api = new CollisionAwareInteractiveDisplayApi(delegate, index::contains);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("economy", "new_shop");

        var result = api.windows().register(WindowSpec.builder(id).size(2.0f, 1.0f).build());

        assertTrue(result.success());
        assertTrue(api.windows().registeredIds().contains(id));
    }
}

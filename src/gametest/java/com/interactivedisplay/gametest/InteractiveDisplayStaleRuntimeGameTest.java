package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ImageComponentDefinition;
import com.interactivedisplay.core.component.ImageType;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import com.interactivedisplay.core.window.WindowInstance;
import com.interactivedisplay.entity.VirtualWindowHolder;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class InteractiveDisplayStaleRuntimeGameTest implements CustomTestMethodInvoker {
    private static final String WINDOW_ID = "qa_stale_runtime";
    private static final String COMPONENT_ID = "content";

    @SuppressWarnings("removal")
    @GameTest
    public void sameComponentIdShouldNeverReuseStaleRuntimeAcrossDefinitionChanges(GameTestHelper helper) throws Exception {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Path configRoot = FabricLoader.getInstance().getConfigDir().resolve("interactivedisplay");
        Path windows = configRoot.resolve("windows");
        Path windowFile = windows.resolve(WINDOW_ID + ".yaml");
        Files.createDirectories(windows);

        try {
            Files.writeString(windowFile, textYaml("before"), StandardCharsets.UTF_8);
            helper.assertTrue(manager.reloadOne(WINDOW_ID).success(), Component.literal("initial stale-runtime fixture failed to load"));
            var opened = manager.createWindow(player, WINDOW_ID, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            helper.assertTrue(opened.success(), Component.literal("initial stale-runtime fixture failed to open: " + opened.message()));

            WindowInstance current = manager.findActiveWindow(player.getUUID(), WINDOW_ID);
            assertText(helper, current.runtime(COMPONENT_ID), "before");

            current = rewriteAndReload(helper, manager, player, windowFile, current, textYaml("after"),
                    runtime -> assertText(helper, runtime, "after"));
            current = rewriteAndReload(helper, manager, player, windowFile, current, itemYaml(),
                    runtime -> {
                        helper.assertTrue(runtime.definition() instanceof ImageComponentDefinition image && image.imageType() == ImageType.ITEM,
                                Component.literal("ITEM definition did not replace TEXT definition"));
                        helper.assertTrue(runtime.displayElement() instanceof ItemDisplayElement,
                                Component.literal("ITEM runtime did not materialize ItemDisplayElement"));
                    });
            current = rewriteAndReload(helper, manager, player, windowFile, current, blockYaml(),
                    runtime -> {
                        helper.assertTrue(runtime.definition() instanceof ImageComponentDefinition image && image.imageType() == ImageType.BLOCK,
                                Component.literal("BLOCK definition did not replace ITEM definition"));
                        helper.assertTrue(runtime.displayElement() instanceof BlockDisplayElement,
                                Component.literal("BLOCK runtime did not materialize BlockDisplayElement"));
                    });
            current = rewriteAndReload(helper, manager, player, windowFile, current, mapYaml(),
                    runtime -> {
                        helper.assertTrue(runtime.definition() instanceof ImageComponentDefinition image && image.imageType() == ImageType.MAP,
                                Component.literal("MAP definition did not replace BLOCK definition"));
                        helper.assertTrue(runtime.displayElement() instanceof ItemDisplayElement,
                                Component.literal("MAP runtime did not materialize map ItemDisplayElement"));
                        helper.assertTrue(runtime.mapCanvas() != null, Component.literal("MAP runtime did not create a canvas"));
                    });
            current = rewriteAndReload(helper, manager, player, windowFile, current, buttonYaml(),
                    runtime -> {
                        helper.assertTrue(runtime.definition() instanceof ButtonComponentDefinition button && "Reloaded Button".equals(button.label()),
                                Component.literal("BUTTON definition/label did not replace MAP definition"));
                        helper.assertTrue(runtime.displayElement() instanceof TextDisplayElement,
                                Component.literal("BUTTON runtime did not materialize TextDisplayElement"));
                        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).size() == 1,
                                Component.literal("BUTTON reload did not produce exactly one interaction binding"));
                    });
            current = rewriteAndReload(helper, manager, player, windowFile, current, panelYaml(),
                    runtime -> {
                        helper.assertTrue(runtime.definition() instanceof PanelComponentDefinition,
                                Component.literal("PANEL definition did not replace BUTTON definition"));
                        helper.assertTrue(runtime.displayElement() instanceof TextDisplayElement,
                                Component.literal("PANEL runtime did not materialize TextDisplayElement"));
                        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).isEmpty(),
                                Component.literal("stale BUTTON binding survived PANEL reload"));
                    });

            helper.assertTrue(current != null && current.runtime(COMPONENT_ID) != null,
                    Component.literal("final stale-runtime fixture disappeared"));
        } finally {
            manager.removeWindow(player.getUUID(), WINDOW_ID);
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
            Files.deleteIfExists(windowFile);
            manager.reloadAll();
        }

        helper.succeed();
    }

    private static WindowInstance rewriteAndReload(
            GameTestHelper helper,
            com.interactivedisplay.core.window.WindowManager manager,
            ServerPlayer player,
            Path windowFile,
            WindowInstance previous,
            String yaml,
            Consumer<WindowComponentRuntime> assertion
    ) throws Exception {
        var previousHolder = previous.virtualHolder();
        WindowComponentRuntime previousRuntime = previous.runtime(COMPONENT_ID);
        Files.writeString(windowFile, yaml, StandardCharsets.UTF_8);

        var reload = manager.reloadOne(WINDOW_ID);
        helper.assertTrue(reload.success(), Component.literal("reloadOne failed while changing stale-runtime fixture: " + reload.message()));
        WindowInstance next = manager.findActiveWindow(player.getUUID(), WINDOW_ID);
        helper.assertTrue(next != null && next != previous, Component.literal("WindowInstance was reused after definition change"));
        helper.assertTrue(next.virtualHolder() != previousHolder, Component.literal("VirtualWindowHolder was reused after definition change"));
        WindowComponentRuntime nextRuntime = next.runtime(COMPONENT_ID);
        helper.assertTrue(nextRuntime != null && nextRuntime != previousRuntime,
                Component.literal("component runtime object was reused for the same component ID"));
        assertion.accept(nextRuntime);

        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        helper.assertTrue(previousHolder.entityCount() == 0,
                Component.literal("superseded holder retained virtual entities after reload"));
        return next;
    }

    private static void assertText(GameTestHelper helper, WindowComponentRuntime runtime, String expected) {
        helper.assertTrue(runtime.definition() instanceof TextComponentDefinition text && expected.equals(text.content()),
                Component.literal("TEXT definition content mismatch: expected " + expected));
        helper.assertTrue(runtime.displayElement() instanceof TextDisplayElement textElement
                        && expected.equals(textElement.getText().getString()),
                Component.literal("TEXT display content mismatch: expected " + expected));
    }

    private static String base(String component) {
        return """
                id: qa_stale_runtime
                size: { width: 3.0, height: 2.0 }
                components:
                %s
                """.formatted(component.indent(2));
    }

    private static String textYaml(String content) {
        return base("""
                - id: content
                  type: text
                  position: { x: 0.0, y: 0.0, z: 0.0 }
                  width: 1.5
                  height: 0.4
                  content: %s
                """.formatted(content));
    }

    private static String itemYaml() {
        return base("""
                - id: content
                  type: image
                  position: { x: 0.0, y: 0.0, z: 0.0 }
                  size: { width: 1.0, height: 1.0 }
                  imageType: item
                  value: minecraft:diamond
                  scale: 1.0
                """);
    }

    private static String blockYaml() {
        return base("""
                - id: content
                  type: image
                  position: { x: 0.0, y: 0.0, z: 0.0 }
                  size: { width: 1.0, height: 1.0 }
                  imageType: block
                  value: minecraft:stone
                  scale: 1.0
                """);
    }

    private static String mapYaml() {
        return base("""
                - id: content
                  type: image
                  position: { x: 0.0, y: 0.0, z: 0.0 }
                  size: { width: 1.0, height: 1.0 }
                  imageType: map
                  value: sample_local.png
                  scale: 1.0
                """);
    }

    private static String buttonYaml() {
        return base("""
                - id: content
                  type: button
                  position: { x: 0.0, y: 0.0, z: 0.0 }
                  size: { width: 1.4, height: 0.35 }
                  label: Reloaded Button
                  clickType: both
                  action:
                    type: close_window
                """);
    }

    private static String panelYaml() {
        return base("""
                - id: content
                  type: panel
                  position: { x: 0.0, y: 0.0, z: 0.0 }
                  size: { width: 2.0, height: 1.0 }
                  backgroundColor: '#88000000'
                  children: []
                """);
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}

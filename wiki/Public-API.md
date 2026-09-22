# Public API

InteractiveDisplay API v1 is a server-side integration API for other Fabric mods. The supported compatibility boundary is:

    com.interactivedisplay.api.*

Do not import com.interactivedisplay.core, entity, schema, polymer, or internal packages from a consumer mod.

## Consumer mod metadata

Add InteractiveDisplay as a dependency and register the custom entrypoint:

    {
      "depends": {
        "interactivedisplay": ">=1.1.0"
      },
      "entrypoints": {
        "interactivedisplay": [
          "com.example.ExampleDisplayIntegration"
        ]
      }
    }

The extension entrypoint is for registration. Runtime operations should be invoked after a Minecraft server is ready.

## Register a window

    package com.example;

    import com.interactivedisplay.api.InteractiveDisplayEntrypoint;
    import com.interactivedisplay.api.InteractiveDisplayRegistrar;
    import com.interactivedisplay.api.window.WindowSpec;
    import net.minecraft.resources.ResourceLocation;

    public final class ExampleDisplayIntegration implements InteractiveDisplayEntrypoint {
        private static final ResourceLocation STATUS =
                ResourceLocation.fromNamespaceAndPath("example", "status");
        private static final ResourceLocation CLOSE =
                ResourceLocation.fromNamespaceAndPath("example", "close");

        @Override
        public void register(InteractiveDisplayRegistrar registrar) {
            registrar.callbacks().register(CLOSE, context -> {
                context.windows().close(context.player(), context.windowId());
            });

            registrar.windows().register(
                    WindowSpec.builder(STATUS)
                            .size(3.0f, 1.5f)
                            .offset(2.0f, 0.0f, 0.4f)
                            .text("title", text -> text
                                    .position(0.0f, 0.35f, 0.01f)
                                    .size(2.5f, 0.35f)
                                    .content("Status: online")
                                    .refreshInterval(20))
                            .button("close", button -> button
                                    .position(0.0f, -0.45f, 0.01f)
                                    .size(1.0f, 0.35f)
                                    .label("Close")
                                    .click(WindowSpec.Click.BOTH)
                                    .action(WindowSpec.Actions.callback(CLOSE)))
                            .build()
            );
        }
    }

Always inspect RegistrationResult. A false result means that the definition was not registered.

## Open a registered window

    import com.interactivedisplay.api.InteractiveDisplayApi;
    import com.interactivedisplay.api.window.WindowOpenOptions;

    var result = InteractiveDisplayApi.get()
            .windows()
            .open(player, STATUS, WindowOpenOptions.playerView());

    if (!result.success()) {
        // Handle result.reason() and result.message().
    }

Available window options are:

- WindowOpenOptions.playerView()
- WindowOpenOptions.playerFixed()
- WindowOpenOptions.fixed(anchor, yaw, pitch)

Before the runtime attaches, open returns the reason runtime_not_ready. An unknown definition returns window_not_found.

## Existing YAML groups

API v1 can open an existing YAML group:

    var result = InteractiveDisplayApi.get()
            .groups()
            .open(player, SHOP_GROUP, GroupOpenOptions.playerView());

GroupApi supports open, close, isOpen, find, and GroupHandle.close(). It does not currently create group definitions programmatically.

## Callbacks and custom actions

Callbacks are useful for button behavior that is local to the consumer mod:

    registrar.callbacks().register(CLOSE, context -> {
        context.windows().close(context.player(), context.windowId());
    });

Custom actions can carry immutable string parameters:

    var buy = ResourceLocation.fromNamespaceAndPath("economy", "buy");
    registrar.actions().register(buy, context -> {
        String product = context.parameters().get("product");
        // Run the consumer mod's server-side purchase logic.
    });

For programmatic buttons, bind the action:

    button.action(registrar.actions().bind(
            buy,
            Map.of("product", "diamond_sword")
    ));

The same namespaced action can be used from YAML.

## Lifecycle events

EventApi supports removable subscriptions:

    var opened = registrar.events().onWindowOpened(event -> {
        // event.ownerId(), event.windowId(), event.mode()
    });

    var clicked = registrar.events().onButtonClicked(event -> {
        // event.ownerId(), event.windowId(), event.componentId()
    });

    opened.close();
    clicked.close();

Listener exceptions are isolated. Internal reload and rebuild maintenance does not emit public open/close events.

## ID policy

Use the consumer mod's namespace for windows, groups, callbacks, and actions:

    economy:shop
    economy:shop_group
    economy:buy

The built-in namespace interactivedisplay is reserved for InteractiveDisplay's own definitions. A YAML/Java window ID collision is rejected instead of silently replacing the existing definition.

## API v1 exclusions

The following are intentionally outside the current stable surface:

- programmatic group definitions
- MAP-canvas construction
- bundled MAP assets
- direct component mutation or an active-window update API
- a separate API-only Maven artifact

Use YAML, Placeholder API refresh, callbacks, and custom actions within these limits.

# InteractiveDisplay Public API v1

InteractiveDisplay 1.1.0 introduces a server-side API for other Fabric mods without exposing its Polymer, entity, schema, or runtime implementation classes. API v1 targets Minecraft 1.21.8.

## Consumer dependency

Declare InteractiveDisplay as a mod dependency so its API bootstrap runs before your integration:

```json
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
```

Use `com.interactivedisplay.api.*` as the supported source/binary compatibility boundary. Do not depend on `core`, `entity`, `schema`, `polymer`, or `internal` packages.

## Register a window and callback

```java
package com.example;

import com.interactivedisplay.api.InteractiveDisplayEntrypoint;
import com.interactivedisplay.api.InteractiveDisplayRegistrar;
import com.interactivedisplay.api.window.WindowSpec;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class ExampleDisplayIntegration implements InteractiveDisplayEntrypoint {
    private static final ResourceLocation STATUS = id("example", "status");
    private static final ResourceLocation CLOSE_CALLBACK = id("example", "close_callback");
    private static final ResourceLocation PURCHASE = id("example", "purchase");

    @Override
    public void register(InteractiveDisplayRegistrar registrar) {
        registrar.callbacks().register(CLOSE_CALLBACK, context -> {
            context.windows().close(context.player(), context.windowId());
        });

        registrar.actions().register(PURCHASE, context -> {
            String product = context.parameters().get("product");
            // Run consumer-mod purchase logic here.
        });

        registrar.events().onButtonClicked(event -> {
            // Observe InteractiveDisplay button clicks without touching runtime internals.
        });

        registrar.windows().register(
                WindowSpec.builder(STATUS)
                        .size(3.0f, 1.5f)
                        .offset(2.0f, 0.0f, 0.4f)
                        .transition(5, WindowSpec.TransitionType.SCALE, WindowSpec.TransitionType.SCALE)
                        .panel("background", panel -> panel
                                .position(0.0f, 0.0f, 0.0f)
                                .size(3.0f, 1.5f)
                                .background("#88000000"))
                        .text("title", text -> text
                                .position(0.0f, 0.35f, 0.01f)
                                .size(2.5f, 0.35f)
                                .content("Hello %player:name%")
                                .refreshInterval(20))
                        .button("buy", button -> button
                                .position(0.0f, -0.20f, 0.01f)
                                .size(1.2f, 0.35f)
                                .label("Buy")
                                .action(registrar.actions().bind(
                                        PURCHASE,
                                        Map.of("product", "diamond_sword")
                                )))
                        .button("close", button -> button
                                .position(0.0f, -0.55f, 0.01f)
                                .size(1.0f, 0.35f)
                                .label("Close")
                                .click(WindowSpec.Click.BOTH)
                                .action(WindowSpec.Actions.callback(CLOSE_CALLBACK)))
                        .build()
        );
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
```

Public API window, callback, and action IDs are namespaced. `interactivedisplay:main_menu` is adapted to the legacy built-in `main_menu` YAML ID; IDs from other namespaces stay canonical, for example `example:status`.

## Open a window at runtime

```java
import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.api.window.WindowOpenOptions;

InteractiveDisplayApi.get()
        .windows()
        .open(player, STATUS, WindowOpenOptions.playerView());
```

Other supported open modes:

```java
WindowOpenOptions.playerFixed();
WindowOpenOptions.fixed(anchor, yaw, pitch);
```

`WindowApi.find(...)` returns a restricted `WindowHandle`; it never exposes `WindowInstance`, Polymer virtual elements, or other internal runtime types.

## Programmatic window components

API v1 supports:

- text, including placeholder refresh intervals
- buttons with left/right/both click policies
- panel backgrounds
- item displays
- block displays
- close, open-window, callback, run-command, and bound custom-action button actions
- enter/exit transitions

Programmatic registrations are kept separately by `WindowManager` and are restored after normal YAML reloads. A programmatic definition cannot replace an already-loaded definition with the same internal ID.

## Bundled YAML windows

A consumer mod can ship default UI definitions inside its own JAR:

```text
data/<modid>/interactivedisplay/windows/*.yaml
```

Example for mod id `economy`:

```text
data/economy/interactivedisplay/windows/shop.yaml
```

The YAML `id` must already be namespaced with the owning mod id:

```yaml
id: economy:shop
size:
  width: 3.0
  height: 2.0
components:
  - id: title
    type: text
    position: { x: 0.0, y: 0.0, z: 0.0 }
    content: Economy Shop
```

On first startup InteractiveDisplay copies bundled definitions into `config/interactivedisplay/windows/` using a `<modid>__<filename>.yaml` name. Existing target files are never overwritten, so server operators can edit the installed copy directly. Bundled API v1 discovery covers window YAML files; groups and bundled MAP assets remain follow-up work.

## Custom actions

Custom actions are namespaced handlers registered by another mod. `ActionApi.bind(...)` binds immutable string parameters to a button while reusing InteractiveDisplay's existing callback/click pipeline.

```java
ResourceLocation BUY = ResourceLocation.fromNamespaceAndPath("economy", "buy");

registrar.actions().register(BUY, context -> {
    String product = context.parameters().get("product");
    buy(context.player(), product);
});

button.action(registrar.actions().bind(BUY, Map.of("product", "diamond_sword")));
```

Handlers can be registered before the Minecraft server runtime is ready. The bound action resolves the current handler when clicked, so registration and window declaration order do not need to match.

## Lifecycle events

`EventApi` provides removable subscriptions for public window lifecycle and button-click observation:

```java
var opened = registrar.events().onWindowOpened(event -> {
    event.ownerId();
    event.windowId();
    event.mode();
});

var clicked = registrar.events().onButtonClicked(event -> {
    event.ownerId();
    event.windowId();
    event.componentId();
});

// Optional cleanup if your integration has its own lifecycle.
opened.close();
clicked.close();
```

`WINDOW_OPENED` / `WINDOW_CLOSED` are emitted for public API opens/closes and successful UI `open_window` / `close_window` navigation. Button-click events are emitted when a valid UI hit reaches the click handler. Internal rebuild/reload maintenance does not emit lifecycle events.

## Lifecycle

Callback/window/action/event registration is allowed during the `interactivedisplay` extension entrypoint, before the Minecraft server runtime is ready. Runtime operations such as `open` return `runtime_not_ready` until `WindowManager` has been attached.

The extension API is published through Fabric Loader ObjectShare under `interactivedisplay:api`; `InteractiveDisplayApi.get()` is the supported lookup method for runtime use.

## Deliberately not exposed in API v1

The first stable boundary does not yet expose programmatic groups, MAP-canvas construction, bundled groups/MAP assets, or a separate API-only Maven artifact. Those can be added as backward-compatible API extensions without exposing the current rendering implementation.

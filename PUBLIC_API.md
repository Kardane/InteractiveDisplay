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
import net.minecraft.resources.ResourceLocation;

public final class ExampleDisplayIntegration implements InteractiveDisplayEntrypoint {
    private static final ResourceLocation STATUS = id("example", "status");
    private static final ResourceLocation CLOSE_CALLBACK = id("example", "close_callback");

    @Override
    public void register(InteractiveDisplayRegistrar registrar) {
        registrar.callbacks().register(CLOSE_CALLBACK, context -> {
            context.windows().close(context.player(), context.windowId());
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
                        .button("close", button -> button
                                .position(0.0f, -0.45f, 0.01f)
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

Public API window IDs are namespaced. `interactivedisplay:main_menu` is adapted to the legacy built-in `main_menu` YAML ID; IDs from other namespaces stay canonical, for example `example:status`.

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
- close, open-window, callback, and run-command button actions
- enter/exit transitions

Programmatic registrations are kept separately by `WindowManager` and are restored after normal YAML reloads. A programmatic definition cannot replace an already-loaded definition with the same internal ID.

## Lifecycle

Callback/window registration is allowed during the `interactivedisplay` extension entrypoint, before the Minecraft server runtime is ready. Runtime operations such as `open` return `runtime_not_ready` until `WindowManager` has been attached.

The extension API is published through Fabric Loader ObjectShare under `interactivedisplay:api`; `InteractiveDisplayApi.get()` is the supported lookup method for runtime use.

## Deliberately not exposed in API v1

The first stable boundary does not yet expose programmatic groups, MAP-canvas construction, custom action types, lifecycle events, or bundled YAML discovery from consumer JARs. Those can be added as backward-compatible API extensions without exposing the current rendering implementation.

# Compatibility and Limitations

## Supported baseline

| Area | Current baseline |
| --- | --- |
| Minecraft | 1.21.8 |
| Java | 21 |
| Fabric Loader | 0.18.0 or newer |
| Fabric API | 0.136.0+1.21.8 |
| Polymer | 0.13.13+1.21.8 family |
| API style | Server-side Fabric custom entrypoint and ObjectShare |

Treat the release metadata as authoritative when it differs from this page.

## What consumer mods can use today

| Capability | Status |
| --- | --- |
| YAML windows | Supported |
| YAML groups | Supported |
| Programmatic windows | Supported for text, buttons, panels, items, and blocks |
| Window open/close/find | Supported |
| Group open/close/find | Supported for existing YAML groups |
| Callbacks | Supported |
| Namespaced custom actions | Supported |
| Lifecycle events | Supported |
| Placeholder refresh | Supported |
| MAP images | YAML/runtime path, FIXED windows only |
| Programmatic MAP images | Not exposed in API v1 |
| Programmatic group builder | Not exposed in API v1 |
| Direct active-window component updates | Not exposed |
| API-only Maven artifact | Not currently provided |

## Rendering and client boundaries

InteractiveDisplay is designed for a server-side Fabric deployment. Consumer mods do not need to depend on Polymer types in their own source code, but the server and client still need a compatible Polymer resource-pack flow for the UI to render correctly.

The pointer item is part of the interaction contract. A consumer mod should either document how players obtain it or provide its own server-side onboarding.

## Versioning

The public source boundary is com.interactivedisplay.api.*. Do not compile against core, entity, schema, polymer, or internal classes.

API v1 uses Minecraft/Fabric server types such as ServerPlayer, ResourceLocation, and Vec3. It is therefore not a provider-neutral UI API and should be version-tested with each Minecraft baseline supported by the consumer mod.

## Current confidence level

The repository contains unit tests, GameTests, CI build configuration, API boundary tests, and collision tests. These are useful regression protections, but a consumer mod should still run its own dedicated-server and graphical-client smoke tests.

The remaining high-value validation areas are:

- independent consumer Fabric mod build and entrypoint execution;
- two consumer mods registering different and colliding IDs;
- all public API open modes;
- callback and custom-action invocation;
- owner-only visibility with two real clients;
- PLAYER_FIXED and PLAYER_VIEW movement and click alignment;
- dimension and vehicle lifecycle;
- repeated open, close, reload, and multi-player soak tests.

## When to choose InteractiveDisplay

Choose it when the project needs a server-authoritative, owner-scoped 3D UI and can accept the pointer-item interaction model.

Reconsider it when the project requires a client-native GUI, a large mutable component tree, full programmatic group composition, or a published dependency artifact with a stable cross-version compatibility promise.

# InteractiveDisplay Wiki

InteractiveDisplay is a server-side 3D HUD and window framework for Minecraft 1.21.8 Fabric.

Windows are defined with YAML or registered through the stable Java API. Rendering uses owner-only Polymer virtual entities, so a player's private UI is not sent to nearby players as ordinary world entities.

## Choose a path

- [Getting Started](Getting-Started) — install the mod, start a server, open a sample window, and verify the pointer item.
- [YAML Configuration](YAML-Configuration) — define windows, groups, components, actions, and position modes.
- [Public API](Public-API) — integrate InteractiveDisplay from another Fabric mod.
- [Bundled Definitions](Bundled-Definitions) — ship default YAML windows and groups inside a consumer mod JAR.
- [Operations and Troubleshooting](Operations-and-Troubleshooting) — reload, inspect, and diagnose server or client-side issues.
- [Compatibility and Limitations](Compatibility-and-Limitations) — version policy, API boundaries, and known gaps.

## At a glance

- Target Minecraft: 1.21.8
- Target Java: 21
- Runtime environment: dedicated server
- Position modes: FIXED, PLAYER_FIXED, and PLAYER_VIEW
- Components: text, buttons, panels, item displays, block displays, and MAP images in FIXED windows
- Interaction: server-side raycast while the InteractiveDisplay pointer is held in the main hand
- Integration: Fabric custom entrypoint, ObjectShare API, callbacks, custom actions, and lifecycle events

## Support boundary

Consumer mods should use only the package com.interactivedisplay.api.*. The core, entity, schema, polymer, and internal packages are implementation details and are not a compatibility boundary.

This wiki describes API v1 and the current 1.21.8 codebase. Always verify that the JAR, README, and wiki describe the same release before distributing a consumer mod.

## Important limitations

API v1 does not currently expose programmatic group builders, MAP-canvas construction, bundled MAP assets, or a separate API-only Maven artifact. See [Compatibility and Limitations](Compatibility-and-Limitations) before choosing InteractiveDisplay as a hard dependency.

## Project documentation

- [README](https://github.com/Kardane/InteractiveDisplay/blob/master/README.md)
- [Public API reference](https://github.com/Kardane/InteractiveDisplay/blob/master/PUBLIC_API.md)
- [Build and test guide](https://github.com/Kardane/InteractiveDisplay/blob/master/docs/BUILD_AND_TEST_GUIDE.md)

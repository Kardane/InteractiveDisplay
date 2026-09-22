# Operations and Troubleshooting

## Useful commands

List loaded windows:

    interactivedisplay list

List groups:

    interactivedisplay group list

Reload all YAML definitions:

    interactivedisplay reload

Reload one window:

    interactivedisplay reload <windowId>

Inspect runtime state:

    interactivedisplay debug status
    interactivedisplay debug recent [player]
    interactivedisplay debug window <windowId> <player>
    interactivedisplay debug bindings <player>

## The window is not visible

Check in this order:

1. The file is under config/interactivedisplay/windows/ and ends in .yaml.
2. The YAML ID is unique and matches the command or API ID.
3. The server log has no schema validation error.
4. The player is in the correct dimension for a FIXED window.
5. The player accepted the Polymer server resource pack.
6. The player has the InteractiveDisplay pointer in the main hand.
7. The window uses a supported position mode for its components.

For a MAP component, confirm that the window is FIXED. PLAYER_FIXED and PLAYER_VIEW MAP creation is rejected rather than silently converted to an item display.

## The button does not click

Confirm all of the following:

- the pointer is in the main hand;
- the player is looking at the visible button;
- the raycast reaches the same surface that is rendered;
- the button clickType accepts the input;
- a command action passes command_whitelist.yaml;
- a callback or custom action is registered before the click occurs;
- the action target is in the same namespace used by the definition.

For a left-click path, remember that Minecraft may emit more than one related packet path in the same tick. InteractiveDisplay includes same-tick handling to avoid duplicate execution, but verify the exact behavior when integrating a consumer action with side effects.

## Runtime API returns runtime_not_ready

Registration is allowed during the interactivedisplay extension entrypoint, before the WindowManager is attached. Runtime operations such as open and close must wait until the server runtime is ready.

Use the registrar passed to InteractiveDisplayEntrypoint for registration. Use InteractiveDisplayApi.get() for later runtime operations.

## YAML reload failed

InteractiveDisplay validates YAML before replacing the active definition. A failed focused reload should keep the previous valid definition active. Inspect the reported source path and validation message, correct the file, and run the focused reload again.

Avoid editing a file while another tool is writing it. Use quoted color values and check duplicate component IDs, duplicate window IDs, component size, action fields, and group references.

## Distinguishing server proof from client proof

The following prove server or API state only:

- Gradle build success;
- unit tests;
- GameTest success;
- loaded and broken definition counts;
- API registration results;
- fake-player open/close results.

They do not prove that a real vanilla client rendered the UI correctly. Client verification must separately cover resource-pack acceptance, owner-only visibility, movement, view rotation, hover, click alignment, vehicles, and dimension changes.

## Performance watch points

Each server tick processes active owners, their active windows, and window components. Dynamic Placeholder refresh, MAP canvas synchronization, and multiple PLAYER_VIEW windows increase the work per tick.

Before deploying a large UI workload, test the actual player and window counts used by the server. Do not infer large-server readiness from a single-player GameTest.

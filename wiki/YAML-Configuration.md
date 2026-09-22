# YAML Configuration

InteractiveDisplay loads YAML files from:

    config/interactivedisplay/windows/
    config/interactivedisplay/groups/

Only files ending in .yaml are loaded. Legacy JSON and .yml files are not a supported window or group format.

## Minimal window

    id: example:status
    size:
      width: 3.0
      height: 1.5
    offset:
      forward: 2.0
      horizontal: 0.0
      vertical: 0.4
    components:
      - id: title
        type: text
        position:
          x: 0.0
          y: 0.35
          z: 0.01
        size:
          width: 2.5
          height: 0.35
        content: "Status: <placeholder>"
        refreshInterval: 20

      - id: close
        type: button
        position:
          x: 0.0
          y: -0.45
          z: 0.01
        size:
          width: 1.0
          height: 0.35
        label: "Close"
        clickType: both
        action:
          type: close_window

Window IDs should be namespaced when they are owned by another mod. For example, a shop mod should use example:shop rather than a global shop ID.

Color values containing # must be quoted because YAML treats # as a comment marker.

## Components

Supported component types are:

- text
- button
- text_input
- panel
- image

Image components support item, block, and map image types. MAP components are supported only in FIXED windows.

Panels may contain nested child components in YAML. Programmatic Java WindowSpec currently does not expose the same nested child builder.

## Text input

`text_input` renders as an interactive text field in the display. Clicking it opens Minecraft's native dialog text input and submitting the dialog updates the runtime value for that player.

    - id: search
      type: text_input
      position:
        x: 0.0
        y: 0.0
        z: 0.01
      size:
        width: 2.0
        height: 0.35
      placeholder: "Search..."
      initialValue: ""
      maxLength: 64
      clickType: right
      dialogTitle: "Search"
      dialogLabel: "Query"
      confirmLabel: "Apply"
      cancelLabel: "Cancel"

The runtime value is player-local and lasts for the lifetime of the active window. Closing and reopening the window resets it to `initialValue`. Submissions are exposed through `EventApi.onTextInputSubmitted`.

## Buttons and actions

Built-in actions include:

- close_window
- open_window
- switch_mode_fixed
- switch_mode_player_fixed
- switch_mode_player_view
- toggle_placement_tracking
- run_command
- callback

Button input policies are left, right, and both. The default interaction path requires the pointer item in the main hand.

run_command actions must pass the command whitelist in:

    config/interactivedisplay/command_whitelist.yaml

Keep the whitelist narrow, especially when using a permissionLevel override.

## Custom actions

Another Fabric mod can register a namespaced action such as economy:buy. YAML may then reference it:

    action:
      type: economy:buy
      product: diamond_sword
      amount: 2

Custom-action parameters must be scalar YAML values. InteractiveDisplay exposes them to the handler as immutable strings. Nested objects and arrays are rejected.

## Groups

A group defines an initial window and the navigation relationship between several windows:

    id: economy:shop_group
    initialWindowId: economy:shop
    defaultMode: player_view
    windows:
      - windowId: economy:shop

Group window references should use fully namespaced IDs. A group can be opened through a command or through GroupApi, but API v1 does not provide a programmatic group-definition builder.

## Position modes

### FIXED

The window is attached to a world position and dimension. MAP components are supported in this mode.

### PLAYER_FIXED

The window follows the owner as a virtual passenger while preserving its configured orientation.

### PLAYER_VIEW

The window follows the owner's position and view rotation. It is intended for owner-bound HUDs.

Player-bound windows migrate across dimensions. A FIXED window is removed when its owner leaves the window's dimension.

## Reload

Reload all definitions:

    interactivedisplay reload

Reload one window definition:

    interactivedisplay reload example:status

A failed focused reload preserves the previously valid definition. Programmatic windows are restored across normal YAML reloads, but an operator YAML definition cannot be silently replaced by a programmatic definition with the same ID.

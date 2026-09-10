# InteractiveDisplay

`InteractiveDisplay` is a server-side 3D HUD/window system for Minecraft **1.21.8 Fabric**. Windows are defined with YAML and rendered as **owner-only Polymer Virtual Entities**, so opening a UI does not add vanilla `Display` entities to the world and nearby players do not receive another player's HUD.

## Features

- YAML-only window and group configuration
- `FIXED`, `PLAYER_FIXED`, `PLAYER_VIEW` position modes
- owner-only Polymer Virtual Entity rendering
- text, button, panel, item, block, and map-image components
- left/right/both button click modes
- server-side pointer + raycast interaction
- dynamic Placeholder API text refresh
- enter/exit window transitions and hover scaling
- focused single-window reload
- owner-bound window migration across dimension changes
- command whitelist and Fabric permissions integration
- Polymer resource-pack autohost/bootstrap
- debug status, event history, and binding inspection

## Runtime model

### Visibility

Each window has an owner-filtered `ElementHolder`. Only the owner is allowed to watch its virtual elements.

- `FIXED` uses a Polymer `ManualAttachment` at a world position.
- `PLAYER_FIXED` uses an `EntityAttachment` on the owning player and virtual passenger elements.
- `PLAYER_VIEW` also uses player passengers; player translation is inherited from the passenger relationship while yaw/pitch changes update the relative display transformation.

Polymer injects virtual passenger IDs into passenger packets only for connections that are watching the corresponding holder. InteractiveDisplay does not maintain a parallel ride-packet registry.

`PLAYER_FIXED` and `PLAYER_VIEW` are player-bound HUD modes. When the owner changes dimensions they are rebuilt in the new `ServerLevel`. `FIXED` remains world-bound and is removed when the owner leaves its dimension.

### Interaction

InteractiveDisplay does not create Polymer interaction entities. The server computes the hovered button from the owner's eye position, view vector, window transform, and button hit box.

A click is consumed only when:

1. the player holds the InteractiveDisplay pointer in the main hand;
2. the server raycast hits a button; and
3. the button's `clickType` accepts that input.

Supported values are `left`, `right`, and `both`. Left-click packet paths are de-duplicated per server tick so a single physical click does not execute the action twice when Minecraft emits both attack and swing-related packets.

## Requirements

- Minecraft `1.21.8`
- Java `21`
- Fabric Loader `0.18.0`
- Fabric API `0.136.0+1.21.8`
- Polymer `0.13.13+1.21.8`

The Gradle build includes the required server-side dependencies.

## Build and run

```bash
./gradlew build
./gradlew runServer
```

Windows:

```bat
gradlew.bat build
gradlew.bat runServer
```

CI uses Java 21 and runs the full Gradle build, including unit tests and Fabric Loom remapping.

## Configuration

Runtime configuration is stored under:

```text
run/config/interactivedisplay/
├── command_whitelist.yaml
├── groups/
│   └── menu_group.yaml
├── images/
│   └── sample_local.png
└── windows/
    ├── gallery.yaml
    ├── gallery_remote.example.yaml.disabled
    ├── main_menu.yaml
    └── main_menu2.yaml
```

Defaults bundled in the mod are copied only when the corresponding config file does not already exist.

InteractiveDisplay reads `.yaml` files only. `.yml` and legacy `.json` window/group files are not loaded. Legacy JSON files are only reported as warnings.

### Window example

```yaml
id: main_menu
size:
  width: 3.0
  height: 2.0
offset:
  forward: 2.0
  horizontal: 0.0
  vertical: 0.5
transition:
  duration: 5
  enter: scale
  exit: scale
components:
  - id: title
    type: text
    position:
      x: 0.0
      y: 0.4
      z: 0.01
    size:
      width: 2.5
      height: 0.4
    content: "InteractiveDisplay"
    fontSize: 0.7
    refreshInterval: 20
    color: "#FFFFFF"
    alignment: center

  - id: close
    type: button
    position:
      x: 0.0
      y: -0.5
      z: 0.01
    size:
      width: 0.5
      height: 0.35
    label: "Close"
    clickType: both
    hoverScale: 1.08
    backgroundColor: "#AA992222"
    hoverColor: "#EECC4444"
    action:
      type: close_window
```

### Window fields

| Field | Description |
| --- | --- |
| `id` | Unique window ID |
| `size.width`, `size.height` | Window reference size |
| `offset.forward` | Distance forward from the placement reference |
| `offset.horizontal` | Horizontal offset |
| `offset.vertical` | Vertical offset |
| `layout` | `absolute`, `vertical`, or `horizontal` |
| `components` | Component definitions |
| `transition` | Optional enter/exit transition configuration |

### Transitions

```yaml
transition:
  duration: 6
  enter: slide_up
  exit: scale
```

Supported transition types:

- `none`
- `scale`
- `slide_up`
- `slide_down`

`duration` is measured in server ticks. Exit transitions keep the virtual holder alive until the animation duration completes, then Polymer destroys it.

### Text

```yaml
- id: status
  type: text
  position:
    x: 0.0
    y: 0.0
    z: 0.01
  size:
    width: 2.0
    height: 0.4
  content: "Current value: <placeholder>"
  fontSize: 0.5
  refreshInterval: 20
  color: "#FFFFFF"
  background: "#00000000"
```

`refreshInterval` controls Placeholder API re-resolution:

- `0` or omitted: resolve at creation time only;
- positive integer: re-resolve every N server ticks;
- the `TextDisplayElement` is updated only when the resolved `Component` changed.

The exact placeholder syntax and available placeholders depend on Placeholder API and installed integrations.

YAML treats `#` as a comment marker, so color values must be quoted.

### Buttons

```yaml
- id: action
  type: button
  position:
    x: 0.0
    y: -0.4
    z: 0.01
  size:
    width: 1.2
    height: 0.35
  label: "Action"
  clickType: left
  hoverScale: 1.05
  backgroundColor: "#88000000"
  hoverColor: "#AAFFFFFF"
  clickSound: "minecraft:ui.button.click"
  action:
    type: callback
    id: example
```

`clickType` values:

| Value | Input |
| --- | --- |
| `left` | attack/left click only |
| `right` | use/right click only |
| `both` | either input |

`hoverScale` must be greater than `0`; `1.0` disables scale change. Hover color and scale are interpolated through the virtual display tracked data.

### Images

| `imageType` | `value` |
| --- | --- |
| `item` | Minecraft item ID such as `minecraft:diamond` |
| `block` | Minecraft block ID such as `minecraft:stone` |
| `map` | local file in `config/interactivedisplay/images/` or supported remote URL |

MAP images are decoded server-side, drawn to Map Canvas, and shown through an `ItemDisplayElement`. Config images are **not** copied into Polymer's resource-pack texture tree; the old full-directory image export step has been removed.

Remote map images use a bounded cache. Fresh cache entries are used before HTTP, and stale entries can be used as fallback if refresh fails.

### Panels

Panels are display-backed visual containers and can contain nested components. Supported child layout modes are `absolute`, `vertical`, and `horizontal`.

## Groups

```yaml
id: menu_group
initialWindowId: main_menu
defaultMode: player_fixed
windows:
  - windowId: main_menu
    offset:
      forward: 2.0
      horizontal: 0.0
      vertical: 0.5
    orbit:
      yaw: 0.0
      pitch: 0.0
```

Groups provide navigation between multiple windows while retaining group placement information.

## Button actions

Supported action types:

| Action | Fields |
| --- | --- |
| `close_window` | none |
| `open_window` | `target` |
| `switch_mode_fixed` | none |
| `switch_mode_player_fixed` | none |
| `toggle_placement_tracking` | none |
| `run_command` | `command`, optional `permissionLevel` (`0`-`4`) |
| `callback` | `id` |

## Command whitelist

`run_command` actions must pass `config/interactivedisplay/command_whitelist.yaml`.

```yaml
allowedPrefixes:
  - "say"
  - "title"
  - "tellraw"
```

A configured entry must match the command boundary. For example, `say` accepts `say hello` but does not accept a different command whose name merely begins with `say`.

Keep this list minimal, especially when an action uses a permission-level override.

## Commands

```mcfunction
/interactivedisplay create <windowId> <player> fixed [x y z]
/interactivedisplay create <windowId> <player> player_fixed [yaw pitch]
/interactivedisplay create <windowId> <player> player_view [yaw pitch]
/interactivedisplay remove <windowId> <player>
/interactivedisplay reload
/interactivedisplay reload <windowId>
/interactivedisplay list

/interactivedisplay group create <groupId> <player> fixed [x y z]
/interactivedisplay group create <groupId> <player> player_fixed [yaw pitch]
/interactivedisplay group create <groupId> <player> player_view [yaw pitch]
/interactivedisplay group remove <groupId> <player>
/interactivedisplay group list

/interactivedisplay debug status
/interactivedisplay debug recent [player]
/interactivedisplay debug window <windowId> <player>
/interactivedisplay debug bindings <player>
```

`/interactivedisplay reload <windowId>` performs a focused load of that window file instead of parsing every window and group. A failed focused reload leaves the previously loaded valid definition in place.

## Position modes

### `FIXED`

The window is bound to the world/dimension. It uses `ManualAttachment` and does not follow the player. If its owner changes dimension, that active fixed window is removed.

### `PLAYER_FIXED`

The window is a virtual passenger of the owner. Translation follows player movement client-side through the passenger relationship. The configured yaw/pitch remain fixed relative to the selected placement.

### `PLAYER_VIEW`

The window is also a virtual passenger. Player movement is inherited through the passenger relationship while the server updates the relative transform when the owner changes view yaw/pitch.

Both player-bound modes migrate automatically when the owner changes dimension.

## Resource pack

Polymer resource-pack bootstrap remains responsible for mod assets and autohost. InteractiveDisplay no longer performs a destructive full export/re-encode of `config/interactivedisplay/images`, because current ITEM/BLOCK/MAP render paths do not consume those exported textures.

## Development and validation

Before merging runtime changes, run:

```bash
./gradlew build --stacktrace
```

This covers Java compilation, unit tests, and Loom remapping in CI.

Recommended runtime smoke checks:

- two players: A's UI must not be visible to B;
- `PLAYER_FIXED`: walk, sprint, jump, crouch;
- `PLAYER_VIEW`: move while rotating view quickly;
- Overworld/Nether/End transition for player-bound windows;
- fixed-window removal on dimension change;
- left/right/both click buttons;
- dynamic text refresh;
- enter/exit transition and hover scale;
- multiple simultaneous player-attached windows;
- vehicle/passenger state;
- MAP images and click alignment while moving.

Additional Polymer architecture notes are in `POLYMER_VIRTUAL_ENTITY_NOTES.md`.

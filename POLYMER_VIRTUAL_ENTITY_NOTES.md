# Polymer Virtual Entity architecture

InteractiveDisplay renders its 3D HUD with Polymer Virtual Entity. It does not add vanilla `Display` entities to the server world.

## Visibility contract

- Every window uses an owner-filtered `ElementHolder`.
- Only the owning player's connection is accepted by `ElementHolder#startWatching`.
- Nearby players do not receive the virtual display elements.
- MAP canvases register only the owner as a viewer.
- Server-side raycast remains the interaction source of truth; no Polymer interaction entity is required.

## Attachments by position mode

- `FIXED`: `ManualAttachment`, anchored to a world position.
- `PLAYER_FIXED`: `EntityAttachment` on the owner, with display elements registered through `addPassengerElement`.
- `PLAYER_VIEW`: the same player attachment, while look yaw/pitch updates the relative display transformation.

Polymer already injects `ElementHolder#getAttachedPassengerEntityIds()` into outgoing passenger packets on a per-viewer basis. InteractiveDisplay therefore does not maintain a second passenger registry or manually rewrite ride packets.

Player-bound modes are rebuilt in the player's new `ServerLevel` when the owner changes dimensions. `FIXED` windows remain world-bound and are removed when their owner leaves that dimension.

## Rendering

- text/button/panel: `TextDisplayElement`
- item/map: `ItemDisplayElement`
- block: `BlockDisplayElement`
- real root/display entities and UUID lookup/discard lifecycle: removed

Display transformations are also used for:

- player-relative positioning without per-element movement packets
- button `hoverScale`
- window enter/exit transitions (`scale`, `slide_up`, `slide_down`)

## Interaction

Buttons support `clickType: left`, `right`, or `both`.

- right click is intercepted from item/block/entity use packets
- left click is intercepted from attack/swing/block-destroy-start packets
- same-tick left-click duplicates are consumed without executing the action twice

The pointer item and server raycast are still required before a UI click is consumed.

## Dynamic text

Text components can set `refreshInterval` in server ticks. A value of `0` (default) leaves the text static after creation. Positive values re-resolve Placeholder API text at that cadence and send an update only when the resulting component changed.

## Validation

Changes to this rendering layer must pass the Java 21 Gradle build, unit tests, and Fabric Loom remap. Runtime smoke testing should cover two-player owner-only visibility, player movement, dimension changes, vehicle/passenger interactions, crouch/swim/elytra offsets, and click alignment.

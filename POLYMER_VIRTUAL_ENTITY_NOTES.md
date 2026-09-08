# Polymer Virtual Entity migration notes

This branch migrates InteractiveDisplay rendering from real world `Display` entities to Polymer Virtual Entity elements.

## Visibility contract

- Each window owns one `ElementHolder` with a `ManualAttachment`.
- Only the window owner is registered with `ElementHolder#startWatching`.
- Nearby players are not automatically added as watchers.
- MAP canvases register only the owner as a viewer.
- Server-side raycast remains the interaction source of truth; no Polymer interaction entity is added.

## Rendering

- text/button/panel: `TextDisplayElement`
- item/map: `ItemDisplayElement`
- block: `BlockDisplayElement`
- real root/display entities and their UUID lifecycle are removed.

## Validation before merge

- Java 21 full Gradle build and Loom remap must pass.
- Existing positioning, hit-test, schema, command, and interaction unit tests must pass.
- An in-game two-player smoke test is still required to verify that Player A's window is invisible to Player B while A can still hover/click it.

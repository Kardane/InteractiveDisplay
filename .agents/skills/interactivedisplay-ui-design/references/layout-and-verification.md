# Renderer-aware layout and verification

Recheck the relevant source when the renderer changes. These notes describe the current Minecraft 26.3 implementation, not a permanent public contract.

## Non-obvious geometry

| Concern | Source to inspect |
| --- | --- |
| Parent bounds and anchors | `core/layout/LayoutBounds.java`, `MeditateLayoutEngine.java` |
| Button background, label, and hit box | `core/component/ButtonBoxModel.java`, `core/window/WindowComponentRuntime.java` |
| Image scale, render origin, and text background | `entity/DisplayEntityFactory.java` |
| Player basis and pitch | `core/positioning/CoordinateTransformer.java`, `WindowPositionTracker.java` |
| Live passenger origin and ray hit | `entity/VirtualWindowHolder.java`, `core/window/UiHitResolver.java` |

Source paths above start at `src/main/java/com/interactivedisplay/`.

- Window bounds are centered: bottom is `-height/2`. Panel and resolved layout boxes use a bottom-based y coordinate; a panel placed at y=0 extends upward rather than centering itself. Use `anchor: center` for a centered backdrop.
- Text display rendering and declared component bounds are not interchangeable. Actual line height comes from font scale and wrapping; changing `size.height` alone does not enlarge glyphs.
- ITEM/BLOCK paths use `image.scale` for their planar visual scale. Do not assume `size.width/height` directly resize the rendered model.
- Button dimensions, label content area, and hit geometry come from the resolved box. Padding reduces available label space. Check line count and label height, not just the declared rectangle.
- `overflow: error` catches resolved box overflow but is not a renderer clip mask. It does not prove actual image/text bounds or hovered controls fit.
- An anchor is resolved using bottom-based layout geometry. Do not assume every component's visually centered model has the same origin as a panel.
- For player-bound displays, shared surface bases and passenger origins affect both rendering and ray hits. Preserve consistency rather than adjusting unrelated coordinates until one screenshot looks right.

### Current scaled-block caveat

The renderer currently subtracts a constant half-block on both planar axes, even when `image.scale` is below one. For the player-bound showcase, a block scale of `s=0.78` uses a local x/y correction `(1-s)/2=0.11` to retain its previous visual center.

This is a source-specific configuration workaround, not a universal block formula. Recheck `displayRenderPosition`, rotation, model bounds, and the actual position mode before applying it. Do not carry the offset into a renderer that already scales its origin correction, or assume it works unchanged for FIXED windows.

MAP images remain FIXED-only. Do not switch image types or modes to bypass that restriction.

## Faithful configuration checks

For edited YAML, use the current project classes rather than a substitute schema:

1. `ConfigDocumentLoader.load`: duplicate keys, tags, document shape, and normalization.
2. `SchemaValidator.validate`: supported fields, values, and component IDs.
3. `WindowDefinitionParser.parse`: actual component definitions.
4. `MeditateLayoutEngine.calculate`: resolved layout and overflow.
5. `ButtonBoxModel.resolve`: final label/content boxes and wrapped lines.

Use the bundled validator from the repository root with JDK 25:

```powershell
.\gradlew.bat -I .agents/skills/interactivedisplay-ui-design/scripts/validate-ui.gradle validateInteractiveDisplayUi "-PuiConfigDir=run/config" "-PuiWindows=main_menu,sample_index,display_showcase,font_showcase"
```

`uiConfigDir` is the directory **containing** `interactivedisplay/`, not the windows directory. Select only the affected IDs with `uiWindows`; the default is the four sample screens. For shipped definitions, use `-PuiConfigDir=src/main/resources/defaults`. The script reads YAML and writes build output only; it neither reloads nor starts a server. It uses the actual loader, schema, parser, layout engine, and button box model. It enforces configured layout overflow policies and checks estimated button height and hover bounds. It reports missing YAML navigation targets as warnings because API-registered windows may exist only at runtime.

The script does not predict actual font wrapping, model artwork bounds, clipping, button intersections, runtime API targets, or rendered interaction. A successful run is static validation, not graphical acceptance. For image-heavy screens, inspect the source-specific origin rules above and the actual screenshot.

Check the configuration's intended geometry in addition to parser success:

- All changed family IDs load; destinations exist and retain their intended navigation semantics.
- Resting and hovered buttons stay inside the visible panel and do not intersect dividers or neighboring controls.
- Button labels fit their content area at the delivered font scale.
- Sample artwork stays inside its area and retains its intended visual center.
- Supporting text has sufficient space for actual wrapping.
- Only authorized runtime files changed; compare backups/hashes when useful.

Do not turn implementation-mirroring tests into permanent source tests for a routine YAML tweak. Add durable regressions when changing non-trivial rendering or hit logic.

## Runtime and evidence boundaries

Use the repository's required build and test commands from `AGENTS.md`. Build/GameTest success is regression evidence, not a screenshot of operator YAML.

Before launching anything, inspect the existing server and client. Reuse a suitable authorized instance; keep isolated verification under `build/`. Do not change worlds, access lists, authentication, or resources outside the requested UI scope.

For YAML-only edits to existing windows, reload exactly the affected window IDs. For a newly added window, use the full `/interactivedisplay reload` to discover it. Font assets and Java changes require a rebuilt JAR and a normal server restart; see [fonts-and-delivery.md](fonts-and-delivery.md). For the current family:

```text
/interactivedisplay reload main_menu
/interactivedisplay reload sample_index
/interactivedisplay reload display_showcase
/interactivedisplay reload font_showcase
```

Use an available authorized console/bridge to run them when feasible. Otherwise provide the commands and say they were not executed. Never present an on-disk edit as a confirmed in-game update.

For graphical acceptance, observe the refreshed screens at comparable viewing conditions. Follow menu -> basic window -> return, and menu -> showcase -> return/close. Inspect normal/hover states and test relevant clicks when a game interface is available. Movement, rotation, multiple-player visibility, and vanilla-client compatibility need their own evidence when claimed.

Report the result as separate layers: files written, parser/layout checks, build/server regression, server reload, and rendered interaction. Unavailable game access is a limitation to report, not a reason to alter the user's environment or invent a 95-point result.

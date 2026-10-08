# Your first InteractiveDisplay window

> **Created**: 2026-10-08 KST  
> **Last updated**: 2026-10-08 KST  
> **Modified by**: Codex, OpenAI  
> **Version**: 1.0.0

## Find the right files

This is a Fabric dedicated-server mod for Minecraft 26.3 and JDK 25. It sends owner-only virtual entities to the client. YAML describes windows; the client accepts a Polymer resource pack for the pointer and fonts.

- `src/main/resources/defaults/interactivedisplay/windows/`: tracked definitions shipped in the mod. The loader copies known defaults only when missing.
- `run/config/interactivedisplay/windows/`: this checkout's actual test-server definitions, when present. Editing shipped files does not replace existing operator YAML.
- `src/main/resources/assets/interactivedisplay/font/`: font providers, TTF files, and license.
- `build/`: disposable validation output and scratch examples. Never treat an existing temporary script here as a prerequisite.

Read `wiki/Getting-Started.md` for initial server setup, permissions, and resource-pack acceptance. Check `gradle.properties` and `run/start.ps1` before using a different installation. A fresh clone may have no `run/` server; do not create or replace one merely to validate a UI.

## Try the template without changing a server

From the repository root in PowerShell, set `JAVA_HOME` to your installed JDK 25, then copy the starter to scratch configuration:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
New-Item -ItemType Directory -Force build/ui-skill-example/config/interactivedisplay/windows | Out-Null
Copy-Item .agents/skills/interactivedisplay-ui-design/assets/hello_ui.yaml build/ui-skill-example/config/interactivedisplay/windows/hello_ui.yaml
.\gradlew.bat -I .agents/skills/interactivedisplay-ui-design/scripts/validate-ui.gradle validateInteractiveDisplayUi "-PuiConfigDir=build/ui-skill-example/config" "-PuiWindows=hello_ui"
```

Expected result: `UI PASS hello_ui` and `BUILD SUCCESSFUL`. The template has a centered dark panel, a heading, two specimen lines, and one visible close button. The validator does not start a server or prove the rendered appearance.

Change `content` first. `fontSize` changes glyph scale; `size.height` changes layout bounds. Coordinates are layout/world units, not screen pixels. In a 1.8-high window, the vertical bounds are -0.9 to +0.9. Panels use a bottom-based y coordinate; the backdrop uses `anchor: center` to avoid extending upward from y=0.

## Install in the authorized test server

Confirm the actual server working directory. If it is this checkout's `run/`, copy the example to `run/config/interactivedisplay/windows/hello_ui.yaml`. Before overwriting any existing file, back it up outside the loaded windows directory. Keep the filename and `id: hello_ui` consistent. Use `.yaml`, not `.yml`.

To connect an existing menu, add an `open_window` button targeting `hello_ui`. Reserve a free rectangle rather than overlapping existing controls. This example needs 1.72 units of width, 0.26 of height, and a small hover margin:

```yaml
- id: hello_link
  type: button
  position: { x: 0.0, y: -0.3, z: 0.04 } # Replace with free space in the chosen menu.
  size: { width: 1.72, height: 0.26 }
  label: "My first window"
  fontSize: 0.36
  padding: { horizontal: 0.10, vertical: 0.04 }
  hoverScale: 1.015
  backgroundColor: "#FF285975"
  hoverColor: "#FF347391"
  clickType: both
  action: { type: open_window, target: hello_ui }
```

Validate both the changed menu and `hello_ui` by selecting their IDs. For the existing `sample_index`, enlarge/reflow its panel if needed; the example coordinates above are not a safe drop-in position for that occupied menu. For a back button in the new window, use `open_window` with `target: sample_index` instead of `close_window`. Keep close semantics when the example is standalone.

When distributing a **new bundled default**, also register its resource in `SchemaLoader.ensureDefaultAssets()`. A file in `defaults/` alone is not automatically installed. Existing operator files are intentionally not overwritten. For a YAML-only local example, no Java change is needed.

## Open it in-game

Use an authorized operator account and the commands supported by this checkout. In player chat:

```text
/interactivedisplay reload
/interactivedisplay list
/give @s interactivedisplay:pointer
/interactivedisplay create hello_ui @s player_view
```

The full reload discovers new files. After editing an existing file, use `/interactivedisplay reload hello_ui` and reopen the window. `@s` refers to the current player; a dedicated-server console must name the player instead. Hold the pointer in the main hand and accept the server resource pack. `player_view` follows the player's view, `player_fixed` follows their position with a fixed orientation, and `fixed` uses a world anchor. MAP images require `fixed`.

Check readable text, margins, normal and hover states, both permitted click types, menu navigation, and close/back behavior. Keep the same FOV, viewing distance, and resolution for before/after screenshots. If input or fonts fail, use [fonts-and-delivery.md](fonts-and-delivery.md) and `wiki/Operations-and-Troubleshooting.md` before changing coordinates.

## Finish the task

For runtime/Java changes, run the repository's full `gradlew.bat build` with JDK 25. YAML or skill-only changes need the affected validation and link checks; new scripts must actually be run. Report files changed, static checks, server application/reload, and observed graphical interaction separately. Do not claim a score or screenshot quality from a passing build. Preserve worlds, access lists, authentication, and unrelated edits. Push only when the user authorizes it.

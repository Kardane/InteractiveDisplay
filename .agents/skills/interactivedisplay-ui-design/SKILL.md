---
name: interactivedisplay-ui-design
description: Design, review, score, and refine InteractiveDisplay 3D YAML windows in this repository, including connected sample screens, actual test-server configuration, visual consistency, and renderer-aware validation. Use for this mod's UI work, not unrelated web UI or general Minecraft administration.
---

# InteractiveDisplay UI Design

Create a coherent, readable, pointer-operated UI family within this mod's existing rendering capabilities. Use the requested visual direction; the established sample style is a starting point, not a mandatory theme.

All source and runtime paths below are relative to the repository root containing this skill. Follow that root's `AGENTS.md` when present; do not install this skill globally.

## Start here

For a first UI task or a fresh checkout, read [first-window.md](references/first-window.md). It covers the project map, a working template, menu navigation, exact commands, and acceptance checks. Invoke this project-local skill as `$interactivedisplay-ui-design`, for example: "Create a small window using the first-window example and verify its server configuration."

- For appearance and screenshot reviews, use [design-system.md](references/design-system.md).
- For coordinates and executable validation, use [layout-and-verification.md](references/layout-and-verification.md).
- For Pretendard selection, sharpness, missing glyphs, or resource-pack updates, use [fonts-and-delivery.md](references/fonts-and-delivery.md).

The validator lives in this skill's `scripts/` directory; it does not depend on a previous session's `build/` files. The starter in `assets/hello_ui.yaml` is a template, not a file to install without authorization.

## Bind the request to the real UI

- For a review or score request, inspect and evaluate without editing. For an implementation request, continue through edits and appropriate verification.
- Identify the displayed window ID from its content, YAML, navigation, and available debug state. Do not assume a screenshot is `sample_index` merely because the user arrived from the sample menu.
- For test-server work, inspect the actual server working directory and `run/config/interactivedisplay/windows/`. Editing `src/main/resources/defaults/interactivedisplay/` does not update existing operator files: bundled defaults are copied only when missing.
- Inventory the UI family before changing it. Follow `open_window` targets, group window references, and back/close paths. In the current sample family, check `main_menu`, `sample_index`, `display_showcase`, and `font_showcase`. Do not leave an in-scope destination in an obsolete style.
- Distinguish runtime YAML, bundled defaults, and consumer-provided definitions. Change only the surfaces authorized by the request. A screenshot is evidence, not authorization to reset server data.
- Back up affected operator YAML outside the loaded windows directory. Preserve unrelated work and server settings.

## Design the family

Read [design-system.md](references/design-system.md) for the established palette, size ranges, critique lessons, and scoring criteria.

Start with the screen's task and primary action. Make hierarchy visible through type size, spacing, grouping, and action color before adding decoration.

- Size the panel for its content and in-game viewing distance. Avoid a mostly empty full-screen surface for two choices.
- Make controls identifiable before hover; use a visible button surface, clear label, and restrained hover.
- Keep headings, descriptions, content, and footer actions in distinct regions. Maintain interior margins in resting and hovered states.
- Give comparable samples equal presentation areas, then balance their *visible* artwork sizes. Equal model scales do not imply equal visual weight.
- Keep Korean helper text short, sufficiently large, and contrasted at the actual game resolution. Preserve established language and identifiers.
- Match sibling windows' spacing, type roles, surfaces, and action semantics. Primary navigation may be accented; closing a normal UI is not a destructive action.
- Respect planar Minecraft rendering and the bundled Pretendard font metrics. Do not introduce browser CSS assumptions, rounded corners, custom fonts, or new assets without checking support and scope.

## Implement with the renderer in mind

Read [layout-and-verification.md](references/layout-and-verification.md) before changing coordinates, sizes, scaling, or validation.

Prefer YAML adjustments when they fully satisfy the request. Follow the project-required Goinmul workflow for code or configuration changes. If renderer changes are necessary, make that scope explicit and follow the repository's implementation and test requirements.

Keep IDs, valid action targets, owner-scoped input, pointer requirements, and position-mode constraints intact. Do not silently replace a working control with decorative text.

## Verify and deliver

- Validate the actual edited files using the mod's loader, schema validator, parser, layout engine, and resolved button boxes where feasible.
- Check clipping/overflow, text wrapping, hover bounds, image alignment, and navigation across the entire changed family. Generic YAML parsing alone is insufficient.
- Follow the repository's required build and runtime checks for the delivered change.
- Distinguish file edits from loaded server state. Reload affected IDs through an available authorized server interface, or give exact commands and state that reload was not executed. Do not restart or alter access settings merely to obtain a preview.
- Inspect refreshed game screenshots when available. A layout preview, build, or GameTest cannot prove the final rendered visual quality or click alignment.
- Name the changed server files and verified layers. Report the unobserved layer clearly. A score such as 95/100 is a subjective rendered-screen assessment, not a test result or an automatic reward for making changes.

# Getting Started

## Requirements

- Minecraft 26.3
- Java 25
- Fabric Loader 0.19.5 or newer
- Fabric API 0.161.0+26.3 or the compatible version declared by the release
- Polymer Core, Polymer Resource Pack, and Polymer Autohost versions compatible with the release

InteractiveDisplay is a server-side mod. The exact dependency set is declared in the release JAR metadata. Do not mix a JAR from one Minecraft or Polymer version with a different runtime without testing that combination.

## Install

Install the InteractiveDisplay JAR in the server's mods directory together with Fabric Loader and the dependencies required by that release.

Start the server once. InteractiveDisplay creates or populates the following configuration root:

    config/interactivedisplay/
    ├── command_whitelist.yaml
    ├── windows/
    ├── groups/
    └── images/

Bundled defaults are copied only when the corresponding target file is absent. Existing operator files are not overwritten.

## Open the sample UI

From the server console or an operator command source:

    interactivedisplay list
    interactivedisplay group list

For a player named PlayerA:

    interactivedisplay create main_menu PlayerA player_fixed
    interactivedisplay remove main_menu PlayerA

The sample group can be opened with:

    interactivedisplay group create sample_group PlayerA player_fixed

Use the command syntax documented by the installed release if command suggestions differ.

## Give the pointer item

InteractiveDisplay consumes UI input only while the pointer is in the player's main hand:

    give PlayerA interactivedisplay:pointer

The pointer is required for hover and click detection. Holding another item or using the off-hand alone does not activate the UI hit test.

## Resource pack

The server-side Polymer resource-pack bootstrap provides the assets required by the pointer and virtual display paths. If the pointer appears as a vanilla fallback item or the UI is invisible, verify that the client accepted the server resource pack before debugging window coordinates.

UI text and text inputs use bundled Pretendard Regular; button labels use Pretendard Medium. The fonts are delivered through the Polymer pack, so clients do not need to install them. Reconnect and accept the updated pack after a server update. Explicit non-default fonts in JSON text are preserved. Spaces, unsupported glyphs, and geometry-only panel/button backgrounds retain the Minecraft default font.

The sample menu includes a Pretendard weight comparison (`font_showcase`) with all nine static weights, from Thin (100) through Black (900). Text components can select them through JSON `content`, for example `'{"text":"Bold example","font":"interactivedisplay:ui_bold"}'`. Available font IDs end in `ui_thin`, `ui_extralight`, `ui_light`, `ui_regular`, `ui_medium`, `ui_semibold`, `ui_bold`, `ui_extrabold`, and `ui_black`. These are separate static fonts; variable-font axis controls are not exposed. All nine use 8x oversampling.

The unmodified TTF files come from [Pretendard v1.3.9](https://github.com/orioncactus/pretendard/releases/tag/v1.3.9) and are distributed under the SIL Open Font License 1.1. The copyright and license are included alongside the fonts in `assets/interactivedisplay/font/license-pretendard.txt` in both the mod and generated pack. Font metrics can change visual wrapping; verify the actual screens in-game when adjusting tight layouts.

## First verification checklist

1. The server reaches the normal Done state.
2. The InteractiveDisplay runtime reports that it is ready.
3. The target player has the pointer in the main hand.
4. The sample window opens without a schema error.
5. The player who owns the window can see it.
6. A second player cannot see the owner's private window.
7. A button click produces the expected action.

Server startup and GameTest success do not by themselves prove graphical client alignment. Use the client verification procedure in [Operations and Troubleshooting](Operations-and-Troubleshooting).

# Getting Started

## Requirements

- Minecraft 1.21.8
- Java 21
- Fabric Loader 0.18.0 or newer
- Fabric API 0.136.0+1.21.8 or the compatible version declared by the release
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

## First verification checklist

1. The server reaches the normal Done state.
2. The InteractiveDisplay runtime reports that it is ready.
3. The target player has the pointer in the main hand.
4. The sample window opens without a schema error.
5. The player who owns the window can see it.
6. A second player cannot see the owner's private window.
7. A button click produces the expected action.

Server startup and GameTest success do not by themselves prove graphical client alignment. Use the client verification procedure in [Operations and Troubleshooting](Operations-and-Troubleshooting).

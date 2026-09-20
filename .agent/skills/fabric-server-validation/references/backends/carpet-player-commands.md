# Carpet fake-player command vocabulary

Common Carpet `/player` actions include:

```text
/player <name> spawn
/player <name> kill
/player <name> stop
/player <name> attack once
/player <name> attack continuous
/player <name> attack interval <ticks>
/player <name> use once
/player <name> use continuous
/player <name> use interval <ticks>
/player <name> jump
/player <name> move forward|backward|left|right
/player <name> look north|south|east|west|up|down
/player <name> look at <x> <y> <z>
/player <name> sneak
/player <name> unsneak
/player <name> sprint
/player <name> unsprint
/player <name> hotbar <slot>
/player <name> mount
/player <name> dismount
/player <name> swapHands
```

Carpet command syntax varies across releases/features. If a command is rejected on the installed Carpet build, inspect `/help player`, the installed Carpet version, or its source rather than retrying guessed syntax.

## Example semantic scenario

```text
arrange target block at isolated coords
give bot required item
spawn bot
teleport bot near target
look bot at target
use once
poll target block/entity/mod state
assert exact expected state
stop + kill bot
cleanup fixture
```

The assertion step should use MCP Fabric structured reads whenever possible.

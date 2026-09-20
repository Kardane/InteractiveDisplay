---
name: carpet-player-harness
description: Drive Carpet Mod fake players as deterministic test actors for a Minecraft 1.21.8 Fabric development server, normally through MCP Fabric `run_command`, and assert authoritative server state afterward. Use when server-side behavior specifically requires player-shaped actions such as use, attack, movement, looking, hotbar selection, block interaction, portal entry, or trigger activation, but a real network client is not required.
---

# Carpet Player Harness

## Role in the merged skill set

Player-action helper under $fabric-server-validation. It may still be invoked directly when the user explicitly asks for this tool/lane, but it does not replace the top-level implementation or validation policy.


Treat Carpet fake players as **actuators**, not as the source of truth for assertions.

## Scenario lifecycle

Follow this sequence:

1. **Arrange** — isolate coordinates/world state and create required blocks/entities/items.
2. **Spawn** — create a uniquely named fake player.
3. **Prime** — set position, orientation, game mode, inventory/hotbar, effects, and any mod-specific prerequisite state.
4. **Act** — execute the smallest `/player` action sequence that represents the user behavior.
5. **Await** — poll an observable state/event with a deadline.
6. **Assert** — read authoritative server state through MCP Fabric or a diagnostic command.
7. **Cleanup** — stop actions, remove the bot, and remove fixture state.

Read `references/player-commands.md` for the stable command vocabulary.

## Command transport

Prefer MCP Fabric `run_command` because Codex can issue commands and immediately follow them with structured reads. If the repository already has a stable RCON/test harness, reuse it rather than introducing another transport merely for style.

## Reliability rules

- Use a unique bot name such as `Test_<feature>_<short-id>` when parallel or repeated runs are possible.
- Run `player <name> stop` before cleanup if continuous/interval actions may be active.
- Teleport/orient explicitly; do not depend on the operator's current position.
- Prefer exact `look at`/cardinal direction commands to manual mouse movement.
- Never infer feature success from `/player` command success alone.
- Replace `sleep 2` with a bounded predicate such as “player dimension becomes X”, “block becomes Y”, “entity count changes”, or “event appears”.

## Boundary

Carpet fake players are not socket-backed real clients. Do **not** use this lane to prove login/configuration/play protocol behavior, encryption/authentication, real disconnect semantics, or packet ordering visible only across a network connection. Use `mineflayer-e2e` for those requirements.

# Structured runtime harness: MCP Fabric + Carpet

Use this auxiliary route when a running isolated Fabric development server can reproduce the behavior faster than authoring a new GameTest, or when the action naturally requires a player-shaped actor.

## Roles

```text
Codex
  ├─ mcdev-mcp                 source/mapping/call-path evidence
  ├─ MCP Fabric               observer + command transport + fixture control
  └─ Carpet fake player       player-shaped actuator
```

Keep the roles separate. Carpet performs actions; MCP Fabric observes authoritative runtime state. Neither is a real socket-backed Minecraft client.

## Preferred lifecycle

```text
baseline observation
→ isolate fixture coordinates/state
→ spawn unique Carpet fake player if required
→ prime position/look/inventory/gamemode
→ perform one meaningful action
→ poll state/event with deadline
→ assert authoritative result
→ capture useful diagnostics on failure
→ stop/remove fake player and clean fixture
```

Do not use `/player` command success as feature proof. Do not use fixed multi-second sleeps when a block/entity/player/event predicate can be polled instead.

## When this route is better than GameTest

- reproducing an existing bug against a server that is already running;
- exploring an uncertain player interaction sequence before encoding a regression;
- validating command/admin/runtime state that MCP Fabric exposes directly;
- exercising Carpet fake-player semantics that are awkward to model directly inside GameTest;
- collecting structured state immediately around a failure.

## When GameTest is still preferred

- the scenario is deterministic and can become a committed regression test;
- CI should run it without an externally managed server;
- world/tick/entity logic is the actual contract and player input is incidental;
- the MCP/Carpet setup would add more moving pieces than the behavior requires.

A good workflow is often **B+ reproduce → B GameTest regression → fix → B rerun**, with B+ retained only as a diagnostic harness if it remains useful.

## Escalate to Mineflayer only for a real network boundary

Use the integrated Mineflayer backend when the requirement includes login, configuration/play connection lifecycle, reconnect/disconnect, a socket-backed player path, or protocol-visible behavior that Carpet cannot exercise. Label that evidence `protocol-bot`; it is not unmodified-vanilla proof.

## Safety

- target only disposable/local development servers;
- keep MCP Fabric loopback-only and authenticated;
- never commit bearer tokens;
- isolate coordinates and bot names;
- avoid broad destructive commands unless the fixture/world is disposable.

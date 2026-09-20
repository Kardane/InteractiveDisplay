# Minecraft Fabric Codex Skills — merged set

This package combines the existing Minecraft skills with the MCP Fabric / GameTest / Carpet / mcdev-mcp / Mineflayer skills without creating two competing validation orchestrators.

## Architecture

```text
minecraft-fabric-server-dev              implementation orchestrator
├─ mcdev-source-analysis                 source/mapping/call-graph helper
└─ fabric-server-validation              SINGLE validation orchestrator
   ├─ fabric-gametest                    durable server/world regression tests
   ├─ mcpfabric-runtime                  structured running-server observer/transport
   │  └─ carpet-player-harness           fake-player actuator when needed
   ├─ mineflayer-e2e                     socket/protocol-client lane only when justified
   ├─ Fabric Client GameTest / Loom production routes
   ├─ true unmodified-vanilla black-box gate when the product contract requires it
   └─ packet/wire tracing as late diagnostic tiers

minecraft-java-reference-hub            JE domain/version reference
minecraft-java-1.21.8-world-nbt         offline world NBT/Anvil tooling
minecraft-java-datapack-engineering      datapacks
minecraft-java-resourcepack-engineering resource packs
```

## Main policy change

The previously generated `minecraft-fabric-test-orchestrator` skill is intentionally **not included**. Your existing `fabric-server-validation` already has a richer evidence ladder, production-run semantics, vanilla-client fidelity rules, trace tooling, and self-tests. Maintaining both would create ambiguous routing.

`fabric-server-validation` now owns test-route selection. The five new skills are specialized execution helpers.

## Recommended development loop

```text
$minecraft-fabric-server-dev
  inspect repository
  → $mcdev-source-analysis only when internals/mappings are uncertain
  → implement minimal change
  → $fabric-server-validation
      A/B: unit or $fabric-gametest for durable regression
      B+: $mcpfabric-runtime + $carpet-player-harness for fast runtime reproduction
      network contract: $mineflayer-e2e
      higher-fidelity production/vanilla/client gates only when the requirement demands them
```

A particularly useful bug-fix loop is:

```text
MCP Fabric + Carpet reproduction
→ capture authoritative failing state
→ encode the same invariant as a GameTest when practical
→ fix code
→ rerun GameTest
→ optionally rerun runtime scenario
→ build/production checks
```

This gives Carpet/MCP excellent exploratory speed without making an externally-running server a prerequisite for every CI regression.

## Install in a repository

Copy the `.agents` directory into the repository root, or run:

```bash
./install.sh /path/to/repository
```

To install into the current repository:

```bash
./install.sh .
```

MCP setup helpers are in `scripts/`. Tokens must not be committed.

## Important boundaries

- MCP Fabric + Carpet: server runtime semantics, **not a real network client**.
- Mineflayer: socket/protocol bot, **not an unmodified vanilla client**.
- Fabric Client GameTest: instrumented Fabric test client, **not unmodified vanilla**.
- Actual “no client mod / vanilla player” release contracts still use the existing explicit vanilla black-box gate.
- Computer Use/manual GUI should be reserved for client-visible behavior that cannot be established by lower layers.

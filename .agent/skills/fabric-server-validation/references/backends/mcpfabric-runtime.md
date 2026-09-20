---
name: mcpfabric-runtime
description: Inspect and control a running Minecraft 1.21.8 Fabric dedicated server through the MCP Fabric MCP server. Use when Codex needs structured runtime observations, block/entity/player state, server commands, events, fixture setup/cleanup, or a transport for Carpet `/player` commands without using Minecraft GUI or Computer Use.
---

# MCP Fabric Runtime

## Role in the merged skill set

Runtime-observation helper under $fabric-server-validation. It may still be invoked directly when the user explicitly asks for this tool/lane, but it does not replace the top-level implementation or validation policy.


Use MCP Fabric as a structured server observer and controlled command transport.

## Procedure

1. Start by calling the MCP Fabric status/capability tool. Confirm that the target is the intended development server and that required groups are available.
2. Observe before mutating. Capture the minimum baseline state required for the test.
3. Prefer structured read tools over parsing console text.
4. Use world/admin write tools or `run_command` to arrange only the smallest fixture needed.
5. Perform the action under test.
6. Assert the resulting state with structured reads and/or events.
7. Clean up temporary entities, players, blocks, effects, and test data when possible.

Read `references/tools.md` for the server-side tool categories expected from MCP Fabric.

## Dedicated-server boundary

On a dedicated server, do not attempt client-only MCP Fabric tools such as movement key control, screenshots, client inventory UI manipulation, or navigation. Use Carpet fake players for player-shaped server actions and Mineflayer when a real network client is required.

## Assertion policy

A successful `run_command` call proves only that the command ran. It does not prove the feature worked. Follow actions with state assertions such as:

- block ID/state at coordinates,
- player dimension/position/status,
- entity existence/properties,
- inventory/equipment where exposed,
- join/leave/death/damage/spawn event observations,
- an explicit diagnostic command whose output represents authoritative mod state.

Prefer polling until a state predicate becomes true with a bounded tick/time deadline. Avoid fixed sleeps unless no observable predicate exists.

## Safety

Use only development/test servers. Keep the bridge loopback-only and authenticated. Never print or commit the MCP Fabric bearer token. Do not execute destructive world-wide commands unless the test fixture is disposable and the scope is explicit.

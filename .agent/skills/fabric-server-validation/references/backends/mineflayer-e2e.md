---
name: mineflayer-e2e
description: Build and run narrow Mineflayer-based end-to-end tests against a Minecraft Java 1.21.8 Fabric development server when a real network client connection is materially required. Use for login/join/disconnect/reconnect behavior, protocol-visible synchronization, real connection lifecycle, chat/network interactions, or cases where Carpet fake players cannot represent the behavior; do not use for ordinary world logic that GameTest or Carpet can prove more cheaply.
---

# Mineflayer E2E

## Role in the merged skill set

Protocol-client helper under $fabric-server-validation. It may still be invoked directly when the user explicitly asks for this tool/lane, but it does not replace the top-level implementation or validation policy.


Use Mineflayer as the **real network client** lane, not as the default gameplay test framework.

## Preconditions

- Target only an isolated development/test server.
- Confirm Minecraft version `1.21.8` (or the repository's explicit target).
- Reuse an existing Node test package/package manager if present.
- Keep dependencies locked with the repository's lockfile.

## Test design

1. Start from `scripts/mineflayer-smoke.mjs` or the repository's existing harness.
2. Give the bot a unique test username for offline/local test servers.
3. Wait on semantic events (`spawn`, messages, entity/block updates, disconnect) rather than arbitrary sleeps.
4. Wrap every awaited state in a bounded timeout that produces a useful failure message.
5. Assert only the network/client-visible behavior that justified this lane.
6. Always call `bot.quit()`/cleanup in `finally` where possible so failed tests do not leak clients.

## What belongs here

Good reasons to use Mineflayer:

- code runs specifically on real player join/disconnect lifecycle,
- behavior depends on a socket-backed Minecraft connection,
- packet/network synchronization is the requirement,
- reconnect or multi-client behavior is being tested,
- Carpet fake player behavior is known to bypass the path under test.

Bad reasons:

- breaking a block,
- using an item when server state is the only requirement,
- waiting for ordinary server ticks,
- checking a command result,
- testing server-side persistence that GameTest can establish.

## Compatibility handling

Mineflayer supports modern 1.21 releases including 1.21.8, but protocol libraries can still have version-specific bugs. If a failure occurs before the mod-specific behavior begins, separate harness/protocol incompatibility from a mod regression before changing production Java code.

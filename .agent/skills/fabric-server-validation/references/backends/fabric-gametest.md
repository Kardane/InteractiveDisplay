---
name: fabric-gametest
description: Create, repair, and run Fabric/Minecraft server GameTests for Minecraft Java 1.21.8 Fabric mods. Use when behavior depends on the actual Minecraft server, world, ticks, blocks, entities, commands, events, registries, persistence, or Mixins but does not require a real network client or visual GUI verification.
---

# Fabric GameTest

## Role in the merged skill set

Implementation helper under $fabric-server-validation. It may still be invoked directly when the user explicitly asks for this tool/lane, but it does not replace the top-level implementation or validation policy.


Use server GameTest as the default integration-test layer for server-side gameplay behavior.

## Repository-first setup

1. Inspect `build.gradle`/`build.gradle.kts`, Loom/Fabric API versions, mappings, and existing test configuration before adding anything.
2. Reuse an existing GameTest source set and conventions if present.
3. If GameTest is absent, adapt the Fabric Loom `fabricApi { configureTests { ... } }` setup to the repository rather than replacing existing Gradle structure. The 1.21.8 Fabric docs reference uses `createSourceSet = true` and a test mod ID.
4. Do not silently change mapping families. Examples from official docs may use Mojang mappings while the project may use Yarn or another mapping set.

See `references/1.21.8-setup.md` for a minimal reference pattern.

## Test design

- Keep one behavioral reason for failure per test where practical.
- Build only the fixture required by the behavior.
- Use GameTest tick scheduling/assertion mechanisms for delayed behavior instead of wall-clock sleeps.
- Prefer direct world/entity assertions over chat or screenshot assertions.
- Explicitly succeed only after the final predicate has been verified.
- Add cleanup only where the framework fixture boundary does not already isolate the state.

## Debug loop

1. Run the narrowest available GameTest task or test selection supported by the repository.
2. If the test does not compile, inspect the project's 1.21.8 mappings/source before rewriting around guessed API names.
3. If the test times out, identify the missing state transition or tick dependency. Do not simply inflate timeout values unless the real behavior requires it.
4. Once the targeted test passes, run the repository's normal test/build task because server GameTests are commonly wired into `build` through Loom/Fabric test configuration.

## Escalation

Escalate to `carpet-player-harness` when the behavior specifically depends on player-style input/action semantics that are awkward or misleading to reproduce directly inside GameTest. Escalate to `mineflayer-e2e` only when socket/protocol/login semantics are part of the requirement.

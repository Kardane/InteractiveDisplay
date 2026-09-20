# Upstream source notes

These notes were checked against upstream documentation on 2026-08-17. They are context, not permission to assume the target project uses the same versions or APIs.

## OpenAI Codex skills

Official OpenAI documentation defines a skill as a directory with a required `SKILL.md` containing `name` and `description`, plus optional `scripts/`, `references/`, `assets/`, and `agents/openai.yaml`. Repository-scoped skills are discovered under `.agents/skills`. Codex uses progressive disclosure: name/description are used for discovery, then the full `SKILL.md` is loaded when selected. OpenAI's best practices say to keep a skill focused and prefer instructions over scripts unless deterministic behavior or external tooling is needed.

Source:
https://developers.openai.com/codex/build-skills

## Fabric automated testing

Current Fabric documentation (26.2 at check time) distinguishes unit testing/Fabric Loader JUnit from server and client GameTests. It documents `fabricApi.configureTests`, separate gametest source sets, `runClientGameTest`, and a CI example that runs Client GameTest through a Loom production client task.

Source:
https://docs.fabricmc.net/develop/automatic-testing

## Fabric Loom/Fabric API test DSL

Current Fabric documentation shows `fabricApi.configureTests` options including test source sets, separate test mod ids, server/client GameTests, EULA configuration, and client-test options.

Source:
https://docs.fabricmc.net/develop/loom/fabric-api

## Fabric Loom production run tasks

Current Loom documentation states that distributed mods are remapped and that production-run testing can catch differences between development and production. It documents `ServerProductionRunTask` and `ClientProductionRunTask`. The server task uses the Fabric server launcher and is described as keeping the environment as close to production as possible.

Source:
https://docs.fabricmc.net/develop/loom/production-run-tasks

Fabric's automated-testing guide demonstrates a production client gametest by registering a `ClientProductionRunTask` with `-Dfabric.client.gametest` and Fabric API on the production runtime configuration. The example task name is `runProductionClientGameTest`; it is not a universal built-in task name.

Source:
https://docs.fabricmc.net/develop/automatic-testing

## Fabric Client GameTest status and dedicated server

Current Fabric API Javadocs for 0.154.2+26.2 mark `net.fabricmc.fabric.api.client.gametest.v1` as `@Experimental`. They document:

- a `TestWorldBuilder` capable of creating a dedicated server;
- `TestDedicatedServerContext` as an **in-process dedicated server** context;
- `connect()` for connecting the test client;
- server-context helpers for running/computing on the server thread;
- deterministic tick/network synchronization behavior, including packet handling before the next tick after `waitTick()`;
- the possibility that low-level Netty hooks require disabling the network synchronizer.

Sources:
https://maven.fabricmc.net/docs/fabric-api-0.154.2%2B26.2/net/fabricmc/fabric/api/client/gametest/v1/package-summary.html
https://maven.fabricmc.net/docs/fabric-api-0.154.2%2B26.2/net/fabricmc/fabric/api/client/gametest/v1/world/TestWorldBuilder.html
https://maven.fabricmc.net/docs/fabric-api-0.154.2%2B26.2/net/fabricmc/fabric/api/client/gametest/v1/context/TestDedicatedServerContext.html
https://maven.fabricmc.net/docs/fabric-api-0.154.2%2B26.2/net/fabricmc/fabric/api/client/gametest/v1/context/TestServerContext.html

## Client-fidelity terminology used by this skill

Fabric's official testing documentation and Client GameTest Javadocs describe a Fabric client testing runtime. Loom's production client tasks likewise belong to the Fabric/Loom launch path. The upstream docs do not define these facilities as an "unmodified vanilla client" compatibility certification.

This skill therefore makes an explicit engineering distinction:

- **Fabric Client GameTest / production Fabric client**: official Fabric/Loom testing or production-run evidence;
- **instrumented Fabric probe client**: custom test client with machine-readable assertions;
- **protocol bot**: independent protocol implementation;
- **unmodified vanilla client**: Minecraft client process with no Fabric Loader, no client mod, and no in-client test probe.

The rule that a no-client-mod product promise requires a separate black-box vanilla gate is a validation policy derived from those boundaries, not a Fabric-provided test tier. It prevents a stronger compatibility claim from being inferred from a different client implementation/runtime.

Relevant upstream sources:
https://docs.fabricmc.net/develop/automatic-testing
https://docs.fabricmc.net/develop/loom/production-run-tasks
https://maven.fabricmc.net/docs/fabric-api-0.154.2%2B26.2/net/fabricmc/fabric/api/client/gametest/v1/package-summary.html

## Fabric physical environment caveat

Fabric's `fabric.mod.json` documentation defines physical-side loading constraints such as `environment: server`, server-specific entrypoints, and environment-scoped Mixins. Because Client GameTest's dedicated server is documented as in-process, a client GameTest process must not be assumed to reproduce a fresh physical dedicated-server Loader launch.

The official-first consequence used by this skill is:

1. use Loom `ServerProductionRunTask` when the physical production-server/package boundary itself is what needs verification;
2. if an **unmodified vanilla client** is part of the actual product contract, verify that boundary separately with a black-box compatibility gate;
3. use custom instrumented split-process E2E when the same physical-server contract also requires deep machine-readable client observations that official production-server verification cannot provide.

The vanilla compatibility gate and split-process topology are engineering validation patterns derived from the official boundaries; neither is itself a Fabric-provided testing framework.

Sources:
https://docs.fabricmc.net/develop/loader/fabric-mod-json
https://docs.fabricmc.net/develop/loom/production-run-tasks
https://maven.fabricmc.net/docs/fabric-api-0.154.2%2B26.2/net/fabricmc/fabric/api/client/gametest/v1/context/TestDedicatedServerContext.html

## Fabric networking

Current Fabric networking documentation centers custom payload registration and client/server networking helpers. This skill therefore prefers owned custom-payload semantic observation and reserves generic vanilla packet capture for narrow, version-verified, test-only instrumentation.

Source:
https://docs.fabricmc.net/develop/networking

## Version warning

Minecraft/Fabric names and APIs change materially across versions. The target repository remains the source of truth for exact imports, mappings, Gradle DSL, packet classes, Mixin targets, and available task types.

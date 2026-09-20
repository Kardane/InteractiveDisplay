---
name: fabric-server-validation
description: "Validate Minecraft Fabric server-side mod changes with the cheapest reliable evidence: Fabric Loader JUnit, server/client GameTests, MCP Fabric structured runtime inspection, Carpet fake-player harnesses, Loom production-run verification, Mineflayer protocol-client E2E when justified, explicit unmodified-vanilla compatibility gates when required, instrumented physical client/server E2E, and focused packet/state traces. Use when implementing, debugging, reviewing, or verifying Fabric server-side gameplay, synchronization, networking, vanilla-client compatibility, or release-environment behavior. Do not use for client-rendering-only mods or non-Fabric loaders."
---

# Fabric server validation

Turn Fabric server-side changes into a closed validation loop that Codex can execute itself. Prefer the smallest deterministic evidence that crosses the behavior boundary; do not collect packet data merely because it is available.

## Rules

1. Inspect the repository before choosing APIs, mappings, Java versions, Gradle tasks, or test syntax. The project is the source of truth for exact versions.
2. Prefer semantic state assertions over packet existence. For synchronization behavior, authoritative server state plus client-observed state is stronger evidence than a packet trace.
3. Use Fabric/Loom-native test and production-run facilities before inventing custom process orchestration.
4. When Minecraft internals, mappings, lifecycle ordering, or Mixin targets are uncertain, use configured mcdev-mcp source analysis (normally through `minecraft-fabric-server-dev`) instead of guessing from another Minecraft version.
5. Use the **integrated MCP Fabric runtime backend** for structured observation/fixture control and the **integrated Carpet player backend** for player-shaped server actions. They are primarily fast reproduction/diagnostic lanes; prefer a committed Unit/GameTest regression when the behavior can be expressed there cleanly.
6. Use the **integrated Mineflayer E2E backend** only when a socket-backed Minecraft connection or protocol lifecycle is materially part of the contract. A Mineflayer bot is a protocol bot, not an unmodified vanilla client.
7. Treat Fabric Client GameTest as version-sensitive. Current Fabric API marks the client gametest package `@Experimental`; verify the project version and dependency Javadocs before using exact classes/signatures.
8. Keep instrumentation test-only whenever practical. Do not ship packet recorders or test Mixins unless explicitly required.
9. Do not silently accept the Minecraft EULA or alter legal/credential settings.
10. Keep network tests local/in-process unless the user explicitly names an authorized remote fixture.
11. Do not record secrets, authentication/session material, full chat, or arbitrary payload bytes by default.
12. Never create a golden snapshot of the full packet stream. Assert only causally relevant packet types/fields/order/counts.
13. Do not weaken assertions to obtain a green build. Report BLOCKED or partial evidence when the required boundary cannot be exercised.
14. Never call a Fabric Client GameTest client, Fabric probe client, production Fabric client, or protocol bot "vanilla". An unmodified vanilla-client contract requires its own black-box compatibility gate.

## 1. Discover the project

Resolve this skill directory as `<skill-dir>` and run:

```bash
python <skill-dir>/scripts/inspect_fabric_project.py --repo . --tasks --output build/validation/fabric-env.json
```

If Python 3.10+ is unavailable, inspect manually:

- Gradle settings/build files and version catalogs
- `gradle.properties`
- production and test `fabric.mod.json` files
- Mixin configs
- test/gametest source sets
- Gradle tasks containing `test`, `game`, `server`, `client`, `production`, `check`, or `build`

Record versions, mappings, Java toolchain, production mod ids, test setup, EULA opt-in, and whether production initialization is physical-server-sensitive (`environment: server`, dedicated `server` entrypoint, server-scoped Mixins, or explicit physical-side branches).

## 2. Choose the proving route

Use the cheapest route that proves the requirement. See `references/validation-ladder.md` for details.

| Tier | Route | Primary evidence |
|---|---|---|
| A | Unit / Fabric Loader JUnit | direct input/output assertions |
| B | Server GameTest | authoritative server/world state |
| B+ | Structured runtime harness | MCP Fabric observations + optional Carpet fake-player actions |
| C | Fabric Client GameTest | server state + instrumented Fabric test-client state |
| D | Loom production-run verification | remapped production artifact/environment behavior |
| E | Vanilla-client compatibility E2E | unmodified vanilla client black-box evidence |
| F | Instrumented split-process E2E | physical dedicated server + separate Fabric probe/test client evidence |
| G | Packet-object trace | focused network diagnosis |
| H | Wire/Netty trace | framing/compression/encryption/codec diagnosis |

Selection rules:

- Pure deterministic logic → A.
- Live server/world behavior without a client contract → B. Use the integrated Fabric GameTest backend; read `references/backends/fabric-gametest.md` only when implementation details are needed.
- Fast reproduction/diagnosis on an isolated running development server, especially when the scenario needs player-shaped actions but not a real network connection → B+ using MCP Fabric, optionally with Carpet fake players. Convert durable regressions to A/B when practical.
- “client receives/sees/synchronizes” and the relevant production initialization is active in the Client GameTest runtime → C. This proves an instrumented Fabric test-client contract, **not** vanilla compatibility.
- Release/remapping/launcher/physical-server fidelity matters → add D.
- The product contract explicitly says players need **no client mod / unmodified vanilla client** → add E. If faithful black-box automation is unavailable, keep the vanilla portion BLOCKED or manual rather than substituting C/F.
- The contract requires a fresh physical dedicated-server Loader path plus deep machine-readable client-state assertions → F, normally after D.
- Add G only when packet semantics themselves matter or earlier tiers fail ambiguously.
- Use H only for defects below packet-object semantics.

Do not treat B+ as proof of real network-client behavior or vanilla compatibility. Do not treat D as a substitute for client-behavior proof. Do not treat C/F or a protocol bot as a substitute for E when the actual contract is unmodified-vanilla compatibility. Conversely, do not jump directly to custom F when official Loom production runs already prove the environment-specific part of the contract.

## 3. Baseline before changing the harness

1. Run the narrowest existing relevant test.
2. When practical, run the repository's existing `test`, `check`, or `build` verification.
3. Separate pre-existing failures from failures introduced by the current work.

For noisy Gradle tasks, after discovering exact task names:

```bash
python <skill-dir>/scripts/run_gradle_validation.py --repo . --task <task>
```

Do not invent task names.

## 4. Write the smallest proving test

Use this causal shape:

```text
arrange deterministic state
→ perform one meaningful action
→ wait by ticks/events/framework barriers
→ assert authoritative server state
→ if client-visible, assert client-observed state
→ add packet assertions only if they add proof or diagnosis
```

Avoid arbitrary sleeps. Normalize dynamic ids, UUIDs, ports, and timestamps. Scope packet expectations to one scenario/connection/phase/window.

Read `references/fabric-test-recipes.md` before adding JUnit/GameTest code.

## 5. Use Fabric-native routes first

### Unit and Server GameTest

Follow the repository's existing conventions. Use Fabric Loader JUnit only when Loader/Minecraft initialization is actually needed. Use Server GameTest for gameplay/world/tick contracts that do not require client observation.


When creating or repairing a 1.21.8 server GameTest, read `references/backends/fabric-gametest.md` and, when setup is uncertain, `references/backends/fabric-gametest-1.21.8-setup.md`. GameTest is an internal backend of this skill, not a separately selected skill.

### Structured runtime harness: MCP Fabric + Carpet

Use this lane for fast reproduction, diagnosis, fixture setup, and machine-readable runtime assertions on an isolated development dedicated server.

- **MCP Fabric** is the observer/command transport: inspect blocks, entities, players, mod diagnostics, commands, and events using structured tools where available. Read `references/backends/mcpfabric-runtime.md` and `references/backends/mcpfabric-tools.md` as needed.
- **Carpet fake player** is the actuator when the behavior specifically requires player-shaped use/attack/movement/look/hotbar/portal interactions. Read `references/backends/carpet-player-harness.md` and `references/backends/carpet-player-commands.md` as needed.
- A successful `/player` command or MCP command is not the assertion. Follow it with authoritative state/event checks.
- Prefer bounded state/event polling over fixed sleeps.
- This lane is not a socket-backed client and is not vanilla-client evidence.
- When a B+ reproduction exposes a stable bug, add an A/B regression whenever the contract can be expressed without losing fidelity.

Read `references/structured-runtime-harness.md` before building a new Carpet/MCP scenario.

### Fabric Client GameTest

When supported by the project version, Client GameTest can provide direct client-state observation and may create/connect to an **in-process dedicated server**. Current Fabric API also synchronizes packet handling around test ticks. These are version-specific, experimental APIs; verify exact behavior before coding.

The test client runs in Fabric's client testing environment. **Do not describe it as an unmodified vanilla client and do not use a Client GameTest PASS as vanilla-compatibility proof.**

Use Client GameTest only when the feature's relevant production initialization is genuinely active in that runtime. A loaded mod id alone is insufficient if the feature is registered only by physical-server-specific initialization.

### Loom production-run verification

Before custom process orchestration, check whether Loom production run tasks can prove the environment/package boundary. Current Loom provides `ServerProductionRunTask` and `ClientProductionRunTask`; Fabric's automated-testing documentation also demonstrates a production client gametest task built on `ClientProductionRunTask`.

Use production runs for questions such as:

- does the remapped artifact start/load in a production-like launcher environment?
- does a physical dedicated server activate server-only metadata/entrypoints/Mixins?
- does the production client gametest still pass outside the ordinary development run?

Do not assume task names such as `prodServer` or `runProductionClientGameTest` exist; those are examples and custom task registrations. Discover or add version-compatible tasks only when needed.

Read `references/production-run-validation.md` before modifying Gradle.

### Unmodified vanilla-client compatibility

When the product promise is “server-side only; ordinary vanilla players need no client mod,” treat that as a separate compatibility contract. A true vanilla client is black-box: no Fabric Loader, no client probe mod, and no in-client assertion code.

Use an existing external/black-box automation harness when available. If the required effect is visual/UI-only and no faithful black-box observation exists, report `MANUAL GATE REQUIRED` or `BLOCKED`; do not replace it with a Fabric test client and call the vanilla contract proven.

Read `references/vanilla-compatibility-e2e.md`.

### Instrumented split-process E2E

This is **not a Fabric-provided test framework**. It is a custom integration topology derived from Fabric Loader side semantics and is reserved for contracts that require a fresh physical dedicated-server process and a separate **instrumented** client at the same time.

Typical trigger:

```text
physical-server-sensitive production initialization
+
deep client-state/network assertions that need a probe
```

A Fabric probe client or protocol bot can improve automation and diagnosis, but neither is vanilla-client proof. When the required contract is specifically socket/login/reconnect/protocol-visible behavior and a protocol bot is sufficient, use the integrated Mineflayer backend (`references/backends/mineflayer-e2e.md`) and label the evidence `protocol-bot`. Keep production metadata unchanged. Do not change `environment: server` to `*` just to make an in-process test pass.

Read `references/split-process-e2e.md` before using `scripts/run_split_e2e.py`.

## 6. Add packet tracing only when useful

Read `references/packet-tracing.md` first. Prefer:

1. owned custom-payload semantic hooks;
2. narrow vanilla packet-object hooks verified against the exact mapped version;
3. Netty/wire hooks only for codec/framing/compression/encryption defects.

Write focused JSONL traces under `build/validation/traces/`. Do not reflectively dump full packet objects.

Useful helpers:

```bash
python <skill-dir>/scripts/summarize_trace.py build/validation/traces/<trace>.jsonl
python <skill-dir>/scripts/assert_trace.py build/validation/traces/<trace>.jsonl <assertions.json>
```

Use `references/trace-schema.md` for the trace/assertion contract.

## 7. Diagnose causally

For outbound synchronization:

```text
input/action
→ server handler
→ authoritative server state
→ tracking/send decision
→ packet/payload
→ client handling
→ client-observed state
```

Interpret failures in that order:

- server state wrong → server logic first;
- server state right, packet absent → tracking/send conditions;
- packet present, client state wrong → fields/order/client application/protocol compatibility;
- all states correct but test fails → test synchronization/assertion/flakiness.

See `references/diagnostic-playbooks.md` for common scenarios.

## 8. Finish with evidence, not confidence language alone

Report:

```text
Verdict: PASS | FAIL | BLOCKED
Scope: <feature/change>
Environment: MC <version>, Loader <version>, Fabric API <version>, Loom <version>, Java <version>
Routes:
- <task/test>: PASS|FAIL — <what boundary it proves>
Runtime actor: none | carpet-fake-player | real-player-client
Client fidelity: none | fabric-client-gametest | production-fabric-client | unmodified-vanilla | fabric-probe | protocol-bot
Vanilla gate: not required | PASS | FAIL | BLOCKED | MANUAL GATE REQUIRED
Production fidelity: not needed | PASS|FAIL|BLOCKED — <task/evidence>
Packet evidence: not needed | <focused summary>
Artifacts: <paths>
Limitations: <unexercised boundary>
```

A gameplay/network PASS requires evidence at the route appropriate to the actual user-visible contract. Compilation alone is not behavioral proof.

## Reference loading guide

Load only the backend needed for the selected tier; do not read all backend documents by default.

- Tier choice/pass criteria: `references/validation-ladder.md`
- Fabric JUnit/GameTest recipes: `references/fabric-test-recipes.md`; backend mechanics: `references/backends/fabric-gametest.md`
- MCP Fabric + Carpet runtime route: `references/structured-runtime-harness.md`; backend mechanics under `references/backends/`
- Loom production runs and production Client GameTest: `references/production-run-validation.md`
- Unmodified vanilla-client compatibility: `references/vanilla-compatibility-e2e.md`
- Instrumented physical-server + client topology: `references/split-process-e2e.md`; Mineflayer backend: `references/backends/mineflayer-e2e.md`
- Scenario diagnosis: `references/diagnostic-playbooks.md`
- Packet instrumentation: `references/packet-tracing.md`
- Trace schema/assertions: `references/trace-schema.md`
- Real-repository integration fixture matrix: `references/integration-fixture-matrix.md`
- Upstream documentation notes: `references/source-notes.md`

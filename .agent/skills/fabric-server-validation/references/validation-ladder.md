# Validation ladder for Fabric server-side work

Choose the lowest-cost route that crosses every boundary in the requirement. **Client fidelity is a separate axis from test depth.** Production-environment fidelity, vanilla-client compatibility, instrumented client observation, and packet diagnostics are different concerns; do not collapse them into one PASS.

## Tier A — Unit / Fabric Loader JUnit

Best for codecs, parsers, deterministic helpers, permission predicates, and data transformations.

Not sufficient for world ticking, tracking, network delivery, or client-visible synchronization.

## Tier B — Server GameTest

Best for authoritative server behavior: commands, block/entity state, inventories, scheduled ticks, world events, and server lifecycle.

Evidence standard: deterministic arrange/action/assert, tick-bounded completion, authoritative server-world assertions.

## Tier B+ — Structured runtime harness (MCP Fabric + optional Carpet)

Use this as a fast **runtime reproduction/diagnostic lane** against an isolated running development dedicated server.

Evidence standard:

- structured MCP Fabric observations of authoritative server state/events;
- Carpet fake-player actions only when player-shaped server interaction is required;
- bounded polling around semantic predicates;
- explicit cleanup/isolation;
- clear statement that the actor is not a socket-backed client.

Tier B+ is especially useful before a fix because it lets Codex reproduce and inspect a bug without GUI automation. For durable regressions, prefer moving the proving assertion into Tier A/B when doing so preserves the relevant behavior boundary.

Tier B+ does **not** prove login/network-client behavior and never proves unmodified-vanilla compatibility.

## Tier C — Fabric Client GameTest

Best for client-visible behavior when the project version supports the necessary API and the relevant production initialization is active in the Client GameTest runtime.

Current Fabric API Client GameTest is marked `@Experimental`. Current Javadocs document deterministic client/server tick behavior, network synchronization around `waitTick()`, and an in-process dedicated-server context that can connect the test client. Treat all exact API names and behavior as version-specific.

Evidence standard:

- authoritative server state;
- direct client-observed state inside the Fabric test-client runtime;
- deterministic tick/event progression;
- packet trace only when needed.

### Client-fidelity limitation

A Fabric Client GameTest client is an **instrumented Fabric test client**, not an unmodified vanilla Minecraft client. A Tier C PASS therefore proves the behavior in the Fabric Client GameTest runtime; it does not independently prove a “no client mod / vanilla client” product contract.

### Physical-side limitation

An in-process dedicated server is not equivalent to launching a fresh physical dedicated-server Loader process. When production behavior depends on `environment: server`, a dedicated `server` entrypoint, server-scoped Mixins, or explicit physical-side branches, verify the physical server path separately with Tier D. Continue to Tier E or F according to the actual client contract.

## Tier D — Loom production-run verification

Use Loom production run tasks to test remapped artifacts and launcher/environment behavior closer to distribution conditions.

Current Loom documents:

- `ServerProductionRunTask`: uses the Fabric server launcher and is intended to be as close to production as possible;
- `ClientProductionRunTask`: production client launcher task;
- Fabric's automated-testing guide shows a production client gametest task created with `ClientProductionRunTask` plus `-Dfabric.client.gametest`.

Best for:

- production remapping/packaging differences;
- server-only metadata/entrypoint/Mixin activation on a physical server;
- release/CI verification outside ordinary development runs;
- production Fabric Client GameTest verification.

Tier D proves production-like Fabric launch/package behavior. It does **not** by itself prove that an unmodified vanilla client is compatible.

## Tier E — Unmodified vanilla-client compatibility E2E

Use this tier when the actual product contract says ordinary players can connect with **no client mod** and the expected client is an unmodified vanilla Minecraft client.

The client is black-box by definition:

```text
physical Fabric dedicated server + production mod
                ⇅
unmodified vanilla Minecraft client
```

Evidence must match the behavior being promised. Successful login can prove a narrow join-compatibility requirement, but it does not prove a later inventory/UI/visual contract.

For automatable black-box behavior, use an existing external process/UI harness that leaves the Minecraft client itself unmodified. For a client-visible effect that cannot be faithfully observed externally, report `MANUAL GATE REQUIRED` or `BLOCKED` rather than substituting a Fabric probe client.

Not substitutes for Tier E:

- Fabric Client GameTest;
- `ClientProductionRunTask` with Fabric test/runtime code;
- a Fabric client probe;
- a protocol bot;
- a packet trace alone.

Read `vanilla-compatibility-e2e.md` for evidence rules.

## Tier F — Instrumented split-process E2E

This is a custom harness, not a Fabric-provided testing API.

Use it when the contract requires both:

1. a fresh physical dedicated-server process with production loading semantics; and
2. deep machine-readable client assertions from a separate Fabric test/probe client or another explicitly instrumented client.

Evidence standard:

- production server artifact/initialization confirmed;
- semantic server-ready barrier;
- authoritative server result;
- separate instrumented client-observed result;
- explicit client-fidelity label;
- bounded lifecycle and cleanup.

A protocol bot may be appropriate for a pure protocol contract, but it is not equivalent to either the Fabric/Mojang client implementation or an unmodified vanilla-client compatibility gate.

Prefer existing repository integration tasks. Use `split-process-e2e.md` only when official/established routes are insufficient.

## Tier G — Packet-object trace

Use for missing/duplicate/wrong-recipient/wrong-field/wrong-order diagnosis or when the packet contract itself is the requirement.

A packet being emitted normally does not prove the client applied the intended behavior. A packet trace also does not upgrade an instrumented client into vanilla-client evidence.

## Tier H — Wire / Netty trace

Reserve for framing, compression, encryption, byte codec, or pipeline defects. Keep a higher-level state assertion alongside wire evidence.

## Decision examples

### Command changes server state

Tier B. Add D only when production packaging/side behavior is part of the risk. For rapid reproduction on an already-running dev server, B+ can establish the behavior first; convert it to B for a durable regression when practical.

### Entity metadata must synchronize to a client used only for automated development validation

Tier C if the relevant initialization is active. Add G only if client state fails or exact packet semantics matter.

### Server-side-only mod promises vanilla clients need no mod

Use B/C for fast internal validation where useful, D for production server fidelity, and **E as the release compatibility gate**. C is supporting evidence, not a replacement for E.

### `environment: server` mod must start correctly after remap

Tier D with a production server run. No client is needed if startup/loading is the whole contract.

### `environment: server` mod changes entity state and an unmodified vanilla client must observe it

Use B for narrow server logic where useful, D for physical production-server loading, and E for the actual vanilla compatibility contract. If the black-box vanilla gate fails or cannot expose enough state, use F/G diagnostically—but do not replace E with them.

### `environment: server` mod needs deep client-state assertions, but vanilla compatibility is not the contract

Use B where useful, D for physical production-server loading, then F for the combined physical-server + instrumented-client contract.

### Client GameTest should pass in CI/release-like runtime

Tier C during development plus Tier D using a production `ClientProductionRunTask`-based client gametest when supported by the project version. Label client fidelity as Fabric, not vanilla.

### Rubber-banding only with compression enabled

Reproduce at C/E/F as appropriate to the actual client contract; add H only around the compression/frame boundary after higher-level evidence isolates the issue.

## Confidence labels

- **High**: all relevant behavior, environment, and required client-fidelity boundaries are directly exercised with deterministic or faithful black-box assertions.
- **Medium**: core behavior is exercised but one required boundary is simulated, instrumented differently from production, or unavailable.
- **Low**: compilation/log inspection/indirect evidence only.

Never present low-confidence indirect evidence as a full behavioral PASS. Never report a vanilla compatibility PASS unless the client-fidelity boundary itself was exercised.

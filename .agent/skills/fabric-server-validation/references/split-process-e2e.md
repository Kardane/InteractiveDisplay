# Instrumented split-process E2E for physical-server-sensitive Fabric mods

> **Status:** This is a custom integration harness, not a Fabric-provided testing framework. Prefer Fabric/Loom native GameTests and production run tasks first. Use this topology when one scenario must cross both a fresh physical dedicated-server Loader boundary and a separate **instrumented** client boundary.

If the actual contract is “ordinary players use an unmodified vanilla client,” read `vanilla-compatibility-e2e.md` first. A Fabric probe client or protocol bot is useful diagnostic/automation evidence but is **not** a substitute for that vanilla compatibility gate.

Use this topology after confirming that the feature depends on a physical dedicated-server Loader path and the contract needs machine-readable client observations that cannot be obtained from the production server run alone. Examples of physical-side triggers include `"environment": "server"`, a dedicated `server` entrypoint, a server-scoped Mixin config, or explicit physical-side branching. If physical-server startup/loading alone is the contract, prefer Loom `ServerProductionRunTask` instead.

## Why this branch exists

Fabric Loader's `environment` metadata and environment-specific entrypoints/Mixins are physical-side loading constraints. A mod marked `server` is not loaded in a client environment, and the dedicated `server` entrypoint is defined for the physical server side. Modern Fabric Client GameTest can create a dedicated server, but that server is explicitly **in-process**. Therefore, do not assume that an in-process dedicated server proves behavior whose production initialization depends on a fresh physical server launch.

Treat this as a topology constraint, not as a reason to weaken production metadata.

## Preferred topology

```text
orchestrator / Gradle task
    ├── physical dedicated-server JVM
    │     ├── production mod
    │     ├── test-only server observer (optional)
    │     └── isolated run dir + loopback port
    │
    └── separate instrumented client/test JVM
          ├── Fabric test/probe client, or
          └── protocol bot when the contract is protocol-only

client ⇄ localhost Minecraft protocol ⇄ server
```

The orchestrator owns lifecycle and result collection. The two game processes should not own each other's cleanup.

## Client-fidelity labels

Always name what actually ran:

- `fabric-probe` — Minecraft client running Fabric/test instrumentation;
- `protocol-bot` — independent protocol implementation;
- `other-instrumented` — another explicitly described test client.

Do not use `vanilla` for any of these. If an unmodified vanilla client is launched through an external black-box harness, report that scenario under the vanilla compatibility gate instead, even if the same lifecycle helper coordinates the processes.

## Before building this

Check whether the repository already has one of these:

- a multiplayer integration-test task;
- a test client mod/source set;
- a Docker/CI Minecraft server fixture;
- a protocol test client;
- a server console/RCON harness;
- integration-test process orchestration.

Reuse the existing route instead of creating a second one.

## Server process contract

Start the exact production server artifact or development server configuration that loads the production mod.

Requirements:

1. Use a fresh isolated run directory per test suite.
2. Bind only to loopback unless the user explicitly requests an authorized remote fixture.
3. Use an OS-assigned or dynamically reserved port when the project supports it; otherwise serialize the fixed-port tests.
4. Verify the expected production mod id/version was loaded. Do not infer success only because the TCP port opened.
5. Wait for a semantic ready marker such as the server's completed-startup event/log before launching the client.
6. Keep world settings deterministic where possible.
7. Capture stdout/stderr to artifacts but report only relevant tails to Codex.
8. Stop the server cleanly first; force-kill the process tree only as cleanup fallback.

### EULA

Do not write `eula=true` merely because the harness needs it. Use an existing explicit repository/user opt-in. Otherwise prepare the harness and report E2E execution as blocked.

## Instrumented client process contract

Use the least invasive client that exposes the boundary actually under test.

### Fabric Minecraft client probe

Use a test-only client source set/entrypoint that:

- receives host/port/scenario through test-only args or system properties;
- connects only to loopback by default;
- uses only credentials/session handling already provided by the authorized local test environment;
- waits for join/play readiness;
- performs one scenario action;
- observes the minimum client state needed for the assertion;
- writes a small machine-readable result;
- disconnects and exits with a nonzero code on assertion failure.

Exact connect APIs are Minecraft/Fabric-version-sensitive. Resolve them from the project's mapped sources; do not paste a connection implementation from a different version.

This client is instrumented Fabric, not vanilla.

### Protocol bot

A protocol library can be cheaper when the contract is purely protocol/state and does not require actual Minecraft client application behavior. It is not a drop-in substitute for a real Minecraft client when testing rendering-adjacent state, vanilla handlers, menu behavior, movement reconciliation, or version-specific client semantics.

Pin the protocol library to the exact Minecraft protocol and keep it test-only. Label the evidence `protocol-bot`.

## Result channels

Prefer explicit result artifacts over scraping huge logs.

Example:

```text
build/validation/e2e/<scenario>/
├── server.log
├── client.log
├── server-result.json
├── client-result.json
├── packet-trace.jsonl       # only when needed
└── verdict.json
```

`server-result.json` should contain authoritative values only. `client-result.json` should contain observed values only. The orchestrator compares them and produces `verdict.json`.

Do not share live mutable Minecraft objects across threads/processes. Serialize compact primitive/identifier/record values.

## Synchronization

Use semantic barriers instead of sleeps:

```text
server process started
→ server ready marker
→ client launched
→ client joined PLAY state
→ scenario landmark emitted
→ server mutation/handler complete
→ expected client state/event observed within N ticks
→ assertions
```

Wall-clock timeouts are still necessary as deadlock guards, but they are not the success criterion.

## Packet observation in split-process tests

Prefer packet-object observers in the process that owns the relevant object:

- outbound S2C: server observer near decoded packet/payload send;
- inbound C2S: server observer near listener dispatch or owned payload handler;
- client application: client observer near decoded handler/state change.

Correlate both sides using deterministic scenario/connection aliases, not TCP ports or random runtime ids.

If the packet is vanilla and exact packet observation is unnecessary, skip tracing and compare server/client state directly.

## Failure classification

- server did not start / production mod not loaded → harness/configuration failure;
- server state wrong → production server logic failure;
- server state right, client never receives expected state → synchronization/tracking/network failure;
- client connects but test probe cannot perform action → client harness failure until proven otherwise;
- packet reaches client process but client state is wrong → packet fields/order/client application path;
- both result files agree but verdict fails → assertion/orchestrator bug.

If the product contract is vanilla compatibility, none of these instrumented outcomes may be promoted to a vanilla PASS. Use them to diagnose the separate vanilla gate.

## Cleanup guarantees

The orchestrator must use `finally`/shutdown hooks and bound each process with a timeout. Record process exit codes. Never leave a development server listening after a failed Codex run.

Do not broadly kill `java` processes. Kill only the PIDs/process groups created by the harness.

## Bundled lifecycle orchestrator

Once project-specific server/client commands exist, `scripts/run_split_e2e.py` can coordinate them. It intentionally does **not** know Fabric Gradle task names or Minecraft connect APIs. Pass exact argv arrays after project discovery and set `--client-fidelity` to describe the client that actually runs.

Useful features:

- no shell parsing; server/client commands are JSON argv arrays;
- loopback host and a selected local port exported through `FABRIC_VALIDATION_HOST` / `FABRIC_VALIDATION_PORT`;
- `{port}`, `{repo}`, `{output_dir}`, `{scenario}` argument placeholders;
- semantic server-ready regex;
- optional production-mod-loaded regex;
- optional required JSON results containing `verdict: PASS` or `pass: true`;
- `--client-proof-json` for explicit black-box client evidence; `vanilla-black-box` cannot PASS from process exit code alone;
- explicit client-fidelity label in the result;
- bounded startup/client/shutdown timeouts;
- server/client logs under one scenario directory;
- graceful `stop` followed by process-group termination fallback;
- common credential-like command arguments redacted from the report.

Example shape after exact commands have been discovered:

```bash
python <skill-dir>/scripts/run_split_e2e.py \
  --scenario entity-sync \
  --client-fidelity fabric-probe \
  --server-cmd-json '["./gradlew","<server-test-task>","<arg-with-{port}>"]' \
  --client-cmd-json '["./gradlew","<client-test-task>","<arg-with-{port}>"]' \
  --server-ready-regex '<server ready marker>' \
  --server-loaded-regex '<production mod loaded marker>' \
  --require-pass-json '{output_dir}/server-result.json' \
  --require-pass-json '{output_dir}/client-result.json'
```

The free-port selection is a convenience, not a reservation held until Minecraft binds. If the project's launch mechanism cannot accept a dynamic port safely, use a dedicated fixed local port and serialize those tests.

When Gradle itself is the long-lived wrapper around the game process, prefer an existing integration-test task with explicit lifecycle semantics. If you must orchestrate `runServer`/`runClient`-style tasks directly, consider `--no-daemon` so process ownership and cleanup are less ambiguous. Confirm stdin forwarding before relying on the graceful `stop` path; the orchestrator also has process-tree termination fallback.

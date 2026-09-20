# Vanilla-client compatibility E2E

Use this gate only when the product contract explicitly requires players to connect with an **unmodified vanilla Minecraft client**—that is, no Fabric Loader, no client mod, and no test probe inside the game client.

This gate is deliberately separate from Fabric Client GameTest and from an instrumented Fabric client. Those are valuable automated test clients, but they are not proof that an unmodified vanilla client behaves correctly.

## What counts as vanilla evidence

Strongest evidence comes from the exact client fidelity required by the product contract:

```text
physical Fabric dedicated server + production mod
                ⇅ normal Minecraft protocol
unmodified vanilla Minecraft client
```

The vanilla client is black-box by definition. Do not inject Mixins, Fabric API, a probe mod, or assertion code into it and still label the result "vanilla".

Acceptable automation may wrap the client **externally**—for example, an existing repository/CI launcher or black-box UI/process harness—provided the Minecraft client process itself remains unmodified. Reuse an existing authorized harness when available. Do not build account-token scraping, authentication bypasses, unofficial client downloads, or credential capture into this skill.

When using `scripts/run_split_e2e.py` to coordinate such a harness, set `--client-fidelity vanilla-black-box` and provide at least one `--client-proof-json` emitted by the external harness. The helper deliberately leaves the verdict `BLOCKED` when vanilla mode has only a zero process exit code and no explicit black-box proof artifact.

## What does not prove vanilla compatibility

The following are useful supporting evidence but are not substitutes for this gate:

- Fabric Client GameTest;
- `ClientProductionRunTask` or a Fabric production client carrying Fabric Loader/test code;
- a Fabric client probe mod/source set;
- a protocol bot/library;
- a packet trace by itself;
- successful server startup;
- successful login alone when the requirement is a later gameplay/UI behavior.

Never relabel one of these as "vanilla" to make the matrix green.

## Black-box evidence model

Because an unmodified client cannot write internal assertion results for the test, choose observable evidence that matches the contract.

### Connectivity/compatibility contract

For a requirement such as "vanilla players can join and remain connected":

- production mod is confirmed loaded on the physical server;
- the unmodified client reaches the intended protocol/play state;
- the connection remains healthy for a bounded scenario window;
- no required-client-mod/custom-channel disconnect occurs;
- relevant server-side join/player state is correct.

This can usually be automated without reading client internals.

### Client action → server effect

For a requirement driven by a vanilla player action, use an existing black-box input harness if available, then assert the resulting authoritative server state. Examples include movement, interaction, command/chat input where appropriate, container interaction, and acknowledgements visible to the server.

Do not assume that a protocol bot exercises the same vanilla client implementation path.

### Server action → client-visible effect

This is the difficult case. Server-side state plus packet emission does **not** directly prove that an unmodified client displayed/applied the intended result.

Use one of these, in order:

1. an existing external black-box client automation harness that can observe the required client-visible result without modifying the client;
2. a vanilla protocol acknowledgement/response that causally demonstrates the relevant client handling, when the protocol provides one and that acknowledgement is actually sufficient for the contract;
3. a manual vanilla-client gate when the effect is visual/UI-only and no faithful black-box automation exists.

If none is available, report the vanilla-visible portion as `BLOCKED` or `MANUAL GATE REQUIRED`. Do not substitute an instrumented Fabric client and call the requirement proven.

## Relationship to instrumented E2E

Instrumented split-process E2E is complementary:

```text
Vanilla compatibility gate
→ proves the no-client-mod product contract at black-box level

Instrumented Fabric client/probe
→ provides deeper client-state assertions and faster diagnosis
```

When a vanilla compatibility test fails, an instrumented Fabric client or focused packet trace may help isolate the defect. Passing the diagnostic route does not erase the failing vanilla gate.

## Physical server requirement

For a server-only Fabric mod, prefer a physical production-like dedicated-server launch before the vanilla client connects. Use Loom production-run facilities when they fit the repository/version. If the final contract depends on both physical-server loading and vanilla-client behavior, record evidence for both boundaries.

## Result reporting

Always state client fidelity explicitly:

```text
Client fidelity: unmodified-vanilla
Vanilla gate: PASS | FAIL | BLOCKED | MANUAL GATE REQUIRED
Observation type: black-box-process | external-ui | protocol-ack | manual
Server artifact/environment: <task/artifact>
Scenario: <one behavior>
Evidence: <bounded concrete observations>
Limitations: <what client internals were not observed>
```

A vanilla PASS must never be inferred from the words `client test`, `production client`, or `split-process` alone.

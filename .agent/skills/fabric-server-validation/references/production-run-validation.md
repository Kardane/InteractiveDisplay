# Loom production-run validation

Use this reference when release/remapping/launcher/physical-side fidelity matters. These are Fabric Loom facilities, unlike the custom split-process harness.

## Why this tier exists

Fabric Loom's production run tasks execute remapped mod artifacts in an environment closer to distribution than ordinary development runs. Current Fabric documentation explicitly recommends production testing because development and production differ slightly after remapping.

Current Loom exposes task types conceptually like:

```gradle
tasks.register("prodServer", net.fabricmc.loom.task.prod.ServerProductionRunTask) {
    installerVersion = "<compatible Fabric installer>"
    // loaderVersion/minecraftVersion may default from the project in current Loom.
    // mods.from ... must point at remapped production-compatible artifacts.
    runDir = file("build/validation/prod-server")
}
```

and:

```gradle
tasks.register("prodClient", net.fabricmc.loom.task.prod.ClientProductionRunTask) {
    useXVFB = true // useful on supported headless CI; inspect project/OS first
}
```

These are patterns, not guaranteed copy-paste syntax for every Loom version. Inspect the project's Loom version and exact task API before editing Gradle.

## ServerProductionRunTask

Use when you need to verify:

- the remapped production mod loads;
- a physical dedicated-server Loader environment activates `environment: server` metadata;
- dedicated `server` entrypoints/server-scoped Mixins initialize;
- production-like server launcher behavior differs from the dev run;
- release smoke tests should catch mapping/packaging/runtime differences.

Current Fabric documentation states that the server production run task uses the same server launcher distributed by Fabric and is intended to keep the environment as close to production as possible.

A successful server start proves physical-server/package initialization, not client synchronization by itself.

## Production Client GameTest

Fabric's current automated-testing guide demonstrates a production client gametest by registering a `ClientProductionRunTask` and adding:

```gradle
jvmArgs.add("-Dfabric.client.gametest")
```

with Fabric API on the production runtime configuration. The documentation uses the example task name `runProductionClientGameTest`; do not assume that task already exists.

Use this to re-run client gametests in a production-style **Fabric client** runtime/CI environment. It is especially useful for catching differences that ordinary `runClientGameTest` in the development runtime misses.

This is not an unmodified vanilla-client compatibility test. A `ClientProductionRunTask`-based GameTest still belongs to the Fabric test/runtime path and must be labeled accordingly.

It also does **not** turn an in-process dedicated server created by Client GameTest into a fresh physical-server Loader process. If server-only production initialization and deep instrumented client observation must be proven in the same scenario, use instrumented split-process E2E after the production server route is validated. If the product contract instead requires an unmodified vanilla client, use the separate vanilla compatibility gate.

## Recommended escalation

```text
normal server/client GameTest proves behavior
        ↓ release/environment risk exists
Loom production run proves production artifact/environment
        ↓ choose the actual client-fidelity contract
        ├─ unmodified vanilla required → vanilla compatibility E2E
        └─ deep probe assertions required → instrumented split-process E2E
        ↓ ambiguous network failure only
focused packet trace
```

## Discovery before adding tasks

1. Run `./gradlew tasks --all --console=plain` (or wrapper equivalent).
2. Search build scripts for `ServerProductionRunTask`, `ClientProductionRunTask`, `productionRuntimeMods`, and existing production/test tasks.
3. Reuse existing tasks and run directories when they already express the intended contract.
4. Resolve exact Loom version/API from the repository.
5. Keep generated run directories under `build/` or the repository's existing ignored test-run location.

## EULA and credentials

A physical server may require EULA acceptance. Do not create or flip EULA acceptance automatically. Reuse an explicit repository/user opt-in or report the run as BLOCKED.

Do not introduce real player credentials merely to run a validation path. Prefer local/offline test behavior where the official test framework supports it.

## Official references checked when authored

- Fabric Loom Production Run Tasks: https://docs.fabricmc.net/develop/loom/production-run-tasks
- Fabric Automated Testing: https://docs.fabricmc.net/develop/automatic-testing

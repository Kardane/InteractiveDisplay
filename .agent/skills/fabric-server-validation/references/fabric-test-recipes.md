# Fabric test recipes

These recipes describe patterns, not copy-paste guarantees. Fabric and Minecraft APIs are version-sensitive. Inspect the repository and exact dependency sources before importing classes or targeting methods.

## 1. Project-first version discipline

Before editing Gradle:

1. Read `gradle.properties`, version catalogs, and build scripts.
2. Determine whether the project uses Groovy or Kotlin DSL.
3. Determine its mappings style.
4. List Gradle tasks.
5. Search for existing `fabricApi.configureTests`, `fabric-loader-junit`, GameTest entrypoints, and test source sets.
6. Reuse existing conventions.

Do not migrate mappings, Loom, Fabric API, Java, or Minecraft versions merely to get a test API unless the user asked for an upgrade.

## 2. Unit tests with Fabric Loader JUnit

Current Fabric documentation shows Fabric Loader JUnit as the route for unit tests that need Minecraft/Fabric runtime behavior.

Conceptual Groovy form:

```gradle
dependencies {
    testImplementation "net.fabricmc:fabric-loader-junit:${project.loader_version}"
}

test {
    useJUnitPlatform()
}
```

Use only syntax compatible with the project’s Loader/Loom generation and build DSL.

Some registry-dependent tests need Minecraft bootstrap initialization. Inspect the exact version’s test docs/source before adding bootstrap calls.

## 3. Configuring Fabric GameTests

Modern Fabric Loom/Fabric API versions expose a test DSL similar to:

```gradle
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "example-test-mod"
        enableGameTests = true
        enableClientGameTests = true
        // eula = true  // DO NOT add unless the user/repository has explicitly accepted it.
    }
}
```

Important:

- `createSourceSet = true` keeps test code/resources separate from production.
- The test mod id must not collide with the production mod id.
- Do not blindly add `eula = true`. That is an EULA acceptance setting.
- On older Fabric versions, the DSL/import names may differ or not exist.

## 4. Server GameTest pattern

Modern Fabric exposes a GameTest entrypoint/annotation API. A conceptual test looks like:

```java
public final class FeatureGameTests {
    @GameTest
    public void verifiesServerBehavior(GameTestHelper helper) {
        // arrange deterministic world state
        // act
        // assert authoritative world state
        helper.succeed();
    }
}
```

Exact package names, annotation names, helper methods, and registration style vary by version. Resolve them from the project dependency sources.

Test design rules:

- one feature contract per test
- use test-relative positions where the framework expects them
- avoid sleeps
- bound asynchronous success by ticks
- assert negative cases when the bug involved over-broad effects

## 5. Dedicated-server Client GameTest pattern

Current Fabric API marks the `net.fabricmc.fabric.api.client.gametest.v1` package `@Experimental`. Treat exact classes, signatures, and synchronization behavior as version-sensitive and verify them against the project's Fabric API Javadocs/sources.

On current Fabric Client GameTest APIs, a client test can create an **in-process** dedicated server and connect to it. This is the preferred low-friction E2E topology only when the production mod and the feature's relevant initialization path are active in that client JVM. The client is a Fabric test client; **do not call it unmodified vanilla or use it as a vanilla-compatibility PASS.**

**Important physical-side caveat:** an in-process Client GameTest is not equivalent to a fresh physical dedicated-server Loader launch. If production behavior relies on `environment: server`, a dedicated `server` entrypoint, server-scoped Mixins, or explicit physical-side checks, first use `production-run-validation.md` to verify the physical production-server path. If the product contract requires an unmodified vanilla client, continue with `vanilla-compatibility-e2e.md`; if it requires deep machine-readable assertions from a separate probe client, use `split-process-e2e.md`. Do not weaken production loading metadata merely for the test.

Before using this shape, assert/check that the production mod id and the feature's relevant production initialization path are active in the test process. Mod-loaded alone is insufficient if the feature is registered only from a physical-server path.

Conceptual shape:

```java
public final class DedicatedServerSyncTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var server = context.worldBuilder().createServer();
             var connection = server.connect()) {

            server.runOnServer(minecraftServer -> {
                // arrange authoritative state on the server thread
            });

            // Trigger the behavior using the closest realistic interface.
            // Examples: client input, a command, or a registered custom payload.

            context.waitTick();

            var authoritative = server.computeOnServer(minecraftServer -> {
                // return a compact immutable value, not a live server object
                return /* value */;
            });

            var clientLevel = connection.getClientLevel();
            // Assert client-observed state using client gametest helpers.
        }
    }
}
```

Current Fabric API documentation states that Client GameTests synchronize packet processing so that a tick wait is sufficient for packets to be handled before the next tick. Treat that as version-specific behavior and verify it for the project’s Fabric API.

### Why use `computeOnServer`

Do not leak mutable server objects to the client test thread. Compute a compact value on the server thread and return that value for comparison.

Good returns:

- coordinates
- booleans
- primitive counts
- strings/identifiers
- immutable records made for the test

Avoid returning:

- live entities
- levels/world objects
- mutable inventories
- network handlers

## 6. Production-run verification before custom E2E

For release/remapping/physical-side fidelity, prefer Loom production run tasks before writing custom orchestration. Current Loom documents `ServerProductionRunTask` and `ClientProductionRunTask`, and Fabric's automated-testing guide demonstrates a production client gametest task based on `ClientProductionRunTask`.

Use `references/production-run-validation.md` for the decision and Gradle patterns. Do not assume example task names exist in the target repository.

## 7. EULA handling

If dedicated tests require EULA acceptance:

- If the repository already has an explicit test EULA opt-in, use it.
- If the user explicitly instructs you to enable the EULA setting and has accepted the terms, apply it.
- Otherwise, do not change the setting. Mark the E2E run as blocked and still prepare the test code/configuration that does not constitute acceptance.

## 8. Network synchronization and low-level hooks

Modern Fabric Client GameTest can synchronize packet processing around client/server ticks. Low-level Netty hooks may conflict with that mechanism.

Therefore:

1. First instrument owned custom payload send/receive code.
2. If needed, instrument decoded packet-object send/dispatch points.
3. Avoid directly modifying the Netty pipeline.
4. Only if the bug is actually in the pipeline, use Netty instrumentation and document any network-synchronizer setting you had to change.

## 9. Useful command-selection strategy

Never assume task names. Discover them first.

Typical candidates across Fabric projects include:

```text
test
check
build
runGameTest
runClientGameTest
runServer
<custom ServerProductionRunTask>
<custom ClientProductionRunTask / production client gametest task>
```

`prodServer` and `runProductionClientGameTest` are documentation examples, not universal built-in task names.

Use the exact tasks printed by the project.

Run narrow before broad:

```text
relevant single unit test
→ relevant Server/Fabric Client GameTest
→ production-run verification when release/physical-side fidelity matters
→ choose required client fidelity:
   unmodified vanilla black-box gate OR instrumented split-process E2E
→ check/build
```

## 10. CI behavior

If the project already runs tests in CI, integrate with that convention. Preserve Gradle/JUnit reports. For packet diagnostics, upload or retain `build/validation/**` only on failure when feasible so successful CI output stays small.

For headless client tests, modern Loom/Fabric supports virtual-framebuffer execution in Linux CI. Verify the exact project version and existing CI before adding packages or production-run tasks.

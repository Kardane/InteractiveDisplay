# Integration fixture matrix

Use this matrix when validating the skill itself against real Fabric repositories or when introducing it into CI. The bundled `self_test.py` is only a synthetic smoke test; it does not launch Minecraft.

## Fixture A — common/environment `*` with Fabric Client GameTest

Purpose: prove the low-friction Fabric-native client/server route without overstating client fidelity.

Minimum characteristics:

- production `fabric.mod.json` uses `environment: "*"` or otherwise loads the relevant initialization in the client test JVM;
- modern Fabric API with Client GameTest support;
- one deterministic server mutation that must become visible in the client;
- no physical-server-only entrypoint/Mixin required for the feature.

Expected route:

```text
Server GameTest if useful
→ Fabric Client GameTest
→ optional production Fabric Client GameTest
```

Acceptance:

- authoritative server assertion passes;
- Fabric test-client-observed assertion passes;
- report labels client fidelity as Fabric Client GameTest, not vanilla;
- packet trace is not required for a normal PASS.

## Fixture B — physical-server-sensitive production mod with instrumented client

Purpose: prove that the skill does not mistake an in-process Client GameTest server for a fresh physical server Loader launch, and that the custom E2E client is labeled accurately.

Minimum characteristics:

- production mod has at least one real physical-side constraint: `environment: "server"`, dedicated `server` entrypoint, server-scoped Mixin, or explicit physical-side branch;
- production server task runs the remapped artifact;
- one client-visible behavior requires deep machine-readable observation from a separate Fabric probe/test client.

Expected route:

```text
Server GameTest for narrow logic when useful
→ ServerProductionRunTask-based production server verification
→ instrumented split-process E2E only for the remaining client-observed contract
```

Acceptance:

- production server load/initialization is explicitly evidenced;
- split-process is not used merely to prove startup/loading;
- client fidelity is recorded as `fabric-probe` (or another accurate non-vanilla label);
- when split-process is required, both server-result and client-result independently pass;
- no instrumented result is reported as vanilla compatibility proof.

## Fixture C — server-side-only mod with unmodified vanilla-client contract

Purpose: prove the skill keeps vanilla compatibility independent from Fabric client tests and protocol bots.

Minimum characteristics:

- production server is Fabric with the actual server-side mod artifact;
- product contract explicitly requires no client-side mod;
- an authorized, unmodified vanilla Minecraft client installation is available;
- at least one bounded compatibility scenario can be observed externally or has an explicit manual gate.

Expected route:

```text
Server GameTest / Fabric Client GameTest for fast internal evidence where useful
→ ServerProductionRunTask-based physical server verification
→ unmodified vanilla-client black-box compatibility gate
→ instrumented client or packet trace only for diagnosis if needed
```

Acceptance:

- `Fabric Client GameTest`, Fabric probe, protocol bot, and vanilla client are reported as distinct fidelities;
- a vanilla PASS is issued only if the unmodified vanilla-client boundary was actually exercised;
- login-only evidence is not used to prove a later gameplay/UI behavior;
- visual/UI-only behavior without faithful black-box automation is marked `MANUAL GATE REQUIRED` or `BLOCKED`, not silently replaced with a probe client;
- no credentials/session tokens are captured into artifacts.

## Fixture D — older/alternate Fabric generation

Purpose: prove version discipline and graceful fallback.

Minimum characteristics:

- Minecraft/Fabric/Loom generation whose testing API differs from the current documentation line or lacks the modern Client GameTest shape;
- existing repository conventions that must be preserved.

Expected route:

- inspector reports the actual version/task surface;
- Codex does not paste current 26.2 imports/task APIs blindly;
- it uses the repository's existing tests or the least-invasive version-compatible fallback;
- if the user requires vanilla compatibility, that requirement remains separate even when modern Client GameTest APIs are unavailable.

Acceptance:

- no unrelated Minecraft/Fabric/Loom upgrade is introduced just to obtain a test API;
- limitations are reported explicitly if no faithful client E2E route exists.

## Release gate for this skill package

Before claiming that the skill itself has been integration-tested across Fabric versions and client fidelities, run it against all four real fixture profiles and record:

```text
fixture repo/commit
Minecraft version
Loader/Fabric API/Loom versions
Java version
commands executed
expected route selected
actual route selected
client fidelity
vanilla gate status when applicable
PASS/FAIL/BLOCKED/MANUAL GATE REQUIRED
artifact paths
known limitations
```

Do not equate the bundled synthetic helper smoke tests with this real-Minecraft fixture gate.

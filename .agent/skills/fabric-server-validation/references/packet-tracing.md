# Packet tracing for Fabric validation

Packet analysis is valuable when it answers a specific question. It becomes counterproductive when every packet is captured without a hypothesis.

## Primary principle

Trace at the highest semantic layer that still distinguishes the failure.

```text
project payload object
    ↓ if insufficient
Minecraft packet object
    ↓ if insufficient
codec/frame
    ↓ if insufficient
Netty bytes
```

## 1. Owned custom payloads

For payloads defined by the mod, do not begin with Mixins.

Prefer a test-only observer around the send/receive boundary already owned by the project. Record:

- payload identifier/type
- direction
- selected decoded fields
- player/connection alias
- server tick
- scenario/test name

Do not record the entire encoded buffer unless codec bytes are the thing under test.

For current Fabric networking APIs, custom payload types are registered for their play/configuration direction and sent/received through Fabric networking helpers. Exact method/type names vary across Minecraft/Fabric versions, so use the project’s own imports as the source of truth.

## 2. Vanilla packet-object tracing

Fabric API networking helpers focus on custom payloads; do not assume there is a stable public Fabric event that observes every vanilla packet for every project version.

When a vanilla packet itself must be observed, use a **test-only Mixin** or an existing project instrumentation point.

### Choosing a Mixin target

Never target a method by memory.

For the exact project version:

1. Generate/open Minecraft sources using the project’s Loom setup.
2. Locate the connection/network listener class actually used on the dedicated server.
3. Identify the narrowest method where a decoded packet object is available:
   - outbound: immediately before the packet is handed to the connection/encoder
   - inbound: immediately after decode or immediately before server listener dispatch
4. Confirm method descriptor, generic erasure, overloads, and thread.
5. Add the Mixin only to the gametest/test mod config when possible.
6. Gate recording by a system property or active-test flag.

Prefer a point above Netty so Fabric Client GameTest network synchronization remains intact.

### Avoid broad reflection dumps

Packet classes change frequently and can contain:

- components/text
- commands/chat
- large registries/tags
- NBT/data components
- opaque custom payload bytes

Define explicit summarizers:

```java
interface PacketSummarizer {
    boolean supports(Object packet);
    Map<String, Object> summarize(Object packet);
}
```

Only implement summarizers needed by current tests.

## 3. Trace record design

A trace record should answer:

- Which test generated it?
- Which connection?
- Which direction?
- Which protocol phase?
- At what causal sequence/tick?
- Which packet/payload type?
- Which few fields mattered?

Use JSONL so tools can stream/filter without loading a huge trace into model context.

Do not make wall-clock time the primary ordering key. Use a monotonically increasing sequence and Minecraft ticks when available.

## 4. Connection aliases

Runtime connection identifiers are noisy. Assign deterministic aliases inside the test, for example:

```text
client-0
player-under-test
observer-0
```

If player UUIDs are needed for correlation, keep them in the trace only when they are test-generated/non-sensitive. Prefer aliases in assertions.

## 5. Dynamic value normalization

Do not compare traces on unstable values such as:

- ephemeral TCP ports
- monotonic/wall times
- runtime entity ids unless explicitly bound by the test
- random UUIDs
- packet sequence values unrelated to behavior

Translate runtime identifiers to scenario aliases before assertion when possible.

## 6. Cardinality and order

Packet counts can be useful but brittle.

Assert `count == 1` only when duplicate sending is itself incorrect. Otherwise prefer `count >= 1` within a narrow tick/action window.

Order assertions should involve the minimum necessary subsequence, for example:

```text
spawn(target) before metadata(target)
```

Do not snapshot the order of unrelated keepalive, chunk, recipe, registry, or tracking traffic.

## 7. Bounded recording

Use one or more of:

- packet-type allowlist
- payload-id allowlist
- connection allowlist
- protocol-phase filter
- active scenario flag
- tick window
- bounded ring buffer

A useful default policy is:

- keep summaries/counts for the whole scenario
- retain detailed records only for allowlisted packet types
- on failure, print/export a small window around matching records

## 8. Privacy/safety defaults

Redact by default:

- authentication/session information
- signed chat internals unless specifically under test
- full command/chat text when not needed
- arbitrary custom-payload byte arrays
- plugin/mod secrets or tokens

Prefer structural summaries such as lengths, identifiers, enums, booleans, coordinates, ids mapped to aliases, and hashes only when a stable hash meaningfully proves equality.

## 9. Wire/Netty tracing

Only descend to bytes when you are testing:

- frame length/VarInt boundaries
- compression
- encryption transition
- codec mismatch
- malformed bytes
- a peer disconnect before object decode

At this level:

- keep the server local
- redact payload bytes
- record frame sizes/state transitions before content
- preserve packet/state tests alongside byte evidence
- expect version-specific pipeline details

If a Client GameTest network synchronizer must be disabled due to low-level hooks, note that in the final validation limitations and compensate with stronger explicit tick/event waits.

## 10. Failure localization examples

### Server state correct, client stale

Trace only the likely sync packets to the affected connection. If absent, inspect tracking/send conditions. If present, inspect fields and client application.

### Duplicate inventory update

Record only container/inventory packets for one menu/session and assert cardinality inside the action tick window.

### Teleport rubber-band

Correlate:

- server authoritative position
- outbound teleport/position-correction packet summary
- inbound movement/ack if relevant
- client position after synchronized ticks

Avoid collecting unrelated chunk and entity traffic.

### Custom payload ignored

Assert:

- payload type registered for the correct direction/phase
- sender says peer can receive if the API exposes that capability
- payload send occurred
- receiver executed
- resulting server/client state changed

The final state change is the pass criterion unless the packet contract itself is the requirement.

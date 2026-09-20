# Fabric server-side diagnostic playbooks

Use these as test-shaping recipes. Exact packet/class/API names depend on the repository's Minecraft/Fabric version and mappings.

## 1. Custom C2S payload mutates server state

Proof target:

```text
client action
→ C2S custom payload handled
→ authorization/validation
→ server state mutation
```

Preferred evidence:

1. Unit-test payload validation/codec separately when useful.
2. Network E2E sends the real payload or triggers the real UI/action.
3. Assert authoritative server state.
4. Add a semantic payload trace only if handler execution is ambiguous.

Do not use packet arrival as the final PASS if the requirement is a state mutation.

Negative cases often matter more here: unauthorized player, stale id, malformed bounds, wrong dimension, replay/duplicate action.

## 2. Server mutation must synchronize to clients

Examples: tracked entity data, block/entity state, inventory state, scoreboard/bossbar-like state.

Proof target:

```text
server mutation
→ tracking/sync decision
→ network update
→ affected client's local state
```

Preferred test:

- assert server state first
- assert the target client's corresponding state within a tick bound
- when audience/tracking matters, also assert an uninvolved observer did **not** receive/apply the update

Only add packet trace if the client state is stale or recipient scoping is the bug.

## 3. Entity spawn / metadata / removal

Use stable scenario aliases for entities. Runtime entity ids are correlation data, not golden values.

Useful assertions:

- authoritative entity exists/removed on server
- target client sees/loses corresponding entity
- relevant metadata value matches client-side representation
- observer visibility follows tracking rules

If ordering is suspected, assert only the causal subsequence such as `spawn(target) → metadata(target)`, not all packets between them.

## 4. Teleport / movement reconciliation

Rubber-banding is a boundary-crossing failure, so prefer real client E2E.

Capture/compare:

- server authoritative position and dimension
- client position after bounded synchronized ticks
- teleport/position-correction packet only when needed
- C2S acknowledgment/movement only when it helps explain a correction loop

Do not compare every movement packet. Record a small window around the teleport landmark.

## 5. Inventory / menu synchronization

First define whether the contract is server inventory truth, menu/container state, or what the player's client displays.

Typical proof:

```text
open/act on menu
→ server container mutation
→ slot/data synchronization
→ client menu state
```

Scope traces by container/menu session and connection. Dynamic container ids should be aliased. Exact packet count is usually brittle unless duplicate updates are the bug.

## 6. Commands and permissions

Most command logic does **not** need packet tracing.

Use Server GameTest or loader-backed unit tests for:

- registration
- parsing
- permission predicates
- server state effects
- failure behavior

Add client/network E2E only when the requirement explicitly includes client-visible feedback, suggestions, or multiplayer behavior.

Never record full command text if arguments may contain secrets or personal data; preserve only the structural fields required by the test.

## 7. Login / configuration / play-phase behavior

This is one of the cases where protocol phase matters.

Record:

- connection alias
- phase transition landmarks
- owned payload ids or narrowly selected vanilla packet types
- disconnect reason category, sanitized

Avoid collecting authentication/session material. For physical-server-sensitive mods, use split-process E2E because Loader-side environment fidelity is part of the contract.

## 8. Disconnect / rejection behavior

A disconnect test should assert both cause and resulting lifecycle state:

- server deliberately rejected or closed the connection for the expected reason category
- client reached disconnected state
- server cleaned up player/session state

Packet/wire evidence is secondary unless the bug is codec/framing-level.

## 9. Tracking / recipient bugs

Use at least two deterministic client aliases when recipient selection is the requirement:

```text
player-under-test  — should receive
observer-0         — should not receive
```

Assert application state first. A focused packet trace can then establish whether the wrong audience was selected server-side or the client applied state incorrectly.

## 10. Flaky timing bug

Do not respond by adding larger sleeps.

Instead identify a semantic barrier:

- end of server tick
- player join complete
- chunk/entity tracking established
- handler completion
- client packet application
- menu open/close event

Bound the number of ticks/events. After a fix, repeat the narrow deterministic test several times before the broader suite.

# Packet trace schema and declarative assertions

The bundled scripts intentionally use a small, tolerant JSONL schema. Extra fields are allowed.

## Trace line

Recommended shape:

```json
{
  "schema": 1,
  "test": "teleport_sync",
  "seq": 17,
  "server_tick": 204,
  "client_tick": 203,
  "direction": "S2C",
  "phase": "PLAY",
  "connection": "player-under-test",
  "packet": "ClientboundPlayerPositionPacket",
  "payload": null,
  "fields": {
    "x": 100.0,
    "y": 64.0,
    "z": 100.0,
    "relative": false
  }
}
```

Required by convention:

- `schema`: integer, currently `1`
- `test`: scenario identifier
- `seq`: monotonic integer within the recorder
- `direction`: `C2S` or `S2C`
- `packet`: stable class/simple-type name or semantic packet label

Recommended when known:

- `server_tick`
- `client_tick`
- `phase`: `HANDSHAKE`, `STATUS`, `LOGIN`, `CONFIGURATION`, `PLAY`, or project-specific equivalent
- `connection`: deterministic alias
- `payload`: custom payload identifier
- `fields`: allowlisted decoded values

## Event records

The same JSONL file may contain non-packet landmarks. Set `kind`:

```json
{"schema":1,"kind":"landmark","test":"teleport_sync","seq":12,"server_tick":200,"name":"action:teleport"}
```

Packet records may omit `kind` or use `"kind":"packet"`.

Landmarks make it easier to scope traces around the causal action.

## Declarative assertion file

`assert_trace.py` accepts this structure:

```json
{
  "expect": [
    {
      "name": "teleport packet sent",
      "match": {
        "direction": "S2C",
        "connection": "player-under-test",
        "packet": {"regex": "Teleport|Position"},
        "fields.x": 100.0,
        "fields.y": 64.0,
        "fields.z": 100.0
      },
      "count": {"min": 1, "max": 2}
    }
  ],
  "forbid": [
    {
      "name": "no teleport to observer",
      "match": {
        "direction": "S2C",
        "connection": "observer-0",
        "packet": {"regex": "Teleport|Position"},
        "fields.x": 100.0,
        "fields.y": 64.0,
        "fields.z": 100.0
      }
    }
  ]
}
```


## Global and local scope

A spec may define a top-level `scope` applied before all assertions:

```json
{
  "scope": {"test": "teleport_sync", "phase": "PLAY"},
  "expect": [],
  "forbid": []
}
```

Each `expect`, `forbid`, or `ordered` entry may also define its own `scope`. Use this to keep assertions readable without relying on accidental uniqueness.

## Minimal ordering assertions

When order is behaviorally relevant, use an `ordered` subsequence instead of snapshotting the whole stream:

```json
{
  "ordered": [
    {
      "name": "spawn precedes metadata",
      "scope": {"connection": "player-under-test"},
      "steps": [
        {"packet": {"regex": "Spawn"}, "fields.entity_alias": "target"},
        {"packet": {"regex": "Metadata|EntityData"}, "fields.entity_alias": "target"}
      ],
      "max_server_ticks": 2
    }
  ]
}
```

The script finds the first in-order subsequence. It does not require unrelated packets to be absent between steps.

## Match operators

A match key can be a top-level field or dotted path such as `fields.entity_id`.

A scalar means equality.

Operator objects supported by the bundled script:

```json
{"regex": "Teleport.*"}
{"in": ["PLAY", "CONFIGURATION"]}
{"gte": 1}
{"lte": 5}
{"gt": 0}
{"lt": 100}
{"exists": true}
```

Use one operator per field for clarity.

## Count semantics

For `expect`:

- no `count` means at least one match
- `{"eq": 1}` means exactly one
- `{"min": 1}` means one or more
- `{"max": 2}` sets only an upper bound; normally pair it with `min`

For `forbid`, any match fails.

## Scope before assertion

Prefer to emit only relevant records. If a trace contains multiple tests or connections, add those fields to each assertion instead of relying on accidental uniqueness.

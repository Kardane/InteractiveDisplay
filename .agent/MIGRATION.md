# Migration from the two separate skill packs

## Keep

All original skills from `skills(3).zip` are retained. Their specialist references and scripts remain in place.

## Add

- `mcdev-source-analysis`
- `fabric-gametest`
- `mcpfabric-runtime`
- `carpet-player-harness`
- `mineflayer-e2e`

## Remove / do not install in parallel

- `minecraft-fabric-test-orchestrator`

Its responsibilities are merged into `fabric-server-validation`. If you already installed it globally or in the project, remove that skill directory to avoid two orchestration policies matching the same prompts.

## Modified existing skills

### `minecraft-fabric-server-dev`

Now acts explicitly as the implementation orchestrator and delegates source analysis and behavioral validation.

### `fabric-server-validation`

Remains the single validation orchestrator and gains:

- a B+ structured runtime lane using MCP Fabric + optional Carpet fake players;
- explicit delegation to `fabric-gametest` for server GameTest mechanics;
- explicit Mineflayer routing for protocol-bot contracts;
- preservation of the original vanilla-client compatibility distinction.

### `minecraft-java-reference-hub`

Now states its boundary with source-level Fabric/Mixin development to reduce accidental overlap.

## Suggested prompts

General feature/bug work:

```text
$minecraft-fabric-server-dev 이 변경을 구현해. Minecraft 내부 호출이나 Mixin target은 추측하지 말고 필요하면 mcdev-source-analysis를 사용하고, 완료 후 fabric-server-validation으로 가장 싼 충분한 검증을 수행해.
```

Runtime reproduction:

```text
$fabric-server-validation 이 버그를 GUI 없이 재현해. 실행 중 dev server에서 가능하면 MCP Fabric + Carpet fake player로 먼저 실패 상태를 재현하고, 이후 가능한 경우 GameTest 회귀 테스트로 고정해.
```

Protocol-specific bug:

```text
$fabric-server-validation 이 문제에서 실제 socket/login lifecycle이 필요한지 먼저 판단해. 필요할 때만 mineflayer-e2e를 사용하고 protocol-bot evidence로 명확히 라벨링해.
```

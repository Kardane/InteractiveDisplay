# 모델/아이템/블록 상태 구조 가이드 (1.21.8)

## 목차

1. 전체 의존 관계
2. `blockstates` 규칙
3. `models/block` 규칙
4. `models/item` 규칙
5. `items`(아이템 표시 로직) 규칙
6. 트러블슈팅
7. 근거

## 1) 전체 의존 관계

블록 렌더 경로:
- `blockstates/*.json` -> `models/block/*.json` -> `textures/*`

아이템 렌더 경로(1.21.8 바닐라 기준):
- `items/*.json` -> `models/item/*.json` -> `textures/item/*`

## 2) `blockstates` 규칙

경로:
- `assets/<ns>/blockstates/<block_id>.json`

핵심 패턴:
- `variants`: 상태 조합별 모델 매핑
- `multipart`: 조건별 모델 조합

### `variants` 예시

```json
{
  "variants": {
    "": {
      "model": "minecraft:block/oak_planks"
    }
  }
}
```

### `multipart` 예시

```json
{
  "multipart": [
    {
      "apply": { "model": "minecraft:block/dark_oak_fence_post" }
    },
    {
      "when": { "north": "true" },
      "apply": { "model": "minecraft:block/dark_oak_fence_side", "uvlock": true }
    }
  ]
}
```

## 3) `models/block` 규칙

경로:
- `assets/<ns>/models/block/*.json`

주요 키:
- `parent`
- `textures`
- `elements`
- `display` (필요 시)
- `ambientocclusion`

### 기본 예시

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "minecraft:block/oak_planks"
  }
}
```

### `elements` 사용 예시

```json
{
  "textures": {
    "side": "minecraft:block/cake_side",
    "top": "minecraft:block/cake_top"
  },
  "elements": [
    {
      "from": [1, 0, 1],
      "to": [15, 8, 15],
      "faces": {
        "up": { "texture": "#top" },
        "north": { "texture": "#side" }
      }
    }
  ]
}
```

## 4) `models/item` 규칙

경로:
- `assets/<ns>/models/item/*.json`

주요 키:
- `parent`
- `textures`
- `display`
- `gui_light`

예시:

```json
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "minecraft:item/diamond_sword"
  }
}
```

## 5) `items`(아이템 표시 로직) 규칙

경로:
- `assets/<ns>/items/*.json`

1.21.8 바닐라에서 자주 보이는 `model.type`:
- `minecraft:model`
- `minecraft:condition`
- `minecraft:select`
- `minecraft:range_dispatch`
- `minecraft:composite`
- `minecraft:special`

`specific` 타입(예: `minecraft:head`, `minecraft:shield`, `minecraft:trident`, `minecraft:bundle/selected_item`)도 사용된다.

### 최소 예시 (`minecraft:model`)

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "minecraft:item/diamond_sword"
  }
}
```

### 조건 분기 예시 (`minecraft:condition`)

```json
{
  "model": {
    "type": "minecraft:condition",
    "component": "minecraft:lodestone_tracker",
    "on_true": {
      "type": "minecraft:model",
      "model": "minecraft:item/compass_16"
    },
    "on_false": {
      "type": "minecraft:model",
      "model": "minecraft:item/compass_00"
    }
  }
}
```

### 범위 분기 예시 (`minecraft:range_dispatch`)

```json
{
  "model": {
    "type": "minecraft:range_dispatch",
    "property": "minecraft:time",
    "entries": [
      { "threshold": 0.0, "model": { "type": "minecraft:model", "model": "minecraft:item/clock_00" } },
      { "threshold": 0.5, "model": { "type": "minecraft:model", "model": "minecraft:item/clock_01" } }
    ]
  }
}
```

실무 메모:
- 바닐라 1.21.8에서는 `models/item`의 `overrides`보다 `items/*.json` 분기 구조가 핵심이다.

## 6) 트러블슈팅

1. 블록이 보라/검정 체크무늬로 뜸
- `model` 경로 오타 또는 텍스처 경로 누락이 거의 원인

2. 아이템이 기본 모델로만 보임
- `items/<id>.json` 미배치 또는 `model.type` 구조 오류

3. 회전/UV 이상
- `blockstates` 회전값(`x`, `y`, `uvlock`)과 모델 좌표계 불일치

4. 조건 분기 무시
- `component`/`property` 키를 잘못 쓴 경우

## 7) 근거

- 1.21.8 client JAR 실측:
  - `assets/minecraft/blockstates` 1107개
  - `assets/minecraft/models` 3426개
  - `assets/minecraft/items` 1416개
- 추출 시각: 2026-03-05 (KST)

# InteractiveDisplay — 서버사이드 3D HUD 프레임워크

> **기준 버전**: Minecraft Java Edition 1.21.8 · Fabric · JDK 21  
> **최종 수정**: 2026-03-06  
> **상태**: 구현 진행 중 (Phase 1 / 1.5 / 2 주요 항목 반영)

---

## 0. 진행 현황

### 2026-03-06 기준 완료

- `Phase 1`
  - 창 정의 로드/검증
  - `text`, `button`, `panel`, `image` 기본 렌더
  - `FIXED` 생성/제거/리로드
  - 명령어/디버그 명령
- `Phase 1.5`
  - 구조화 로그
  - 최근 실패 스냅샷
  - 디버그 status/recent/window/bindings
- `Phase 2 핵심`
  - `PLAYER_FIXED`, `PLAYER_VIEW`
  - hover, pooling, interpolation
  - `run_command`, `callback`
  - `Polymer` 자동 리소스팩 빌드/호스팅
  - config 이미지 -> Polymer source asset 병합
  - 포인터 아이템 기반 입력
  - `interaction` 엔티티 제거 후 raycast 기반 hit-test
- `추가 확장`
  - 버튼 `fontSize`, `clickSound`
  - `switch_mode_fixed`, `switch_mode_player_fixed`
  - `open_window`를 현재 창 교체 방식으로 전환
  - 그룹 정의 `groups/*.json`
  - `/interactivedisplay group create/remove/list`
  - `player_fixed` 상대 각도 `~ ~`, `~15 ~-10`
  - `PLAYER_FIXED` orbit yaw/pitch 의미 재정의
  - `PLAYER_FIXED`, `PLAYER_VIEW` deadzone/smoothing/threshold 적용

### 현재 구현 기준으로 원안과 달라진 점

- 원안의 `interaction` 엔티티 기반 입력은 폐기됨
- 현재 입력은 `포인터 아이템 + 서버 raycast + packet mixin` 기준
- `PLAYER_FIXED`의 `yaw/pitch`는 창 자체 회전이 아니라 orbit 각도 의미
- `PLAYER_FIXED` 표시 pitch는 항상 `0`
- `open_window`는 추가 창 오픈이 아니라 현재 창 교체 의미
- 그룹 시스템이 별도 루트 JSON로 추가됨

### 현재 남은 리스크 / 후속 후보

- 창 전환 중 새 창 spawn 실패 시 완전 롤백은 아직 미흡
- `meditate-layout` 의존성은 남아 있지만 실제 레이아웃 계산에는 미사용
- Polymer 리소스팩 강제 적용/거부 플로우는 런타임 수동 검증 계속 필요
- 그룹 전환과 standalone 전환이 함께 섞일 때 UX 규칙은 추가 정리 여지 있음

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [기술 스택](#2-기술-스택)
3. [아키텍처 설계](#3-아키텍처-설계)
4. [핵심 개념](#4-핵심-개념)
5. [컴포넌트 시스템](#5-컴포넌트-시스템)
6. [레이아웃 엔진](#6-레이아웃-엔진)
7. [상호작용 메커니즘](#7-상호작용-메커니즘)
8. [창(Window) JSON 스키마](#8-창window-json-스키마)
9. [명령어 인터페이스](#9-명령어-인터페이스)
10. [성능 전략](#10-성능-전략)
11. [보안 및 다중 플레이어 정책](#11-보안-및-다중-플레이어-정책)
12. [구현 로드맵](#12-구현-로드맵)
13. [검증 계획](#13-검증-계획)
14. [리스크 분석](#14-리스크-분석)
15. [향후 확장](#15-향후-확장)

---

## 1. 프로젝트 개요

### 목표

마인크래프트 플레이어 앞(또는 주변)에 **Display Entity** 기반의 **상호작용 가능한 3D HUD**(이하 "창")를 서버사이드 전용으로 띄우는 프레임워크.

### 비전

현실의 VR 기기가 공간에 메뉴 패널을 띄우는 것처럼, 마인크래프트 월드 내에 클릭 가능한 UI 패널을 생성한다. **기존 리소스팩 HUD와는 근본적으로 다르며**, 3D 공간에 존재하는 엔티티 기반 인터페이스다.

### 핵심 차별점

| 기존 HUD (리소스팩/BossBar) | InteractiveDisplay |
|---|---|
| 2D 오버레이, 클릭 불가 | 3D 공간 배치, 좌/우클릭 상호작용 |
| 클라이언트 모드 필요 | 서버사이드 전용 (바닐라 클라이언트 호환) |
| 정적 표시 | 동적 컴포넌트, 실시간 JSON 리로드 |

---

## 2. 기술 스택

### 런타임 환경

| 항목 | 값 |
|---|---|
| Minecraft | Java Edition `1.21.8` |
| Mod Loader | Fabric Loader (최신 안정) |
| Java | JDK `21` |
| 동작 모드 | **서버사이드 전용** (클라이언트 코드 X) |
| 진입점 | `DedicatedServerModInitializer` |

### 핵심 라이브러리

| 라이브러리 | 용도 | 필수 여부 |
|---|---|---|
| **Fabric API** | 이벤트, 네트워킹, 라이프사이클 | 필수 |
| **Polymer** | 서버사이드 엔티티/블록 표현 계층 | 필수 |
| **SGUI** | 컨테이너/인벤토리 GUI (fallback) | 선택 |
| **Map Canvas API (RGBMapUtils)** | 이미지 → 맵 아이템 인코딩 | 필수 |
| **Player Data API** | 플레이어별 상태 저장 | 선택 |
| **Predicate API** | 조건부 로직 평가 | 선택 |
| **Placeholder API** | 동적 텍스트 치환 | 선택 |
| **Fabric Permissions API** | 권한 기반 접근 제어 | 필수 |

### 범용 라이브러리

| 라이브러리 | 용도 | 필수 여부 |
|---|---|---|
| **[Meditate Layout](https://github.com/lyze237/gdx-FlexBox)** | CSS Flexbox 기반 레이아웃 계산 엔진 (순수 Java, Yoga 포트) | 필수 |
| **[JOML](https://github.com/JOML-CI/JOML)** | 3D 벡터·행렬·Quaternion 연산 (위치 계산, 시야 추적, 회전) | 필수 |
| **[Caffeine](https://github.com/ben-manes/caffeine)** | 고성능 캐시 (레이아웃 계산 결과 캐싱) | Phase 3에서 추가 |

> **참조**: [FabricMinigameTemplate](https://github.com/Kardane/FabricMinigameTemplate)에서 마인크래프트 라이브러리 통합 예시 확인

---

## 3. 아키텍처 설계

### 계층 구조

```
interactivedisplay/
├─ core/                    # 핵심 도메인 (서버 진실 소스)
│  ├─ component/            # 컴포넌트 정의 (Text, Button, Image)
│  ├─ layout/               # 레이아웃 엔진
│  ├─ window/               # 창 생명주기 관리
│  │  ├─ WindowManager.java      # 창 생성·제거·조회
│  │  ├─ WindowInstance.java      # 개별 창 인스턴스 상태
│  │  └─ WindowDefinition.java   # JSON → 정의 파싱 결과
│  ├─ interaction/          # 상호작용 처리
│  │  ├─ HitDetector.java        # 레이캐스트 → 컴포넌트 판정
│  │  ├─ ClickHandler.java       # 클릭 이벤트 디스패치
│  │  └─ FocusManager.java       # 포커스/호버 상태 관리
│  └─ positioning/          # 위치 모드 전략
│     ├─ PositionMode.java       # enum: FIXED, PLAYER_FIXED, PLAYER_VIEW
│     ├─ FixedStrategy.java
│     ├─ PlayerFixedStrategy.java
│     └─ PlayerViewStrategy.java
├─ schema/                  # JSON 파싱·검증
│  ├─ SchemaLoader.java          # JSON 파일 로드 + 핫 리로드
│  ├─ SchemaValidator.java       # 스키마 유효성 검증
│  └─ dto/                       # JSON 매핑 DTO
├─ entity/                  # 엔티티 생성·관리
│  ├─ DisplayEntityPool.java     # Display Entity 풀링/재사용
│  ├─ DisplayEntityFactory.java  # 엔티티 생성 팩토리
│  └─ InteractionEntityBinder.java  # Interaction Entity 바인딩
├─ polymer/                 # Polymer 표현 계층 (core 분리)
│  └─ PolymerBridge.java         # core ↔ Polymer 변환 어댑터
├─ mixin/                   # 최소 Mixin 주입
│  └─ PlayerInteractionMixin.java   # 플레이어 상호작용 감지 확장
└─ command/                 # Brigadier 명령어 트리
   └─ InteractiveDisplayCommand.java
```

### 설계 원칙

1. **서버 상태가 진실 (SSOT)**: 모든 창 상태는 서버에서 관리
2. **core ↔ polymer 분리**: Polymer API를 core에서 직접 호출 금지
3. **Mixin 최소화**: Fabric API로 불가능한 지점만 최소 주입
4. **컴포넌트 단위 책임 분리**: 컴포넌트마다 하나의 역할
5. **JSON 기반 선언적 UI**: 창 구조를 코드가 아닌 JSON으로 정의

---

## 4. 핵심 개념

### Display Entity 활용

마인크래프트 1.20+에서 추가된 Display Entity 3종을 활용한다:

| Entity | 용도 |
|---|---|
| `text_display` | 텍스트 렌더링 (컴포넌트 텍스트, 라벨, 제목, 배경) |
| `item_display` | 아이템 모델 표시 (아이콘, 장식) |
| `block_display` | 블록 모델 표시 (아이콘, 장식) |

### Interaction Entity

`minecraft:interaction` 엔티티를 각 클릭 가능한 영역에 배치하여 좌/우클릭을 감지한다.

```
[text_display] ← 시각 표현
      │
[interaction]  ← 히트박스 (좌클릭: attack, 우클릭: use)
      │
  서버 이벤트 ← ClickHandler로 디스패치
```

### 위치 모드 (PositionMode)

| 모드 | 설명 | 구현 방식 |
|---|---|---|
| `FIXED` | 월드 좌표에 고정 | 생성 좌표에 엔티티 배치, 이후 이동 없음 |
| `PLAYER_FIXED` | 플레이어를 따라다니나 시야와 무관 | 매 tick 플레이어 위치 기준 오프셋 적용 (시야각 무시) |
| `PLAYER_VIEW` | 플레이어 시야 방향에 고정 | 매 tick 플레이어 위치 + 시야 방향 벡터 기반 좌표 계산 |

#### 위치 계산 수식 (PLAYER_VIEW) — JOML 활용

```
displayPos = playerEyePos + (lookDirection × forwardOffset)
                          + (rightVector × horizontalOffset)
                          + (upVector × verticalOffset)
```

- `lookDirection`: 플레이어 시선 단위 벡터 (`JOML Vector3f`)
- `rightVector`: `lookDirection.cross(UP, new Vector3f()).normalize()` (JOML 외적)
- `upVector`: `rightVector.cross(lookDirection, new Vector3f()).normalize()` (JOML 외적)
- `forwardOffset/horizontalOffset/verticalOffset`: JSON에서 설정 가능
- Display Entity의 `left_rotation`/`right_rotation`은 JOML `Quaternionf`로 계산

---

## 5. 컴포넌트 시스템

### 5.1 공통 속성

모든 컴포넌트는 아래 공통 속성을 가진다:

```json
{
  "id": "unique_component_id",
  "type": "text | button | image | panel",
  "position": { "x": 0.0, "y": 0.0, "z": 0.0 },
  "size": { "width": 1.0, "height": 0.5 },
  "visible": true,
  "opacity": 1.0
}
```

### 5.2 Text 컴포넌트

텍스트를 표시한다. 마인크래프트 JSON 텍스트 컴포넌트 형식을 지원한다.

| 속성 | 타입 | 설명 |
|---|---|---|
| `content` | string / JSON text | 표시할 텍스트 (Placeholder API 치환 지원) |
| `fontSize` | float | 텍스트 크기 (scale 계수, 기본 `1.0`) |
| `color` | string | 색상 (`#RRGGBB` 또는 마인크래프트 색상명) |
| `alignment` | enum | `left`, `center`, `right` |
| `lineWidth` | int | 텍스트 줄 바꿈 너비 (픽셀 단위, 기본값: 창 너비) |
| `shadow` | boolean | 텍스트 그림자 (기본 `true`) |
| `background` | string | 배경색 (`#AARRGGBB`, 기본 투명) |

**구현**: `text_display` 엔티티 1개로 매핑. `text`, `line_width`, `background`, `text_opacity`, `shadow` NBT 활용.

### 5.3 Button 컴포넌트

클릭 가능한 버튼. 내부에 `text_display` + `interaction` 엔티티 조합.

| 속성 | 타입 | 설명 |
|---|---|---|
| `label` | string | 버튼 텍스트 |
| `action` | object | 클릭 시 동작 정의 |
| `hoverColor` | string | 호버 시 색상 변경 |
| `clickType` | enum | `LEFT`, `RIGHT`, `BOTH` (기본 `BOTH`) |

**Action 타입**:

| Action | 설명 | 예시 |
|---|---|---|
| `close_window` | 현재 창 닫기 | `{ "type": "close_window" }` |
| `open_window` | 다른 창 열기 | `{ "type": "open_window", "target": "settings" }` |
| `run_command` | 서버 명령 실행 | `{ "type": "run_command", "command": "say hello" }` |
| `callback` | 등록된 콜백 호출 | `{ "type": "callback", "id": "on_confirm" }` |

### 5.4 Image 컴포넌트

이미지를 표시한다. 3가지 표시 모드를 지원한다.

| 속성 | 타입 | 설명 |
|---|---|---|
| `imageType` | enum | `ITEM`, `BLOCK`, `MAP` |
| `value` | string | 아이템/블록 ID 또는 맵 이미지 경로 |
| `scale` | float | 표시 크기 배율 |

**imageType별 구현**:

| 타입 | 엔티티 | 설명 |
|---|---|---|
| `ITEM` | `item_display` | 아이템 모델을 3D로 표시 |
| `BLOCK` | `block_display` | 블록 모델을 3D로 표시 |
| `MAP` | `item_display` | Map Canvas API로 인코딩된 지도 아이템을 `item_display`로 표시 |

### 5.5 Panel 컴포넌트 (컨테이너)

다른 컴포넌트를 담는 컨테이너. 배경 및 그룹 제어용.

| 속성 | 타입 | 설명 |
|---|---|---|
| `backgroundColor` | string | 패널 배경색 |
| `children` | array | 자식 컴포넌트 목록 |
| `padding` | float | 내부 여백 |
| `layout` | enum | `vertical`, `horizontal`, `absolute` |

**구현**: `text_display`(투명 텍스트 + 배경색)로 배경을 표현하고, 자식 컴포넌트를 상대 좌표로 배치.

---

## 6. 레이아웃 엔진 — Meditate (Flexbox) 기반

### 엔진 선택 근거

레이아웃 계산에 **Meditate Layout** (Facebook Yoga의 순수 Java 포트)을 사용한다.

| 항목 | 설명 |
|---|---|
| **직접 구현 대비 이점** | `padding`, `margin`, `align`, `justify`, `wrap`, `grow`, `shrink` 등 복잡한 레이아웃 속성을 직접 구현할 필요 없음 |
| **순수 Java** | JNI 없이 Fabric 서버 환경에서 안전하게 동작 |
| **렌더링 무관** | 좌표/크기만 계산하므로 Display Entity 배치에 바로 적용 가능 |

### 레이아웃 유형 → Flexbox 매핑

| InteractiveDisplay 모드 | Meditate (Flexbox) 매핑 |
|---|---|
| `vertical` | `flexDirection = COLUMN` |
| `horizontal` | `flexDirection = ROW` |
| `absolute` | `positionType = ABSOLUTE` (Flexbox 흐름 무시, 수동 좌표) |

### 좌표 체계

- 창의 **로컬 좌표계**를 사용 (왼쪽 위가 원점)
- 단위: 블록 (1.0 = 1블록 크기)
- 각 컴포넌트의 `position`은 부모 컨테이너 기준 상대 좌표
- Z축은 레이어링(겹침 순서)에 사용 (값이 클수록 앞)
- Meditate가 계산한 2D 좌표(x, y)를 JOML `Vector3f`로 변환하여 Display Entity 오프셋에 적용

### Meditate 활용 흐름

```java
// 1. JSON → Meditate 노드 트리 변환
YogaNode root = YogaNodeFactory.create();
root.setWidth(windowDef.size.width * SCALE);
root.setHeight(windowDef.size.height * SCALE);
root.setFlexDirection(YogaFlexDirection.COLUMN);

for (ComponentDef comp : windowDef.components) {
    YogaNode node = YogaNodeFactory.create();
    node.setWidth(comp.size.width * SCALE);
    node.setHeight(comp.size.height * SCALE);
    node.setMargin(YogaEdge.BOTTOM, comp.margin * SCALE);
    root.addChildAt(node, root.getChildCount());
}

// 2. 레이아웃 계산 (렌더링 없이 좌표만 산출)
root.calculateLayout(YogaConstants.UNDEFINED, YogaConstants.UNDEFINED);

// 3. 결과 → Display Entity 오프셋
for (int i = 0; i < root.getChildCount(); i++) {
    YogaNode node = root.getChildAt(i);
    float x = node.getLayoutX() / SCALE;
    float y = node.getLayoutY() / SCALE;
    // → DisplayElement.setOffset(new Vec3d(x, -y, zLayer))
}
```

### 렌더 파이프라인

```
JSON 파일 로드
    │
SchemaValidator (유효성 검증)
    │
WindowDefinition (파싱된 정의 객체)
    │
Meditate LayoutEngine (Flexbox 계산 → 각 컴포넌트의 상대 좌표 결정)
    │
JOML 좌표 변환 (로컬 2D → 월드 3D 좌표, 회전 적용)
    │
DisplayEntityFactory (Display Entity + Interaction Entity 생성)
    │
WindowInstance (활성 창 인스턴스 등록)
```

---

## 7. 상호작용 메커니즘

### 히트 감지 방식

1. 각 클릭 가능한 컴포넌트(Button)에 `minecraft:interaction` 엔티티를 바인딩
2. `interaction` 엔티티의 `width`/`height`를 컴포넌트 크기에 맞춤
3. 플레이어가 `interaction` 엔티티를 좌클릭(`attack`) 또는 우클릭(`use`)하면 서버에서 감지

### 이벤트 흐름

```
플레이어 클릭
    │
Fabric API 이벤트 (AttackEntityCallback / UseEntityCallback)
    │
InteractionEntityBinder → 어떤 WindowInstance의 어떤 컴포넌트인지 역참조
    │
ClickHandler → Action 실행 (close_window / open_window / run_command / callback)
    │
WindowManager → 필요 시 창 상태 갱신 + 엔티티 업데이트
```

### 호버 감지 (선택 구현)

- 매 tick 플레이어의 시선 레이캐스트를 수행
- 가장 가까운 `interaction` 엔티티 판별
- 호버 상태 진입/이탈 시 `text_display`의 `background` 색상 변경으로 피드백

> ⚠️ **성능 경고**: 호버는 매 tick 레이캐스트가 필요하므로 Phase 2에서 구현하며, 대상 플레이어 수에 따라 호출 빈도를 제한한다.

---

## 8. 창(Window) JSON 스키마

### 파일 위치

```
config/interactivedisplay/windows/
├─ main_menu.json
├─ settings.json
├─ confirm_dialog.json
└─ ...
```

### 스키마 예시

```json
{
  "id": "main_menu",
  "title": "메인 메뉴",
  "size": {
    "width": 3.0,
    "height": 2.0
  },
  "offset": {
    "forward": 2.0,
    "horizontal": 0.0,
    "vertical": 0.5
  },
  "background": {
    "type": "solid",
    "color": "#CC000000"
  },
  "components": [
    {
      "id": "title_text",
      "type": "text",
      "position": { "x": 1.5, "y": 0.2, "z": 0.01 },
      "content": "§6§l서버 메뉴",
      "fontSize": 2.0,
      "alignment": "center",
      "shadow": true
    },
    {
      "id": "btn_settings",
      "type": "button",
      "position": { "x": 0.5, "y": 0.8, "z": 0.01 },
      "size": { "width": 2.0, "height": 0.3 },
      "label": "§f설정",
      "hoverColor": "#44FFFFFF",
      "action": {
        "type": "open_window",
        "target": "settings"
      }
    },
    {
      "id": "btn_close",
      "type": "button",
      "position": { "x": 0.5, "y": 1.3, "z": 0.01 },
      "size": { "width": 2.0, "height": 0.3 },
      "label": "§f닫기",
      "action": {
        "type": "close_window"
      }
    },
    {
      "id": "icon_diamond",
      "type": "image",
      "position": { "x": 0.1, "y": 0.1, "z": 0.02 },
      "imageType": "ITEM",
      "value": "minecraft:diamond",
      "scale": 0.5
    }
  ]
}
```

### 핫 리로드

- `SchemaLoader`가 파일 감시(WatchService) 또는 명령어 기반으로 JSON을 재로드
- 이미 열린 창은 리로드 시 자동으로 재생성 (옵션으로 제어 가능)
- 파싱 실패 시 기존 정의 유지 + 콘솔 에러 로그

---

## 9. 명령어 인터페이스

### 명령어 트리

```
/interactivedisplay
├─ create <windowId> <player> <positionMode> [x y z]
│   └─ 플레이어에게 창 생성
│      positionMode: fixed | player_fixed | player_view
│      [x y z]: FIXED 모드일 때 좌표 지정 (선택)
│
├─ remove <windowId> <player>
│   └─ 플레이어에게 열린 창 제거
│
├─ removeall <player>
│   └─ 플레이어의 모든 창 제거
│
├─ reload [windowId]
│   └─ 전체 또는 특정 창 JSON 리로드
│
├─ list [player]
│   └─ 로드된 창 목록 또는 플레이어의 활성 창 목록 표시
│
└─ debug
    ├─ entities <player>
    │   └─ 플레이어 창의 Display Entity 수 표시
    ├─ hitbox <player> <on|off>
    │   └─ Interaction Entity 히트박스 시각화 토글
    └─ performance
        └─ 틱당 엔티티 수, 갱신 빈도 등 성능 지표 표시
```

### 권한 노드

| 노드 | 기본 레벨 | 설명 |
|---|---|---|
| `interactivedisplay.create` | OP 2 | 창 생성 |
| `interactivedisplay.remove` | OP 2 | 창 제거 |
| `interactivedisplay.reload` | OP 3 | JSON 리로드 |
| `interactivedisplay.debug` | OP 3 | 디버그 명령 |
| `interactivedisplay.use` | 모든 플레이어 | 열린 창과 상호작용 |

---

## 10. 성능 전략

### 엔티티 비용 분석

| 구성 | 예상 엔티티 수 | 비용 수준 |
|---|---|---|
| 텍스트 3 + 버튼 2 | 텍스트 3 + 버튼(텍스트 2 + 상호작용 2) = **7** | 낮음 |
| 중형 메뉴 (10개 컴포넌트) | **~20** | 중간 |
| 복합 대시보드 (20+ 컴포넌트) | **~50** | 높음 |

### 최적화 전략

| 전략 | 설명 |
|---|---|
| **엔티티 풀링** | 닫힌 창의 엔티티를 즉시 제거하지 않고 비활성 풀에 보관, 재사용 |
| **갱신 주기 제한** | `PLAYER_VIEW` 모드의 좌표 갱신을 매 tick이 아닌 2~4 tick 간격으로 제한 |
| **최대 동시 창 수** | 플레이어당 최대 동시 열린 창 수 제한 (기본 `3`) |
| **최대 엔티티 수** | 플레이어당 InteractiveDisplay 엔티티 총 수 제한 (기본 `100`) |
| **시야 거리 컬링** | `FIXED` 모드 창은 플레이어가 일정 거리 이상 떨어지면 엔티티 비활성화 |
| **변경 감지** | 상태가 변하지 않은 컴포넌트의 엔티티 데이터는 갱신 패킷 생략 |

### Phase 2: 패킷 방식 전환 기준

엔티티 실제 소환 방식의 비용이 감당 불가능할 때 패킷 기반으로 전환한다:

| 지표 | 임계값 |
|---|---|
| 서버 TPS 하락 | 20 → 18 이하 |
| 플레이어당 평균 엔티티 | 50개 초과 |
| 동시 접속자 | 30명 이상에서 지연 발생 |

**패킷 방식 전환 시**:
- `ClientboundAddEntityPacket` / `ClientboundSetEntityDataPacket`으로 클라이언트에만 엔티티 표시
- 서버 엔티티 인스턴스 비용 제거
- 다른 플레이어에게 보이지 않는 것이 보장됨

---

## 11. 보안 및 다중 플레이어 정책

### 소유권 격리

| 정책 | 설명 |
|---|---|
| **시각 공유** | 실제 소환 방식에서는 타 플레이어가 창을 볼 수 있음 (Phase 1) |
| **상호작용 차단** | 창의 소유자가 아닌 플레이어의 `interaction` 이벤트는 즉시 무시 |
| **소유자 검증** | 모든 클릭 이벤트에서 `WindowInstance.owner == event.player` 검증 |

### 명령어 보안

- `run_command` Action의 경우, 실행 권한을 **서버 콘솔**이 아닌 **플레이어 컨텍스트**로 제한
- 화이트리스트 방식: 허용된 명령어 패턴만 실행 가능 (설정 파일에서 관리)
- 명령어 실행 로그를 반드시 기록

### 악용 방지

| 위험 | 대응 |
|---|---|
| 창 대량 생성으로 엔티티 폭주 | 플레이어당 최대 창 수 + 전체 엔티티 상한 |
| 클릭 스팸 | 클릭 쿨다운 (기본 200ms) |
| JSON 인젝션 | 서버 관리자만 JSON 파일 접근, 런타임 동적 JSON 생성 금지 |

---

## 12. 구현 로드맵

### Phase 1: 핵심 기반 (MVP)

> 목표: 텍스트·버튼 기반 창을 생성·제거·클릭할 수 있는 최소 기능

| # | 작업 | 의존성 |
|---|---|---|
| 1-1 | 프로젝트 스캐폴딩 (Fabric mod 구조) + **Meditate, JOML 의존성 통합** | — |
| 1-2 | JSON 스키마 로더 + 검증기 | — |
| 1-3 | **Meditate 기반 레이아웃 엔진** (Flexbox 계산 → 좌표 산출) | 1-1 |
| 1-4 | **JOML 기반 좌표 변환** (로컬 2D → 월드 3D, 회전) | 1-1 |
| 1-5 | `text_display` 기반 Text 컴포넌트 | 1-3, 1-4 |
| 1-6 | `interaction` 엔티티 기반 클릭 감지 | 1-1 |
| 1-7 | Button 컴포넌트 (Text + Interaction 조합) | 1-5, 1-6 |
| 1-8 | WindowManager (생성/제거/조회) | 1-2, 1-5 |
| 1-9 | PositionMode: FIXED (JOML 좌표 고정 배치) | 1-8 |
| 1-10 | 기본 명령어 (`create`, `remove`, `reload`) | 1-8 |
| 1-11 | 소유자 외 상호작용 차단 | 1-6 |
| 1-12 | Action 시스템 (close_window, open_window) | 1-7, 1-8 |

### Phase 2: 확장 컴포넌트 + 위치 모드

| # | 작업 | 의존성 |
|---|---|---|
| 2-1 | Image 컴포넌트 (ITEM, BLOCK) | Phase 1 |
| 2-2 | Image 컴포넌트 (MAP, Map Canvas API 통합) | 2-1 |
| 2-3 | Panel 컨테이너 + Meditate Flexbox 중첩 레이아웃 | Phase 1 |
| 2-4 | PositionMode: PLAYER_FIXED (JOML 위치 추적) | Phase 1 |
| 2-5 | PositionMode: PLAYER_VIEW (JOML 시선 벡터 + Quaternion 회전) | 2-4 |
| 2-6 | 엔티티 풀링 + 갱신 최적화 | Phase 1 |
| 2-7 | 호버 감지 + 시각 피드백 (JOML 레이캐스트) | Phase 1 |
| 2-8 | `run_command`, `callback` Action | Phase 1 |

### Phase 3: 운영 안정화

| # | 작업 | 의존성 |
|---|---|---|
| 3-1 | 패킷 기반 렌더링 전환 (선택) | Phase 2 |
| 3-2 | Placeholder API 통합 (동적 텍스트) | Phase 2 |
| 3-3 | 애니메이션 (페이드 인/아웃, 스케일) | Phase 2 |
| 3-4 | **Caffeine 캐시 통합** (레이아웃 계산 결과 캐싱, 동일 창 다중 플레이어 최적화) | Phase 2 |
| 3-5 | 성능 모니터링 + 디버그 명령어 | Phase 2 |
| 3-6 | API 공개 (다른 모드에서 창 생성 가능) | Phase 2 |

---

## 13. 검증 계획

### 구조 검증

- [ ] `verify-mod-env.sh`: Fabric 모드 구조 + `environment=server` 확인
- [ ] `fabric.mod.json` 진입점 + mixin 설정 파싱 검증
- [ ] 클라이언트 전용 import 사용 탐지

### 빌드 검증

```bash
./gradlew clean build --no-daemon
```

### 런타임 검증

| 시나리오 | 기대 결과 |
|---|---|
| `/interactivedisplay create main_menu @p fixed` | 플레이어 앞에 메인 메뉴 창 생성 |
| 버튼 좌클릭 | Action 실행 (창 닫기/열기) |
| 버튼 우클릭 | Action 실행 (clickType에 따라) |
| 타 플레이어가 창 클릭 | 이벤트 무시, 로그 기록 |
| `/interactivedisplay reload` | JSON 변경사항 즉시 반영 |
| `/interactivedisplay remove main_menu @p` | 창 + 모든 관련 엔티티 제거 |
| 플레이어 접속 해제 | 해당 플레이어의 모든 창 자동 제거 |

### 성능 검증

- [ ] 플레이어 5명 × 창 2개 × 10분 — TPS 19+ 유지
- [ ] 엔티티 풀링 활성 시 엔티티 총 수 30% 이하 감소 확인
- [ ] `PLAYER_VIEW` 모드에서 시야 회전 시 끊김 없는 추적 확인

---

## 14. 리스크 분석

| # | 리스크 | 등급 | 완화책 |
|---|---|---|---|
| R1 | Display Entity 대량 생성 시 TPS 하락 | **높음** | 엔티티 상한, 풀링, Phase 3에서 패킷 전환 |
| R2 | `PLAYER_VIEW` 모드의 매 tick 좌표 갱신 비용 | **중간** | 갱신 주기 제한 (2~4 tick), 변경 감지 |
| R3 | Interaction Entity 히트박스 정밀도 부족 | **중간** | 히트박스 크기 튜닝, 레이캐스트 보조 판정 |
| R4 | 버전 업 시 Display Entity NBT 필드 변경 | **중간** | NBT 접근을 팩토리로 격리, 마이그레이션 체크리스트 |
| R5 | Polymer API 변경으로 런타임 오류 | **중간** | PolymerBridge 어댑터 패턴, 버전 고정 |
| R6 | 타 플레이어가 창을 볼 수 있어 혼란 | **낮음** | Phase 3 패킷 전환으로 완전 격리 가능 |

---

## 15. 향후 확장

| 기능 | 설명 |
|---|---|
| **스크롤 컴포넌트** | 긴 목록을 스크롤 가능한 영역에 표시 |
| **입력 필드** | 채팅 입력 기반 텍스트 입력 컴포넌트 |
| **Flexbox 확장 속성** | Meditate의 `flexWrap`, `flexGrow`, `flexShrink`, `alignSelf` 등 고급 Flexbox 속성 노출 |
| **그리드 레이아웃** | Meditate Flexbox wrap 기반 아이템 그리드, 인벤토리 스타일 배치 |
| **애니메이션 시스템** | JOML 보간(lerp/slerp) 기반 컴포넌트 등장/퇴장 애니메이션, 전환 효과 |
| **템플릿/스타일 시스템** | CSS처럼 스타일을 분리하여 재사용 |
| **데이터 바인딩** | 스코어보드/플레이어 데이터와 텍스트 자동 동기화 |
| **다국어 지원** | lang 키 기반 자동 번역 |
| **공개 API** | 다른 Fabric 모드에서 InteractiveDisplay 창을 프로그래밍 방식으로 생성 |

---

> **부록**: 이 기획서는 `minecraft-fabric-server-dev`, `minecraft-java-reference-hub`, `minecraft-java-datapack-engineering`, `minecraft-java-resourcepack-engineering` 스킬의 실무 품질 규약을 기반으로 작성되었다.

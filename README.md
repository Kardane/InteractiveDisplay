# InteractiveDisplay


`InteractiveDisplay`는 Minecraft 1.21.8 Fabric 서버에서 동작하는 서버사이드 3D HUD / 창 시스템임.  
`display entity` 기반으로 UI를 띄우고, JSON 설정으로 창 레이아웃과 버튼 동작을 정의하는 구조임.

## 스크린샷

[![InteractiveDisplay 미리보기](https://i.imgur.com/U5LeBmMh.jpg)](https://imgur.com/a/EsAEpMW)
사진 클릭하면 영상나옴

## 핵심 특징

- JSON 기반 창(`windows`) / 그룹(`groups`) 정의
- `FIXED`, `PLAYER_FIXED`, `PLAYER_VIEW` 배치 모드 지원
- `text`, `button`, `panel`, `image` 컴포넌트 지원
- 버튼 액션으로 창 열기, 모드 전환, 명령 실행, 콜백 처리 지원
- 포인터 아이템 + raycast 기반 클릭 판정
- Polymer 자동 리소스팩 부트스트랩 지원
- 디버그 명령과 최근 이벤트 기록 지원

## 기술 스택

- Minecraft `1.21.8`
- Fabric Loader `0.18.0`
- Fabric API `0.136.0+1.21.8`
- Polymer `0.13.13+1.21.8`
- Java `21`
- Gradle / Fabric Loom

## 빠른 시작

### 1. 전제 조건

- WSL 또는 Linux 환경
- JDK 21
- Fabric 서버 실행 환경

### 2. 빌드

샌드박스 환경에서 가장 안정적인 방법

```bash
./scripts/sandbox-build.sh
```

테스트 실행

```bash
./scripts/sandbox-test.sh
```

권한 있는 환경에서 일반 Gradle 검증

```bash
GRADLE_USER_HOME=/home/parkj/test-projects/MC_InteractiveDisplay/.gradle ./gradlew compileJava --no-daemon
GRADLE_USER_HOME=/home/parkj/test-projects/MC_InteractiveDisplay/.gradle ./gradlew test --no-daemon
```

자세한 검증 절차는 [`BUILD_AND_TEST_GUIDE.md`](/home/parkj/test-projects/MC_InteractiveDisplay/BUILD_AND_TEST_GUIDE.md) 참고.

## 설정 파일 위치와 역할

기본 설정 루트는 `run/config/interactivedisplay/` 임.

```text
run/config/interactivedisplay/
├── command_whitelist.json
├── groups/
│   └── menu_group.json
├── images/
│   └── sample_local.png
└── windows/
    ├── gallery.json
    ├── gallery_remote.example.json.disabled
    ├── main_menu.json
    └── main_menu2.json
```

| 경로 | 역할 |
| --- | --- |
| `windows/` | 각 창의 루트 설정 파일 위치. `id`, `size`, `offset`, `layout`, `components`를 정의함 |
| `groups/` | 여러 창을 묶는 그룹 설정 위치. 어떤 창이 먼저 열리는지, 각 창이 그룹 내에서 어디에 배치되는지 정의함 |
| `images/` | `imageType: "MAP"`에서 로컬 파일을 쓸 때 참조하는 이미지 저장 위치 |
| `command_whitelist.json` | `run_command` 액션으로 실행 가능한 명령 접두사를 제한하는 보안 설정 파일. 기본값은 빈 배열이라 아무 명령도 허용되지 않음 |

## 설정 스키마 상세

### 1. 창 파일 경로

- `run/config/interactivedisplay/windows/*.json`

예시 파일

- `run/config/interactivedisplay/windows/main_menu.json`
- `run/config/interactivedisplay/windows/main_menu2.json`
- `run/config/interactivedisplay/windows/gallery.json`

### 2. 창 루트 구조

모든 창 JSON은 최소한 아래 구조를 가짐.

```json
{
  "id": "main_menu",
  "size": {
    "width": 3.2,
    "height": 2.4
  },
  "offset": {
    "forward": 3.0,
    "horizontal": 0.0,
    "vertical": 0.5
  },
  "layout": "absolute",
  "components": []
}
```

| 필드 | 설명 | 비고 |
| --- | --- | --- |
| `id` | 창 고유 식별자 | 명령어의 `<windowId>`와 연결 |
| `size.width`, `size.height` | 창 전체 기준 크기 | 둘 다 0보다 커야 함 |
| `offset.forward`, `offset.horizontal`, `offset.vertical` | 플레이어 또는 월드 기준 기본 오프셋 | 생략 가능 |
| `layout` | 창 루트 레이아웃 | `absolute`, `vertical`, `horizontal`, 생략 시 `absolute` |
| `components` | 실제 렌더링할 컴포넌트 배열 | 필수 |

### 3. 그룹 파일 경로

- `run/config/interactivedisplay/groups/*.json`

예시 파일

- `run/config/interactivedisplay/groups/menu_group.json`

예시 구조

```json
{
  "id": "menu_group",
  "initialWindowId": "main_menu",
  "defaultMode": "player_fixed",
  "windows": [
    {
      "windowId": "main_menu",
      "offset": { "forward": 2.0, "horizontal": 0.0, "vertical": 0.5 },
      "orbit": { "yaw": 0.0, "pitch": 0.0 }
    }
  ]
}
```

| 필드 | 설명 | 비고 |
| --- | --- | --- |
| `id` | 그룹 고유 식별자 | 필수 |
| `initialWindowId` | 그룹 생성 시 처음 여는 창 ID | 필수 |
| `defaultMode` | 기본 배치 모드 | `fixed`, `player_fixed`, `player_view` |
| `windows[]` | 그룹에 포함되는 창 목록 | 필수 |
| `windows[].windowId` | 포함할 창 ID | 필수 |
| `windows[].offset` | 그룹 내 개별 창 위치 오프셋 | 선택 |
| `windows[].orbit` | 상대 배치 각도 | `player_fixed`, `player_view` 계열에서 주로 사용 |

### 4. 컴포넌트 공통 필드

| 필드 | 설명 | 기본값 / 비고 |
| --- | --- | --- |
| `id` | 컴포넌트 고유 식별자 | 필수 |
| `type` | 컴포넌트 종류 | `text`, `button`, `image`, `panel` |
| `position.x`, `position.y`, `position.z` | 컴포넌트 로컬 좌표 | 필수 |
| `visible` | 표시 여부 | 생략 시 `true` |
| `opacity` | 투명도 | `0.0` ~ `1.0`, 생략 시 `1.0` |
| `layout` | 일부 컨테이너에서 사용하는 레이아웃 모드 | `absolute`, `vertical`, `horizontal` |

### 5. 컴포넌트 타입

| 타입 | 용도 | 주요 필드 | 기본값 / 주의 |
| --- | --- | --- | --- |
| `text` | 텍스트 표시 | `content`, `width`/`height` 또는 `size`, `fontSize`, `color`, `alignment`, `lineWidth`, `shadow`, `background` | `fontSize=0.5`, `color=#FFFFFF`, `alignment=left`, `lineWidth=200`, `shadow=true`, `background=#00000000` |
| `button` | 클릭 가능한 버튼 | `label`, `size.width`, `size.height`, `fontSize`, `backgroundColor`, `hoverColor`, `clickSound`, `clickType`, `action` | `fontSize=1.0`, `backgroundColor=#00000000`, `hoverColor=#44FFFFFF` |
| `image` | 아이템, 블록, 맵 이미지 표시 | `size.width`, `size.height`, `imageType`, `value`, `scale` | `scale=1.0` |
| `panel` | 자식 컴포넌트 컨테이너 | `size.width`, `size.height`, `backgroundColor`, `padding`, `layout`, `children` | `backgroundColor=#00000000`, `padding=0.0`, `layout=absolute` |

버튼 `clickType` 관련 주의

| 항목 | 내용 |
| --- | --- |
| 스키마 허용값 | `left`, `right`, `both` |
| 현재 런타임 동작 | 최종적으로 `RIGHT`로 정규화 |
| 실무 권장 | 우클릭 버튼 기준으로만 설정 |

`imageType`별 `value` 해석

| `imageType` | `value` 의미 | 예시 |
| --- | --- | --- |
| `item` | 아이템 식별자 문자열 | `minecraft:diamond` |
| `block` | 블록 식별자 문자열 | `minecraft:stone` |
| `map` | 로컬 파일명 또는 원격 URL | `sample_local.png`, `https://textures.minecraft.net/...` |

관련 예시 파일

- `run/config/interactivedisplay/windows/gallery.json`
- `run/config/interactivedisplay/windows/gallery_remote.example.json.disabled`

### 6. 버튼 액션 타입

버튼의 `action.type`은 아래 값만 지원함.

| 액션 타입 | 설명 | 추가 필드 |
| --- | --- | --- |
| `close_window` | 현재 창 닫기 | 없음 |
| `open_window` | 다른 창 열기 | `target` |
| `switch_mode_fixed` | 현재 창을 월드 고정 모드로 전환 | 없음 |
| `switch_mode_player_fixed` | 현재 창을 플레이어 고정 모드로 전환 | 없음 |
| `toggle_placement_tracking` | 창 위치 추적 상태 토글 | 없음 |
| `run_command` | 명령 실행 | `command`, 선택 `permissionLevel(0~4)` |
| `callback` | 코드에 등록된 콜백 실행 | `id` |

예시

```json
{
  "action": {
    "type": "run_command",
    "command": "title @s actionbar {\"text\":\"버튼 컨텍스트 실행\"}",
    "permissionLevel": 2
  }
}
```

### 7. 버튼 명령 실행 보안 설정

`run_command`는 아무 명령이나 바로 실행되지 않음. 아래 파일에서 접두사 화이트리스트를 먼저 통과해야 함.

- `run/config/interactivedisplay/command_whitelist.json`

기본 구조

```json
{
  "allowedPrefixes": []
}
```

예시

```json
{
  "allowedPrefixes": [
    "say ",
    "title ",
    "tellraw "
  ]
}
```

| 항목 | 설명 |
| --- | --- |
| 슬래시 처리 | `/` 유무는 내부에서 정규화됨 |
| 매칭 방식 | 접두사 일치 방식 |
| 운영 권장 | 최소 권한 명령만 열어두는 게 안전 |

### 8. 배치 모드

| 모드 | 설명 | 명령어 인자 |
| --- | --- | --- |
| `fixed` | 월드 좌표 고정 | `x y z` |
| `player_fixed` | 플레이어 기준 상대 위치에 고정 | `yaw pitch` |
| `player_view` | 시야를 따라가는 모드 | `yaw pitch` |

### 9. 설정 작성 순서 추천

1. `windows/`에 단일 창부터 작성
2. `/interactivedisplay reload`로 문법과 로드 상태 확인
3. `groups/`에서 여러 창 배치 정의
4. 필요한 경우 `images/`에 로컬 이미지 추가
5. `run_command`를 쓸 때만 `command_whitelist.json` 갱신

### 10. 실전 예시 파일

- 메인 메뉴: `run/config/interactivedisplay/windows/main_menu.json`
- 두 번째 메뉴: `run/config/interactivedisplay/windows/main_menu2.json`
- 이미지 갤러리: `run/config/interactivedisplay/windows/gallery.json`
- 그룹 예시: `run/config/interactivedisplay/groups/menu_group.json`

## 주요 명령어

```mcfunction
/interactivedisplay list
/interactivedisplay reload
/interactivedisplay create <windowId> <player> fixed
/interactivedisplay create <windowId> <player> player_fixed
/interactivedisplay create <windowId> <player> player_view
/interactivedisplay remove <windowId> <player>
/interactivedisplay group list
/interactivedisplay group create <groupId> <player> fixed
/interactivedisplay group remove <groupId> <player>
/interactivedisplay debug status
/interactivedisplay debug recent
/interactivedisplay debug bindings <player>
```

좌표와 각도 인자는 모드에 따라 추가 가능함.

- `fixed`: `x y z`
- `player_fixed`, `player_view`: `yaw pitch`

## 동작 개요

1. 서버 시작 시 설정 파일 로드
2. Polymer 리소스팩 파일 준비
3. 명령어 또는 그룹 생성으로 창 생성
4. 플레이어가 포인터 아이템으로 UI를 가리키고 클릭
5. 버튼 액션 실행 후 필요 시 창 전환 또는 명령 실행

핵심 엔트리포인트는 [`InteractiveDisplay.java`](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/InteractiveDisplay.java) 임.

## 프로젝트 구조

```text
src/main/java/com/interactivedisplay/
├── command/        명령어 트리와 입력 해석
├── core/           창, 배치, 상호작용, 컴포넌트 핵심 런타임
├── entity/         display entity 생성 및 풀링
├── item/           포인터 아이템 등록
├── polymer/        리소스팩 부트스트랩
├── schema/         JSON 로드와 검증
└── debug/          디버그 이벤트 기록
```

아키텍처 상세는 [`ARCHITECTURE_REVIEW.md`](/home/parkj/test-projects/MC_InteractiveDisplay/ARCHITECTURE_REVIEW.md) 참고.

## 현재 지원 범위

- 창 로드 / 리로드
- 그룹 생성 / 제거 / 탐색
- hover / 클릭 처리
- 텍스트 / 버튼 / 패널 / 이미지 렌더링
- 명령 실행 / 콜백 / 창 이동 액션
- 디버그 상태 및 최근 이벤트 조회

## 라이선스

MIT License. 자세한 내용은 [`LICENSE`](/home/parkj/test-projects/MC_InteractiveDisplay/LICENSE) 참고.

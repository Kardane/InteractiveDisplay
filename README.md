# InteractiveDisplay

[img](https://imgur.com/a/EsAEpMW)

`InteractiveDisplay`는 Minecraft 1.21.8 Fabric 서버에서 동작하는 서버사이드 3D HUD / 창 시스템임.  
`display entity` 기반으로 UI를 띄우고, JSON 설정으로 창 레이아웃과 버튼 동작을 정의하는 구조임.

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

## 설정 파일 위치

기본 설정은 아래 경로 기준임.

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

- `windows/`: 단일 창 정의
- `groups/`: 여러 창을 묶는 그룹 정의
- `images/`: 로컬 이미지 소스
- `command_whitelist.json`: 버튼에서 실행 가능한 명령 허용 목록

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

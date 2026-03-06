# InteractiveDisplay 아키텍처 리뷰

> 기준 시점: 2026-03-06  
> 기준 브랜치 상태: 로컬 작업 트리  
> 대상 버전: Minecraft 1.21.8 / Fabric / JDK 21

---

## 1. 문서 목적

이 문서는 현재 `InteractiveDisplay`가 실제로 어떤 구조로 동작하는지, 초안 설계와 어디가 달라졌는지, 지금 기준에서 유지 가능한 구조인지 점검하기 위한 리뷰 문서다.

핵심 목적은 4개다.

- 현재 구조를 코드 기준으로 정확히 정리
- 강점과 기술 부채를 분리
- 다음 변경에서 건드리면 안 되는 축과 바꿔도 되는 축을 구분
- 운영/디버그/확장 리스크를 미리 노출

---

## 2. 현재 시스템 한 줄 요약

`JSON 정의 기반 3D 창 시스템`이고, 서버가 `display entity`를 직접 관리하며, 입력은 `포인터 아이템 + raycast + packet mixin`으로 처리하는 구조다.

초기 계획과 비교하면 가장 큰 차이는 이거다.

- `interaction 엔티티` 기반 클릭에서 벗어남
- `pointer item`을 들고 있을 때만 버튼 상호작용 허용
- `group`과 `window navigation` 개념이 런타임에 들어옴
- `PLAYER_FIXED`는 이제 "창 회전"이 아니라 "orbit 위치" 중심 의미로 재정의됨

---

## 3. 구현 범위 상태

### 완료된 범위

- 창 정의 로드/검증/리로드
- `text`, `button`, `panel`, `image`
- `FIXED`, `PLAYER_FIXED`, `PLAYER_VIEW`
- hover, 클릭, `run_command`, `callback`
- 디버그 이벤트/최근 실패 스냅샷
- Polymer 자동 리소스팩 부트스트랩
- 포인터 아이템
- `switch_mode_fixed`, `switch_mode_player_fixed`
- `open_window` 현재 창 교체
- `group create/remove/list`
- `player_fixed` 상대 각도 입력 `~ ~`

### 아직 정리 덜 된 범위

- 새 창 spawn 실패 시 완전 롤백
- `meditate-layout` 실제 도입 여부 정리
- 그룹/standalone 혼합 UX 규칙 문서화
- 실제 서버 런타임에서 대규모 창 수 운용 검증

---

## 4. 패키지 구조 리뷰

### `command`

주요 파일

- [InteractiveDisplayCommand.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/command/InteractiveDisplayCommand.java)
- [InteractiveDisplayCommandTree.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/command/InteractiveDisplayCommandTree.java)
- [AngleInput.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/command/AngleInput.java)

역할

- Brigadier 트리 정의
- selector argument 파싱
- `player_fixed` 상대 각도 해석
- `WindowManager` 호출 결과를 사용자 메시지로 변환

평가

- 장점: 입력 해석과 실제 런타임 호출이 분리돼 있음
- 약점: `InteractiveDisplayCommand`가 커지고 있음. 현재는 아직 관리 가능하지만 그룹/모드 전환 로직이 더 늘면 분리 필요

### `schema`

주요 파일

- [SchemaLoader.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/schema/SchemaLoader.java)
- [SchemaValidator.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/schema/SchemaValidator.java)

역할

- `windows/*.json`
- `groups/*.json`
- 샘플 파일 자동 생성
- action/type/mode 검증
- MAP 이미지 로컬/원격 해석

평가

- 장점: 로드와 검증이 분리돼 있고, 실패 시 기존 정의 유지 정책이 명확함
- 약점: `SchemaLoader`가 샘플 파일 생성, 원격 MAP 처리, group 파싱까지 떠안아서 책임이 무거움

### `core/window`

주요 파일

- [WindowManager.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/core/window/WindowManager.java)
- [WindowInstance.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/core/window/WindowInstance.java)
- [WindowGroupDefinition.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/core/window/WindowGroupDefinition.java)
- [WindowGroupInstance.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/core/window/WindowGroupInstance.java)
- [WindowNavigationContext.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/core/window/WindowNavigationContext.java)

역할

- 창 생성/제거/재구성
- 그룹 런타임 상태 관리
- hover 대상 탐색
- action executor 구현
- pooling 연계

평가

- 장점: 지금 시스템의 단일 진실 소스가 분명함
- 약점: `WindowManager`가 이미 크다. 생성, 이동, hover, reload, 그룹 전환, 액션 실행까지 다 들고 있음

판단

- 지금은 유지 가능
- 다음 단계에서 더 기능을 넣으면 `WindowSpawner`, `WindowNavigator`, `GroupRuntimeStore` 정도로 분리하는 게 맞음

### `core/interaction`

주요 파일

- [ClickHandler.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/core/interaction/ClickHandler.java)
- [UiHitResult.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/core/interaction/UiHitResult.java)

역할

- hit 결과를 action dispatch로 연결
- close/open/switch/run_command/callback 분기

평가

- 장점: 입력 판정과 액션 디스패치가 분리돼 있음
- 약점: action type이 더 늘면 분기문이 다시 커짐

### `core/positioning`

주요 파일

- [CoordinateTransformer.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/core/positioning/CoordinateTransformer.java)
- [WindowPositionTracker.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/core/positioning/WindowPositionTracker.java)

역할

- local 2D -> world 3D 변환
- orbit 위치 계산
- `PLAYER_FIXED`, `PLAYER_VIEW` smoothing/deadzone/threshold

평가

- 장점: 수학 로직이 entity 관리 코드에서 분리돼 있음
- 장점: `PLAYER_FIXED`와 `PLAYER_VIEW` 정책이 코드상 분리돼 있음
- 약점: 상수가 하드코딩이라 운영 중 미세 튜닝에 빌드가 필요

### `entity`

주요 파일

- [DisplayEntityFactory.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/entity/DisplayEntityFactory.java)
- [DisplayEntityPool.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/entity/DisplayEntityPool.java)

역할

- display entity 생성/이동/재설정
- hover 색상 반영
- panel/text/button/image 표시
- pooling

평가

- 장점: Minecraft 엔티티 세부 NBT가 한곳에 몰려 있어서 추적 가능
- 약점: 배경 panel과 text/button 렌더가 `text_display` 우회 구현에 강하게 의존

---

## 5. 런타임 흐름 리뷰

### 서버 시작

1. [InteractiveDisplay.java](/home/parkj/test-projects/MC_InteractiveDisplay/src/main/java/com/interactivedisplay/InteractiveDisplay.java) 에서 item/resource pack/command/event 등록
2. `SERVER_STARTED`에서 `WindowManager` 생성
3. resource pack 재빌드
4. `reloadAll()`로 windows/groups 로드

### 창 생성

1. 명령어가 `windowId/groupId`, 대상 플레이어, mode, anchor/orbit 입력을 해석
2. `WindowManager`가 definition/groupDefinition 조회
3. 레이아웃 계산
4. 위치 계산
5. entity spawn 또는 pool 재사용
6. `WindowInstance` 또는 `WindowGroupInstance` 등록

### 클릭 처리

1. packet mixin이 우클릭을 잡음
2. 포인터 아이템이 아니면 즉시 무시
3. `WindowManager.findUiHit()`가 현재 smoothed 좌표 기준 raycast
4. `ClickHandler`가 action dispatch
5. 성공 시 `clickSound` 재생

### tick 업데이트

1. `WindowManager.tick()`
2. 모드별 target transform 계산
3. deadzone 적용
4. smoothing 적용
5. 이동 필요 시 entity 갱신
6. hover 계산

---

## 6. 설계 의사결정 리뷰

### 결정 1. `interaction` 엔티티 제거

현재 선택

- 클릭 입력은 `포인터 아이템 + raycast`
- entity hitbox 대신 수학 판정 사용

장점

- 버튼마다 별도 interaction 엔티티가 필요 없음
- hover/click 판정 로직이 하나로 수렴

단점

- 입력 계층이 `packet mixin`에 의존
- 맨손 우클릭을 일부러 포기한 구조
- 시각 위치와 논리 좌표가 어긋나면 바로 클릭 불가가 됨

판단

- 현재 요구사항에선 맞는 선택
- 다만 입력 계층은 계속 회귀 테스트가 필요

### 결정 2. 포인터 아이템 게이팅

장점

- 일반 우클릭과 UI 입력 충돌을 강하게 줄임
- UX가 명확함

단점

- 사용자가 아이템을 들고 있어야만 상호작용 가능
- VR 느낌은 오히려 약해질 수 있음

판단

- 현재 서버사이드 입력 구조에서는 합리적

### 결정 3. `PLAYER_FIXED`를 orbit 개념으로 재정의

장점

- `yaw=90`이면 창이 서쪽으로 가는 직관적 모델
- 창 회전과 창 위치 의미가 섞이지 않음

단점

- 초안에서 생각했던 "고정 회전값"과 의미가 달라짐
- 문서/샘플/명령 설명을 계속 맞춰줘야 함

판단

- 현재 구현 기준으로 이쪽이 훨씬 덜 헷갈림

### 결정 4. 그룹 시스템을 별도 JSON 루트로 분리

장점

- 기존 window JSON을 거의 안 건드림
- standalone과 group이 명확히 분리됨

단점

- 문서 없이 보면 window, group 두 축이 생겨 학습 비용이 증가
- reload/rebuild 경로가 늘어남

판단

- 맞는 선택
- 대신 운영 문서가 반드시 필요함

---

## 7. 강점

- 서버 중심 상태 관리가 명확함
- 테스트가 이미 명령/스키마/위치/interaction까지 넓게 깔려 있음
- Polymer, pointer, group, navigation이 서로 완전히 엉킨 상태는 아님
- 디버그 이벤트와 명령이 있어서 운영 추적성이 좋음

---

## 8. 기술 부채

### 8.1 `WindowManager` 비대화

현재 가장 큰 구조적 부채다.

문제

- 창 생성
- reload
- group 관리
- hover
- click target 검색
- action executor
- pooling 연계

전부 한 파일에 모여 있다.

당장 위험하진 않지만, 다음 단계에서 drag/scroll/animation이 들어오면 유지비가 급증한다.

### 8.2 `SchemaLoader` 책임 과다

현재는 다음이 한 파일에 들어 있다.

- JSON 파싱
- 검증 호출
- 샘플 파일 생성
- 이미지 예제 생성
- group 로드
- remote MAP 연동

이건 나중에 `WindowSchemaLoader`, `GroupSchemaLoader`, `DefaultConfigBootstrap` 정도로 쪼개는 게 맞다.

### 8.3 `meditate-layout` 사실상 미사용

현재 이름만 `MeditateLayoutEngine`이고 실제 계산은 내부 수제 구현이다.

이건 둘 중 하나로 정리해야 한다.

- 진짜 라이브러리 도입
- 아니면 디펜던시 제거

반쯤 걸친 상태가 제일 안 좋다.

### 8.4 panel 렌더 품질 한계

panel 배경은 여전히 `text_display` 우회 표현에 기대는 부분이 있다.

문제

- 크기/배경 품질이 display 자체 제약을 받음
- 진짜 사각 패널로 보기엔 한계가 남음

### 8.5 전환 실패 롤백

`open_window`, 그룹 전환, 모드 전환에서 target 검증은 넣었지만, spawn 중간 실패 시 이전 창을 100% 보존하는 완전 트랜잭션 수준은 아니다.

---

## 9. 성능 관점 리뷰

### 현재 괜찮은 점

- hover/click은 owner 기준만 계산
- pooling 있음
- update interval `2 tick`
- deadzone/smoothing으로 과잉 업데이트 줄임

### 주의할 점

- 플레이어 수 x 활성 창 수 x interactive component 수가 커지면 `findUiHit()` 비용이 바로 늘어남
- `WindowManager.tick()` 한 곳에서 대부분 처리하므로 장기적으로는 owner 단위 분할이 필요할 수 있음
- MAP/Canvas sync는 실제 인원수와 창 수가 커질 때 비용이 튈 수 있음

현재 판단

- 지금 규모에서는 충분히 감당 가능
- 대규모 서버 기준 최적화는 아직 이르다

---

## 10. 보안/운영 리뷰

### 좋은 점

- `run_command`는 whitelist 기반
- pointer item 없으면 UI 액션 실행 안 됨
- debug 정보는 명령 권한 분리됨
- schema 실패 시 기존 정의 유지 정책 있음

### 주의점

- `run_command`는 계속 whitelist 운영이 핵심
- group/window JSON은 운영자가 직접 수정하므로 문법 오류 대응 문서가 필요
- resource pack 자동 적용 실패 시 클라이언트 검증 절차가 반드시 있어야 함

---

## 11. 추천 정리 순서

### 우선순위 높음

1. 전환 실패 롤백 보강
2. group/window 운영 문서 정리
3. `WindowManager` 분리 기준 합의

### 우선순위 중간

1. `meditate-layout` 유지/제거 결정
2. panel 표현 계층 보강
3. runtime manual test checklist 고정

### 우선순위 낮음

1. `PLAYER_VIEW` 전용 시각 품질 고도화
2. drag/scroll 등 연속 입력
3. pointer visual polish

---

## 12. 최종 판단

현재 구조는 "망가진 실험 코드" 쪽은 아니다.  
다만 이미 `WindowManager`와 `SchemaLoader`에 책임이 몰리기 시작해서, 다음 단계 기능을 더 얹기 전에 문서화와 경계 정리가 필요하다.

지금 기준 아키텍처 평가는 이렇다.

- 구조 명확성: 좋음
- 확장성: 보통 이상
- 유지보수성: 지금은 괜찮음, 다음 단계 전 분리 필요
- 운영 추적성: 좋음
- 표현 품질: Minecraft display 제약으로 제한적

한 줄 결론:

`지금 구조는 계속 밀어도 되지만, 다음 단계부터는 WindowManager/SchemaLoader 분리 없이는 금방 무거워짐`

# InteractiveDisplay 빌드 / 검증 / 수동 테스트 가이드

> 기준 시점: 2026-03-06  
> 대상 환경: WSL / Linux shell / JDK 21 / Minecraft 1.21.8 / Fabric

---

## 1. 문서 목적

이 문서는 4가지를 정리한다.

- 코드 빌드 방법
- 자동 검증 방법
- 자주 걸리는 함정
- 서버 수동 테스트 절차

이 프로젝트는 일반 Gradle 실행과 샌드박스 실행이 완전히 같지 않다. 그 차이를 먼저 이해해야 덜 꼬인다.

---

## 2. 전제 조건

필수

- WSL 안에서 작업
- JDK 21
- 권한 있는 환경에서 최소 1회 Gradle 의존성/loom 캐시 생성 완료

현재 프로젝트는 샌드박스 제약 때문에 아래 상황이 있다.

- 일반 `./gradlew ...`는 `.gradle` 캐시 락이나 로컬 소켓 제한에 걸릴 수 있음
- 샌드박스에서는 `scripts/sandbox-build.sh`, `scripts/sandbox-test.sh`가 더 안정적

---

## 3. 권장 검증 순서

가장 안전한 순서는 이거다.

1. 샌드박스 빌드
2. 샌드박스 테스트
3. 필요하면 권한 있는 환경에서 Gradle `compileJava`, `test`
4. 실제 서버 수동 테스트

---

## 4. 샌드박스 빌드

명령

```bash
./scripts/sandbox-build.sh
```

의미

- 로컬 캐시를 재사용해서 메인 소스 컴파일
- `.sandbox-build` 아래에 classpath, main class, resource 결과물 생성

성공 기준

- 출력에 `sandbox build ok`

실패 시 먼저 볼 것

- `~/.gradle/caches/fabric-loom` 캐시가 있는지
- 프로젝트 `.gradle` 안에 remapped mod cache가 이미 있는지
- 권한 있는 환경에서 Gradle 빌드를 한 번이라도 돌렸는지

---

## 5. 샌드박스 테스트

명령

```bash
./scripts/sandbox-test.sh
```

의미

- 샌드박스용 컴파일 결과를 기준으로 테스트 실행
- headless 강제
- 소켓 생성이 필요한 일부 테스트는 제외

현재 제외 테스트

- `com.interactivedisplay.schema.RemoteImageCacheTest`

성공 기준

- 마지막에 `tests successful`

주의

- 이 스크립트가 통과했다고 해서 클라이언트 리소스팩 적용, 실제 서버 입력, display 시각 품질까지 보장되진 않음
- 수동 테스트는 별도로 필요

---

## 6. 일반 Gradle 빌드

권한 있는 환경에서 권장 명령

```bash
GRADLE_USER_HOME=/home/parkj/test-projects/MC_InteractiveDisplay/.gradle ./gradlew compileJava --no-daemon
GRADLE_USER_HOME=/home/parkj/test-projects/MC_InteractiveDisplay/.gradle ./gradlew test --no-daemon
```

주의

- `.gradle/caches/journal-1` 락 충돌이 자주 난다
- 이미 다른 Gradle 프로세스가 잡고 있으면 바로 실패한다

대응

1. 기존 Gradle 프로세스 종료 확인
2. 같은 `GRADLE_USER_HOME`로 동시에 여러 빌드 돌리지 않기
3. 그래도 꼬이면 샌드박스 스크립트로 우선 검증

---

## 7. 자주 겪는 문제와 대응

### 7.1 `.gradle` 저널 락

증상

- `Timeout waiting to lock journal cache`

원인

- 다른 Gradle 프로세스가 같은 캐시 사용 중

대응

- 해당 프로세스 종료
- 같은 캐시 경로 중복 사용 금지

### 7.2 샌드박스 네트워크 제한

증상

- Gradle distribution 다운로드 실패
- localhost 소켓 생성 실패

원인

- 샌드박스가 네트워크/소켓 막음

대응

- `scripts/sandbox-build.sh`
- `scripts/sandbox-test.sh`
- 또는 권한 상승/샌드박스 밖에서 Gradle 실행

### 7.3 Polymer 리소스팩 적용 안 됨

확인 순서

1. 서버 시작 로그에서 resource pack bootstrap 경고 확인
2. `config/polymer/auto-host.json`, `resource-pack.json` 생성 여부 확인
3. `run/polymer/source_assets` 경로 생성 여부 확인
4. 새 클라이언트 접속 시 pack 적용 팝업/필수 적용 상태 확인

### 7.4 창이 안 열림

확인 순서

1. `/interactivedisplay list`
2. `/interactivedisplay group list`
3. `/interactivedisplay debug recent`
4. 최신 로그에서 `SCHEMA_VALIDATION_FAILED` 확인

### 7.5 버튼 클릭 안 됨

확인 순서

1. 메인핸드에 `interactivedisplay:pointer` 들고 있는지
2. 버튼 hover 색상 변하는지
3. `/interactivedisplay debug bindings <player>` 결과 확인
4. 현재 창 mode와 hit 위치가 어긋나지 않는지 시각 확인

---

## 8. 서버 실행 전 체크리스트

- `run/config/interactivedisplay/windows` JSON 문법 확인
- `run/config/interactivedisplay/groups` JSON 문법 확인
- `command_whitelist.json` 확인
- 포인터 아이템 모델/텍스처 리소스가 리소스팩에 포함되는지 확인
- 새 그룹 또는 새 action type을 넣었으면 `/interactivedisplay reload` 테스트 선행

---

## 9. 수동 테스트 기본 시나리오

### 9.1 기본 로드

명령

```mcfunction
/interactivedisplay reload
/interactivedisplay list
/interactivedisplay group list
```

기대 결과

- `main_menu`, `gallery` 같은 기본 창이 보임
- `menu_group` 같은 기본 그룹이 보임
- broken 항목이 없거나, 있으면 바로 원인 추적 가능

### 9.2 FIXED 창 생성

명령

```mcfunction
/interactivedisplay create main_menu @s fixed
```

확인 항목

- 창이 월드에 고정됨
- 제목/내용/배경/버튼이 정상 배치됨
- 버튼 hover와 클릭이 정상

### 9.3 PLAYER_FIXED 절대 orbit

명령

```mcfunction
/interactivedisplay create main_menu @s player_fixed 0 0
/interactivedisplay create main_menu @s player_fixed 90 0
/interactivedisplay create main_menu @s player_fixed -90 0
/interactivedisplay create main_menu @s player_fixed 180 0
```

확인 항목

- `0`은 플레이어 남쪽
- `90`은 플레이어 서쪽
- `-90`은 플레이어 동쪽
- `180`은 플레이어 북쪽
- 창 면은 항상 플레이어를 향함
- 표시 pitch는 기울지 않음

### 9.4 PLAYER_FIXED 상대 orbit

명령

```mcfunction
/interactivedisplay create main_menu @s player_fixed ~ ~
/interactivedisplay create main_menu @s player_fixed ~15 ~-10
```

확인 항목

- 명령 실행 당시 시점 기준으로 orbit 위치가 정해짐
- 창은 다시 플레이어를 향함
- 이후 플레이어가 움직여도 orbit 기준만 유지되고 현재 시선 자체는 추적하지 않음

### 9.5 PLAYER_VIEW 안정화

명령

```mcfunction
/interactivedisplay create main_menu @s player_view
```

확인 항목

- 미세한 시선 흔들림에 과민하게 튀지 않음
- 빠른 회전 후 급정지 시 조금 늦게 따라오되 과하게 떠다니지 않음
- hover 위치와 실제 렌더 위치가 맞음

### 9.6 버튼 action

확인 항목

- `close_window`
- `open_window`
- `switch_mode_fixed`
- `switch_mode_player_fixed`
- `run_command`
- `callback`
- `clickSound`

버튼 클릭 시 기대 결과

- action consumed면 효과음 재생
- invalid sound id면 warn 로그만 남고 액션은 계속 처리

### 9.7 그룹 전환

명령

```mcfunction
/interactivedisplay group create menu_group @s player_fixed
```

확인 항목

- 초기 창이 그룹 정의대로 열림
- 그룹 내 다른 창으로 전환 시 mode 유지
- 창마다 `offset`, `orbit` 차이가 적용됨
- 그룹 제거 시 현재 그룹 창이 정상 제거됨

### 9.8 포인터 아이템

명령

```mcfunction
/give @s interactivedisplay:pointer
```

확인 항목

- 포인터를 들었을 때만 hover/click 반응
- 다른 아이템 또는 빈손이면 버튼 액션 실행 안 됨

### 9.9 리소스팩

확인 항목

- 새 접속 클라이언트가 자동 리소스팩 적용 받는지
- 거부 시 접속 제한 정책이 실제로 원하는 대로 동작하는지
- pointer 아이템 모델/텍스처가 정상 렌더되는지
- config 이미지가 `run/polymer/source_assets`로 복사되는지

---

## 10. 수동 테스트 심화 시나리오

### 그룹 + 모드 전환 조합

1. `group create menu_group @s player_fixed`
2. 그룹 내 링크 버튼 클릭
3. `switch_mode_fixed` 버튼 클릭
4. 다시 링크 버튼 클릭

확인 항목

- 그룹 컨텍스트 유지 여부
- mode 유지/전환 규칙
- 창 위치와 hover 위치 일치 여부

### 리로드 회귀

1. 창 띄움
2. JSON 수정
3. `/interactivedisplay reload`
4. 기존 창/그룹 재구성 확인

확인 항목

- 기존 활성 창이 새 정의로 갱신되는지
- 오류가 있으면 기존 성공본 정의 유지되는지

### 실패 경로

1. 없는 창 target을 가진 버튼 클릭
2. 잘못된 group JSON 배치
3. invalid clickSound 사용
4. whitelist에 없는 `run_command`

확인 항목

- 서버 크래시 없이 실패
- debug recent에 원인 노출
- 기존 창 상태가 불필요하게 사라지지 않는지

---

## 11. 운영 중 주의할 점

### 11.1 JSON 수정 후 바로 라이브 반영 금지

권장 순서

1. 파일 수정
2. `/interactivedisplay reload`
3. `/interactivedisplay list`
4. `/interactivedisplay debug recent`
5. 새 창 생성

바로 실서비스 그룹 창부터 건드리면 오류 추적이 귀찮아진다.

### 11.2 창 정의와 그룹 정의를 같이 바꿀 때

권장

- window 먼저
- reload
- group 수정
- reload

한 번에 두 축 바꾸면 어디서 깨졌는지 보기 어려움

### 11.3 대형 창 추가 시

반드시 확인할 것

- interactive component 수
- hover 지연
- player_view 추적 품질
- map canvas sync 비용

---

## 12. 현재 추천 검증 세트

코드 수정 직후 최소 검증

```bash
./scripts/sandbox-build.sh
./scripts/sandbox-test.sh
```

릴리즈 전 권장 검증

```bash
GRADLE_USER_HOME=/home/parkj/test-projects/MC_InteractiveDisplay/.gradle ./gradlew compileJava --no-daemon
GRADLE_USER_HOME=/home/parkj/test-projects/MC_InteractiveDisplay/.gradle ./gradlew test --no-daemon
```

운영 반영 전 수동 검증

1. `reload`
2. `list`, `group list`
3. `fixed`, `player_fixed`, `player_view`
4. 포인터 hover/click
5. mode switch
6. open_window
7. group navigation
8. resource pack 적용

---

## 13. 마지막 정리

이 프로젝트는 "컴파일 성공"만으로 끝나는 종류가 아니다.

반드시 같이 봐야 하는 축은 이거다.

- 명령 파싱
- 위치 계산
- raycast 클릭
- 리소스팩 적용
- 그룹 전환

한 줄 결론:

`자동 테스트로 계약을 지키고, 실제 서버에서는 위치/입력/리소스팩 3개를 꼭 수동 확인해야 함`

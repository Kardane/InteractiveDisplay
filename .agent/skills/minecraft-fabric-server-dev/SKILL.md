---
name: minecraft-fabric-server-dev
description: "Minecraft Java Edition 1.21.8 Fabric 서버사이드 모드를 설계, 구현, 디버깅, 배포할 때 사용한다. Fabric API, Mixin, Polymer, Minecraft 내부 소스/매핑/call graph 분석(mcdev-mcp)을 하나의 구현 워크플로우로 다루며, 행동 검증만 fabric-server-validation에 위임한다. WSL/Linux 스캐폴딩, 버전 잠금, 주입 충돌 대응, 빌드/릴리스/롤백 절차를 포함한다."
---

# Minecraft Fabric Server Dev

이 스킬은 "돌아가기만 하는 모드"가 아니라, 운영 서버에서 장애 없이 굴러가는 서버사이드 Fabric 모드를 만드는 실무용 표준이다.

## 적용 범위

- Minecraft Java Edition `1.21.8`
- Fabric Loader + Fabric API
- Mixin 기반 런타임 주입
- Patbox Polymer 기반 서버 표현 계층
- WSL/Linux 셸(`.sh`) 기준 자동화

## 빠른 시작

1. 새 프로젝트 생성
`./scripts/new-fabric-server-mod.sh -ProjectName <name> -PackageBase <pkg> -OutputDir <path>`

2. 생성 직후 정합성 검증
`./scripts/verify-mod-env.sh -ProjectDir <path>/<name>`

3. Minecraft 내부 구현, lifecycle, Mixin target이 불확실하면 **내장 mcdev-mcp 소스 분석 절차**를 먼저 수행한다. `references/mcdev-source-analysis.md`와 `references/mcdev-query-playbook.md`를 필요한 범위만 읽는다.

4. placeholder 버전 치환 후 빌드
`./gradlew clean build`

5. 런타임/행동 검증은 `fabric-server-validation`에 위임. 기본적으로 GUI/Computer Use보다 GameTest 또는 MCP Fabric + Carpet 상태 검증을 우선

## 실무 워크플로우

### 1) 요구사항 잠금

- 서버 타입: 소규모/중규모/대규모 멀티
- 핵심 기능: 명령, 이벤트, 상태 동기화, 시각 표현
- 위험도 분류: 낮음(편의) / 중간(진행) / 높음(경제·전투)

### 2) 버전 잠금

- Java Toolchain: `21`
- Minecraft: `1.21.8`
- Fabric API/Loader/Polymer: `1.21.8` 호환 안정 버전으로 고정
- 버전 변경은 한 축씩만 수행하고 매번 서버 기동 검증

### 3) 아키텍처 잠금

- `core`: 게임 규칙/상태/권한(서버 진실 소스)
- `polymer`: 서버 상태를 클라이언트 표현으로 투영
- `mixin`: 기본 API로 못 잡는 지점만 최소 주입

### 4) 구현 루프

1. 순수 서버 로직 작성
2. Minecraft 내부 메서드/호출 순서가 불확실하면 mcdev-mcp로 class/method/caller/callee를 확인하고, 필요 시 `references/mcdev-source-analysis.md`를 읽는다.
3. 단위 기능 검증
4. 필요한 최소 Mixin 주입
5. Polymer 표현 연결
6. `fabric-server-validation`로 가장 싼 충분한 검증 경로를 선택해 행동 검증
7. 로그/프로파일 점검 후 다음 기능으로 이동

### 5) 검증 루프

- 구조 검증: `verify-mod-env.sh`
- 빌드 검증: `./gradlew clean build`
- 행동/런타임 검증: `fabric-server-validation`에 위임
- 기본 우선순위: Unit/JUnit → Server GameTest → MCP Fabric + Carpet runtime harness → 실제 network contract가 필요할 때 Mineflayer → 필요한 경우에만 client/vanilla gate
- 안정성 검증: 반복 틱/다중 플레이어/예외 흐름
- 컴파일/서버 기동 성공만으로 인게임 동작 PASS를 선언하지 않는다.

### 6) 릴리스/롤백

- 릴리스 전 체크리스트 100% 통과
- 아티팩트명/버전/의존성 메타 일치 확인
- 직전 안정 버전 JAR + 롤백 절차 항상 보관

## 스크립트 계약 (WSL/Linux)

### `scripts/new-fabric-server-mod.sh`

- 목적: 1.21.8 서버사이드 모드 스캐폴드 생성
- 기본 계약:
  - `-ProjectName <name>` 또는 `--project-name <name>`
  - `-PackageBase <pkg>` 또는 `--package-base <pkg>`
  - `-OutputDir <path>` 또는 `--output-dir <path>`
- 확장 옵션:
  - `--mod-id <id>`
  - `--minecraft-version <version>`
  - `--loader-version <ver>`
  - `--fabric-api-version <ver>`
  - `--polymer-version <ver>`
  - `--yarn-mappings <ver>`

생성 결과:
- `gradle.properties`, `build.gradle`, `settings.gradle`
- `fabric.mod.json`, `<modid>.mixins.json`
- `MainMod`, `ServerLifecycleMixin`, `PolymerBridge` 샘플 클래스

### `scripts/verify-mod-env.sh`

- 목적: 구조/메타/버전/서버 전용 규칙 검증
- 계약:
  - `-ProjectDir <path>` 또는 `--project-dir <path>`
- 주요 검사:
  - 필수 파일/디렉터리 존재
  - `minecraft_version=1.21.8`, Java 21, 버전 키 존재
  - `fabric.mod.json` JSON 파싱 + `environment=server`
  - mixin 설정 JSON 파싱/핵심 키 검증
  - 클라이언트 전용 import 사용 탐지(경고)
  - placeholder 미치환 값 탐지(경고)

## 스킬 경계와 내부 백엔드

이 스킬은 **Fabric 구현 관제탑**이다. 별도의 source-analysis 스킬을 선택하게 하지 않고 mcdev-mcp 분석을 이 스킬 내부 절차로 수행한다. 행동 검증 정책만 `fabric-server-validation`에 위임한다.

| 상황 | 처리 위치 |
|---|---|
| Minecraft 1.21.8 클래스/메서드/field/call graph/Mixin target 분석 | 이 스킬 내부 mcdev-mcp 절차 |
| 변경 사항의 행동/런타임 검증 | `fabric-server-validation` |
| `.dat`/`.mca` 오프라인 월드 데이터 검사/수정 | `minecraft-java-world-nbt` |
| 데이터팩/리소스팩 제작 | `minecraft-java-content-engineering` |
| 일반 Java Edition 버전별 레퍼런스 질의 | `minecraft-java-reference-hub` |

일반적인 수정 루프:

```text
repo inspect
→ 필요 시 mcdev-mcp source/call-graph 분석
→ production code 최소 수정
→ fabric-server-validation
   ├─ Unit / Server GameTest
   ├─ MCP Fabric + Carpet fake player
   ├─ Mineflayer protocol E2E
   └─ higher-fidelity client/vanilla/packet gate when justified
→ broader regression/build
→ evidence report
```

테스트 정책의 단일 source of truth는 `fabric-server-validation`이다. GameTest, MCP Fabric, Carpet, Mineflayer를 별도 활성 Skill로 분리하지 않는다.

## 문제 대응 우선순위

1. 빌드 실패면 버전 키/매핑/의존성부터 확인
2. 서버 기동 실패면 `fabric.mod.json`/entrypoint/mixins 파일 확인
3. Mixin 실패면 시그니처/At 지점/우선순위부터 축소 검증
4. Polymer 이상이면 서버 상태와 표시 계층 결합 여부부터 분리

## 안티패턴

- 기능 여러 개를 하나의 Mixin에 몰아넣는 방식
- client API 호출을 서버 경로에 섞는 방식
- 버전 업/다운을 한번에 여러 라이브러리로 진행하는 방식
- 체크리스트 없이 "되겠지" 배포하는 방식
- 서버 상태로 증명 가능한 동작을 기본적으로 Computer Use/수동 GUI 조작으로 검증하는 방식

## 리팩토링/검증 공통 규칙 (범용)

이 절은 특정 프로젝트가 아니라 Fabric 서버사이드 모드 전반에 공통 적용하는 규칙이다.

### 1) 패스스루 래퍼 제거 규칙

- `A -> B`로 그대로 전달만 하는 메서드/클래스(로직 0, 검증 0, 변환 0)는 제거 후보로 본다.
- 공개 API 유지를 위해 남겨야 하면 다음 중 하나는 반드시 있어야 한다.
  - 입력 검증
  - 권한/상태 게이트
  - 타입/포맷 변환
  - 계측/로깅/트랜잭션 경계
- 위 조건이 없으면 호출 깊이만 늘어나므로 직접 호출로 평탄화한다.

### 2) 중첩 정책 구조 평탄화 규칙

- `Policy.InnerPolicy.method()` 형태에서 메서드가 단순 분기/비교 수준이면 평탄화한다.
- 권장 형태:
  - `enum Decision`
  - `static Decision decideX(...)`
  - `static boolean/int resolveX(...)`
- 중첩 클래스를 유지하는 경우는 "도메인 경계 분리 이득"이 분명할 때만 허용.

### 3) 패키지/디렉터리 정리 규칙

- 하위 패키지에 파일 1개만 있고 의미적 경계가 약하면 부모로 승격.
- 빈 패키지 디렉터리는 즉시 삭제.
- 파일 이동 후 `package` 선언과 import를 항상 같은 턴에 정리.

### 4) 참조 정합성 점검 규칙

- 리네임/이동/병합 직후 구 경로 참조 0건을 보장.
- 권장 점검:
  - `rg -n "<old.package|old.class>" src/main/java src/test/java`
  - 결과가 0건인지 확인 후 다음 단계 진행.

### 5) 빌드/테스트 검증 표준

- 서버사이드 모드 리팩토링 후 최소 검증 명령:
  - `GRADLE_USER_HOME=<repo>/.gradle ./gradlew test --no-daemon`
- 큰 구조 변경일수록 `clean build`를 추가:
  - `GRADLE_USER_HOME=<repo>/.gradle ./gradlew clean build --no-daemon`

### 6) Mixin 안전 분기 규칙

- 입력 차단/행동 제한 Mixin에는 운영 예외 분기를 명시적으로 둔다.
  - 예: 크리에이티브, OP 권한, 관리자 태그
- 제한 로직 실패 시 복구 동작(인벤토리 동기화 등)을 같이 둔다.
- 안전 기본값 원칙:
  - 보안/악용 방지 로직은 fail-closed
  - 운영 편의 경로는 명확한 조건에서만 예외 허용

### 7) 명령어/권한/문서 동기화 규칙

- Brigadier 명령 트리 변경 시 다음을 한 세트로 업데이트:
  - 실제 명령어 경로
  - 권한 노드
  - README 운영 문서
- 셋 중 하나라도 누락되면 릴리즈 금지.

### 8) 커밋 메시지 규칙

- Conventional Commits 사용:
  - `feat: ...`
  - `fix: ...`
  - `refactor: ...`
  - `docs: ...`
- 구조 이동/대량 삭제는 메시지에 의도를 명시:
  - 예: `refactor: flatten package hierarchy and remove pass-through services`

### 9) 과엔지니어링 점검 체크리스트

- 클래스가 "의미 있는 책임" 대신 "호출 전달"만 하는가?
- 인터페이스/전략 패턴이 실제 확장 요구보다 앞서 있는가?
- 추상화 1단계 추가로 디버깅 경로가 길어졌는가?
- 같은 목적의 타입/유틸 네이밍이 중복되거나 중첩되는가?
- 테스트가 추상화 자체를 검증하느라 도메인 검증이 약해졌는가?

하나라도 `예`면 먼저 단순화 방향을 검토한다.

### 10) 변경 파이프라인 고정 순서

1. 파일 이동/병합/삭제
2. `package`/import/호출 경로 치환
3. 빈 디렉터리 정리
4. `rg`로 구 참조 0건 확인
5. `./gradlew test --no-daemon` 검증
6. 커밋(Conventional Commit)
7. 푸시/릴리즈

## 참조 문서 인덱스

- Minecraft 1.21.8 소스/call graph/mapping 분석: [references/mcdev-source-analysis.md](references/mcdev-source-analysis.md)
- mcdev-mcp 질의 패턴: [references/mcdev-query-playbook.md](references/mcdev-query-playbook.md)
- Fabric 설정/버전 전략: [references/fabric-setup-1.21.8.md](references/fabric-setup-1.21.8.md)
- Mixin 패턴/충돌 해소: [references/mixin-patterns.md](references/mixin-patterns.md)
- Polymer 서버 사용 패턴: [references/polymer-server-usage.md](references/polymer-server-usage.md)
- 검증/릴리스 체크리스트: [references/verification-release-checklist.md](references/verification-release-checklist.md)
- 운영 장애 시그니처: [references/runtime-failure-signatures.md](references/runtime-failure-signatures.md)

## 출력 템플릿

```markdown
기준 버전: Java 1.21.8 / JDK 21

요약
- 목표: ...
- 범위: core | mixin | polymer

구현 단계
1. ...
2. ...
3. ...

검증
- 구조 검증: PASS|FAIL
- 빌드 검증: PASS|FAIL
- 런타임 검증: PASS|FAIL

리스크
- 낮음|중간|높음: ...
```

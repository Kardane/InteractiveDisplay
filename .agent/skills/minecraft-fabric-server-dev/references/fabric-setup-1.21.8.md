# Fabric 서버사이드 환경 설정 (Java 1.21.8)

## 목차

1. 대상 고정값
2. 프로젝트 구조 표준
3. 버전 잠금 전략
4. Gradle 설정 기준
5. 서버사이드 규칙
6. 초기 부팅 검증
7. CI 권장 항목

## 1) 대상 고정값

- Minecraft: `1.21.8`
- Java: `JDK 21`
- 실행 환경: Dedicated Server 우선

왜 고정하나:
- Mixin 시그니처와 Yarn 매핑은 버전에 민감하다.
- 버전이 흔들리면 Mixin/Polymer 장애 분석 비용이 폭증한다.

## 2) 프로젝트 구조 표준

```text
<project>/
├─ build.gradle
├─ settings.gradle
├─ gradle.properties
└─ src/main/
   ├─ java/<package>/
   │  ├─ MainMod.java
   │  ├─ mixin/
   │  │  └─ ServerLifecycleMixin.java
   │  └─ polymer/
   │     └─ PolymerBridge.java
   └─ resources/
      ├─ fabric.mod.json
      └─ <modid>.mixins.json
```

원칙:
- `core`, `mixin`, `polymer` 책임을 물리적으로 분리한다.
- 엔트리포인트 클래스를 얇게 유지한다.

## 3) 버전 잠금 전략

`gradle.properties` 권장 키:

```properties
org.gradle.jvmargs=-Xmx2G
minecraft_version=1.21.8
yarn_mappings=1.21.8+build.1
loader_version=<loader_compatible_version>
fabric_api_version=<fabric_api_compatible_version>
polymer_version=<polymer_compatible_version>
maven_group=com.example
archives_base_name=example-mod
```

운영 규칙:
1. `minecraft_version` 변경 전에는 나머지 버전 변경 금지
2. 버전 키 1개 변경 -> 빌드/부팅/핵심 시나리오 검증
3. 통과 후 다음 키 변경

## 4) Gradle 설정 기준

`build.gradle` 핵심:

- `fabric-loom` 플러그인 적용
- `minecraft`, `mappings`, `fabric-loader`, `fabric-api`, `polymer-core` 선언
- Java toolchain 21 고정
- `fabric.mod.json` version expand

권장 검증 명령:

```bash
./gradlew clean build --stacktrace
./gradlew dependencies
```

실무 메모:
- `repositories`에 Fabric/Polymer 저장소 누락 시 의존성 해석이 실패한다.
- plugin 버전 문제는 종종 dependency 문제처럼 보인다. 분리해서 본다.

## 5) 서버사이드 규칙

1. `fabric.mod.json`에서 `"environment": "server"` 유지
2. 서버 경로 코드에 `net.minecraft.client` import 금지
3. 클라이언트 전용 렌더 API는 Polymer 어댑터 계층 바깥에서 호출 금지

## 6) 초기 부팅 검증

1. 정적 검증
- `verify-mod-env.sh` 실행

2. 빌드
- `./gradlew clean build`

3. 런타임
- 개발 서버 부팅
- 엔트리포인트 초기화 로그 확인
- Mixin apply 실패 로그 0건 확인

4. 기능 검증
- 서버 명령/이벤트/상호작용 핵심 흐름 확인
- Polymer 표현 대상 기능 점검

## 7) CI 권장 항목

최소 파이프라인:
1. Java 21 셋업
2. `./gradlew clean build`
3. `verify-mod-env.sh` 실행
4. 아티팩트 업로드

추가 권장:
- 릴리스 브랜치에서만 서명/배포
- `gradle.properties` placeholder 미치환 차단

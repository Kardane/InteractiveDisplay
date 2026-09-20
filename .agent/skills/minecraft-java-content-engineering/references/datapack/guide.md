---
name: minecraft-java-datapack-engineering
description: 마인크래프트 자바 에디션 데이터팩을 실무 수준으로 설계, 구현, 검증, 운영해야 할 때 사용한다. pack.mcmeta 버전별 작성법(1.21.8~1.21.11), 폴더 구조, mcfunction 문법, 함수 매크로 작성/호출, NBT 및 structure .nbt 처리, 콘텐츠 카탈로그, 서버 배포/롤백, 성능/보안 검증 절차를 포함한다.
---

# Minecraft Java Datapack Engineering

이 스킬은 데이터팩을 "돌아가게" 만드는 수준이 아니라, 서버 운영까지 버틸 수 있게 만드는 제작 표준이다.

## 빠른 시작

1. 목표와 대상 버전을 먼저 확정한다.
2. `references/mcmeta-by-version.md`에서 버전별 `pack.mcmeta` 형식을 고정한다.
3. `references/datapack-folder-structure.md`의 표준 구조로 폴더를 잡는다.
4. `references/datapack-content-catalog.md`에서 필요한 콘텐츠 타입만 선택한다.
5. `references/datapack-syntax-guide.md` 기준으로 `mcfunction`/JSON/NBT를 작성한다.
6. 함수 매크로가 필요하면 `references/function-macro-guide.md`를 먼저 확인한다.
7. 필요하면 `scripts/create_datapack_scaffold.sh`로 기본 골격을 생성한다.
8. `scripts/validate_datapack_layout.sh`로 1차 검증 후 배포한다.

## 워크플로우

### 1) 요구사항 잠금

- 서버 종류: 싱글/소규모 멀티/대규모 멀티
- 핵심 콘텐츠: 명령/발전과제/루트테이블/월드젠/대화/밸런스
- 위험도: 낮음(편의) / 중간(진행) / 높음(경제·PvP·운영)

### 2) 버전 잠금

- `1.21.8`과 `1.21.11`은 `pack.mcmeta` 작성 방식이 다르다.
- 버전 미확정 상태에서 문법 단정 금지.
- 버전 차이는 `references/datapack-version-diff.md`에서 먼저 점검.

### 3) 아키텍처 설계

- 데이터 경계: `function`, `advancement`, `loot_table`, `worldgen`, `dialog` 등
- 실행 경계: `load`와 `tick` 분리
- 운영 경계: 백업/롤백/권한/성능 기준 정의

### 4) 구현

- `mcfunction`은 단계별로 쪼개고 디버깅 가능한 흐름으로 작성
- 함수 매크로는 고정 명령 재사용이 명확할 때만 사용
- NBT/item component는 경로/타입/대상을 분리해 설명
- 루프형 명령은 항상 범위를 좁힌 선택자로 제한

### 5) 검증

- 구조 검증: 파일 경로/확장자/네임스페이스
- 문법 검증: 명령어 실행 경로, JSON 파싱, 태그 로딩
- 운영 검증: TPS, 권한 오남용, 롤백 리허설

## 콘텐츠 적용 범위

이 스킬은 데이터팩으로 다루는 핵심 콘텐츠 전반을 커버한다.

- 게임플레이: advancement, recipe, loot_table, tags, function, predicate
- 시스템 데이터: damage_type, enchantment, enchantment_provider, chat_type, dialog
- 월드/환경: dimension_type, worldgen 하위 전부(biome, structure_set, template_pool 등)
- 비주얼/개체 정의: banner_pattern, painting_variant, trim_material, trim_pattern, wolf_variant 등
- 테스트/실험: test_environment, test_instance, timeline, datapacks(실험팩)

상세 카탈로그는 [references/datapack-content-catalog.md](references/datapack-content-catalog.md) 참조.

## 실무 품질 규약

1. 첫 줄에 기준 버전을 명시한다.
2. `load`와 `tick`의 책임을 분리한다.
3. 함수는 목적별로 쪼개고 파일당 역할 1개를 유지한다.
4. 선택자 범위를 최소화한다(`@a`/`@e` 전역 남용 금지).
5. 경제/PvP 영향 기능은 롤백 절차를 같이 제공한다.
6. 버전 업 대응은 마이그레이션 체크리스트 기반으로 진행한다.

## 금지사항

- 버전 미확정 상태에서 `pack.mcmeta` 샘플을 단정해서 주기
- 데이터팩 로직과 리소스팩 시각 변경을 혼합 설명
- `.nbt` 구조 파일 수정 시 백업 없이 직접 덮어쓰기
- 고빈도 tick 함수에서 전 대상 풀스캔 실행

## 파일 인덱스

- 콘텐츠 카탈로그: [references/datapack-content-catalog.md](references/datapack-content-catalog.md)
- 문법 가이드: [references/datapack-syntax-guide.md](references/datapack-syntax-guide.md)
- 함수 매크로 가이드: [references/function-macro-guide.md](references/function-macro-guide.md)
- 폴더 구조: [references/datapack-folder-structure.md](references/datapack-folder-structure.md)
- NBT/.nbt 가이드: [references/nbt-and-structure-nbt.md](references/nbt-and-structure-nbt.md)
- 버전별 mcmeta: [references/mcmeta-by-version.md](references/mcmeta-by-version.md)
- 버전 차이: [references/datapack-version-diff.md](references/datapack-version-diff.md)

## 스크립트

### `scripts/create_datapack_scaffold.sh`

데이터팩 기본 골격을 버전별 `pack.mcmeta`와 함께 생성한다.

```bash
./scripts/create_datapack_scaffold.sh \
  --version 1.21.11 \
  --pack-name my_pack \
  --namespace mypack \
  --output-dir /tmp/datapacks
```

### `scripts/validate_datapack_layout.sh`

핵심 구조와 버전별 `pack.mcmeta` 키를 1차 검증한다.

```bash
./scripts/validate_datapack_layout.sh \
  --pack-dir /tmp/datapacks/my_pack \
  --version 1.21.11
```

## 출력 템플릿

```markdown
기준 버전: Java Edition <version>

요약
- 목표: ...

구현 단계
1. ...
2. ...
3. ...

검증
- 함수 로드 확인: ...
- 명령 실행 확인: ...
- 운영 영향 확인: ...

리스크
- 낮음|중간|높음: ...
```

---
name: minecraft-java-resourcepack-engineering
description: 마인크래프트 자바 에디션 1.21.8 리소스팩을 실무 수준으로 설계, 제작, 검증, 배포할 때 사용한다. pack.mcmeta(1.21.8), 폴더/파일 구조, 이미지 규격, 텍스처 규칙, blockstates/models/items, 코어 셰이더, 아틀라스, 폰트, 사운드, 확장자 규칙, WSL 검증 스크립트까지 포함한다.
---

# Minecraft Java Resourcepack Engineering

대충 보이는 텍스처 교체가 아니라, 서버/클라이언트 운영에서 재현 가능한 리소스팩 제작 표준을 제공한다.

## 빠른 시작

1. 대상 버전을 `Java Edition 1.21.8`으로 고정한다.
2. `references/resourcepack-1.21.8-pack-mcmeta.md`로 `pack.mcmeta`를 먼저 확정한다.
3. `references/resourcepack-1.21.8-content-catalog.md`에서 필요한 콘텐츠 타입을 선택한다.
4. 골격이 필요하면 `scripts/create_resourcepack_scaffold.sh`로 기본 구조를 생성한다.
5. 텍스처/모델/셰이더/폰트는 각 참조 문서를 따라 구현한다.
6. `scripts/validate_resourcepack_layout.sh`로 구조/포맷/확장자 검증 후 배포한다.

## 워크플로우

### 1) 요구사항 잠금

- 목표: UI 리스킨 / 아이템 외형 / 블록 외형 / 폰트 / 사운드 / 셰이더 / 혼합
- 배포 대상: 싱글 / 모드팩 / 멀티 서버 리소스팩 강제 배포
- 품질 기준: 시각 완성도, 프레임 영향, 충돌 허용치

### 2) 버전 잠금

- 이 스킬의 기본 버전은 `1.21.8`이다.
- `pack_format`은 `64`를 사용한다.
- 버전 미확정 상태에서 샘플 JSON을 확정하지 않는다.

### 3) 구조 설계

- 네임스페이스 전략: `minecraft`(바닐라 오버라이드) vs 커스텀 네임스페이스
- 자산 경계: 텍스처/모델/아이템 정의/폰트/사운드/셰이더를 분리
- 운영 경계: 개발용 팩과 배포용 팩 구분

### 4) 구현

- 텍스처는 먼저 해상도 기준(베이스 16px 또는 의도된 고해상도)을 고정한다.
- 아이템 표현 분기는 `items/*.json` 기준으로 설계한다.
- 블록 표현은 `blockstates` + `models/block`를 함께 설계한다.
- 셰이더 변경은 작은 단위로 적용하고 즉시 시각 회귀를 확인한다.

### 5) 검증

- 구조 검증: 필수 경로, 파일명, 소문자 규칙
- 포맷 검증: `pack.mcmeta`와 JSON 파싱
- 자산 검증: 확장자 규칙, 참조 경로 유효성, 애니메이션 `png.mcmeta` 짝 검증

### 6) 릴리스

- 압축 배포 전 로컬 월드에서 마지막 시각 점검
- 서버 배포 시 캐시 갱신 전략(파일명/URL 갱신) 포함
- 롤백 ZIP을 항상 함께 보관

## 포함 범위

이 스킬은 1.21.8 기준으로 아래 리소스팩 콘텐츠를 다룬다.

- 메타: `pack.mcmeta`, `pack.png`
- 시각 자산: `textures`, `blockstates`, `models`, `items`, `equipment`, `particles`
- 렌더링: `atlases`, `shaders/core`, `shaders/post`, `shaders/include`, `post_effect`
- 텍스트/지역화: `lang`, `font`, `texts`
- 오디오: `sounds.json`, `sounds/**/*.ogg`
- 기타: `waypoint_style`, 루트 레벨 리소스 설정 파일

## 실무 규약

1. 기준 버전과 `pack_format`을 문서 첫 줄에 명시한다.
2. 파일 경로/파일명을 소문자로 유지한다.
3. 바닐라 오버라이드는 `assets/minecraft/...`로만 한다.
4. 신규 콘텐츠 네임스페이스는 프로젝트 전용 prefix를 사용한다.
5. 셰이더/폰트/사운드는 변경 단위를 작게 쪼개고 즉시 확인한다.
6. 배포 ZIP에는 개발 임시 파일을 포함하지 않는다.

## 금지사항

- 1.21.8에서 `pack_format` 누락 또는 오기재
- 텍스처 크기를 무작정 키워 성능 저하를 만드는 방식
- `items`/`models`/`blockstates` 경로를 섞어놓고 원인 추적 불가능하게 만드는 구조
- 셰이더 대규모 교체를 한 번에 반영

## 참조 파일

- 콘텐츠 카탈로그: [references/resourcepack-1.21.8-content-catalog.md](references/resourcepack-1.21.8-content-catalog.md)
- `pack.mcmeta`: [references/resourcepack-1.21.8-pack-mcmeta.md](references/resourcepack-1.21.8-pack-mcmeta.md)
- 폴더/파일 구조: [references/resourcepack-1.21.8-folder-file-structure.md](references/resourcepack-1.21.8-folder-file-structure.md)
- 이미지/텍스처 규칙: [references/resourcepack-1.21.8-image-texture-spec.md](references/resourcepack-1.21.8-image-texture-spec.md)
- 모델/블록/아이템 구조: [references/resourcepack-1.21.8-model-item-blockstate.md](references/resourcepack-1.21.8-model-item-blockstate.md)
- 코어 셰이더/아틀라스/폰트: [references/resourcepack-1.21.8-shader-atlas-font.md](references/resourcepack-1.21.8-shader-atlas-font.md)
- 검증/릴리스 체크리스트: [references/resourcepack-1.21.8-validation-release.md](references/resourcepack-1.21.8-validation-release.md)

## 스크립트

### `scripts/create_resourcepack_scaffold.sh`

1.21.8 기준 리소스팩 골격 생성.

```bash
./scripts/create_resourcepack_scaffold.sh \
  --pack-name my_pack \
  --namespace minecraft \
  --output-dir /tmp/resourcepacks
```

### `scripts/validate_resourcepack_layout.sh`

구조/포맷/확장자 1차 검증.

```bash
./scripts/validate_resourcepack_layout.sh \
  --pack-dir /tmp/resourcepacks/my_pack
```

## 출력 템플릿

```markdown
기준 버전: Java Edition 1.21.8

요약
- 목표: ...
- 범위: 텍스처|모델|사운드|셰이더|폰트

구현
1. ...
2. ...
3. ...

검증
- pack.mcmeta: PASS|FAIL
- 구조 검증: PASS|FAIL
- 시각 검수: PASS|FAIL

리스크
- 낮음|중간|높음: ...
```

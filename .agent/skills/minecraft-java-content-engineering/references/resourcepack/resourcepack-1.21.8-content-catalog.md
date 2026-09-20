# 리소스팩 콘텐츠 카탈로그 (Java Edition 1.21.8)

## 목차

1. 사용 원칙
2. 루트 파일
3. `assets/<namespace>` 콘텐츠 전체 맵
4. 확장자 규칙
5. 우선순위 제작 순서
6. 근거

## 1) 사용 원칙

- 이 문서는 "1.21.8에서 리소스팩으로 건드릴 수 있는 콘텐츠"를 빠르게 찾는 인덱스다.
- 전부 한 번에 만지지 말고, 요구사항에 맞는 디렉터리만 선택한다.
- 바닐라 교체는 `assets/minecraft/...` 경로를 사용한다.

## 2) 루트 파일

| 파일 | 필수 | 설명 |
|---|---|---|
| `pack.mcmeta` | 필수 | 리소스팩 메타 (`pack_format: 64`) |
| `pack.png` | 권장 | 팩 아이콘 (일반적으로 256x256 권장) |

## 3) `assets/<namespace>` 콘텐츠 전체 맵

| 경로 | 주요 확장자 | 용도 | 실무 메모 |
|---|---|---|---|
| `atlases/` | `.json` | 텍스처 아틀라스 소스 구성 | `directory`, `single`, `paletted_permutations` 소스 타입 사용 |
| `blockstates/` | `.json` | 블록 상태 -> 모델 매핑 | `variants` 또는 `multipart` 패턴 |
| `equipment/` | `.json` | 장비/하네스/방어구 레이어 정의 | 말/라마/방어구 표현 커스터마이즈에 사용 |
| `font/` | `.json`, `.zip` | 폰트 provider 구성 | `bitmap`, `space`, `reference` 기반, 유니폰트 ZIP 연계 가능 |
| `font/include/` | `.json` | 폰트 include 세트 | `default`, `space`, `unifont` 분리 관리 |
| `items/` | `.json` | 아이템 표시 로직 정의 | `minecraft:model`, `condition`, `select`, `range_dispatch`, `special` 사용 |
| `lang/` | `.json` | 번역 문자열 | 키 충돌 관리 필수 |
| `models/block/` | `.json` | 블록 모델 | `parent`, `textures`, `elements`, `display` |
| `models/item/` | `.json` | 아이템 모델 | 기본 parent/texture 모델 자산 |
| `particles/` | `.json` | 파티클 텍스처 지정 | `textures` 배열 기반 |
| `post_effect/` | `.json` | 후처리 파이프라인 | 셰이더 패스와 유니폼 연결 |
| `shaders/core/` | `.vsh`, `.fsh` | 렌더 타입 셰이더 | GLSL 150, include 의존성 관리 |
| `shaders/post/` | `.vsh`, `.fsh` | 후처리 셰이더 | post effect JSON에서 참조 |
| `shaders/include/` | `.glsl` | 공통 GLSL include | `#moj_import` 대상 파일 |
| `sounds.json` | `.json` | 사운드 이벤트 정의 | 이벤트 키 -> 사운드 배열 매핑 |
| `sounds/` | `.ogg` | 사운드 파일 | 경로/이벤트 이름 일치 필수 |
| `texts/` | `.txt`, `.json` | 스플래시/엔딩/크레딧 텍스트 | 라인 단위 또는 JSON 텍스트 |
| `textures/` | `.png`, `.png.mcmeta` | 대부분의 시각 자산 | 애니메이션은 `png.mcmeta`로 제어 |
| `waypoint_style/` | `.json` | 웨이포인트 아이콘 스타일 | 스프라이트 목록 기반 |

### 루트 레벨(네임스페이스 내부) 특수 파일

| 파일 | 용도 |
|---|---|
| `gpu_warnlist.json` | GPU 경고/차단 정책 관련 리소스 |
| `regional_compliancies.json` | 지역 규정/정책 관련 리소스 |

## 4) 확장자 규칙

1. 1.21.8 바닐라 자산 기준 주요 확장자
- `.json`, `.png`, `.png.mcmeta`, `.ogg`, `.vsh`, `.fsh`, `.glsl`, `.txt`, `.zip`

2. 경로 규칙
- 소문자 경로 사용 권장 (`snake_case`)
- 공백/대문자/한글 경로는 런처/툴체인 호환성을 해칠 수 있어 배포팩에서는 피한다.

3. 참조 규칙
- JSON 내부 리소스 식별자는 확장자 없이 `namespace:path/to/file` 형태를 유지한다.

## 5) 우선순위 제작 순서

### 1순위 (대부분 프로젝트)

- `textures/`
- `models/block`, `models/item`
- `blockstates/`
- `items/`
- `lang/`

### 2순위 (콘텐츠 확장)

- `sounds.json` + `sounds/`
- `font/`
- `particles/`
- `atlases/`

### 3순위 (고급 커스터마이징)

- `shaders/core`, `shaders/post`, `post_effect/`
- `equipment/`
- `waypoint_style/`

## 6) 근거

- Mojang 1.21.8 client JAR 자산 경로 실측 (`assets/minecraft/*`)
- Mojang 1.21.8 asset index 실측 (`minecraft/sounds/*`, `minecraft/sounds.json`, 폰트 ZIP)
- 추출 기준 시각: 2026-03-05 (KST)

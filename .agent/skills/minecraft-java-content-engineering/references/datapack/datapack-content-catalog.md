# 데이터팩 콘텐츠 카탈로그 (Java Edition 1.21.11 기준)

## 목차

1. 카탈로그 사용법
2. 핵심 제작 타입
3. 데이터 레지스트리 타입
4. worldgen 하위 타입
5. tags 하위 타입
6. 실무 우선순위

## 1) 카탈로그 사용법

- 이 문서는 "데이터팩에서 무엇을 만들 수 있는지"를 빠르게 찾는 인덱스다.
- 모든 타입을 한 번에 쓰지 말고, 요구사항에 맞는 타입만 선택한다.
- 실제 경로는 `data/<namespace>/<type>/...` 패턴을 기본으로 잡는다.

## 2) 핵심 제작 타입

| 타입 | 대표 경로 | 용도 | 실무 메모 |
|---|---|---|---|
| `function` | `data/<ns>/function/*.mcfunction` | 실행 로직 | `load/tick` 분리 + 함수 매크로(`$`, `$(key)`) 활용 가능 |
| `tags/function` | `data/<ns>/tags/function/*.json` | 함수 그룹 | `load.json`, `tick.json` 필수 패턴 |
| `advancement` | `data/<ns>/advancement/*.json` | 진행 조건/보상 | criterion 네이밍 일관성 중요 |
| `recipe` | `data/<ns>/recipe/*.json` | 조합법 | 밸런스 영향 큼 |
| `loot_table` | `data/<ns>/loot_table/*.json` | 드롭/보상 | 경제 서버면 시뮬레이션 권장 |
| `predicate` | `data/<ns>/predicate/*.json` | 조건 정의 | 명령/루트 조건 분리 |
| `structure` (`.nbt`) | `data/<ns>/structure/*.nbt` | 구조물 템플릿 | 백업 필수, 바이너리 파일 |

## 3) 데이터 레지스트리 타입

아래 타입은 1.21.11 클라이언트 JAR의 `data/*` 경로 기준으로 확인된 항목이다.

| 타입 | 주 용도 | 비고 |
|---|---|---|
| `banner_pattern` | 배너 패턴 데이터 | 커스텀 패턴 연동 |
| `cat_variant` | 고양이 변종 | 엔티티 변형 |
| `chat_type` | 채팅 표시 규칙 | 서버 메시지 UX |
| `chicken_variant` | 닭 변종 | 엔티티 변형 |
| `cow_variant` | 소 변종 | 엔티티 변형 |
| `damage_type` | 피해 분류 | 전투/밸런스 핵심 |
| `dialog` | 대화 데이터 | NPC/상호작용 흐름 |
| `dimension_type` | 차원 규칙 | 월드 규칙 정의 |
| `enchantment` | 마법부여 정의 | 조합/상호배타 검토 |
| `enchantment_provider` | 인챈트 제공 규칙 | 루팅/거래 확장 |
| `frog_variant` | 개구리 변종 | 엔티티 변형 |
| `instrument` | 악기 데이터 | 사운드/연출 |
| `jukebox_song` | 주크박스 곡 | 콘텐츠 확장 |
| `painting_variant` | 그림 변형 | 장식 요소 |
| `pig_variant` | 돼지 변종 | 엔티티 변형 |
| `test_environment` | 테스트 환경 | 자동 검증 계열 |
| `test_instance` | 테스트 인스턴스 | 자동 검증 계열 |
| `timeline` | 타임라인 데이터 | 이벤트 순서 |
| `trial_spawner` | 트라이얼 스포너 설정 | 전투 콘텐츠 |
| `trim_material` | 방어구 트림 재료 | 아이템 커스터마이징 |
| `trim_pattern` | 방어구 트림 패턴 | 아이템 커스터마이징 |
| `wolf_sound_variant` | 늑대 사운드 변형 | 엔티티 연출 |
| `wolf_variant` | 늑대 변종 | 엔티티 변형 |
| `zombie_nautilus_variant` | 좀비 변종 데이터 | 실험/신규 확장 항목 |
| `datapacks` | 실험 데이터팩 토글 | 실험팩 메타 |

## 4) worldgen 하위 타입

`data/<ns>/worldgen/*`

| 하위 타입 | 용도 |
|---|---|
| `biome` | 생물군계 정의 |
| `configured_carver` | 지형 카버 설정 |
| `configured_feature` | 피처 설정 |
| `density_function` | 지형 밀도 함수 |
| `flat_level_generator_preset` | 평지 프리셋 |
| `multi_noise_biome_source_parameter_list` | 바이옴 소스 파라미터 |
| `noise` | 노이즈 데이터 |
| `noise_settings` | 월드 노이즈 설정 |
| `placed_feature` | 배치된 피처 |
| `processor_list` | 구조 처리기 목록 |
| `structure` | 구조물 생성 정의 |
| `structure_set` | 구조물 세트 |
| `template_pool` | 지그소 템플릿 풀 |
| `world_preset` | 월드 프리셋 |

## 5) tags 하위 타입

`data/<ns>/tags/*`

| 태그 타입 | 용도 |
|---|---|
| `block` | 블록 그룹 |
| `item` | 아이템 그룹 |
| `entity_type` | 엔티티 그룹 |
| `fluid` | 유체 그룹 |
| `damage_type` | 피해 타입 그룹 |
| `enchantment` | 마법부여 그룹 |
| `game_event` | 게임 이벤트 그룹 |
| `instrument` | 악기 그룹 |
| `painting_variant` | 그림 변형 그룹 |
| `point_of_interest_type` | POI 그룹 |
| `banner_pattern` | 배너 패턴 그룹 |
| `dialog` | 대화 그룹 |
| `timeline` | 타임라인 그룹 |
| `worldgen/*` | 월드젠 하위 그룹 |

## 6) 실무 우선순위

### 1순위 (대부분 프로젝트)

- `function`
- `tags/function`
- `loot_table`
- `advancement`
- `recipe`

### 2순위 (콘텐츠 확장)

- `dialog`
- `damage_type`
- `enchantment`
- `worldgen/*`

### 3순위 (고급/특화)

- `timeline`
- `test_environment`, `test_instance`
- 변종 계열(`*_variant`)

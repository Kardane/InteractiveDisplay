# 데이터팩 폴더 구조 가이드

## 목차

1. 최소 구조
2. 실무 표준 구조
3. `.mcfunction` 배치 규칙
4. `.nbt` 파일 다루기
5. 배포 경로

## 1) 최소 구조

```text
<pack_name>/
├─ pack.mcmeta
└─ data/
   ├─ minecraft/
   │  └─ tags/
   │     └─ function/
   │        ├─ load.json
   │        └─ tick.json
   └─ <namespace>/
      └─ function/
         ├─ init/load.mcfunction
         └─ loop/tick.mcfunction
```

## 2) 실무 표준 구조

```text
<pack_name>/
├─ pack.mcmeta
└─ data/
   ├─ minecraft/
   │  └─ tags/
   │     └─ function/
   │        ├─ load.json
   │        └─ tick.json
   └─ <namespace>/
      ├─ function/
      │  ├─ init/
      │  ├─ loop/
      │  ├─ feature/
      │  └─ debug/
      ├─ advancement/
      ├─ loot_table/
      ├─ recipe/
      ├─ predicate/
      ├─ dialog/
      ├─ enchantment/
      ├─ damage_type/
      ├─ dimension_type/
      ├─ structure/              # .nbt 파일 위치
      └─ worldgen/
         ├─ biome/
         ├─ configured_feature/
         ├─ placed_feature/
         ├─ structure/
         ├─ structure_set/
         └─ template_pool/
```

## 3) `.mcfunction` 배치 규칙

- 역할별 디렉터리로 쪼갠다 (`init`, `loop`, `feature`, `debug`)
- 함수명은 동사형으로 시작 (`spawn_wave`, `grant_reward`)
- 호출 경로는 짧고 예측 가능하게 유지한다

예:
- `function mypack:init/load`
- `function mypack:loop/tick`
- `function mypack:feature/quest/check_progress`

## 4) `.nbt` 파일 다루기

### 경로

- 구조 템플릿: `data/<namespace>/structure/<name>.nbt`

### 생성 방식

1. 구조 블록으로 저장
2. 생성된 `.nbt`를 데이터팩 `structure` 폴더로 이동
3. `/place structure <namespace>:<name>`로 검증

### 주의점

- `.nbt`는 바이너리이므로 텍스트 편집기 직접 수정 금지
- 변경 전 원본 백업 필수
- 네임스페이스/파일명 불일치가 가장 흔한 실패 원인

## 5) 배포 경로

### 싱글 월드

- `<world>/datapacks/<pack_name>/`

### 전용 서버

- `<server_root>/world/datapacks/<pack_name>/`

### 운영 절차

1. 배포 전 백업
2. 서버 재시작 또는 `/reload`
3. `load` 함수 동작 확인
4. `tick` 함수 부하 점검


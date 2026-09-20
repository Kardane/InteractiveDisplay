# 데이터팩 문법 가이드 (실무형)

## 목차

1. 기본 인코딩/파일 규칙
2. `mcfunction` 작성 규칙
3. JSON 작성 규칙
4. 태그 파일 규칙
5. 함수 매크로 규칙
6. NBT 다루기 규칙
7. 안티패턴

## 1) 기본 인코딩/파일 규칙

- 텍스트 파일은 UTF-8(가능하면 BOM 없음) 권장
- 줄바꿈은 LF로 통일
- 파일명은 소문자+언더스코어 권장
- 네임스페이스는 짧고 충돌 없는 이름 사용

## 2) `mcfunction` 작성 규칙

### 핵심

- 확장자는 `.mcfunction`
- 한 줄에 명령어 1개
- 주석은 `#`
- 들여쓰기는 의미가 없으니 가독성 용도로만 사용

### 기본 예시

```mcfunction
# 초기화
scoreboard objectives add mypack.kills minecraft.custom:minecraft.player_kills

# 조건 실행
execute as @a[scores={mypack.kills=10..}] run function mypack:reward/grant
```

### 실행 구조 권장

- `function mypack:init/load` : 최초 초기화
- `function mypack:loop/tick` : 반복 처리
- `function mypack:feature/*` : 기능 단위 함수

### 선택자 안전 규칙

- `@a`, `@e` 전역 실행은 조건/거리/태그 제한과 같이 사용
- tick 함수에서 전 엔티티 풀스캔 금지
- 디버깅 단계에서만 `say`/`tellraw` 과다 출력 허용

## 3) JSON 작성 규칙

- 쉼표/따옴표 오류를 가장 먼저 점검
- 정렬은 "키 알파벳"보다 "의미 흐름" 우선
- 큰 파일은 섹션 단위 주석 대체용 키 구조로 가독성 확보

### 최소 `pack.mcmeta` (1.21.8)

```json
{
  "pack": {
    "pack_format": 81,
    "description": "My Datapack (1.21.8)"
  }
}
```

### 최소 `pack.mcmeta` (1.21.11)

```json
{
  "pack": {
    "min_format": [94, 1],
    "max_format": 94,
    "description": "My Datapack (1.21.11)"
  }
}
```

## 4) 태그 파일 규칙

`data/<ns>/tags/function/load.json`

```json
{
  "replace": false,
  "values": [
    "mypack:init/load"
  ]
}
```

`data/<ns>/tags/function/tick.json`

```json
{
  "replace": false,
  "values": [
    "mypack:loop/tick"
  ]
}
```

규칙:
- `replace`는 기본 `false`
- 함수 경로 오타를 가장 먼저 확인
- `minecraft` 네임스페이스 태그를 수정할 때는 충돌 영향 검토

## 5) 함수 매크로 규칙

- 함수 매크로 라인은 `mcfunction`에서 `$`로 시작한다.
- 매크로 변수 참조는 `$(name)` 형태를 사용한다.
- 매크로 함수 실행은 `/function <id> {키:값}` 또는 `/function <id> with ...` 패턴으로 호출한다.
- 필요한 매크로 값이 누락되면 해당 함수 실행이 중단된다.

기본 예시:

```mcfunction
# data/mypack/function/macro/announce.mcfunction
$tellraw @a {"text":"[공지] $(message)","color":"gold"}
$scoreboard players set $(target) mypack.runtime $(value)
```

호출 예시:

```mcfunction
/function mypack:macro/announce {message:"서버 점검 5분 전",target:"#flag",value:1}
```

`with` 사용 예시:

```mcfunction
/function mypack:macro/announce with storage mypack:runtime macro_args
/function mypack:macro/announce with entity @s SelectedItem
```

규칙:
- 문자열, 숫자, 리스트/컴파운드 등은 SNBT 표현으로 전달한다.
- 매크로는 "명령 재사용"이 분명할 때만 쓰고, 남용해서 디버깅 난이도를 올리지 않는다.
- 운영 서버에서는 매크로 호출 입력값 검증(범위/태그/권한)을 같이 설계한다.
- 상세 규칙은 `references/function-macro-guide.md`를 참고한다.

## 6) NBT 다루기 규칙

- 작업 순서: 조회(`/data get`) -> 백업 -> 수정 -> 재조회
- 경로(path)와 타입(type)을 분리해서 설명
- 큰 NBT 수정은 함수로 쪼개 적용

예시:

```mcfunction
# 플레이어 데이터 일부 조회
/data get entity @p SelectedItem

# 커스텀 네임 적용 예시
/data modify entity @p SelectedItem.tag.display.Name set value '{"text":"Veteran Blade"}'
```

## 7) 안티패턴

1. 버전 미확정인데 mcmeta 형식 확정
2. 모든 로직을 `tick` 하나에 몰아넣기
3. 함수 경로를 하드코딩하고 재사용 구조 없음
4. NBT 수정 후 검증 생략
5. 매크로 입력값 검증 없이 전역 실행
6. 서버 운영 이슈를 명령어만으로 해결하려고 시도

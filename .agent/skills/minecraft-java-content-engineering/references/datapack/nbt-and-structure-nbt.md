# NBT 및 structure .nbt 실무 가이드

## 목차

1. NBT 작업 기본 원칙
2. 명령어 기반 NBT 점검
3. 구조 `.nbt` 제작/배포
4. 자주 터지는 문제

## 1) NBT 작업 기본 원칙

- NBT는 항상 "조회 -> 수정 -> 재조회" 순서로 작업
- 대상(entity/item/block)과 경로(path)를 먼저 확정
- 타입 불일치(문자열/숫자/리스트) 방지가 핵심

## 2) 명령어 기반 NBT 점검

### 조회

```mcfunction
/data get entity @p
/data get entity @p Inventory
/data get block ~ ~ ~
```

### 수정 예시

```mcfunction
# 현재 손 아이템 커스텀 이름
/data modify entity @p SelectedItem.tag.display.Name set value '{"text":"Datapack Blade"}'

# 임의 태그 부여
/data modify entity @p Tags append value "mypack_test"
```

### 검증

```mcfunction
/data get entity @p SelectedItem.tag.display
/data get entity @p Tags
```

## 3) 구조 `.nbt` 제작/배포

### 제작 루틴

1. 구조 블록으로 영역 지정
2. `Save`로 `.nbt` 파일 생성
3. 파일을 `data/<namespace>/structure/`로 이동
4. `/place structure <namespace>:<name>`로 배치 테스트

### 운영 루틴

1. 원본 `.nbt` 백업
2. 배치 테스트 월드에서 먼저 검증
3. 본 서버 반영 후 좌표/충돌 확인

## 4) 자주 터지는 문제

1. 네임스페이스 불일치
- 증상: `/place structure` 실패
- 원인: 파일 경로와 호출 식별자 불일치

2. 경로/타입 오타
- 증상: `/data modify` 실패
- 원인: 존재하지 않는 경로 또는 타입 미스매치

3. 운영 반영 즉시 대규모 배치
- 증상: TPS 급락
- 원인: 큰 구조를 다수 동시 배치

대응:
- 배치 간격 제어
- 청크 로딩 전략 점검
- 디버그 월드 선검증


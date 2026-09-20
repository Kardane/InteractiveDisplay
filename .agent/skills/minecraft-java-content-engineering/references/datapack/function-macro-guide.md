# 함수 매크로 가이드 (Datapack Function Macro)

## 목차

1. 개념과 사용 시점
2. 기본 문법
3. 호출 방식
4. 입력값 타입 규칙
5. 실무 패턴
6. 트러블슈팅
7. 버전 노트
8. 근거

## 1) 개념과 사용 시점

함수 매크로는 `mcfunction` 안에서 입력값에 따라 명령을 동적으로 바꾸고 싶을 때 쓴다.

사용 기준:
- 같은 명령 패턴을 다양한 입력으로 반복 호출해야 할 때
- 함수 복제를 줄이고 유지보수 비용을 낮추고 싶을 때
- 디버깅 가능한 범위 안에서 템플릿화가 필요할 때

비권장:
- 단순 고정 명령(매크로 이점 없음)
- 입력값 검증 없이 운영 서버에서 직접 실행

## 2) 기본 문법

### 매크로 라인

- 매크로 명령은 줄의 첫 글자가 `$`여야 한다.
- 변수 참조는 `$(name)` 형태를 사용한다.

예시:

```mcfunction
# data/mypack/function/macro/announce.mcfunction
$tellraw @a {"text":"[공지] $(message)","color":"gold"}
$scoreboard players set $(target) mypack.runtime $(value)
```

### 일반 라인과 혼용

- 같은 함수 파일에 일반 명령과 매크로 명령을 함께 둘 수 있다.
- 일반 명령은 매크로 해석 없이 그대로 실행된다.

## 3) 호출 방식

### A. 리터럴 인자 전달

```mcfunction
/function mypack:macro/announce {message:"서버 재시작 5분 전",target:"#flag",value:1}
```

### B. `with entity`로 전달

```mcfunction
/function mypack:macro/announce with entity @s SelectedItem
```

### C. `with block`로 전달

```mcfunction
/function mypack:macro/announce with block ~ ~ ~ Items[0]
```

### D. `with storage`로 전달

```mcfunction
/function mypack:macro/announce with storage mypack:runtime macro_args
```

## 4) 입력값 타입 규칙

- 문자열: 문자열 토큰으로 삽입된다.
- 숫자: 숫자 토큰으로 삽입된다.
- 리스트/컴파운드: SNBT 형태로 삽입된다.

중요:
- 함수에서 참조한 매크로 키가 호출 시 누락되면 해당 함수 호출은 실패한다.
- 운영 서버에서는 필수 키 누락을 막는 래퍼 함수를 두는 게 안전하다.

## 5) 실무 패턴

### 패턴 A: 브로드캐스트 템플릿

```mcfunction
# data/mypack/function/macro/broadcast.mcfunction
$tellraw @a {"text":"[$(channel)] $(message)","color":"$(color)"}
```

```mcfunction
/function mypack:macro/broadcast {channel:"EVENT",message:"보스 등장",color:"red"}
```

### 패턴 B: 점수 반영 템플릿

```mcfunction
# data/mypack/function/macro/set_score.mcfunction
$scoreboard players set $(who) $(objective) $(score)
```

```mcfunction
/function mypack:macro/set_score {who:"@s",objective:"mypack.runtime",score:1}
```

### 패턴 C: 스토리지 기반 호출

```mcfunction
# 입력 준비
/data modify storage mypack:runtime macro_args set value {message:"wave_3",target:"#wave",value:3}

# 매크로 실행
/function mypack:macro/announce with storage mypack:runtime macro_args
```

## 6) 트러블슈팅

### 문제 1) 함수가 실행되지 않음

점검 순서:
1. 매크로 키 누락 여부
2. `$(key)` 오타 여부
3. `/function ... with ...` 경로가 실제 NBT를 가리키는지
4. 함수 경로/네임스페이스 오타 여부

### 문제 2) 명령어 파싱 오류

- 값이 명령어 문법을 깨는 형태로 들어왔는지 확인
- 문자열/숫자/SNBT 타입이 기대와 맞는지 확인

### 문제 3) 운영 환경에서 남용

- 매크로 호출을 tick 루프에 과도하게 배치하지 말 것
- 입력 데이터 정규화 없이 외부 값을 바로 꽂지 말 것

## 7) 버전 노트

- 본 스킬 범위(1.21.8, 1.21.11)에서 함수 매크로 문법은 동일하게 사용 가능하다.
- 버전 업 시 실제 깨짐 포인트는 매크로보다 `pack.mcmeta` 형식 전환일 가능성이 높다.

## 8) 근거

- Minecraft Wiki - Commands/function (매크로/with 전달 규칙)
  - https://minecraft.wiki/w/Commands/function
- Minecraft Java Edition 1.20.2 기술 변경 (매크로 시스템 도입 맥락)
  - https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-2

# 버전별 `pack.mcmeta` 작성법 (1.21.8 ~ 1.21.11)

## 목차

1. 핵심 요약
2. 버전 매트릭스
3. 실전 예시
4. 작성 규칙
5. 검증 방법
6. 근거

## 1) 핵심 요약

- `1.21.8`: `pack_format` 기반
- `1.21.9+`: `min_format`/`max_format` 기반
- `1.21.11`: minor 버전(`94.1`)을 반영한 `min_format: [94, 1]` 형태 사용 가능

## 2) 버전 매트릭스

| 버전 | data pack version | 권장 `pack.mcmeta` 키 |
|---|---:|---|
| 1.21.8 | 81 | `pack_format` |
| 1.21.9 | 88.0 | `min_format`, `max_format` |
| 1.21.10 | 88.0 | `min_format`, `max_format` |
| 1.21.11 | 94.1 | `min_format`, `max_format` |

## 3) 실전 예시

### 1.21.8 예시

```json
{
  "pack": {
    "description": "My Datapack for 1.21.8",
    "pack_format": 81
  }
}
```

### 1.21.11 예시

```json
{
  "pack": {
    "description": "My Datapack for 1.21.11",
    "min_format": [94, 1],
    "max_format": 94
  }
}
```

### 참고: 1.21.9/1.21.10 내장 데이터팩 패턴

```json
{
  "pack": {
    "description": {"translate": "..."},
    "max_format": 88,
    "min_format": 88
  }
}
```

## 4) 작성 규칙

1. 버전을 먼저 잠근다.
2. 해당 버전 형식만 사용한다.
3. 실험 기능을 켜는 팩이면 `features.enabled`를 명시한다.
4. 설명(description)은 서버 운영자가 구분 가능한 문자열로 작성한다.

## 5) 검증 방법

- JSON 파싱 오류 확인
- `/reload` 시 로그 오류 확인
- `load` 태그 함수가 정상 실행되는지 확인
- 구버전/신버전 혼합 환경이면 테스트 월드에서 먼저 검증

## 6) 근거

- Mojang 공식 기술 변경 안내 (1.21.9): 데이터팩 버전 `88.0`, `pack.mcmeta` 포맷 변경
  - https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9
- Mojang 공식 릴리스 노트 (1.21.11): 데이터팩 버전 `94.1`
  - https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11
- Mojang 버전 메타 + 클라이언트 JAR 내부 `version.json`, 내장 데이터팩 `pack.mcmeta` 실측
  - `version_manifest_v2.json` -> `1.21.8/1.21.9/1.21.10/1.21.11`


# 1.21.8 `pack.mcmeta` 작성 가이드 (리소스팩)

## 목차

1. 핵심 요약
2. 최소 필수 예시
3. 실무 권장 예시
4. 선택 섹션
5. 검증 포인트
6. 근거

## 1) 핵심 요약

- 대상 버전: `Java Edition 1.21.8`
- 리소스팩 버전 값: `pack_format: 64`
- 1.21.8 기준으로 가장 안전한 기본 형태는 `pack.pack_format` + `pack.description`

## 2) 최소 필수 예시

```json
{
  "pack": {
    "pack_format": 64,
    "description": "My Resource Pack (1.21.8)"
  }
}
```

## 3) 실무 권장 예시

```json
{
  "pack": {
    "pack_format": 64,
    "description": {
      "text": "My Resource Pack - 1.21.8",
      "color": "gold"
    }
  }
}
```

권장 이유:
- 문자열 설명보다 텍스트 컴포넌트(`text`, `color`)를 쓰면 표시 스타일 제어가 쉽다.

## 4) 선택 섹션

아래 키는 프로젝트 성격에 따라 추가할 수 있다.

### A. `language`

언어 파일 메타를 팩 수준에서 선언할 때 사용.

```json
{
  "pack": {
    "pack_format": 64,
    "description": "Example"
  },
  "language": {
    "ko_kr": {
      "name": "Korean",
      "region": "대한민국",
      "bidirectional": false
    }
  }
}
```

### B. `filter`

특정 네임스페이스/경로 리소스를 차단해 충돌을 줄일 때 사용.

```json
{
  "pack": {
    "pack_format": 64,
    "description": "Example"
  },
  "filter": {
    "block": [
      {
        "namespace": "minecraft",
        "path": "textures/gui/.*"
      }
    ]
  }
}
```

주의:
- `filter`는 강력하지만 잘못 쓰면 필요한 리소스까지 막는다.

## 5) 검증 포인트

1. `pack.mcmeta`가 UTF-8 JSON인지 확인
2. `pack.pack_format`이 정확히 `64`인지 확인
3. `description` 타입(문자열 또는 텍스트 컴포넌트 객체) 확인
4. 선택 키(`language`, `filter`)를 넣었으면 JSON 구조를 별도 파싱 검증

## 6) 근거

- Mojang 1.21.8 `version.json`의 `pack_version.resource = 64` 실측
- 검증 시각: 2026-03-05 (KST)

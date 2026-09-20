# 데이터팩 버전 차이 정리 (1.21.8 vs 1.21.11)

## 목차

1. 핵심 변화
2. 영향도 매트릭스
3. 마이그레이션 체크리스트
4. 롤백 전략
5. 근거

## 1) 핵심 변화

### A. `pack.mcmeta` 형식 변화

- 1.21.8: `pack_format: 81`
- 1.21.9+: `min_format`/`max_format` 기반으로 전환
- 1.21.11: `data pack version 94.1`(minor 포함)

### B. 데이터팩 버전 점프

- 1.21.8 -> 1.21.11 사이에서 data pack version이 크게 증가
- 문법/검증 규칙 변화 가능성이 커서 "복붙 업그레이드"가 위험

### C. 운영 관점 변화

- 버전 차이 대응 시 `pack.mcmeta` 실수로 로드 실패가 가장 흔함
- CI/검증 스크립트로 버전별 키 체크 자동화 필요

### D. 함수 매크로 관점

- 함수 매크로 자체는 `1.21.8`과 `1.21.11`에서 동일 문법으로 사용 가능
- 차이는 매크로보다 `pack.mcmeta`와 데이터 버전 전환에서 주로 발생
- 따라서 매크로 장애처럼 보여도 먼저 버전 선언/로드 실패를 의심하는 게 안전

## 2) 영향도 매트릭스

| 영역 | 변화 영향 | 리스크 |
|---|---|---|
| `pack.mcmeta` | 매우 큼 | 로드 실패 |
| 함수/명령 구조 | 중간 | 일부 명령 체인 오동작 |
| 함수 매크로 | 낮음~중간 | 입력값 누락 시 함수 실행 중단 |
| NBT/item component | 중간~큼 | 데이터 불일치 |
| loot_table/advancement | 중간 | 보상/진행 오류 |
| 서버 운영 | 큼 | 재시작/롤백 비용 증가 |

## 3) 마이그레이션 체크리스트

1. 백업 생성
- 월드, 데이터팩, 서버 설정을 모두 백업

2. 버전 선언 교체
- `pack.mcmeta`를 대상 버전에 맞게 교체

3. 진입점 점검
- `data/minecraft/tags/function/load.json`
- `data/minecraft/tags/function/tick.json`

4. 핵심 기능 회귀 테스트
- 스코어보드/보상/퀘스트/드롭/구조 배치

5. 운영 테스트
- `/reload` 후 에러 로그
- 5~10분 실부하 시뮬레이션

## 4) 롤백 전략

1. 즉시 조치
- 오류 감지 시 데이터팩 비활성화
- 직전 백업으로 복귀

2. 근본 조치
- 버전별 분기 빌드(또는 브랜치) 유지
- 릴리스 전 자동 검증 스크립트 강제

## 5) 근거

- https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9
- https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11
- Mojang 메타/클라이언트 JAR `version.json` 실측 값

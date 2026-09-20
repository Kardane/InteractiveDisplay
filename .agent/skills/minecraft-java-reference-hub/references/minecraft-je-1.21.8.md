# Minecraft Java Edition 1.21.8 실무 레퍼런스

## 목차

1. 사용 원칙
2. 도메인 실행 카드
3. 실무 복합 시나리오
4. 검증 루틴

## 1) 사용 원칙

- `1.21.8` 기준 문서다.
- 질문이 3개 이상 도메인을 동시에 포함하면 먼저 2개 이하로 쪼개서 답한다.
- 운영 환경 답변은 반드시 성능/권한/롤백까지 포함한다.

## 2) 도메인 실행 카드

| 도메인 | 먼저 확인할 것 | 최소 실행 예시 | 검증 포인트 |
|---|---|---|---|
| 블록 | 상태값/상호작용/획득 | `/setblock ~ ~ ~ stone` | 상태 변화, 인접 블록 반응 |
| 아이템 | 획득/소모/메타 | `/give @p minecraft:diamond_sword` | 지급/소모/인벤토리 반영 |
| 엔티티 | 스폰 조건/밀도 | `/summon zombie ~ ~ ~` | 스폰 제어, 부하 추이 |
| 발전과제 | parent/criterion/보상 | `/advancement grant @p only <id>` | 트리 연결, 보상 중복 |
| 스코어보드 | 계산/표시 objective 분리 | `/scoreboard objectives add quest dummy` | tick 갱신 비용 |
| 마법부여 | 상호 배타/최대 레벨 | `/enchant @p sharpness 5` | 밸런스/악용 |
| 루트 테이블 | 조건/확률/경제 | `/loot give @p loot minecraft:chests/simple_dungeon` | 드롭 가치, 인플레 |
| 다이얼로그 | 트리거/분기/결과 | 분기 JSON + 함수 호출 | 분기 누락, 상태 꼬임 |
| NBT | 경로/자료형/대상 | `/data get entity @p` | path/type mismatch |
| item component | 목적/우선순위/충돌 | 아이템 컴포넌트 적용 후 `/give` 테스트 | 기존 메타와 충돌 |
| 명령어 | as/at/if/run 분리 | `/execute as @a run say ok` | 선택자 범위 |
| 데이터팩 | namespace/load/tick | `/reload` + load 함수 확인 | 함수 경로, 파싱 오류 |
| 리소스팩 | 변경 범위/가독성 | 자산 교체 후 재접속 | 시각 회귀/성능 |
| 멀티플레이어 서버 | 권한/백업/모니터링 | 변경 전 백업 + 적용 | TPS/로그/롤백 |
| 게임 규칙 | 서버 정책 적합성 | `/gamerule doMobSpawning false` | 정책 충돌 |
| 생물군계 | 자원/동선/리스크 | `/locate biome plains` | 이동/자원 편중 |
| 피해 종류 | 분류/방어 상호작용 | 전투 테스트 시나리오 | 과잉 피해 |
| 상태 효과 | 지속/증폭/중첩 | `/effect give @p speed 30 1 true` | PvP 악용 |
| 구조물 | 생성/보상/farm | `/place structure minecraft:village_plains` | 부하/보상 밸런스 |
| 게임 모드 | 권한/전환 정책 | `/gamemode survival @p` | 권한 경계 |
| 조작법 | 키 충돌/접근성 | 설정 점검 체크리스트 | 키맵 충돌 |
| 차원 | 진입/이탈/복귀 | `/execute in minecraft:the_nether run tp @p 0 80 0` | 복귀 실패 |
| 플레이어 | 개인/전역 이슈 분리 | 진단 분기표 | 계정/권한/네트워크 |

## 3) 실무 복합 시나리오

### A. 퀘스트 루프 (발전과제 + 스코어보드 + 루트테이블)

1. 점수 objective 정의
2. advancement criterion 설계
3. 보상 loot table 연결
4. tick에서 진행도 갱신
5. 새 월드/기존 월드 모두 회귀 테스트

### B. 전투 규칙 (피해 종류 + 상태 효과 + gamerule)

1. 피해 분류 정의
2. 효과 부여/해제 규칙 정의
3. gamerule 영향 분석
4. 악용 시나리오 리허설
5. 즉시 완화 명령 세트 준비

### C. 서버 최적화 (엔티티 + 반복 명령 + 구조물)

1. 엔티티 밀집 구간 파악
2. 반복 명령 대상 축소
3. 구조물 farm 제한
4. 5~10분 부하 검증
5. 완화 전/후 지표 비교

## 4) 검증 루틴

### 최소 명령 검증

```mcfunction
/execute as @a run say scope-check
/execute as @a[scores={quest=1..}] run say condition-check
/data get entity @p
```

### 데이터팩 검증

1. `/reload` 후 에러 로그
2. load 함수 진입 확인
3. tick 함수 호출량 확인
4. namespace 충돌 여부 확인

### 운영 검증

1. 백업 생성 확인
2. 권한 테스트
3. 고부하 5분 시나리오
4. 롤백 리허설

# Mixin 주입 패턴과 충돌 대응 (서버사이드)

## 목차

1. 주입 전략 결정표
2. 안전한 주입 순서
3. 패턴별 예시
4. 충돌 대응 프로토콜
5. 디버깅 옵션
6. 안티패턴

## 1) 주입 전략 결정표

| 상황 | 우선 선택 | 이유 |
|---|---|---|
| 단순 후킹/관찰 | `@Inject(HEAD/TAIL)` | 가장 안정적이고 디버깅 쉬움 |
| 조건부 중단 필요 | `@Inject(..., cancellable=true)` | 흐름 차단이 명시적 |
| 특정 호출 치환 필요 | `@Redirect` | 영향 범위가 크므로 마지막 선택 |
| 변수 값 조정 | `@ModifyVariable` | 시그니처 민감, 최소 사용 |
| 상수 수정 | `@ModifyConstant` | 유지보수 비용 큼, 가능하면 회피 |

원칙:
- 강한 도구(`Redirect`, `Modify*`)는 정말 필요할 때만 쓴다.

## 2) 안전한 주입 순서

1. 타겟 메서드 시그니처/매핑 확인
2. `HEAD` 또는 `TAIL`로 최소 주입
3. 서버 부팅 + 재현 시나리오 실행
4. 로그에서 mixin 적용 확인
5. 필요 시 점진적으로 주입 지점 세분화

## 3) 패턴별 예시

### A. HEAD 관찰 주입

```java
@Inject(method = "tick", at = @At("HEAD"))
private void onTickHead(CallbackInfo ci) {
    // 서버 상태 관찰용
}
```

### B. 조건부 취소

```java
@Inject(method = "someMethod", at = @At("HEAD"), cancellable = true)
private void onSomeMethod(CallbackInfo ci) {
    if (shouldCancel()) {
        ci.cancel();
    }
}
```

### C. INVOKE 지점 주입

```java
@Inject(
    method = "targetMethod",
    at = @At(value = "INVOKE", target = "Lnet/minecraft/...;call()V")
)
private void onInvokePoint(CallbackInfo ci) {
    // 최소 로직만 배치
}
```

실무 팁:
- INVOKE 주입은 매핑/시그니처 변경에 특히 취약하다.

## 4) 충돌 대응 프로토콜

1. 증상 분리
- 기동 실패인지, 런타임 오류인지 분리

2. 충돌 후보 확인
- 같은 타겟 메서드에 주입하는 다른 모드 확인

3. 완화 조치
- `priority` 조정
- `require` 값 현실화
- 주입 범위를 좁혀 재배치

4. 재검증
- 단독 실행 -> 최소 조합 -> 전체 조합 순으로 복귀

## 5) 디버깅 옵션

개발 환경에서만 활성화 권장:

```properties
mixin.debug=true
mixin.dumpTargetOnFailure=true
```

로그에서 먼저 볼 키워드:
- `InvalidInjectionException`
- `Mixin apply failed`
- `NoSuchMethodError`
- `ClassNotFoundException`

## 6) 안티패턴

1. 한 클래스에 여러 타겟/기능 주입 몰아넣기
2. 실패 원인 확인 없이 주입 타입만 계속 바꾸기
3. 매핑 확인 없이 이전 버전 시그니처 재사용
4. 예외를 삼켜서 장애 신호를 감추는 로직

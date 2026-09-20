# 폴더/파일 구조 표준 (1.21.8)

## 목차

1. 최소 구조
2. 실무 표준 구조
3. 네임스페이스 전략
4. 파일 배치 규칙
5. 자주 터지는 실수

## 1) 최소 구조

```text
<pack_name>/
├─ pack.mcmeta
├─ pack.png                    # 권장
└─ assets/
   └─ minecraft/
      └─ textures/
         └─ item/
            └─ diamond_sword.png
```

## 2) 실무 표준 구조

```text
<pack_name>/
├─ pack.mcmeta
├─ pack.png
└─ assets/
   └─ <namespace>/
      ├─ atlases/
      ├─ blockstates/
      ├─ equipment/
      ├─ font/
      │  └─ include/
      ├─ items/
      ├─ lang/
      ├─ models/
      │  ├─ block/
      │  └─ item/
      ├─ particles/
      ├─ post_effect/
      ├─ shaders/
      │  ├─ core/
      │  ├─ include/
      │  └─ post/
      ├─ sounds/
      ├─ texts/
      ├─ textures/
      │  ├─ block/
      │  ├─ item/
      │  ├─ entity/
      │  ├─ gui/
      │  ├─ particle/
      │  └─ ...
      └─ waypoint_style/
```

## 3) 네임스페이스 전략

### `minecraft` 사용

- 바닐라 리소스 오버라이드 목적일 때 사용
- 예: 기존 블록/아이템 텍스처 교체

### 커스텀 네임스페이스 사용

- 모드/데이터팩과 연계된 신규 리소스일 때 사용
- 다른 팩과 충돌을 줄이기 쉽다

## 4) 파일 배치 규칙

1. `blockstates/<id>.json` 파일명은 대상 블록 ID와 일치
2. `items/<id>.json` 파일명은 대상 아이템 ID와 일치
3. `models` 내부 참조는 확장자 없이 리소스 식별자로 작성
4. 애니메이션 텍스처는 `*.png`와 `*.png.mcmeta`를 같은 경로에 둔다
5. 사운드 이벤트는 `sounds.json`과 `sounds/*.ogg`를 같이 관리한다

## 5) 자주 터지는 실수

1. `assets/minecraft/items`를 만들지 않고 `models/item`만 교체
- 1.21.8에서 아이템 분기가 예상대로 동작하지 않을 수 있다.

2. 대소문자 불일치
- Windows에서는 보이고 Linux 서버에서만 깨지는 전형적인 원인.

3. 경로 분리자 혼용
- JSON 안에서 `\\`를 쓰지 말고 `/`를 사용.

4. `pack.mcmeta`는 맞는데 내부 JSON 하나가 깨짐
- 전체 팩이 일부만 로드되어 디버깅 난이도가 올라간다.

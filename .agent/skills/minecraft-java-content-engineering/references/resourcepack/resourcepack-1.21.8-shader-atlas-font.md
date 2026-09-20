# 코어 셰이더, 아틀라스, 폰트 가이드 (1.21.8)

## 목차

1. 셰이더 구조
2. `post_effect`와 셰이더 연결
3. 아틀라스 구조
4. 폰트 구조
5. 실무 운영 팁
6. 근거

## 1) 셰이더 구조

경로:
- `assets/<ns>/shaders/core/*.vsh`
- `assets/<ns>/shaders/core/*.fsh`
- `assets/<ns>/shaders/post/*.vsh`
- `assets/<ns>/shaders/post/*.fsh`
- `assets/<ns>/shaders/include/*.glsl`

1.21.8 바닐라 코어 셰이더는 GLSL `#version 150`을 사용한다.

### core 셰이더 예시 (`terrain.vsh` 스타일)

```glsl
#version 150
#moj_import <minecraft:fog.glsl>

in vec3 Position;

void main() {
  gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
```

규칙:
- include 파일은 `#moj_import <namespace:file.glsl>`로 참조
- 셰이더 변수명/유니폼 불일치 시 즉시 렌더 오류로 이어진다

## 2) `post_effect`와 셰이더 연결

경로:
- `assets/<ns>/post_effect/*.json`

역할:
- 렌더 타깃, 패스, 유니폼 값을 선언하고 `shaders/post` 셰이더와 연결한다.

### 최소 예시

```json
{
  "targets": {
    "swap": {}
  },
  "passes": [
    {
      "vertex_shader": "minecraft:post/blur",
      "fragment_shader": "minecraft:post/box_blur",
      "inputs": [
        {
          "sampler_name": "In",
          "target": "minecraft:main",
          "bilinear": true
        }
      ],
      "output": "swap"
    }
  ]
}
```

## 3) 아틀라스 구조

경로:
- `assets/<ns>/atlases/*.json`

1.21.8 바닐라에서 확인되는 source type:
- `minecraft:directory`
- `minecraft:single`
- `minecraft:paletted_permutations`

### 예시

```json
{
  "sources": [
    {
      "type": "minecraft:directory",
      "source": "block",
      "prefix": "block/"
    },
    {
      "type": "minecraft:single",
      "resource": "minecraft:entity/enchanting_table_book"
    }
  ]
}
```

실무 팁:
- 아틀라스 구성은 텍스처 참조 경로와 같이 관리해야 디버깅이 쉽다.

## 4) 폰트 구조

경로:
- `assets/<ns>/font/*.json`
- `assets/<ns>/font/include/*.json`
- 필요 시 `assets/<ns>/font/*.zip`

1.21.8 바닐라에서 확인된 provider type:
- `bitmap`
- `space`
- `reference`

실무에서 자주 쓰는 provider:
- `bitmap`: 텍스처 기반 폰트
- `space`: 문자 폭 조정
- `reference`: provider 세트 재사용

### `bitmap` 예시

```json
{
  "providers": [
    {
      "type": "bitmap",
      "file": "minecraft:font/ascii.png",
      "ascent": 7,
      "height": 8,
      "chars": [
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000",
        " !\"#$%&'()*+,-./"
      ]
    }
  ]
}
```

### `reference` 예시

```json
{
  "providers": [
    {
      "type": "reference",
      "id": "minecraft:include/default"
    }
  ]
}
```

## 5) 실무 운영 팁

1. 셰이더는 단계별 적용
- 한 번에 여러 파일을 갈아엎지 말고, 렌더 타입 단위로 반영

2. 폰트는 가독성 우선
- 폭(`advances`) 조절 후 채팅/UI에서 실제 확인

3. 아틀라스는 참조 오타가 가장 흔한 장애
- 리소스 식별자와 폴더명을 자동 검증 스크립트로 점검

## 6) 근거

- 1.21.8 client JAR 실측:
  - `assets/minecraft/atlases/*.json` 13개
  - `assets/minecraft/shaders/core` 73개 파일
  - `assets/minecraft/shaders/post` 15개 파일
  - `assets/minecraft/shaders/include` 6개 파일
  - `assets/minecraft/font` 및 `font/include` 구성 확인
- 추출 시각: 2026-03-05 (KST)

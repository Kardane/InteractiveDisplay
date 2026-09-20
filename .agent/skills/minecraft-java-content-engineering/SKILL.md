---
name: minecraft-java-content-engineering
description: "Minecraft Java Edition 1.21.8의 데이터팩과 리소스팩을 설계, 구현, 검증, 배포할 때 사용한다. 데이터팩(mcfunction, function macro, loot/advancement/tag/recipe/structure NBT 등)과 리소스팩(pack.mcmeta, textures, models, blockstates, items, shaders, atlases, fonts, sounds 등)을 하나의 콘텐츠 제작 스킬에서 라우팅하며, 필요한 하위 레퍼런스만 선택적으로 읽는다."
---

# Minecraft Java Content Engineering

데이터팩과 리소스팩을 별도 활성 Skill로 두지 않고 **콘텐츠 종류에 따라 내부 레퍼런스를 라우팅**한다. 한 작업이 두 팩을 동시에 요구할 때만 양쪽 문서를 함께 읽는다.

## 라우팅

| 요청 | 사용할 내부 자료 |
|---|---|
| commands, mcfunction, function macro, predicates, loot tables, recipes, advancements, tags, structures | `references/datapack/` |
| textures, PNG, models, blockstates, item models, shaders, atlases, fonts, sounds | `references/resourcepack/` |
| 데이터팩 + 리소스팩을 함께 배포하는 기능 | 양쪽 자료를 사용하되 서버 로직/클라이언트 표현의 책임을 분리 |
| Fabric Java 코드/Mixin/Polymer 구현 | `minecraft-fabric-server-dev` |
| 월드 `.dat`/`.mca` 직접 수정 | `minecraft-java-world-nbt` |

## 컨텍스트 절약 규칙

1. 요청이 데이터팩이면 먼저 `references/datapack/guide.md`만 읽고 필요한 세부 레퍼런스만 추가로 읽는다.
2. 요청이 리소스팩이면 먼저 `references/resourcepack/guide.md`만 읽고 필요한 세부 레퍼런스만 추가로 읽는다.
3. 단순 데이터팩 작업에서 리소스팩 문서를 읽지 않는다. 반대도 동일하다.
4. 버전은 프로젝트/요청의 실제 버전을 우선한다. 이 묶음의 기본 대상은 Java Edition 1.21.8이다.
5. pack format, 폴더명, registry path, item/model 문법은 다른 버전 튜토리얼에서 추측하지 않는다.

## 데이터팩 워크플로우

필요 시 `references/datapack/guide.md`를 읽는다.

기본 순서:

```text
version lock
→ pack.mcmeta / folder layout
→ functions/data definitions
→ deterministic validation
→ server reload/test
→ release/rollback
```

스캐폴딩/정적 검증:

```bash
./scripts/datapack/create_datapack_scaffold.sh ...
./scripts/datapack/validate_datapack_layout.sh ...
```

세부 문서는 `references/datapack/` 아래에 있다.

## 리소스팩 워크플로우

필요 시 `references/resourcepack/guide.md`를 읽는다.

기본 순서:

```text
version lock
→ pack.mcmeta / asset namespace
→ textures/models/items/etc.
→ static validation
→ client resource reload/test
→ release/rollback
```

스캐폴딩/정적 검증:

```bash
./scripts/resourcepack/create_resourcepack_scaffold.sh ...
./scripts/resourcepack/validate_resourcepack_layout.sh ...
```

세부 문서는 `references/resourcepack/` 아래에 있다.

## 교차 팩 설계

데이터팩과 리소스팩이 함께 필요한 기능은 다음 경계를 유지한다.

- 데이터팩: 서버 규칙, 명령, registry/data-driven behavior
- 리소스팩: 클라이언트가 표시하는 texture/model/font/sound 등
- 동일 identifier를 공유할 경우 namespace/path를 문서화한다.
- 한쪽 팩이 없어졌을 때의 degradation/failure mode를 확인한다.
- Fabric/Polymer가 팩 전달이나 서버 표현을 담당하면 Java 구현은 `minecraft-fabric-server-dev`에서 처리한다.

## 완료 조건

컴파일/JSON 파싱만으로 PASS를 선언하지 않는다. 최소한 해당 pack의 layout validator와 실제 Minecraft reload/load 경계를 확인하고, 변경된 기능의 observable behavior를 검증한다.

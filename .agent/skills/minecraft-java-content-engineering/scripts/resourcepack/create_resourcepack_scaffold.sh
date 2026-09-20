#!/usr/bin/env bash
set -euo pipefail

show_usage() {
  cat <<'USAGE'
사용법:
  ./create_resourcepack_scaffold.sh --pack-name <name> --namespace <namespace> --output-dir <path> [--description <text>]

예시:
  ./create_resourcepack_scaffold.sh --pack-name my_pack --namespace minecraft --output-dir /tmp/resourcepacks
USAGE
}

pack_name=""
namespace=""
output_dir=""
description=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      show_usage
      exit 0
      ;;
    --pack-name)
      pack_name="${2:-}"
      shift 2
      ;;
    --namespace)
      namespace="${2:-}"
      shift 2
      ;;
    --output-dir)
      output_dir="${2:-}"
      shift 2
      ;;
    --description)
      description="${2:-}"
      shift 2
      ;;
    *)
      echo "[ERROR] 알 수 없는 인자: $1" >&2
      show_usage
      exit 1
      ;;
  esac
done

if [[ -z "$pack_name" || -z "$namespace" || -z "$output_dir" ]]; then
  echo "[ERROR] 필수 인자가 부족합니다." >&2
  show_usage
  exit 1
fi

if ! [[ "$pack_name" =~ ^[a-z0-9._-]+$ ]]; then
  echo "[ERROR] pack-name은 소문자/숫자/점/언더스코어/하이픈만 허용합니다." >&2
  exit 1
fi

if ! [[ "$namespace" =~ ^[a-z0-9._-]+$ ]]; then
  echo "[ERROR] namespace는 소문자/숫자/점/언더스코어/하이픈만 허용합니다." >&2
  exit 1
fi

if [[ -z "$description" ]]; then
  description="$pack_name (Java 1.21.8)"
fi

pack_dir="$output_dir/$pack_name"
if [[ -e "$pack_dir" ]]; then
  echo "[ERROR] 대상 경로가 이미 존재합니다: $pack_dir" >&2
  exit 1
fi

mkdir -p \
  "$pack_dir/assets/$namespace/atlases" \
  "$pack_dir/assets/$namespace/blockstates" \
  "$pack_dir/assets/$namespace/equipment" \
  "$pack_dir/assets/$namespace/font/include" \
  "$pack_dir/assets/$namespace/items" \
  "$pack_dir/assets/$namespace/lang" \
  "$pack_dir/assets/$namespace/models/block" \
  "$pack_dir/assets/$namespace/models/item" \
  "$pack_dir/assets/$namespace/particles" \
  "$pack_dir/assets/$namespace/post_effect" \
  "$pack_dir/assets/$namespace/shaders/core" \
  "$pack_dir/assets/$namespace/shaders/include" \
  "$pack_dir/assets/$namespace/shaders/post" \
  "$pack_dir/assets/$namespace/sounds" \
  "$pack_dir/assets/$namespace/texts" \
  "$pack_dir/assets/$namespace/textures/block" \
  "$pack_dir/assets/$namespace/textures/item" \
  "$pack_dir/assets/$namespace/textures/entity" \
  "$pack_dir/assets/$namespace/textures/gui" \
  "$pack_dir/assets/$namespace/textures/font" \
  "$pack_dir/assets/$namespace/textures/particle" \
  "$pack_dir/assets/$namespace/waypoint_style"

cat > "$pack_dir/pack.mcmeta" <<EOF_META
{
  "pack": {
    "pack_format": 64,
    "description": "$description"
  }
}
EOF_META

cat > "$pack_dir/assets/$namespace/items/example_icon.json" <<EOF_ITEM_DEF
{
  "model": {
    "type": "minecraft:model",
    "model": "$namespace:item/example_icon"
  }
}
EOF_ITEM_DEF

cat > "$pack_dir/assets/$namespace/models/item/example_icon.json" <<EOF_ITEM_MODEL
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "$namespace:item/example_icon"
  }
}
EOF_ITEM_MODEL

cat > "$pack_dir/assets/$namespace/blockstates/example_block.json" <<EOF_BLOCKSTATE
{
  "variants": {
    "": {
      "model": "$namespace:block/example_block"
    }
  }
}
EOF_BLOCKSTATE

cat > "$pack_dir/assets/$namespace/models/block/example_block.json" <<EOF_BLOCK_MODEL
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "$namespace:block/example_block"
  }
}
EOF_BLOCK_MODEL

cat > "$pack_dir/assets/$namespace/font/default.json" <<EOF_FONT
{
  "providers": [
    {
      "type": "reference",
      "id": "$namespace:include/default"
    }
  ]
}
EOF_FONT

cat > "$pack_dir/assets/$namespace/font/include/default.json" <<EOF_FONT_INCLUDE
{
  "providers": []
}
EOF_FONT_INCLUDE

cat > "$pack_dir/assets/$namespace/atlases/blocks.json" <<EOF_ATLAS
{
  "sources": [
    {
      "type": "minecraft:directory",
      "source": "block",
      "prefix": "block/"
    },
    {
      "type": "minecraft:directory",
      "source": "item",
      "prefix": "item/"
    }
  ]
}
EOF_ATLAS

cat > "$pack_dir/assets/$namespace/particles/example_particle.json" <<EOF_PARTICLE
{
  "textures": [
    "$namespace:particle/example_particle"
  ]
}
EOF_PARTICLE

cat > "$pack_dir/assets/$namespace/post_effect/example_effect.json" <<EOF_POST
{
  "targets": {
    "swap": {}
  },
  "passes": []
}
EOF_POST

cat > "$pack_dir/assets/$namespace/sounds.json" <<EOF_SOUND
{
  "$namespace.example": {
    "sounds": [
      "$namespace:ui/example"
    ]
  }
}
EOF_SOUND

cat > "$pack_dir/assets/$namespace/texts/splashes.txt" <<'EOF_SPLASH'
샘플 리소스팩
EOF_SPLASH

cat > "$pack_dir/readme.txt" <<EOF_README
이 스캐폴드는 Java Edition 1.21.8 리소스팩 기본 골격입니다.

다음 작업:
1) pack.png 추가 (권장 256x256)
2) assets/$namespace/textures 하위 실제 PNG 자산 추가
3) validate_resourcepack_layout.sh로 검증
EOF_README

echo "[OK] 생성 완료: $pack_dir"
echo "[INFO] pack_format: 64 (Java 1.21.8 리소스팩)"
echo "[INFO] 다음 단계: validate_resourcepack_layout.sh --pack-dir $pack_dir"

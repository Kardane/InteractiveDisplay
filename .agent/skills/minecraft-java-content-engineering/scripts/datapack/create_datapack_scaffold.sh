#!/usr/bin/env bash
set -euo pipefail

show_usage() {
  cat <<'USAGE'
사용법:
  ./create_datapack_scaffold.sh --version <1.21.8|1.21.11> --pack-name <name> --namespace <ns> --output-dir <path>

예시:
  ./create_datapack_scaffold.sh --version 1.21.11 --pack-name my_pack --namespace mypack --output-dir /tmp/datapacks
USAGE
}

version=""
pack_name=""
namespace=""
output_dir=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      show_usage
      exit 0
      ;;
    --version)
      version="${2:-}"
      shift 2
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
    *)
      echo "[ERROR] 알 수 없는 인자: $1" >&2
      show_usage
      exit 1
      ;;
  esac
done

if [[ -z "$version" || -z "$pack_name" || -z "$namespace" || -z "$output_dir" ]]; then
  echo "[ERROR] 필수 인자가 부족합니다." >&2
  show_usage
  exit 1
fi

if [[ "$version" != "1.21.8" && "$version" != "1.21.11" ]]; then
  echo "[ERROR] 지원 버전은 1.21.8 또는 1.21.11 입니다." >&2
  exit 1
fi

if ! [[ "$namespace" =~ ^[a-z0-9_\-]+$ ]]; then
  echo "[ERROR] namespace는 소문자/숫자/언더스코어/하이픈만 허용합니다." >&2
  exit 1
fi

pack_dir="$output_dir/$pack_name"
if [[ -e "$pack_dir" ]]; then
  echo "[ERROR] 대상 경로가 이미 존재합니다: $pack_dir" >&2
  exit 1
fi

mkdir -p \
  "$pack_dir/data/minecraft/tags/function" \
  "$pack_dir/data/$namespace/function/init" \
  "$pack_dir/data/$namespace/function/loop" \
  "$pack_dir/data/$namespace/function/feature" \
  "$pack_dir/data/$namespace/advancement" \
  "$pack_dir/data/$namespace/loot_table" \
  "$pack_dir/data/$namespace/recipe" \
  "$pack_dir/data/$namespace/predicate" \
  "$pack_dir/data/$namespace/structure" \
  "$pack_dir/data/$namespace/worldgen"

if [[ "$version" == "1.21.8" ]]; then
  cat > "$pack_dir/pack.mcmeta" <<EOF_MC
{
  "pack": {
    "description": "$pack_name ($version)",
    "pack_format": 81
  }
}
EOF_MC
else
  cat > "$pack_dir/pack.mcmeta" <<EOF_MC
{
  "pack": {
    "description": "$pack_name ($version)",
    "min_format": [94, 1],
    "max_format": 94
  }
}
EOF_MC
fi

cat > "$pack_dir/data/minecraft/tags/function/load.json" <<EOF_LOAD
{
  "replace": false,
  "values": [
    "$namespace:init/load"
  ]
}
EOF_LOAD

cat > "$pack_dir/data/minecraft/tags/function/tick.json" <<EOF_TICK
{
  "replace": false,
  "values": [
    "$namespace:loop/tick"
  ]
}
EOF_TICK

cat > "$pack_dir/data/$namespace/function/init/load.mcfunction" <<EOF_INIT
# $pack_name 초기화 함수
scoreboard objectives add $namespace.runtime dummy
scoreboard players set #loaded $namespace.runtime 1
EOF_INIT

cat > "$pack_dir/data/$namespace/function/loop/tick.mcfunction" <<EOF_LOOP
# $pack_name 틱 함수
execute as @a run function $namespace:feature/heartbeat
EOF_LOOP

cat > "$pack_dir/data/$namespace/function/feature/heartbeat.mcfunction" <<EOF_HEART
# 주기 확인용 샘플 함수
# 실제 구현으로 교체하세요.
EOF_HEART

cat > "$pack_dir/data/$namespace/function/feature/macro_demo.mcfunction" <<EOF_MACRO
# 함수 매크로 샘플
\$tellraw @a {"text":"[\$(channel)] \$(message)","color":"gold"}
EOF_MACRO

echo "[OK] 생성 완료: $pack_dir"
echo "[INFO] 매크로 실행 예시: /function $namespace:feature/macro_demo {channel:\"INFO\",message:\"hello\"}"
echo "[INFO] 다음 단계: validate_datapack_layout.sh로 구조 검증"

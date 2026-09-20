#!/usr/bin/env bash
set -euo pipefail

show_usage() {
  cat <<'USAGE'
사용법:
  ./validate_datapack_layout.sh --pack-dir <path> --version <1.21.8|1.21.11>
USAGE
}

pack_dir=""
version=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      show_usage
      exit 0
      ;;
    --pack-dir)
      pack_dir="${2:-}"
      shift 2
      ;;
    --version)
      version="${2:-}"
      shift 2
      ;;
    *)
      echo "[ERROR] 알 수 없는 인자: $1" >&2
      show_usage
      exit 1
      ;;
  esac
done

if [[ -z "$pack_dir" || -z "$version" ]]; then
  echo "[ERROR] 필수 인자가 부족합니다." >&2
  show_usage
  exit 1
fi

fail=0
warn=0

check_file() {
  local f="$1"
  if [[ -f "$f" ]]; then
    echo "[PASS] 파일 존재: $f"
  else
    echo "[FAIL] 파일 없음: $f"
    fail=$((fail + 1))
  fi
}

check_dir() {
  local d="$1"
  if [[ -d "$d" ]]; then
    echo "[PASS] 디렉터리 존재: $d"
  else
    echo "[WARN] 디렉터리 없음: $d"
    warn=$((warn + 1))
  fi
}

check_file "$pack_dir/pack.mcmeta"
check_file "$pack_dir/data/minecraft/tags/function/load.json"
check_file "$pack_dir/data/minecraft/tags/function/tick.json"
check_dir "$pack_dir/data"

if [[ -f "$pack_dir/pack.mcmeta" ]]; then
  pm="$(cat "$pack_dir/pack.mcmeta")"
  if [[ "$version" == "1.21.8" ]]; then
    if grep -q '"pack_format"[[:space:]]*:[[:space:]]*81' <<<"$pm"; then
      echo "[PASS] 1.21.8 pack_format 확인"
    else
      echo "[FAIL] 1.21.8은 pack_format: 81 필요"
      fail=$((fail + 1))
    fi
  elif [[ "$version" == "1.21.11" ]]; then
    if grep -q '"min_format"' <<<"$pm" && grep -q '"max_format"' <<<"$pm"; then
      echo "[PASS] 1.21.11 min_format/max_format 키 확인"
    else
      echo "[FAIL] 1.21.11은 min_format/max_format 필요"
      fail=$((fail + 1))
    fi

    if grep -qE '"min_format"[[:space:]]*:[[:space:]]*\[[[:space:]]*94[[:space:]]*,[[:space:]]*1[[:space:]]*\]' <<<"$pm"; then
      echo "[PASS] 1.21.11 min_format [94,1] 확인"
    else
      echo "[WARN] 권장 min_format [94,1] 패턴이 아닙니다"
      warn=$((warn + 1))
    fi
  else
    echo "[FAIL] 지원하지 않는 버전: $version"
    fail=$((fail + 1))
  fi
fi

# mcfunction 파일 존재 여부 검사
if find "$pack_dir/data" -type f -name '*.mcfunction' | grep -q .; then
  echo "[PASS] .mcfunction 파일 발견"
else
  echo "[WARN] .mcfunction 파일이 없습니다"
  warn=$((warn + 1))
fi

echo
echo "요약"
echo "  FAIL: $fail"
echo "  WARN: $warn"

if (( fail > 0 )); then
  exit 1
fi

exit 0

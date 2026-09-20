#!/usr/bin/env bash
set -euo pipefail

show_usage() {
  cat <<'USAGE'
사용법:
  ./validate_resourcepack_layout.sh --pack-dir <path>

예시:
  ./validate_resourcepack_layout.sh --pack-dir /tmp/resourcepacks/my_pack
USAGE
}

pack_dir=""

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
    *)
      echo "[ERROR] 알 수 없는 인자: $1" >&2
      show_usage
      exit 1
      ;;
  esac
done

if [[ -z "$pack_dir" ]]; then
  echo "[ERROR] --pack-dir는 필수입니다." >&2
  show_usage
  exit 1
fi

if [[ ! -d "$pack_dir" ]]; then
  echo "[ERROR] 디렉터리가 없습니다: $pack_dir" >&2
  exit 1
fi

fail=0
warn=0

pass() { echo "[PASS] $1"; }
warn_msg() { echo "[WARN] $1"; warn=$((warn + 1)); }
fail_msg() { echo "[FAIL] $1"; fail=$((fail + 1)); }

check_file() {
  local f="$1"
  if [[ -f "$f" ]]; then
    pass "파일 존재: $f"
  else
    fail_msg "파일 없음: $f"
  fi
}

check_dir() {
  local d="$1"
  if [[ -d "$d" ]]; then
    pass "디렉터리 존재: $d"
  else
    fail_msg "디렉터리 없음: $d"
  fi
}

check_json_parse() {
  local f="$1"
  if python3 - <<PY "$f" >/dev/null 2>&1
import json,sys
with open(sys.argv[1], 'r', encoding='utf-8') as fh:
    json.load(fh)
PY
  then
    pass "JSON 파싱 성공: $f"
  else
    fail_msg "JSON 파싱 실패: $f"
  fi
}

check_file "$pack_dir/pack.mcmeta"
check_dir "$pack_dir/assets"

if [[ -f "$pack_dir/pack.mcmeta" ]]; then
  check_json_parse "$pack_dir/pack.mcmeta"
  pack_format="$(python3 - <<'PY' "$pack_dir/pack.mcmeta"
import json,sys
obj=json.load(open(sys.argv[1], 'r', encoding='utf-8'))
print(obj.get('pack',{}).get('pack_format',''))
PY
)"
  if [[ "$pack_format" == "64" ]]; then
    pass "pack_format=64 확인"
  else
    fail_msg "pack_format이 64가 아닙니다. 현재값: ${pack_format:-<없음>}"
  fi
fi

# 파일 확장자 검사
allowed_regex='(\.json|\.png|\.mcmeta|\.ogg|\.vsh|\.fsh|\.glsl|\.txt|\.zip)$'
while IFS= read -r rel; do
  full="$pack_dir/$rel"
  if [[ ! "$rel" =~ $allowed_regex ]]; then
    warn_msg "허용 목록 밖 확장자: $rel"
  fi

  if [[ "$rel" =~ [A-Z] ]]; then
    warn_msg "대문자 포함 경로: $rel"
  fi

  # JSON 계열 파싱
  if [[ "$rel" == *.json || "$rel" == *.mcmeta ]]; then
    check_json_parse "$full"
  fi

done < <(cd "$pack_dir" && find . -type f | sed 's#^./##' | sort)

# png.mcmeta 짝 검사
while IFS= read -r f; do
  png_path="${f%.mcmeta}"
  if [[ -f "$png_path" ]]; then
    pass "애니메이션 짝 파일 확인: $f"
  else
    fail_msg "png.mcmeta에 대응하는 PNG 없음: $f"
  fi
done < <(find "$pack_dir" -type f -name '*.png.mcmeta' | sort)

# blockstates 최소 키 검사
while IFS= read -r f; do
  result="$(python3 - <<'PY' "$f"
import json,sys
obj=json.load(open(sys.argv[1], 'r', encoding='utf-8'))
print('ok' if ('variants' in obj or 'multipart' in obj) else 'bad')
PY
)"
  if [[ "$result" == "ok" ]]; then
    pass "blockstates 키 확인: $f"
  else
    fail_msg "blockstates에 variants/multipart 키가 없음: $f"
  fi
done < <(find "$pack_dir/assets" -type f -path '*/blockstates/*.json' | sort)

# items 최소 키 검사
while IFS= read -r f; do
  result="$(python3 - <<'PY' "$f"
import json,sys
obj=json.load(open(sys.argv[1], 'r', encoding='utf-8'))
print('ok' if isinstance(obj.get('model'), dict) else 'bad')
PY
)"
  if [[ "$result" == "ok" ]]; then
    pass "items model 키 확인: $f"
  else
    fail_msg "items 파일에 model 객체가 없음: $f"
  fi
done < <(find "$pack_dir/assets" -type f -path '*/items/*.json' | sort)

# font provider 기본 키 검사
while IFS= read -r f; do
  result="$(python3 - <<'PY' "$f"
import json,sys
obj=json.load(open(sys.argv[1], 'r', encoding='utf-8'))
print('ok' if isinstance(obj.get('providers'), list) else 'bad')
PY
)"
  if [[ "$result" == "ok" ]]; then
    pass "font providers 확인: $f"
  else
    fail_msg "font JSON에 providers 배열이 없음: $f"
  fi
done < <(find "$pack_dir/assets" -type f -path '*/font/*.json' ! -path '*/font/include/*' | sort)

# sounds.json 검사 (있을 때만)
while IFS= read -r f; do
  result="$(python3 - <<'PY' "$f"
import json,sys
obj=json.load(open(sys.argv[1], 'r', encoding='utf-8'))
if not isinstance(obj, dict):
    print('bad')
    raise SystemExit
for key,val in obj.items():
    if not isinstance(val, dict) or 'sounds' not in val:
        print('bad')
        break
else:
    print('ok')
PY
)"
  if [[ "$result" == "ok" ]]; then
    pass "sounds.json 구조 확인: $f"
  else
    fail_msg "sounds.json 구조 오류: $f"
  fi
done < <(find "$pack_dir/assets" -type f -name 'sounds.json' | sort)

echo
echo "요약"
echo "  FAIL: $fail"
echo "  WARN: $warn"

if (( fail > 0 )); then
  exit 1
fi

exit 0

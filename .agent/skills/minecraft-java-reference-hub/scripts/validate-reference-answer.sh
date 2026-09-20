#!/usr/bin/env bash
set -euo pipefail

show_usage() {
  cat <<'USAGE'
사용법:
  ./validate-reference-answer.sh --file <markdown_path>
USAGE
}

file=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      show_usage
      exit 0
      ;;
    --file)
      file="${2:-}"
      shift 2
      ;;
    *)
      echo "[ERROR] 알 수 없는 인자: $1" >&2
      show_usage
      exit 1
      ;;
  esac
done

if [[ -z "$file" ]]; then
  echo "[ERROR] --file 경로가 필요합니다." >&2
  show_usage
  exit 1
fi

if [[ ! -f "$file" ]]; then
  echo "[ERROR] 파일이 존재하지 않습니다: $file" >&2
  exit 1
fi

fail=0
warn=0

pass(){ echo "[PASS] $1"; }
warn_msg(){ echo "[WARN] $1"; warn=$((warn+1)); }
fail_msg(){ echo "[FAIL] $1"; fail=$((fail+1)); }

content="$(cat "$file")"

if grep -qE '^기준 버전:' <<<"$content"; then
  pass "기준 버전 헤더 확인"
else
  fail_msg "기준 버전 헤더 누락"
fi

for section in "요약" "검증" "리스크"; do
  if grep -qE "^${section}$" <<<"$content"; then
    pass "섹션 확인: $section"
  else
    fail_msg "섹션 누락: $section"
  fi
done

if grep -qE '^구현/설정$|^구현 단계$|^구현$' <<<"$content"; then
  pass "구현 섹션 확인"
else
  fail_msg "구현 섹션 누락"
fi

if grep -qE '낮음|중간|높음' <<<"$content"; then
  pass "리스크 레벨 표기 확인"
else
  warn_msg "리스크 레벨(낮음/중간/높음) 표기가 없습니다"
fi

if grep -qE '@a|@e' <<<"$content"; then
  if grep -qE '범위|필터|제한|조건' <<<"$content"; then
    pass "광역 선택자 사용 시 안전 문맥 확인"
  else
    warn_msg "광역 선택자(@a/@e) 사용 대비 안전 문맥이 약합니다"
  fi
fi

echo
echo "요약"
echo "  FAIL: $fail"
echo "  WARN: $warn"

if (( fail > 0 )); then
  exit 1
fi

exit 0

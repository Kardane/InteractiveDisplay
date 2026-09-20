#!/usr/bin/env bash
set -euo pipefail

show_usage() {
  cat <<'USAGE'
사용법:
  ./verify-mod-env.sh -ProjectDir <path>
  ./verify-mod-env.sh --project-dir <path>
USAGE
}

project_dir=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help|-Help|--Help)
      show_usage
      exit 0
      ;;
    -ProjectDir|--project-dir)
      project_dir="${2:-}"
      shift 2
      ;;
    *)
      echo "[ERROR] 알 수 없는 인자: $1" >&2
      show_usage
      exit 1
      ;;
  esac
done

if [[ -z "$project_dir" ]]; then
  echo "[ERROR] -ProjectDir 인자가 필요합니다." >&2
  show_usage
  exit 1
fi

if [[ ! -d "$project_dir" ]]; then
  echo "[ERROR] 프로젝트 경로가 존재하지 않습니다: $project_dir" >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "[ERROR] python3가 필요합니다." >&2
  exit 1
fi

fail_count=0
warn_count=0

pass() { echo "[PASS] $1"; }
warn_msg() { echo "[WARN] $1"; warn_count=$((warn_count + 1)); }
fail_msg() { echo "[FAIL] $1"; fail_count=$((fail_count + 1)); }

check_file() {
  local path="$1"
  if [[ -f "$project_dir/$path" ]]; then
    pass "파일 존재: $path"
  else
    fail_msg "파일 없음: $path"
  fi
}

json_check() {
  local file="$1"
  if python3 - <<PY "$project_dir/$file" >/dev/null 2>&1
import json,sys
with open(sys.argv[1], 'r', encoding='utf-8') as fh:
    json.load(fh)
PY
  then
    pass "JSON 파싱 성공: $file"
  else
    fail_msg "JSON 파싱 실패: $file"
  fi
}

required_files=(
  "build.gradle"
  "gradle.properties"
  "settings.gradle"
  "src/main/resources/fabric.mod.json"
)

for f in "${required_files[@]}"; do
  check_file "$f"
done

if [[ -f "$project_dir/gradle.properties" ]]; then
  gp="$(cat "$project_dir/gradle.properties")"

  if grep -qE '^minecraft_version=1\.21\.8$' <<<"$gp"; then
    pass "minecraft_version=1.21.8 고정"
  else
    warn_msg "minecraft_version이 1.21.8이 아니거나 누락"
  fi

  for key in yarn_mappings loader_version fabric_api_version polymer_version; do
    if grep -qE "^${key}=" <<<"$gp"; then
      pass "버전 키 확인: $key"
    else
      fail_msg "버전 키 누락: $key"
    fi
  done

  if grep -qE '^(loader_version|fabric_api_version|polymer_version)=TODO_' <<<"$gp"; then
    warn_msg "TODO 버전이 남아 있습니다. 빌드/배포 전 치환 필요"
  fi
fi

if [[ -f "$project_dir/build.gradle" ]]; then
  bg="$(cat "$project_dir/build.gradle")"

  if grep -q "fabric-loom" <<<"$bg"; then
    pass "fabric-loom 선언 확인"
  else
    fail_msg "fabric-loom 선언 누락"
  fi

  if grep -q "JavaLanguageVersion.of(21)" <<<"$bg"; then
    pass "Java 21 toolchain 선언 확인"
  else
    warn_msg "Java 21 toolchain 선언을 찾지 못함"
  fi

  for token in "fabric-loader" "fabric-api" "polymer-core"; do
    if grep -q "$token" <<<"$bg"; then
      pass "의존성 키워드 확인: $token"
    else
      fail_msg "의존성 키워드 누락: $token"
    fi
  done
fi

if [[ -f "$project_dir/src/main/resources/fabric.mod.json" ]]; then
  json_check "src/main/resources/fabric.mod.json"

  fm_env="$(python3 - <<'PY' "$project_dir/src/main/resources/fabric.mod.json"
import json,sys
obj=json.load(open(sys.argv[1], 'r', encoding='utf-8'))
print(obj.get('environment',''))
PY
)"

  if [[ "$fm_env" == "server" ]]; then
    pass "fabric.mod.json environment=server"
  else
    fail_msg "fabric.mod.json environment가 server가 아님: ${fm_env:-<없음>}"
  fi

  fm_version="$(python3 - <<'PY' "$project_dir/src/main/resources/fabric.mod.json"
import json,sys
obj=json.load(open(sys.argv[1], 'r', encoding='utf-8'))
print(obj.get('version',''))
PY
)"

  if [[ "$fm_version" == '${version}' ]]; then
    pass "fabric.mod.json 버전 템플릿 문자열 확인"
  else
    warn_msg "fabric.mod.json version 값 확인 필요: $fm_version"
  fi
fi

shopt -s nullglob
mixin_configs=("$project_dir"/src/main/resources/*.mixins.json)
shopt -u nullglob

if (( ${#mixin_configs[@]} == 0 )); then
  fail_msg "*.mixins.json 파일이 없습니다"
else
  pass "mixins 설정 파일 수: ${#mixin_configs[@]}"
  for cfg in "${mixin_configs[@]}"; do
    rel="${cfg#$project_dir/}"
    if python3 - <<PY "$cfg" >/dev/null 2>&1
import json,sys
obj=json.load(open(sys.argv[1], 'r', encoding='utf-8'))
required=['package','mixins','injectors']
missing=[k for k in required if k not in obj]
if missing:
    raise SystemExit(1)
if not isinstance(obj.get('mixins'), list):
    raise SystemExit(1)
PY
    then
      pass "mixin 설정 구조 확인: $rel"
    else
      fail_msg "mixin 설정 구조 오류: $rel"
    fi
  done
fi

java_root="$project_dir/src/main/java"
if [[ -d "$java_root" ]]; then
  if rg -n "net\.minecraft\.client" "$java_root" >/dev/null 2>&1; then
    warn_msg "서버 코드에서 net.minecraft.client import 흔적 발견"
  else
    pass "클라이언트 전용 import 흔적 없음"
  fi

  if rg -n "@Mixin\(" "$java_root" >/dev/null 2>&1; then
    pass "Mixin 클래스 흔적 확인"
  else
    warn_msg "@Mixin 클래스가 보이지 않습니다"
  fi
else
  fail_msg "src/main/java 디렉터리가 없습니다"
fi

echo
echo "요약"
echo "  FAIL: $fail_count"
echo "  WARN: $warn_count"

if (( fail_count > 0 )); then
  exit 1
fi

exit 0

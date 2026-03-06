#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build_main_dir="$repo_root/build/classes/java/main"
bin_main_dir="$repo_root/bin/main"

if [[ ! -d "$build_main_dir" ]]; then
  echo "오류: $build_main_dir 없음. 먼저 compileJava 실행 필요." >&2
  exit 1
fi

if [[ ! -d "$bin_main_dir" ]]; then
  echo "오류: $bin_main_dir 없음. sync-jdt-output.sh 먼저 실행 필요." >&2
  exit 1
fi

build_signature="$(javap -classpath "$build_main_dir" -p com.interactivedisplay.core.window.WindowInstance | grep 'public com.interactivedisplay.core.window.WindowInstance')"
bin_signature="$(javap -classpath "$bin_main_dir" -p com.interactivedisplay.core.window.WindowInstance | grep 'public com.interactivedisplay.core.window.WindowInstance')"

if [[ "$build_signature" != "$bin_signature" ]]; then
  echo "불일치: WindowInstance 생성자 시그니처 다름" >&2
  echo "build: $build_signature" >&2
  echo "bin  : $bin_signature" >&2
  exit 1
fi

if grep -qE '"(polymer-core|sgui|placeholder-api)"' "$bin_main_dir/fabric.mod.json"; then
  echo "불일치: bin/main/fabric.mod.json 에 오래된 suggests 남아 있음" >&2
  exit 1
fi

echo "JDT 출력 검증 통과"

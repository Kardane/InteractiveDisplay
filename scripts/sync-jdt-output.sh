#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build_main_dir="$repo_root/build/classes/java/main"
build_resources_dir="$repo_root/build/resources/main"
bin_dir="$repo_root/bin"
bin_main_dir="$bin_dir/main"

if [[ ! -d "$build_main_dir" ]]; then
  echo "오류: $build_main_dir 없음. 먼저 compileJava 실행 필요." >&2
  exit 1
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
if [[ -d "$bin_dir" ]]; then
  mv "$bin_dir" "/tmp/interactivedisplay-bin-stale-$timestamp"
fi

mkdir -p "$bin_main_dir"
cp -a "$build_main_dir"/. "$bin_main_dir"/

if [[ -d "$build_resources_dir" ]]; then
  cp -a "$build_resources_dir"/. "$bin_main_dir"/
fi

echo "JDT 출력 동기화 완료: $bin_main_dir"

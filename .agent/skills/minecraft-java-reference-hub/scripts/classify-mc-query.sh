#!/usr/bin/env bash
set -euo pipefail

show_usage() {
  cat <<'USAGE'
사용법:
  ./classify-mc-query.sh --query "<질문 텍스트>"
USAGE
}

query=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      show_usage
      exit 0
      ;;
    --query)
      query="${2:-}"
      shift 2
      ;;
    *)
      echo "[ERROR] 알 수 없는 인자: $1" >&2
      show_usage
      exit 1
      ;;
  esac
done

if [[ -z "$query" ]]; then
  echo "[ERROR] --query 값이 필요합니다." >&2
  show_usage
  exit 1
fi

q="$(echo "$query" | tr '[:upper:]' '[:lower:]')"

mode="빠른 답변"
if [[ "$q" =~ (설계|구조|아키텍처|플레이북|통합|workflow|워크플로우) ]]; then
  mode="구현 레시피"
fi
if [[ "$q" =~ (tps|랙|지연|장애|트러블|오류|롤백|운영|multiplayer|서버) ]]; then
  mode="운영 대응"
fi
if [[ "$q" =~ (비교|차이|마이그레이션|업그레이드|vs) ]] && [[ "$q" =~ (버전|1\.21\.8.*1\.21\.11|1\.21\.11.*1\.21\.8) ]]; then
  mode="버전 비교"
fi

tags=()
append_tag() {
  local tag="$1"
  for t in "${tags[@]:-}"; do
    if [[ "$t" == "$tag" ]]; then
      return
    fi
  done
  tags+=("$tag")
}

[[ "$q" =~ (블록|blockstate|block) ]] && append_tag "블록"
[[ "$q" =~ (아이템|item) ]] && append_tag "아이템"
[[ "$q" =~ (엔티티|entity|mob) ]] && append_tag "엔티티"
[[ "$q" =~ (발전과제|advancement) ]] && append_tag "발전과제"
[[ "$q" =~ (스코어보드|scoreboard) ]] && append_tag "스코어보드"
[[ "$q" =~ (마법부여|enchant) ]] && append_tag "마법부여"
[[ "$q" =~ (루트|loot) ]] && append_tag "루트 테이블"
[[ "$q" =~ (다이얼로그|dialog) ]] && append_tag "다이얼로그"
[[ "$q" =~ (nbt) ]] && append_tag "NBT"
[[ "$q" =~ (item component|컴포넌트) ]] && append_tag "item component"
[[ "$q" =~ (명령어|execute|function) ]] && append_tag "명령어"
[[ "$q" =~ (데이터팩|datapack) ]] && append_tag "데이터팩"
[[ "$q" =~ (리소스팩|resourcepack|resource pack) ]] && append_tag "리소스팩"
[[ "$q" =~ (멀티|서버|multiplayer) ]] && append_tag "멀티플레이어 서버"
[[ "$q" =~ (gamerule|게임 규칙) ]] && append_tag "게임 규칙"
[[ "$q" =~ (생물군계|biome) ]] && append_tag "생물군계"
[[ "$q" =~ (피해|damage) ]] && append_tag "피해 종류"
[[ "$q" =~ (상태 효과|effect) ]] && append_tag "상태 효과"
[[ "$q" =~ (구조물|structure) ]] && append_tag "구조물"
[[ "$q" =~ (게임 모드|gamemode) ]] && append_tag "게임 모드"
[[ "$q" =~ (조작|컨트롤|keybind) ]] && append_tag "조작법"
[[ "$q" =~ (차원|dimension|네더|엔드) ]] && append_tag "차원"
[[ "$q" =~ (플레이어|player) ]] && append_tag "플레이어"

has_128=0
has_1211=0
[[ "$q" =~ 1\.21\.8 ]] && has_128=1
[[ "$q" =~ 1\.21\.11 ]] && has_1211=1

version_hint="미확정"
if (( has_128 == 1 && has_1211 == 1 )); then
  version_hint="1.21.8+1.21.11"
elif (( has_128 == 1 )); then
  version_hint="1.21.8"
elif (( has_1211 == 1 )); then
  version_hint="1.21.11"
fi

if (( ${#tags[@]} == 0 )); then
  append_tag "도메인 미분류"
fi

echo "mode=$mode"
echo "version_hint=$version_hint"
echo -n "tags="
(IFS=','; echo "${tags[*]}")

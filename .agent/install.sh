#!/usr/bin/env bash
set -euo pipefail
root="${1:-.}"
mkdir -p "$root/.agents/skills"
cp -a "$(dirname "$0")/.agents/skills/." "$root/.agents/skills/"
echo "Installed merged Minecraft skills into: $root/.agents/skills"
echo "If minecraft-fabric-test-orchestrator exists there from an older pack, remove it to avoid duplicate routing."

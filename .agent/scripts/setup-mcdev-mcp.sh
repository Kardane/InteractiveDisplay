#!/usr/bin/env bash
set -euo pipefail
codex mcp add mcdev-mcp -- npx -y mcdev-mcp serve || true
npx -y mcdev-mcp init -v "${MC_VERSION:-1.21.8}"
codex mcp get mcdev-mcp

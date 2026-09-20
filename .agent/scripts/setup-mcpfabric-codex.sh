#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 2 ]]; then
  echo "usage: $0 /absolute/path/to/mcpfabric/mcp-server/dist/index.js TOKEN [URL]" >&2
  exit 2
fi
entry="$1"
token="$2"
url="${3:-http://127.0.0.1:25599}"
[[ -f "$entry" ]] || { echo "MCP Fabric server entry not found: $entry" >&2; exit 2; }
codex mcp add mcpfabric \
  --env "MCPFABRIC_URL=$url" \
  --env "MCPFABRIC_TOKEN=$token" \
  -- node "$entry"
codex mcp get mcpfabric

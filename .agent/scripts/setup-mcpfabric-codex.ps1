param(
  [Parameter(Mandatory=$true)][string]$Entry,
  [Parameter(Mandatory=$true)][string]$Token,
  [string]$Url = "http://127.0.0.1:25599"
)
$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $Entry -PathType Leaf)) {
  throw "MCP Fabric server entry not found: $Entry"
}
codex mcp add mcpfabric --env "MCPFABRIC_URL=$Url" --env "MCPFABRIC_TOKEN=$Token" -- node $Entry
codex mcp get mcpfabric

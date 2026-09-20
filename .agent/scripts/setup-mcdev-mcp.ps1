$ErrorActionPreference = "Stop"
$version = if ($env:MC_VERSION) { $env:MC_VERSION } else { "1.21.8" }
try { codex mcp add mcdev-mcp -- npx -y mcdev-mcp serve } catch { }
npx -y mcdev-mcp init -v $version
codex mcp get mcdev-mcp

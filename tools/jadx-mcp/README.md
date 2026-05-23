# jadx-mcp local tooling

Project-local install of [jadx-ai-mcp](https://github.com/zinja-coder/jadx-ai-mcp) (v6.3.0). Lets Claude Code drive JADX-GUI over MCP to inspect decompiled APKs.

This whole `tools/` tree is gitignored.

## Layout

```
tools/jadx-mcp/
├── jadx-ai-mcp-6.3.0.jar        # JADX-GUI plugin (install once, manually)
├── jadx-mcp-server-6.3.0.zip    # original release archive
└── server/jadx-mcp-server/      # extracted Python MCP server
    └── jadx_mcp_server.py       # entry point (run by uv via .mcp.json)
```

## Prerequisites

- **JADX-GUI** with the plugin installed (one-time per machine).
- **uv** on `PATH` — `uv --version` should resolve. Install: <https://docs.astral.sh/uv/getting-started/installation/>.
- **Python 3.10+** (uv auto-fetches if missing).

## One-time setup (per machine)

1. Launch JADX-GUI.
2. `Preferences` (Ctrl+P) → `Plugins` → `Install plugin` → select `jadx-ai-mcp-6.3.0.jar` from this directory.
3. Restart JADX-GUI. Plugin listens on `127.0.0.1:8650`.

## Per-session usage

1. Open JADX-GUI, load the target APK (e.g. the Messenger APK at repo root).
2. Start Claude Code in this repo. Project-scoped [`.mcp.json`](../../.mcp.json) auto-launches the Python server via `uv run jadx_mcp_server.py`.
3. The server connects to the JADX plugin on `127.0.0.1:8650` and exposes ~25 MCP tools (`get_class_source`, `search_method_by_name`, `get_xrefs_to_method`, etc.).

## Re-install / upgrade

Bump `v6.3.0` to the new tag, drop new JAR + zip in here, re-extract, restart JADX-GUI to load new plugin.

```powershell
$ver = "6.3.0"
Invoke-WebRequest "https://github.com/zinja-coder/jadx-ai-mcp/releases/download/v$ver/jadx-ai-mcp-$ver.jar" -OutFile "jadx-ai-mcp-$ver.jar"
Invoke-WebRequest "https://github.com/zinja-coder/jadx-ai-mcp/releases/download/v$ver/jadx-mcp-server-$ver.zip" -OutFile "jadx-mcp-server-$ver.zip"
Expand-Archive "jadx-mcp-server-$ver.zip" -DestinationPath "server" -Force
```

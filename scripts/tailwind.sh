#!/usr/bin/env bash
# Builds src/main/resources/static/css/app.css with the Tailwind standalone CLI.
# Downloads the pinned CLI into .tools/ on first use. Extra args are passed through
# (e.g. --minify for builds, --watch for `make dev`).
set -euo pipefail

TAILWIND_VERSION="v4.3.5"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN="$ROOT/.tools/tailwindcss-$TAILWIND_VERSION"

if [[ ! -x "$BIN" ]]; then
  case "$(uname -s)-$(uname -m)" in
    Linux-x86_64)   asset="tailwindcss-linux-x64" ;;
    Linux-aarch64)  asset="tailwindcss-linux-arm64" ;;
    Darwin-x86_64)  asset="tailwindcss-macos-x64" ;;
    Darwin-arm64)   asset="tailwindcss-macos-arm64" ;;
    *) echo "Unsupported platform: $(uname -s)-$(uname -m)" >&2; exit 1 ;;
  esac
  mkdir -p "$ROOT/.tools"
  echo "Downloading Tailwind CLI $TAILWIND_VERSION ($asset)..."
  curl -fsSL -o "$BIN.tmp" \
    "https://github.com/tailwindlabs/tailwindcss/releases/download/$TAILWIND_VERSION/$asset"
  chmod +x "$BIN.tmp"
  mv "$BIN.tmp" "$BIN"
fi

exec "$BIN" \
  -i "$ROOT/tailwind/input.css" \
  -o "$ROOT/src/main/resources/static/css/app.css" \
  "$@"

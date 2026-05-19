#!/usr/bin/env bash
# Generate SVG + PNG for every .mmd file under docs/rerference/
# Usage: ./scripts/generate-diagrams.sh [--svg-only] [--png-only]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DIAGRAM_DIR="$REPO_ROOT/docs/rerference"

SVG=true
PNG=true

for arg in "$@"; do
  case $arg in
    --svg-only) PNG=false ;;
    --png-only) SVG=false ;;
  esac
done

if ! command -v npx &>/dev/null; then
  echo "ERROR: npx not found. Install Node.js first." >&2
  exit 1
fi

generated=()

for mmd_file in "$DIAGRAM_DIR"/*.mmd; do
  [ -f "$mmd_file" ] || continue
  base="${mmd_file%.mmd}"

  if $SVG; then
    npx --yes @mermaid-js/mermaid-cli \
      -i "$mmd_file" \
      -o "${base}.svg" \
      --backgroundColor white \
      --theme default \
      --quiet
    generated+=("${base}.svg")
  fi

  if $PNG; then
    npx --yes @mermaid-js/mermaid-cli \
      -i "$mmd_file" \
      -o "${base}.png" \
      --scale 5 \
      --width 4000 \
      --backgroundColor white \
      --theme default \
      --quiet
    generated+=("${base}.png")
  fi
done

echo ""
echo "Generated ${#generated[@]} file(s):"
for f in "${generated[@]}"; do
  size=$(du -sh "$f" 2>/dev/null | cut -f1)
  echo "  ✓ $(basename "$f")  ($size)"
done
echo ""
echo "Tip: open .svg files in your browser or VS Code for zero-blur viewing."

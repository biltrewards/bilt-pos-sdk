#!/usr/bin/env bash
# Regenerates bridge.icns from bridge.svg using only tools that ship with macOS:
# qlmanage renders the SVG to a 1024px PNG, sips scales it to each size the
# iconset needs, iconutil packs the .icns. Run from anywhere.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
icons="$here/../src/main/resources/icons"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

qlmanage -t -s 1024 -o "$work" "$icons/bridge.svg" >/dev/null 2>&1
master="$work/bridge.svg.png"
[ -f "$master" ] || { echo "qlmanage did not render $icons/bridge.svg" >&2; exit 1; }

set="$work/bridge.iconset"
mkdir -p "$set"
for size in 16 32 128 256 512; do
  sips -z "$size" "$size" "$master" --out "$set/icon_${size}x${size}.png" >/dev/null
  double=$((size * 2))
  sips -z "$double" "$double" "$master" --out "$set/icon_${size}x${size}@2x.png" >/dev/null
done

iconutil -c icns "$set" -o "$icons/bridge.icns"
echo "wrote $icons/bridge.icns"

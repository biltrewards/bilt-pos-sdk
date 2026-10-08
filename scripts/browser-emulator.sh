#!/usr/bin/env bash
#
#    ____  _ _ _
#   | __ )(_) | |_
#   |  _ \| | | __|
#   | |_) | | | |_
#   |____/|_|_|\__|
#
#   Bilt POS SDK
#
# Starts the browser register emulator (js/apps/emulator) together with a
# Terminal Bridge, so one command gives a working register in the browser.
#
# What it does:
#   1. Writes a development bridge config to a scratch directory: one terminal
#      (unencrypted, trustAll — what development terminals accept) or none with
#      --local, and the emulator's origin in allowedOrigins.
#   2. Starts the bridge in the background and waits for GET /health.
#   3. Starts the emulator's Vite dev server in the foreground and opens it.
#   4. Stops both on exit or Ctrl-C.
#
# Why the bridge runs from its installed launcher rather than `gradlew :bridge:run`:
# a Gradle daemon is a detached process, so on macOS 15+ it is not covered by
# your terminal's Local Network privacy grant and cannot reach a LAN terminal
# (see scripts/terminal-cli.sh). The launcher runs as a direct child of this shell.
#
# Usage:
#   scripts/browser-emulator.sh --terminal 192.168.4.108
#   scripts/browser-emulator.sh --terminal 192.168.4.108:8443 --poi-id VictaLane-275839164
#   scripts/browser-emulator.sh --local
#
# Options:
#   --terminal <ip[:port]>  Terminal on the LAN (port defaults to 8443).
#   --poi-id <id>           POIID to register the terminal under (default DEV-TERMINAL).
#   --local                 No terminal; use the emulator's local-session mode.
#   --port <port>           Bridge port (default 48333).
#   --web-port <port>       Vite dev server port (default 5173).
#   --no-open               Do not open the browser.
#   -h, --help              Show this help.
#
# Env:
#   SKIP_BUILD=1   Reuse the existing bridge launcher and JS build (fast iteration).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRIDGE_LAUNCHER="$REPO_ROOT/bridge/build/install/bridge/bin/bridge"

terminal=""
poi_id="DEV-TERMINAL"
local_only=0
bridge_port=48333
web_port=5173
open_browser=1

usage() { sed -n '/^# Usage:/,/^# Env:/p' "$0" | sed 's/^# \{0,1\}//' | sed '$d'; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --terminal) terminal="${2:?--terminal needs <ip[:port]>}"; shift 2 ;;
    --poi-id) poi_id="${2:?--poi-id needs a value}"; shift 2 ;;
    --local) local_only=1; shift ;;
    --port) bridge_port="${2:?--port needs a value}"; shift 2 ;;
    --web-port) web_port="${2:?--web-port needs a value}"; shift 2 ;;
    --no-open) open_browser=0; shift ;;
    -h | --help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$terminal" && "$local_only" -eq 0 ]]; then
  echo "Pass --terminal <ip[:port]> or --local." >&2
  usage >&2
  exit 2
fi

# pnpm from PATH, otherwise through corepack (version pinned in js/package.json).
if command -v pnpm >/dev/null 2>&1; then
  PNPM=(pnpm)
elif command -v corepack >/dev/null 2>&1; then
  PNPM=(corepack pnpm)
else
  echo "Neither pnpm nor corepack is on PATH; install Node 20+ and run 'corepack enable'." >&2
  exit 1
fi

if [[ "${SKIP_BUILD:-}" != "1" ]]; then
  echo "Building the bridge launcher..."
  "$REPO_ROOT/gradlew" -q -p "$REPO_ROOT" :bridge:installDist
  echo "Installing and building the JS workspace..."
  (cd "$REPO_ROOT/js" && "${PNPM[@]}" install --frozen-lockfile >/dev/null &&
    "${PNPM[@]}" --filter '@bilt/pos-emulator^...' build >/dev/null)
fi

if [[ ! -x "$BRIDGE_LAUNCHER" ]]; then
  echo "Bridge launcher not found at $BRIDGE_LAUNCHER (run without SKIP_BUILD=1)." >&2
  exit 1
fi

# The bridge moves to the next free port when its own is taken, so a check on
# --port could answer for another bridge (the tray app, say) with another
# config. Refuse instead of guessing; Vite's --strictPort would fail later anyway.
port_in_use() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }
for p in "$bridge_port" "$web_port"; do
  if port_in_use "$p"; then
    echo "Port $p on 127.0.0.1 is already in use (another bridge or dev server?)." >&2
    echo "Stop it, or pick another with --port / --web-port." >&2
    exit 1
  fi
done

if [[ "$local_only" -eq 1 ]]; then session_mode=local; else session_mode=terminal; fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/bilt-browser-emulator.XXXXXX")"
config="$work_dir/config.json"

terminals="[]"
if [[ "$local_only" -eq 0 ]]; then
  host="${terminal%%:*}"
  t_port=8443
  [[ "$terminal" == *:* ]] && t_port="${terminal##*:}"
  terminals="[{\"poiId\":\"$poi_id\",\"host\":\"$host\",\"port\":$t_port,\"encryption\":false,\"trustAll\":true}]"
fi

cat >"$config" <<EOF
{
  "port": $bridge_port,
  "allowedOrigins": ["http://127.0.0.1:$web_port", "http://localhost:$web_port"],
  "terminals": $terminals
}
EOF

bridge_pid=""
web_pid=""

# Stops a process and its descendants; pnpm runs vite as a child, which would
# otherwise outlive pnpm and keep the port.
kill_tree() {
  local pid=$1 child
  for child in $(pgrep -P "$pid" 2>/dev/null); do kill_tree "$child"; done
  kill "$pid" 2>/dev/null || true
}

cleanup() {
  trap - EXIT INT TERM
  for pid in "$web_pid" "$bridge_pid"; do
    [[ -n "$pid" ]] && kill_tree "$pid"
  done
  wait 2>/dev/null || true
  rm -rf "$work_dir"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

echo "Starting the Terminal Bridge on 127.0.0.1:$bridge_port (log: $work_dir/bridge.log)..."
JAVA_OPTS="${JAVA_OPTS:-} -Djava.awt.headless=true" \
  "$BRIDGE_LAUNCHER" --config "$config" >"$work_dir/bridge.log" 2>&1 &
bridge_pid=$!

health="http://127.0.0.1:$bridge_port/health"
for _ in $(seq 1 60); do
  if curl -fsS "$health" >/dev/null 2>&1; then break; fi
  if ! kill -0 "$bridge_pid" 2>/dev/null; then
    echo "The bridge exited during startup:" >&2
    tail -20 "$work_dir/bridge.log" >&2
    exit 1
  fi
  sleep 1
done
if ! curl -fsS "$health" >/dev/null 2>&1; then
  echo "The bridge did not answer $health within 60 s:" >&2
  tail -20 "$work_dir/bridge.log" >&2
  exit 1
fi
echo "Bridge ready: $(curl -fsS "$health")"

url="http://127.0.0.1:$web_port"
if [[ "$open_browser" -eq 1 ]]; then
  # Give Vite a moment to bind before the browser asks for the page.
  (sleep 2 && { command -v open >/dev/null && open "$url" || xdg-open "$url"; } >/dev/null 2>&1) &
fi

echo "Browser emulator: $url (Ctrl-C stops the emulator and the bridge)"
# Both servers run as background children so a signal reaches this shell's
# traps immediately, instead of after the foreground dev server exits. The
# session mode becomes the page's launch setting (src/settings.ts), so it opens
# the kind of session the bridge config above can serve.
(cd "$REPO_ROOT/js" &&
  BRIDGE_URL="http://127.0.0.1:$bridge_port" VITE_BILT_BRIDGE_PORT="$bridge_port" \
    VITE_BILT_SESSION_MODE="$session_mode" \
    exec "${PNPM[@]}" --filter @bilt/pos-emulator dev --host 127.0.0.1 --port "$web_port" --strictPort) &
web_pid=$!

# Exit when either server stops on its own, with its status; the EXIT trap
# stops the other.
while kill -0 "$web_pid" 2>/dev/null && kill -0 "$bridge_pid" 2>/dev/null; do
  sleep 1
done
status=0
if ! kill -0 "$bridge_pid" 2>/dev/null; then
  wait "$bridge_pid" || status=$?
  echo "The bridge stopped; last log lines:" >&2
  tail -20 "$work_dir/bridge.log" >&2
  exit $((status == 0 ? 1 : status))
fi
wait "$web_pid" || status=$?
[[ "$status" -eq 0 ]] || echo "The dev server exited with status $status." >&2
exit "$status"

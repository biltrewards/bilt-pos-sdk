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
# Runs the Bilt POS CLI against a terminal as a direct child of your shell,
# instead of through `./gradlew :cli:run`.
#
# Why: `gradlew :cli:run` executes inside a Gradle daemon (and on Gradle 9+
# forks a single-use daemon even with --no-daemon, to honour org.gradle.jvmargs).
# That daemon is a detached background process, so on macOS 15+ (Sequoia/Tahoe)
# it is NOT covered by your terminal's Local Network privacy grant. Connecting
# to a LAN terminal then fails with:
#
#     java.net.NoRouteToHostException: No route to host
#
# This script builds the `application` launcher and execs the `java` it produces
# directly, so the connection is attributed to your terminal's Local Network
# permission (the same grant that lets `ping`/`nc` reach the device).
#
# Usage:
#   scripts/terminal-cli.sh <ip> [options]
#   scripts/terminal-cli.sh 192.168.4.108 --type diagnosis --no-encryption
#   scripts/terminal-cli.sh 192.168.4.108 --type payment --passphrase "$BILT_TERMINAL_PASSPHRASE" --key-id myTerminal
#
# Env:
#   SKIP_BUILD=1   Skip the Gradle build and run the existing launcher (fast iteration).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAUNCHER="$REPO_ROOT/cli/build/install/cli/bin/cli"

if [[ "${SKIP_BUILD:-}" != "1" ]]; then
  # The daemon is fine for *building* — only the network connection must run
  # under the terminal, which the launcher below guarantees.
  "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :cli:installDist -q
fi

if [[ ! -x "$LAUNCHER" ]]; then
  echo "error: launcher not found at $LAUNCHER (run without SKIP_BUILD to build it)" >&2
  exit 1
fi

exec "$LAUNCHER" "$@"

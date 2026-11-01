#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# New single-use daemon inherits nice(19); never reuse a higher-priority daemon.
if command -v ionice >/dev/null 2>&1; then
  exec nice -n 19 ionice -c 3 ./gradlew --no-daemon -Dorg.gradle.priority=low --max-workers=2 "$@"
fi
exec nice -n 19 ./gradlew --no-daemon -Dorg.gradle.priority=low --max-workers=2 "$@"

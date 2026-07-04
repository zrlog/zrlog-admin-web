#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

usage() {
    cat <<EOF
Usage: bash shell/memory-run.sh [--port=17080|17080]

Environment:
  ZRLOG_MEMORY_PORT                  Default port when no argument is provided.
  ZRLOG_MEMORY_FORCE_FRONTEND_BUILD  Set to 1 to rebuild admin static resources.
  ZRLOG_MEMORY_SKIP_FRONTEND_BUILD   Set to 1 to skip admin static resource checks.
EOF
}

PORT="${ZRLOG_MEMORY_PORT:-17080}"
if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    usage
    exit 0
elif [ -n "${1:-}" ]; then
    case "$1" in
        --port=*)
            PORT="${1#--port=}"
            ;;
        *)
            PORT="$1"
            ;;
    esac
fi

if [ -n "${2:-}" ] || ! [[ "$PORT" =~ ^[0-9]+$ ]]; then
    usage >&2
    exit 2
fi

needs_frontend_build=0
if [ "${ZRLOG_MEMORY_SKIP_FRONTEND_BUILD:-0}" != "1" ]; then
    if [ "${ZRLOG_MEMORY_FORCE_FRONTEND_BUILD:-0}" = "1" ] || [ ! -f "src/main/resources/admin/index.html" ]; then
        needs_frontend_build=1
    fi
fi

if [ "$needs_frontend_build" -eq 1 ]; then
    ./mvnw -PnodeBuild -DskipTests package
else
    ./mvnw -q -DskipTests compile
fi

exec ./mvnw exec:java \
    -Dexec.mainClass="com.zrlog.admin.MemoryApplication" \
    -Dexec.args="--port=${PORT}"

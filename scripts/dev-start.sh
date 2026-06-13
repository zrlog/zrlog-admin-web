#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/src/main/frontend"
LOG_DIR="${ZRLOG_ADMIN_DEV_LOG_DIR:-/tmp/zrlog-admin-web-dev}"

BACKEND_URL="${BACKEND_URL:-http://localhost:17080/sub}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:${FRONTEND_PORT}/admin}"

BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"
BACKEND_PID=""
FRONTEND_PID=""
MVNW_ARGS="${MVNW_ARGS:-}"

mkdir -p "$LOG_DIR"

is_ready() {
    env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u all_proxy -u ALL_PROXY \
        NO_PROXY="127.0.0.1,localhost" no_proxy="127.0.0.1,localhost" \
        curl -fsS -o /dev/null "$1" 2>/dev/null
}

wait_until_ready() {
    local name="$1"
    local url="$2"
    local log_file="$3"
    local attempts="${4:-60}"

    for _ in $(seq 1 "$attempts"); do
        if is_ready "$url"; then
            echo "$name ready: $url"
            return 0
        fi
        sleep 1
    done

    echo "$name did not become ready: $url" >&2
    echo "Last log lines from $log_file:" >&2
    tail -n 80 "$log_file" >&2 || true
    return 1
}

cleanup() {
    if [[ -n "$FRONTEND_PID" ]] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
        kill "$FRONTEND_PID" 2>/dev/null || true
    fi
    if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
        kill "$BACKEND_PID" 2>/dev/null || true
    fi
}

trap cleanup EXIT INT TERM

if is_ready "$BACKEND_URL/admin/login"; then
    echo "Backend already running: $BACKEND_URL"
else
    echo "Starting backend: $BACKEND_URL"
    (
        cd "$ROOT_DIR"
        ./mvnw $MVNW_ARGS exec:java -Dexec.mainClass="com.zrlog.admin.Application"
    ) >"$BACKEND_LOG" 2>&1 &
    BACKEND_PID="$!"
    wait_until_ready "Backend" "$BACKEND_URL/admin/login" "$BACKEND_LOG" 90
fi

if is_ready "$FRONTEND_URL"; then
    echo "Frontend already running: $FRONTEND_URL"
else
    echo "Starting frontend: $FRONTEND_URL"
    (
        cd "$FRONTEND_DIR"
        BROWSER=none \
            PORT="$FRONTEND_PORT" \
            CHOKIDAR_USEPOLLING="${CHOKIDAR_USEPOLLING:-true}" \
            WATCHPACK_POLLING="${WATCHPACK_POLLING:-true}" \
            yarn start
    ) >"$FRONTEND_LOG" 2>&1 &
    FRONTEND_PID="$!"
    wait_until_ready "Frontend" "$FRONTEND_URL" "$FRONTEND_LOG" 120
fi

cat <<EOF

Development services are ready.
Frontend: $FRONTEND_URL
Backend:  $BACKEND_URL
Logs:     $LOG_DIR

Press Ctrl-C to stop services started by this script.
EOF

if [[ -n "$BACKEND_PID$FRONTEND_PID" ]]; then
    wait
fi

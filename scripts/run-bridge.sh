#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOST="${CODEX_REMOTE_HOST:-0.0.0.0}"
PORT="${CODEX_REMOTE_PORT:-8787}"
BACKEND="${CODEX_REMOTE_BACKEND:-codex}"
TOKEN_FILE="${CODEX_REMOTE_TOKEN_FILE:-$ROOT_DIR/data/bridge-token.txt}"
STATE_FILE="${CODEX_REMOTE_STATE_FILE:-$ROOT_DIR/data/bridge-state.json}"
SYNC_LOG="${CODEX_REMOTE_SYNC_LOG:-0}"
SYNC_LOG_FILE="${CODEX_REMOTE_SYNC_LOG_FILE:-$ROOT_DIR/data/bridge-sync.log}"

ensure_token() {
  node -e '
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const file = process.argv[1];
fs.mkdirSync(path.dirname(file), { recursive: true });
let token = "";
try { token = fs.readFileSync(file, "utf8").trim(); } catch {}
if (!token) {
  token = crypto.randomBytes(24).toString("hex");
  fs.writeFileSync(file, `${token}\n`, "utf8");
}
process.stdout.write(token);
' "$TOKEN_FILE"
}

list_reachable_ips() {
  if command -v hostname >/dev/null 2>&1; then
    hostname -I 2>/dev/null | tr ' ' '\n' || true
  fi
  if command -v ip >/dev/null 2>&1; then
    ip -4 addr show scope global 2>/dev/null | awk '/inet / { sub(/\/.*/, "", $2); print $2 }' || true
  fi
  if command -v ifconfig >/dev/null 2>&1; then
    ifconfig 2>/dev/null | awk '/inet / { print $2 }' || true
  fi
}

TOKEN="$(ensure_token)"

echo "Codex Remote Control bridge"
echo "Backend: $BACKEND"
echo "Listen:  $HOST:$PORT"
echo "Token:   $TOKEN"
echo
echo "手机端可以粘贴下面任意一个 ws:// 地址到 Host / URL 输入框："
list_reachable_ips \
  | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' \
  | grep -vE '^(127\.|0\.0\.0\.0)' \
  | sort -u \
  | while read -r ip; do
      echo "  ws://$ip:$PORT/?token=$TOKEN"
    done
echo
echo "如果 Codex 需要代理，先在当前 shell 执行：source ~/.zshrc && proxy_on"
echo

cd "$ROOT_DIR"
ARGS=(
  --backend "$BACKEND" \
  --listen "$HOST:$PORT" \
  --state "$STATE_FILE" \
  --token-file "$TOKEN_FILE"
)
if [[ "$SYNC_LOG" == "1" || "$SYNC_LOG" == "true" || "$SYNC_LOG" == "yes" ]]; then
  ARGS+=(--sync-log "$SYNC_LOG_FILE")
  echo "Sync log: $SYNC_LOG_FILE"
fi

exec npm start -- "${ARGS[@]}" "$@"

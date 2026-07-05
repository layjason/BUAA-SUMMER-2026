#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${1:-$SCRIPT_DIR/../../backend}" && pwd)"

log() {
  local message="$1"
  printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$message"
}

test_port() {
  local host="$1"
  local port="$2"
  if command -v nc >/dev/null 2>&1; then
    nc -z -w 3 "$host" "$port" >/dev/null 2>&1
  else
    echo "Warning: nc not found; cannot check ${host}:${port}." >&2
    return 2
  fi
}

get_port_from_endpoint() {
  local endpoint="${1:-}"
  local explicit_port="${2:-}"
  local default_port="$3"
  if [[ -n "$explicit_port" ]]; then
    printf '%s\n' "$explicit_port"
  elif [[ "$endpoint" =~ :([0-9]+)(/.*)?$ ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
  else
    printf '%s\n' "$default_port"
  fi
}

ENV_FILE="$BACKEND_DIR/.env.mailhog.example"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Error: Cannot find environment template: $ENV_FILE" >&2
  exit 1
fi

log ">>> 媒体鉴权测试 - 环境预检查开始..."
log "正在从 $ENV_FILE 注入环境变量..."

while IFS='=' read -r key value; do
  [[ -z "$key" || "$key" =~ ^[[:space:]]*# ]] && continue
  export "$key"="$value"
done < "$ENV_FILE"

export SPRING_PROFILES_ACTIVE=dev
export SPRING_CONFIG_IMPORT="optional:file:.env.mailhog.example[.properties]"

log "正在检查测试素材文件..."
for asset in "$SCRIPT_DIR/test-avatar.png" "$SCRIPT_DIR/test-license.png" "$SCRIPT_DIR/fixtures/chat-image.txt"; do
  if [[ -f "$asset" ]]; then
    log "$(basename "$asset") 存在"
  else
    echo "Warning: missing test asset: $asset" >&2
  fi
done

S3_HOST="${MAYOISTAR_S3_HOST:-127.0.0.1}"
S3_PORT="$(get_port_from_endpoint "${MAYOISTAR_S3_ENDPOINT:-}" "${MAYOISTAR_S3_PORT:-}" 9000)"
log "正在检查 S3 服务..."
if test_port "$S3_HOST" "$S3_PORT"; then
  log "S3 服务探测成功 (${S3_HOST}:${S3_PORT})"
else
  echo "Warning: S3 endpoint is not reachable at ${S3_HOST}:${S3_PORT}. File upload cases may fail." >&2
fi

REDIS_HOST="${MAYOISTAR_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${MAYOISTAR_REDIS_PORT:-${DEV_REDIS_PORT:-6379}}"
log "正在检查 Redis 服务..."
if test_port "$REDIS_HOST" "$REDIS_PORT"; then
  log "Redis 服务探测成功 (${REDIS_HOST}:${REDIS_PORT})"
else
  echo "Warning: Redis is not reachable at ${REDIS_HOST}:${REDIS_PORT}. Backend may fail after startup." >&2
fi

SMTP_PORT="${DEV_MAILHOG_SMTP_PORT:-1025}"
log "正在检查 MailHog 服务..."
if test_port "127.0.0.1" "$SMTP_PORT"; then
  log "MailHog SMTP 探测成功 (127.0.0.1:${SMTP_PORT})"
else
  echo "Warning: MailHog SMTP is not reachable at 127.0.0.1:${SMTP_PORT}. Email cases may fail." >&2
fi

log "正在执行 mvn spring-boot:run..."
cd "$BACKEND_DIR"
mvn spring-boot:run

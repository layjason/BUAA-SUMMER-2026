#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${1:-$SCRIPT_DIR/../../backend}" && pwd)"

ENV_FILE="$BACKEND_DIR/.env.mailhog.example"
if [[ ! -f "$ENV_FILE" ]]; then
    echo "Error: Cannot find MailHog environment template: $ENV_FILE" >&2
    exit 1
fi

# 从 .env.mailhog.example 读取环境变量并导出
while IFS='=' read -r key value; do
    [[ -z "$key" || "$key" =~ ^[[:space:]]*# ]] && continue
    export "$key"="$value"
done < "$ENV_FILE"

export SPRING_PROFILES_ACTIVE=dev
export SPRING_CONFIG_IMPORT="optional:file:.env.mailhog.example[.properties]"

STORAGE_HOST="${MAYOISTAR_S3_HOST:-localhost}"
STORAGE_PORT="${MAYOISTAR_S3_PORT:-9000}"
if command -v nc >/dev/null 2>&1; then
    if ! nc -z -w 3 "$STORAGE_HOST" "$STORAGE_PORT" 2>/dev/null; then
        echo "Warning: RustFS/S3 endpoint is not reachable at ${STORAGE_HOST}:${STORAGE_PORT}. File upload QA cases require docker compose to start rustfs." >&2
    fi
else
    echo "Warning: nc not found; cannot check RustFS/S3 endpoint ${STORAGE_HOST}:${STORAGE_PORT}." >&2
fi

REDIS_HOST="${MAYOISTAR_REDIS_HOST:-localhost}"
REDIS_PORT="${MAYOISTAR_REDIS_PORT:-6379}"
if command -v redis-cli >/dev/null 2>&1; then
    if ! redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping >/dev/null 2>&1; then
        echo "Warning: Redis is not reachable at ${REDIS_HOST}:${REDIS_PORT}. Backend will fail to start because Redis is required for media access cache and rate limiting." >&2
    fi
fi

cd "$BACKEND_DIR"
mvn spring-boot:run

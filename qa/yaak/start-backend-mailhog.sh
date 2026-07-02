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

STORAGE_ENDPOINT="${MAYOISTAR_S3_ENDPOINT:-http://localhost:9000}"
if command -v curl >/dev/null 2>&1; then
    if ! curl -fsS --max-time 3 "$STORAGE_ENDPOINT" >/dev/null 2>&1; then
        echo "Warning: RustFS/S3 endpoint is not reachable: $STORAGE_ENDPOINT. File upload QA cases require docker compose to start rustfs." >&2
    fi
else
    echo "Warning: curl not found; cannot check RustFS/S3 endpoint $STORAGE_ENDPOINT." >&2
fi

cd "$BACKEND_DIR"
mvn spring-boot:run

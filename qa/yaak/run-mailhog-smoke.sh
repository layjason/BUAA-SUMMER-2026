#!/usr/bin/env bash
set -euo pipefail

# 默认参数
WORKSPACE_NAME="${1:-MayoiStar Identity and Merchant Qualification}"
MAILHOG_API_BASE="${2:-http://127.0.0.1:8025}"
BASE_URL="${3:-http://localhost:8080}"
PASSWORD="${4:-Password123!}"
MAIL_TIMEOUT_SECONDS="${5:-30}"
SKIP_MERCHANT=false
if [[ "${6:-}" == "--skip-merchant" ]]; then
    SKIP_MERCHANT=true
fi

if ! command -v jq &>/dev/null; then
    echo "Error: jq is required but not found." >&2
    exit 1
fi

if ! command -v yaak &>/dev/null; then
    echo "Error: yaak CLI not found." >&2
    exit 1
fi

# 调用 yaak CLI 并返回输出行
invoke_yaak_lines() {
    local output
    output="$(yaak "$@" 2>&1)" || {
        local exit_code=$?
        echo "yaak $* failed with exit code $exit_code." >&2
        echo "$output" >&2
        exit $exit_code
    }
    echo "$output" | grep -v '^$' || true
}

# 根据名称从 yaak 列表中获取 ID
# 前置条件：lines 为 yaak list 输出，每行格式为 "<id> - <name>"
get_yaak_id_by_name() {
    local lines="$1"
    local name="$2"
    local line
    line="$(echo "$lines" | grep -E "^[^[:space:]]+[[:space:]]+-[[:space:]].*${name}$" | head -1)"
    if [[ -z "$line" ]]; then
        echo "Cannot find Yaak item named '$name'." >&2
        exit 1
    fi
    echo "$line" | awk '{print $1}'
}

# 获取工作区 ID
get_yaak_workspace_id() {
    local name="$1"
    local workspaces
    workspaces="$(invoke_yaak_lines workspace list)"
    get_yaak_id_by_name "$workspaces" "$name"
}

# 获取第一个环境 ID
get_yaak_environment_id() {
    local workspace_id="$1"
    local environments
    environments="$(invoke_yaak_lines environment list "$workspace_id")"
    local line
    line="$(echo "$environments" | head -1)"
    if [[ -z "$line" ]]; then
        echo "No Yaak environment found in workspace $workspace_id." >&2
        exit 1
    fi
    echo "$line" | awk '{print $1}'
}

# 获取请求 ID
get_yaak_request_id() {
    local workspace_id="$1"
    local name="$2"
    local requests
    requests="$(invoke_yaak_lines request list "$workspace_id")"
    get_yaak_id_by_name "$requests" "$name"
}

# 设置 yaak 环境变量
# 前置条件：environment_id 为有效的环境 ID
# 后置条件：指定环境的变量已合并更新
set_yaak_environment_variables() {
    local environment_id="$1"
    shift

    local env_json
    env_json="$(invoke_yaak_lines environment show "$environment_id" | tr -d '\n')"

    # 构建待合并的变量 JSON 对象
    local new_vars="{}"
    local pair
    for pair in "$@"; do
        local key="${pair%%=*}"
        local value="${pair#*=}"
        new_vars="$(echo "$new_vars" | jq --arg k "$key" --arg v "$value" '.[$k] = $v')"
    done

    # 读取现有变量并合并
    local merged
    merged="$(echo "$env_json" | jq --argjson new "$new_vars" \
        '[.variables[] | {(.name): (.value // "")}] | add // {} | . * $new')"

    # 转换为 yaak 环境变量数组格式
    local updated_variables
    updated_variables="$(echo "$merged" | jq 'to_entries | sort_by(.key) | map({name: .key, value: (.value | tostring), enabled: true})')"

    local payload
    payload="$(echo "$env_json" | jq --argjson vars "$updated_variables" \
        '{id: .id, workspaceId: .workspaceId, name: .name, variables: $vars}' -c)"

    invoke_yaak_lines environment update "--json=$payload" >/dev/null
}

# 发送 yaak 请求
send_yaak_request() {
    local workspace_id="$1"
    local environment_id="$2"
    local name="$3"
    local request_id
    request_id="$(get_yaak_request_id "$workspace_id" "$name")"
    echo "Sending: $name"
    invoke_yaak_lines request send "$request_id" -e "$environment_id" >/dev/null
}

# 从 MailHog API 获取邮件中的 token
# 返回值：邮件正文中 token 参数的值
get_mailhog_token() {
    local recipient="$1"
    local token_purpose="$2"
    local deadline
    deadline="$(($(date +%s) + MAIL_TIMEOUT_SECONDS))"

    while [[ "$(date +%s)" -lt "$deadline" ]]; do
        local response
        response="$(curl -s "$MAILHOG_API_BASE/api/v2/messages")"

        local count
        count="$(echo "$response" | jq -r '.items | length // 0')"
        if [[ "$count" -eq 0 ]]; then
            sleep 1
            continue
        fi

        local i
        for i in $(seq 0 $((count - 1))); do
            local body
            body="$(echo "$response" | jq -r ".items[$i].Content.Body // empty")"
            local to_header
            to_header="$(echo "$response" | jq -r ".items[$i].Content.Headers.To // [] | join(\",\")")"

            if [[ "$to_header" != *"$recipient"* && "$body" != *"$recipient"* ]]; then
                continue
            fi

            local token
            token="$(echo "$body" | grep -oP 'token=\K[^\s"'"'"'&<>]+' | head -1)"
            if [[ -n "$token" ]]; then
                echo "$token"
                return
            fi
        done

        sleep 1
    done

    echo "Cannot find $token_purpose token email for $recipient in MailHog." >&2
    exit 1
}

STAMP="$(date +%Y%m%d%H%M%S)"
PERSONAL_EMAIL="personal.yaak.${STAMP}@example.com"
MERCHANT_EMAIL="merchant.yaak.${STAMP}@example.com"
PERSONAL_NICKNAME="yaak-personal-${STAMP}"
MERCHANT_NICKNAME="yaak-merchant-${STAMP}"

WORKSPACE_ID="$(get_yaak_workspace_id "$WORKSPACE_NAME")"
ENVIRONMENT_ID="$(get_yaak_environment_id "$WORKSPACE_ID")"

# 初始化环境变量
set_yaak_environment_variables "$ENVIRONMENT_ID" \
    "baseUrl=$BASE_URL" \
    "personalEmail=$PERSONAL_EMAIL" \
    "personalPassword=$PASSWORD" \
    "personalNewPassword=Password456!" \
    "personalNickname=$PERSONAL_NICKNAME" \
    "personalNewNickname=${PERSONAL_NICKNAME}-new" \
    "merchantEmail=$MERCHANT_EMAIL" \
    "merchantPassword=$PASSWORD" \
    "merchantNickname=$MERCHANT_NICKNAME" \
    "merchantNewNickname=${MERCHANT_NICKNAME}-new" \
    "merchantName=MayoiStar Test Merchant"

send_yaak_request "$WORKSPACE_ID" "$ENVIRONMENT_ID" "00.01 Get interest tags"

send_yaak_request "$WORKSPACE_ID" "$ENVIRONMENT_ID" "01.01 Register personal"
ACTIVATION_TOKEN="$(get_mailhog_token "$PERSONAL_EMAIL" "personal activation")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "activationToken=$ACTIVATION_TOKEN"
send_yaak_request "$WORKSPACE_ID" "$ENVIRONMENT_ID" "01.03 Activate personal account"
send_yaak_request "$WORKSPACE_ID" "$ENVIRONMENT_ID" "01.04 Login personal"

if [[ "$SKIP_MERCHANT" != true ]]; then
    send_yaak_request "$WORKSPACE_ID" "$ENVIRONMENT_ID" "02.01 Register merchant"
    MERCHANT_ACTIVATION_TOKEN="$(get_mailhog_token "$MERCHANT_EMAIL" "merchant activation")"
    set_yaak_environment_variables "$ENVIRONMENT_ID" "merchantActivationToken=$MERCHANT_ACTIVATION_TOKEN"
    send_yaak_request "$WORKSPACE_ID" "$ENVIRONMENT_ID" "02.02 Activate merchant account"
    send_yaak_request "$WORKSPACE_ID" "$ENVIRONMENT_ID" "02.03 Login merchant"
fi

echo "MailHog + Yaak smoke flow completed."

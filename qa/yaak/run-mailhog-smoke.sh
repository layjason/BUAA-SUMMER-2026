#!/usr/bin/env bash
set -euo pipefail

# 默认参数
WORKSPACE_NAME="${1:-MayoiStar Identity and Merchant Qualification}"
MAILHOG_API_BASE="${2:-http://127.0.0.1:8025}"
BASE_URL="${3:-http://localhost:8080}"
PASSWORD="${4:-Password123!}"
MAIL_TIMEOUT_SECONDS="${5:-30}"

if ! command -v jq &>/dev/null; then
    echo "Error: jq is required but not found." >&2
    exit 1
fi

if ! command -v yaak &>/dev/null; then
    echo "Error: yaak CLI not found." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

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

invoke_yaak_string() {
    invoke_yaak_lines "$@" | tr -d '\n'
}

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

get_yaak_workspace_id() {
    local name="$1"
    local workspaces
    workspaces="$(invoke_yaak_lines workspace list)"
    get_yaak_id_by_name "$workspaces" "$name"
}

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

get_yaak_request_id() {
    local workspace_id="$1"
    local name="$2"
    local requests
    requests="$(invoke_yaak_lines request list "$workspace_id")"
    get_yaak_id_by_name "$requests" "$name"
}

set_yaak_environment_variables() {
    local environment_id="$1"
    shift

    local env_json
    env_json="$(invoke_yaak_string environment show "$environment_id")"

    local new_vars="{}"
    local pair
    for pair in "$@"; do
        local key="${pair%%=*}"
        local value="${pair#*=}"
        new_vars="$(echo "$new_vars" | jq --arg k "$key" --arg v "$value" '.[$k] = $v')"
    done

    local merged
    merged="$(echo "$env_json" | jq --argjson new "$new_vars" \
        '[.variables[] | {(.name): (.value // "")}] | add // {} | . * $new')"

    local updated_variables
    updated_variables="$(echo "$merged" | jq 'to_entries | sort_by(.key) | map({name: .key, value: (.value | tostring), enabled: true})')"

    local payload
    payload="$(echo "$env_json" | jq --argjson vars "$updated_variables" \
        '{id: .id, workspaceId: .workspaceId, name: .name, variables: $vars}' -c)"

    invoke_yaak_lines environment update "--json=$payload" >/dev/null
}

# 发送请求并显示请求/响应详情，返回解析后的响应 JSON
send_yaak_request_json() {
    local name="$1"
    local request_id
    request_id="$(get_yaak_request_id "$WORKSPACE_ID" "$name")"

    # 获取请求详情
    local show_json
    show_json="$(invoke_yaak_string request show "$request_id")"

    local method url body_text body_type
    method="$(echo "$show_json" | jq -r '.method')"
    url="$(echo "$show_json" | jq -r '.url')"
    body_text="$(echo "$show_json" | jq -r '.body.text // empty')"
    body_type="$(echo "$show_json" | jq -r '.bodyType // empty')"

    echo ""
    printf '%0.s═' $(seq 1 60); echo
    echo "  $name"
    printf '%0.s─' $(seq 1 60); echo
    echo "  $method $url"

    # 请求头
    echo "$show_json" | jq -r '.headers[]? | select(.enabled) | "  > \(.name): \(.value)"'

    # 请求体
    if [[ -n "$body_text" ]]; then
        echo "  Body:"
        echo "  $body_text"
    elif [[ "$body_type" == "multipart/form-data" ]]; then
        echo "  Body (form):"
        echo "$show_json" | jq -r '.body.form[]? | select(.enabled) | "    \(.name) = \(if .file then "[file: \(.file)]" else .value end)"'
    else
        echo "  Body: (none)"
    fi

    # 发送请求（verbose 模式）
    local raw_output
    raw_output="$(invoke_yaak_string request send "$request_id" -e "$ENVIRONMENT_ID" -v)"

    # 提取响应状态和正文
    local status_line
    status_line="$(echo "$raw_output" | grep '^< HTTP/' | head -1 | sed 's/^< HTTP\/[0-9.]* //')"

    # 响应正文是 verbose 输出中不以 *, <, > 开头的最后一段
    local resp_body
    resp_body="$(echo "$raw_output" | grep -v '^[*<>]' | grep -v '^$' | tail -1)"

    printf '%0.s─' $(seq 1 60); echo
    echo "  ← $status_line"
    echo "  $resp_body"

    echo "$resp_body" | jq -c '.'
}

# 发送请求（不需要提取响应字段的错误场景）
send_yaak_request() {
    local name="$1"
    send_yaak_request_json "$name" >/dev/null
}

# 从 MailHog API 获取邮件中的 token
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
                echo "  [MailHog] 获取到 $token_purpose token"
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
PERSONAL_EMAIL="yaak-p.${STAMP}@example.com"
MERCHANT_EMAIL="yaak-m.${STAMP}@example.com"
PERSONAL_NICKNAME="yaak-p-${STAMP}"
MERCHANT_NICKNAME="yaak-m-${STAMP}"

WORKSPACE_ID="$(get_yaak_workspace_id "$WORKSPACE_NAME")"
ENVIRONMENT_ID="$(get_yaak_environment_id "$WORKSPACE_ID")"

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
    "merchantName=MayoiStar Test Merchant" \
    "adminUsername=testadminyaak" \
    "adminPassword=AdminPass123!" \
    "avatarFile=${SCRIPT_DIR}/test-avatar.png" \
    "licenseFile=${SCRIPT_DIR}/test-license.png" \
    "activationToken=" \
    "merchantActivationToken=" \
    "resetToken=" \
    "avatarMediaId=" \
    "licenseMediaId=" \
    "personalAccessToken=" \
    "personalRefreshToken=" \
    "personalUserId=" \
    "merchantAccessToken=" \
    "merchantRefreshToken=" \
    "merchantUserId=" \
    "adminAccessToken=" \
    "adminRefreshToken=" \
    "adminUserId="

printf '\n%0.s#' $(seq 1 60); echo
echo "#  00 公共接口"
printf '%0.s#' $(seq 1 60); echo

send_yaak_request "00.01 Get interest tags"
send_yaak_request "00.02 Check nickname available"

printf '\n%0.s#' $(seq 1 60); echo
echo "#  01 个人用户认证与资料"
printf '%0.s#' $(seq 1 60); echo

send_yaak_request "01.01 Register personal"
send_yaak_request "01.02 Login personal before activation should fail"

ACTIVATION_TOKEN="$(get_mailhog_token "$PERSONAL_EMAIL" "personal activation")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "activationToken=$ACTIVATION_TOKEN"
send_yaak_request "01.03 Activate personal account"

RESP="$(send_yaak_request_json "01.04 Login personal")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "personalAccessToken=$(echo "$RESP" | jq -r '.data.tokens.accessToken')" \
        "personalRefreshToken=$(echo "$RESP" | jq -r '.data.tokens.refreshToken')" \
        "personalUserId=$(echo "$RESP" | jq -r '.data.userId')"
    echo "  [env] personalAccessToken 已保存"
fi

send_yaak_request "01.05 Duplicate personal email should fail"
send_yaak_request "01.06 Profile without token should fail"
send_yaak_request "01.07 Get personal profile"
send_yaak_request "01.08 Update personal profile"

RESP="$(send_yaak_request_json "01.09 Upload avatar")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "avatarMediaId=$(echo "$RESP" | jq -r '.data.mediaId')"
    echo "  [env] avatarMediaId 已保存"
    send_yaak_request "01.10 Attach avatar to profile"
fi

RESP="$(send_yaak_request_json "01.11 Refresh personal token")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "personalAccessToken=$(echo "$RESP" | jq -r '.data.accessToken')" \
        "personalRefreshToken=$(echo "$RESP" | jq -r '.data.refreshToken')"
    echo "  [env] tokens 已刷新"
fi

send_yaak_request "01.12 Change password with wrong old password"
send_yaak_request "01.13 Logout personal"
send_yaak_request "01.14 Refresh after logout should fail"

printf '\n%0.s#' $(seq 1 60); echo
echo "#  02 商家用户认证与资质"
printf '%0.s#' $(seq 1 60); echo

send_yaak_request "02.01 Register merchant"

MERCHANT_ACTIVATION_TOKEN="$(get_mailhog_token "$MERCHANT_EMAIL" "merchant activation")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "merchantActivationToken=$MERCHANT_ACTIVATION_TOKEN"
send_yaak_request "02.02 Activate merchant account"

RESP="$(send_yaak_request_json "02.03 Login merchant")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "merchantAccessToken=$(echo "$RESP" | jq -r '.data.tokens.accessToken')" \
        "merchantRefreshToken=$(echo "$RESP" | jq -r '.data.tokens.refreshToken')" \
        "merchantUserId=$(echo "$RESP" | jq -r '.data.userId')"
    echo "  [env] merchantAccessToken 已保存"
fi

send_yaak_request "02.04 Get merchant profile"
send_yaak_request "02.05 Update merchant profile"

RESP="$(send_yaak_request_json "02.06 Upload merchant license")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "licenseMediaId=$(echo "$RESP" | jq -r '.data.mediaId')"
    echo "  [env] licenseMediaId 已保存"
    send_yaak_request "02.07 Submit merchant qualification"
    send_yaak_request "02.08 Get merchant profile after qualification"
fi

send_yaak_request "02.09 Personal token cannot access merchant profile"

printf '\n%0.s#' $(seq 1 60); echo
echo "#  03 密码重置与安全"
printf '%0.s#' $(seq 1 60); echo

send_yaak_request "03.01 Resend activation email"
send_yaak_request "03.02 Send password reset email"

RESET_TOKEN="$(get_mailhog_token "$PERSONAL_EMAIL" "password reset")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "resetToken=$RESET_TOKEN"
send_yaak_request "03.03 Reset password with token"
send_yaak_request "03.04 Wrong password attempt"

printf '\n%0.s#' $(seq 1 60); echo
echo "#  04 管理员操作"
printf '%0.s#' $(seq 1 60); echo

RESP="$(send_yaak_request_json "04.01 Admin login")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "adminAccessToken=$(echo "$RESP" | jq -r '.data.tokens.accessToken')"
    echo "  [env] adminAccessToken 已保存"
    send_yaak_request "04.02 Admin get merchant profile placeholder"
    send_yaak_request "04.03 Admin review merchant placeholder"
else
    echo "  ⚠ 管理员登录失败 (code=$(echo "$RESP" | jq -r '.code'))，请确认 V2 迁移已执行。"
fi

printf '\n%0.s#' $(seq 1 60); echo
echo "#  全量测试完成"
printf '%0.s#' $(seq 1 60); echo

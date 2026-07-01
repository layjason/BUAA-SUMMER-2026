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

# 设置 yaak 环境变量（合并模式）
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

# 发送请求并返回解析后的 JSON 对象
send_yaak_request_json() {
    local name="$1"
    local request_id
    request_id="$(get_yaak_request_id "$WORKSPACE_ID" "$name")"
    echo "Sending: $name"
    local output
    output="$(invoke_yaak_lines request send "$request_id" -e "$ENVIRONMENT_ID")"
    echo "$output" | jq -c '.'
}

# 发送请求，不解析响应
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

echo "========== 00 公共接口 =========="
send_yaak_request "00.01 Get interest tags"
send_yaak_request "00.02 Check nickname available"

echo ""
echo "========== 01 个人用户认证与资料 =========="
send_yaak_request "01.01 Register personal"

# 激活前尝试登录，应失败
send_yaak_request "01.02 Login personal before activation should fail"

ACTIVATION_TOKEN="$(get_mailhog_token "$PERSONAL_EMAIL" "personal activation")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "activationToken=$ACTIVATION_TOKEN"
send_yaak_request "01.03 Activate personal account"

# 登录并提取 token
RESP="$(send_yaak_request_json "01.04 Login personal")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "personalAccessToken=$(echo "$RESP" | jq -r '.data.tokens.accessToken')" \
        "personalRefreshToken=$(echo "$RESP" | jq -r '.data.tokens.refreshToken')" \
        "personalUserId=$(echo "$RESP" | jq -r '.data.userId')"
    echo "  -> personalAccessToken saved"
fi

# 重复注册应失败
send_yaak_request "01.05 Duplicate personal email should fail"

# 无 Token 访问应失败
send_yaak_request "01.06 Profile without token should fail"

send_yaak_request "01.07 Get personal profile"
send_yaak_request "01.08 Update personal profile"

# 上传头像并提取 mediaId
RESP="$(send_yaak_request_json "01.09 Upload avatar")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "avatarMediaId=$(echo "$RESP" | jq -r '.data.mediaId')"
    echo "  -> avatarMediaId saved"
    send_yaak_request "01.10 Attach avatar to profile"
fi

# 刷新 token 并更新
RESP="$(send_yaak_request_json "01.11 Refresh personal token")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "personalAccessToken=$(echo "$RESP" | jq -r '.data.accessToken')" \
        "personalRefreshToken=$(echo "$RESP" | jq -r '.data.refreshToken')"
    echo "  -> tokens refreshed"
fi

send_yaak_request "01.12 Change password with wrong old password"
send_yaak_request "01.13 Logout personal"

# 登出后刷新应失败
send_yaak_request "01.14 Refresh after logout should fail"

echo ""
echo "========== 02 商家用户认证与资质 =========="
send_yaak_request "02.01 Register merchant"

MERCHANT_ACTIVATION_TOKEN="$(get_mailhog_token "$MERCHANT_EMAIL" "merchant activation")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "merchantActivationToken=$MERCHANT_ACTIVATION_TOKEN"
send_yaak_request "02.02 Activate merchant account"

# 登录并提取商家 token
RESP="$(send_yaak_request_json "02.03 Login merchant")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "merchantAccessToken=$(echo "$RESP" | jq -r '.data.tokens.accessToken')" \
        "merchantRefreshToken=$(echo "$RESP" | jq -r '.data.tokens.refreshToken')" \
        "merchantUserId=$(echo "$RESP" | jq -r '.data.userId')"
    echo "  -> merchantAccessToken saved"
fi

send_yaak_request "02.04 Get merchant profile"
send_yaak_request "02.05 Update merchant profile"

# 上传执照并提取 mediaId
RESP="$(send_yaak_request_json "02.06 Upload merchant license")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "licenseMediaId=$(echo "$RESP" | jq -r '.data.mediaId')"
    echo "  -> licenseMediaId saved"
    send_yaak_request "02.07 Submit merchant qualification"
    send_yaak_request "02.08 Get merchant profile after qualification"
fi

# 个人 token 访问商家资料应被拒
send_yaak_request "02.09 Personal token cannot access merchant profile"

echo ""
echo "========== 03 密码重置与安全 =========="
send_yaak_request "03.01 Resend activation email"
send_yaak_request "03.02 Send password reset email"

RESET_TOKEN="$(get_mailhog_token "$PERSONAL_EMAIL" "password reset")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "resetToken=$RESET_TOKEN"
echo "  -> resetToken saved"

send_yaak_request "03.03 Reset password with token"
send_yaak_request "03.04 Wrong password attempt"

echo ""
echo "========== 04 管理员操作 =========="
RESP="$(send_yaak_request_json "04.01 Admin login")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "adminAccessToken=$(echo "$RESP" | jq -r '.data.tokens.accessToken')"
    echo "  -> adminAccessToken saved"
    send_yaak_request "04.02 Admin get merchant profile placeholder"
    send_yaak_request "04.03 Admin review merchant placeholder"
else
    echo "  WARNING: Admin login failed (code=$(echo "$RESP" | jq -r '.code')). 请确认 DevDataInitializer 已创建管理员。"
fi

echo ""
echo "========== 全量测试完成 =========="

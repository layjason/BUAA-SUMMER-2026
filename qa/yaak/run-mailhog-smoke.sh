#!/usr/bin/env bash
set -euo pipefail

# 默认参数
WORKSPACE_NAME="${1:-MayoiStar Identity and Merchant Qualification}"
MAILHOG_API_BASE="${2:-http://127.0.0.1:8025}"
BASE_URL="${3:-http://localhost:8080}"
PASSWORD="${4:-Password123!}"
MAIL_TIMEOUT_SECONDS="${5:-30}"

GREEN='\033[32m'
RED='\033[31m'
YELLOW='\033[33m'
RESET='\033[0m'
BOLD='\033[1m'

# 测试结果记录: "name|expected|actual|passed"
TEST_RESULTS_FILE="$(mktemp)"

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
    invoke_yaak_lines "$@"
}

# 从 yaak verbose 输出中提取 JSON 响应体
get_response_json_from_yaak_output() {
    python3 -c "
import sys, re
text = sys.stdin.read()
text = re.sub(r'\x1b\[[0-9;]*m', '', text)
lines = text.split('\n')
normalized = []
for line in lines:
    trimmed = line.strip()
    if trimmed.startswith('* '):
        continue
    if trimmed.startswith('< '):
        trimmed = trimmed[2:].strip()
    normalized.append(trimmed)
text = '\n'.join(normalized)
start = text.rfind('{\"code\"')
if start < 0:
    sys.stdout.write('')
    sys.exit(0)
depth = 0
in_string = False
escaped = False
for i in range(start, len(text)):
    c = text[i]
    if escaped:
        escaped = False
        continue
    if c == '\\\\':
        escaped = True
        continue
    if c == '\"':
        in_string = not in_string
        continue
    if in_string:
        continue
    if c == '{':
        depth += 1
    if c == '}':
        depth -= 1
        if depth == 0:
            sys.stdout.write(text[start:i+1])
            sys.exit(0)
sys.stdout.write('')
" <<< "$1"
}

# 从 yaak verbose 输出中提取 HTTP 状态码
get_http_status_code_from_yaak_output() {
    local raw_output="$1" line
    while IFS= read -r line; do
        if [[ "$line" =~ ^\<[[:space:]]*HTTP/[0-9.]+[[:space:]]+([0-9]{3}) ]]; then
            local status="${BASH_REMATCH[1]}"
            local reason="${line#*${status} }"
            local code_line="${status}${reason}"
            code_line="$(echo "$code_line" | xargs)"
            printf '%d\t%s\n' "$status" "$code_line"
            return
        fi
    done <<< "$raw_output"
    printf 'null\t\n'
}

get_yaak_id_by_name() {
    local lines="$1" name="$2" line
    line="$(echo "$lines" | grep -E "^[^[:space:]]+[[:space:]]+-[[:space:]].*${name}$" | head -1)"
    if [[ -z "$line" ]]; then
        echo "Cannot find Yaak item named '$name'." >&2
        exit 1
    fi
    echo "$line" | awk '{print $1}'
}

get_yaak_workspace_id() {
    local workspaces
    workspaces="$(invoke_yaak_lines workspace list)"
    get_yaak_id_by_name "$workspaces" "$1"
}

get_yaak_environment_id() {
    local environments line
    environments="$(invoke_yaak_lines environment list "$1")"
    line="$(echo "$environments" | head -1)"
    if [[ -z "$line" ]]; then
        echo "No Yaak environment found in workspace $1." >&2
        exit 1
    fi
    echo "$line" | awk '{print $1}'
}

get_yaak_request_id() {
    local requests
    requests="$(invoke_yaak_lines request list "$1")"
    get_yaak_id_by_name "$requests" "$2"
}

set_yaak_environment_variables() {
    local environment_id="$1" env_json new_vars="{}" pair key value merged updated_variables payload
    shift
    env_json="$(invoke_yaak_string environment show "$environment_id")"
    for pair in "$@"; do
        key="${pair%%=*}"
        value="${pair#*=}"
        new_vars="$(echo "$new_vars" | jq --arg k "$key" --arg v "$value" '.[$k] = $v')"
    done
    merged="$(echo "$env_json" | jq --argjson new "$new_vars" \
        '[.variables[] | {(.name): (.value // "")}] | add // {} | . * $new')"
    updated_variables="$(echo "$merged" | jq 'to_entries | sort_by(.key) | map({name: .key, value: (.value | tostring), enabled: true})')"
    payload="$(echo "$env_json" | jq --argjson vars "$updated_variables" \
        '{id: .id, workspaceId: .workspaceId, name: .name, variables: $vars}' -c)"
    invoke_yaak_lines environment update "--json=$payload" >/dev/null
}

# 记录测试结果
record_result() {
    local name="$1" expected="$2" actual="$3"
    local passed="false"
    IFS=',' read -ra expected_arr <<< "$expected"
    for e in "${expected_arr[@]}"; do
        if [[ "$e" == "$actual" ]]; then
            passed="true"
            break
        fi
    done
    echo "${name}|${expected}|${actual}|${passed}" >> "$TEST_RESULTS_FILE"
}

# 解码 MailHog 邮件正文（Quoted-Printable + HTML）
decode_mailhog_body() {
    local body="$1"
    python3 -c "
import sys, html, re
text = sys.stdin.read()
text = re.sub(r'=\r?\n', '', text)
text = re.sub(r'=([0-9A-Fa-f]{2})', lambda m: chr(int(m.group(1), 16)), text)
sys.stdout.write(html.unescape(text))
" <<< "$body"
}

# 发送请求，显示详情，比对预期 code，返回响应 JSON
# 前置条件：WORKSPACE_ID、ENVIRONMENT_ID 已设置
send_yaak_request_json() {
    local name="$1"
    local expected="${2:-200}"
    local request_id show_json method url body_text body_type raw_output http_status resp_body resp actual_code passed expected_str

    request_id="$(get_yaak_request_id "$WORKSPACE_ID" "$name")"
    show_json="$(invoke_yaak_string request show "$request_id")"

    method="$(echo "$show_json" | jq -r '.method')"
    url="$(echo "$show_json" | jq -r '.url')"
    body_text="$(echo "$show_json" | jq -r '.body.text // empty')"
    body_type="$(echo "$show_json" | jq -r '.bodyType // empty')"

    echo ""
    printf '%0.s═' $(seq 1 60); echo
    echo "  $name"
    printf '%0.s─' $(seq 1 60); echo
    echo "  $method $url"

    echo "$show_json" | jq -r '.headers[]? | select(.enabled) | "  > \(.name): \(.value)"'

    if [[ -n "$body_text" ]]; then
        echo "  Body:"
        echo "  $body_text"
    elif [[ "$body_type" == "multipart/form-data" ]]; then
        echo "  Body (form):"
        echo "$show_json" | jq -r '.body.form[]? | select(.enabled) | "    \(.name) = \(if .file then "[file: \(.file)]" else .value end)"'
    else
        echo "  Body: (none)"
    fi

    raw_output="$(invoke_yaak_string request send "$request_id" -e "$ENVIRONMENT_ID" -v)"
    http_status="$(get_http_status_code_from_yaak_output "$raw_output")"
    resp_body="$(get_response_json_from_yaak_output "$raw_output")"

    printf '%0.s─' $(seq 1 60); echo
    local status_code="$(echo "$http_status" | cut -f1)"
    local status_line="$(echo "$http_status" | cut -f2)"
    echo "  ← $status_line"
    echo "  $resp_body"

    # 解析响应并判断测试结果
    if [[ -z "$resp_body" ]]; then
        resp="{\"code\": $status_code, \"message\": \"\", \"data\": {}}"
    else
        resp="$resp_body"
    fi
    actual_code="$(echo "$resp" | jq -r '.code')"

    record_result "$name" "$expected" "$actual_code"

    passed="false"
    IFS=',' read -ra expected_arr <<< "$expected"
    for e in "${expected_arr[@]}"; do
        if [[ "$e" == "$actual_code" ]]; then
            passed="true"
            break
        fi
    done

    if [[ "$passed" == "true" ]]; then
        echo -e "  ${GREEN}✓ PASS${RESET} (code=$actual_code)"
    else
        expected_str="$expected"
        echo -e "  ${RED}✗ FAIL${RESET} (expected=$expected_str, actual=$actual_code, message=$(echo "$resp" | jq -r '.message'))"
    fi

    echo "$resp"
}

# 发送请求（不需要提取响应字段的错误场景）
send_yaak_request() {
    local name="$1"
    local expected="${2:-200}"
    send_yaak_request_json "$name" "$expected" >/dev/null
}

# 输出跳过信息
write_skip() {
    local reason="$1"
    echo -e "  ${YELLOW}[skip]${RESET} $reason"
}

# 从 MailHog 获取邮件中的 token
get_mailhog_token() {
    local recipient="$1" token_purpose="$2" deadline response messages count i body to_header token
    deadline="$(($(date +%s) + MAIL_TIMEOUT_SECONDS))"
    while [[ "$(date +%s)" -lt "$deadline" ]]; do
        response="$(curl -s "$MAILHOG_API_BASE/api/v2/messages")"
        messages="$(echo "$response" | jq -c '.items | sort_by(.Created) | reverse | .[]')"
        while IFS= read -r message; do
            [[ -z "$message" ]] && continue
            body="$(decode_mailhog_body "$(echo "$message" | jq -r '.Content.Body // empty')")"
            to_header="$(echo "$message" | jq -r '.Content.Headers.To // [] | join(",")')"
            if [[ "$to_header" != *"$recipient"* && "$body" != *"$recipient"* ]]; then continue; fi
            token="$(echo "$body" | grep -oP '[?&]token=\K[^\s"'"'"'&<>]+' | head -1)"
            if [[ -n "$token" ]]; then
                echo "  [MailHog] 获取到 $token_purpose token"
                echo "$token"
                return
            fi
        done <<< "$messages"
        sleep 1
    done
    echo "Cannot find $token_purpose token email for $recipient in MailHog." >&2
    exit 1
}

# 打印测试汇总
print_summary() {
    local total passed failed
    total="$(wc -l < "$TEST_RESULTS_FILE")"
    passed="$(grep -c '|true$' "$TEST_RESULTS_FILE" || echo 0)"
    failed="$(grep -c '|false$' "$TEST_RESULTS_FILE" || echo 0)"

    printf '\n%0.s#' $(seq 1 60); echo
    echo "#  测试结果汇总"
    printf '%0.s#' $(seq 1 60); echo
    echo ""
    echo -e "  通过: ${GREEN}${passed}${RESET} / ${total}"
    if [[ "$failed" -gt 0 ]]; then
        echo -e "  失败: ${RED}${failed}${RESET}"
        echo ""
        echo "  失败的测试:"
        while IFS='|' read -r name expected actual passed_flag; do
            if [[ "$passed_flag" == "false" ]]; then
                echo -e "    ${RED}✗ ${name}${RESET} (expected=${expected}, actual=${actual})"
            fi
        done < "$TEST_RESULTS_FILE"
    fi
    if [[ "$passed" -eq "$total" ]]; then
        echo -e "  ${GREEN}全部通过!${RESET}"
    fi
    rm -f "$TEST_RESULTS_FILE"
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
    "activationToken=" "merchantActivationToken=" "resetToken=" \
    "avatarMediaId=" "licenseMediaId=" \
    "personalAccessToken=" "personalRefreshToken=" "personalUserId=" \
    "merchantAccessToken=" "merchantRefreshToken=" "merchantUserId=" \
    "adminAccessToken=" "adminRefreshToken=" "adminUserId="

printf '\n%0.s#' $(seq 1 60); echo
echo "#  00 公共接口"
printf '%0.s#' $(seq 1 60); echo

send_yaak_request "00.01 Get interest tags"
send_yaak_request "00.02 Check nickname available"

printf '\n%0.s#' $(seq 1 60); echo
echo "#  01 个人用户认证与资料"
printf '%0.s#' $(seq 1 60); echo

send_yaak_request "01.01 Register personal"
send_yaak_request "01.02 Login personal before activation should fail" "10004"

ACTIVATION_TOKEN="$(get_mailhog_token "$PERSONAL_EMAIL" "personal activation")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "activationToken=$ACTIVATION_TOKEN"
send_yaak_request "01.03 Activate personal account"

RESP="$(send_yaak_request_json "01.04 Login personal")"
PERSONAL_LOGIN_READY=false
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "personalAccessToken=$(echo "$RESP" | jq -r '.data.tokens.accessToken')" \
        "personalRefreshToken=$(echo "$RESP" | jq -r '.data.tokens.refreshToken')" \
        "personalUserId=$(echo "$RESP" | jq -r '.data.userId')"
    PERSONAL_LOGIN_READY=true
    echo "  [env] personalAccessToken 已保存"
fi

send_yaak_request "01.05 Duplicate personal email should fail" "10001"
send_yaak_request "01.06 Profile without token should fail" "403"

if [[ "$PERSONAL_LOGIN_READY" == "true" ]]; then
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

    send_yaak_request "01.12 Change password with wrong old password" "10016"
    send_yaak_request "01.13 Logout personal"
    send_yaak_request "01.14 Refresh after logout should fail" "10007"
else
    write_skip "个人用户未成功登录，跳过依赖 personalAccessToken 的用例。"
fi

printf '\n%0.s#' $(seq 1 60); echo
echo "#  02 商家用户认证与资质"
printf '%0.s#' $(seq 1 60); echo

send_yaak_request "02.01 Register merchant"
MERCHANT_ACTIVATION_TOKEN="$(get_mailhog_token "$MERCHANT_EMAIL" "merchant activation")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "merchantActivationToken=$MERCHANT_ACTIVATION_TOKEN"
send_yaak_request "02.02 Activate merchant account"

RESP="$(send_yaak_request_json "02.03 Login merchant")"
MERCHANT_LOGIN_READY=false
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "merchantAccessToken=$(echo "$RESP" | jq -r '.data.tokens.accessToken')" \
        "merchantRefreshToken=$(echo "$RESP" | jq -r '.data.tokens.refreshToken')" \
        "merchantUserId=$(echo "$RESP" | jq -r '.data.userId')"
    MERCHANT_LOGIN_READY=true
    echo "  [env] merchantAccessToken 已保存"
fi

if [[ "$MERCHANT_LOGIN_READY" == "true" ]]; then
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
else
    write_skip "商家用户未成功登录，跳过依赖 merchantAccessToken 的用例。"
fi

if [[ "$PERSONAL_LOGIN_READY" == "true" ]]; then
    send_yaak_request "02.09 Personal token cannot access merchant profile" "403"
else
    write_skip "个人用户未成功登录，跳过个人 token 访问商家接口的权限用例。"
fi

printf '\n%0.s#' $(seq 1 60); echo
echo "#  03 密码重置与安全"
printf '%0.s#' $(seq 1 60); echo

send_yaak_request "03.01 Resend activation email" "200,10012,10015"
send_yaak_request "03.02 Send password reset email"

RESET_TOKEN="$(get_mailhog_token "$PERSONAL_EMAIL" "password reset")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "resetToken=$RESET_TOKEN"
send_yaak_request "03.03 Reset password with token"
send_yaak_request "03.04 Wrong password attempt" "10003"

printf '\n%0.s#' $(seq 1 60); echo
echo "#  04 管理员操作"
printf '%0.s#' $(seq 1 60); echo

RESP="$(send_yaak_request_json "04.01 Admin login")"
if [[ "$(echo "$RESP" | jq -r '.code')" == "200" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" \
        "adminAccessToken=$(echo "$RESP" | jq -r '.data.tokens.accessToken')"
    echo "  [env] adminAccessToken 已保存"
    if [[ "$MERCHANT_LOGIN_READY" == "true" ]]; then
        send_yaak_request "04.02 Admin get merchant profile placeholder"
        send_yaak_request "04.03 Admin review merchant placeholder"
    else
        write_skip "商家用户未成功登录，跳过依赖 merchantUserId 的管理员审核用例。"
    fi
else
    echo -e "  ${YELLOW}⚠ 管理员登录失败，请确认 V2 迁移已执行。${RESET}"
fi

print_summary

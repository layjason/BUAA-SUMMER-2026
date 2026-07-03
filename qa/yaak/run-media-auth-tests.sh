#!/usr/bin/env bash
set -euo pipefail

WORKSPACE_NAME="${1:-MayoiStar Media Download Authorization}"
MAILHOG_API_BASE="${2:-http://127.0.0.1:8025}"
BASE_URL="${3:-http://localhost:8080}"
MAIL_TIMEOUT_SECONDS="${4:-30}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_AVATAR_PATH="$SCRIPT_DIR/test-avatar.png"
TEST_LICENSE_PATH="$SCRIPT_DIR/test-license.png"
TEST_RESULTS_FILE="$(mktemp)"
trap 'rm -f "$TEST_RESULTS_FILE"' EXIT

TEST_USER_EMAIL="test_user@mayoistar.qa"
TEST_USER_PASSWORD="4g9Pf6KNpw4rxe3NL7hij9l2"
TEST_PEER_EMAIL="test_peer@mayoistar.qa"
TEST_PEER_PASSWORD="1QL71Nz-b1aYcP5yzcTn4vSu"
ADMIN_USERNAME="admin"
ADMIN_PASSWORD="uMudtQCQ4ZJ9NKOYyYBtdxg5"
TEST_USER_ID="11111111-1111-1111-1111-111111111111"
TEST_PEER_ID="22222222-2222-2222-2222-222222222222"
ADMIN_USER_ID="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

STAMP="$(date '+%Y%m%d%H%M%S')"
MERCHANT_EMAIL="yaak-media-merchant.$STAMP@example.com"
MERCHANT_PASSWORD="Password123!"
MERCHANT_NAME="MayoiStar Media Test Merchant"
MERCHANT_NICKNAME="media-merchant-$STAMP"
OUTSIDER_EMAIL="yaak-media-outsider.$STAMP@example.com"
OUTSIDER_NICKNAME="media-outsider-$STAMP"
TEAM_NAME="媒体鉴权测试小队-$STAMP"

log() {
  printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

skip_case() {
  printf '  [skip] %s\n' "$1"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: $1 is required but not found." >&2
    exit 1
  fi
}

require_command yaak
require_command jq
require_command curl
require_command python3

if [[ ! -f "$TEST_AVATAR_PATH" ]]; then
  echo "测试图片文件未找到: $TEST_AVATAR_PATH" >&2
  exit 1
fi

if [[ ! -f "$TEST_LICENSE_PATH" ]]; then
  echo "测试执照文件未找到: $TEST_LICENSE_PATH" >&2
  exit 1
fi

invoke_yaak_lines() {
  local output
  output="$(yaak "$@" 2>&1)" || {
    local exit_code=$?
    echo "yaak $* failed with exit code $exit_code." >&2
    echo "$output" >&2
    exit "$exit_code"
  }
  printf '%s\n' "$output" | grep -v '^$' || true
}

invoke_yaak_string() {
  invoke_yaak_lines "$@"
}

# 前置条件：输入为 yaak verbose 输出；后置条件：输出最后一个 code 响应 JSON；不变量：未找到 JSON 时输出空字符串。
get_response_json_from_yaak_output() {
  python3 -c '
import re
import sys

text = re.sub(r"\x1b\[[0-9;]*m", "", sys.stdin.read())
normalized = []
for line in text.splitlines():
    trimmed = line.strip()
    if trimmed.startswith("* "):
        continue
    if trimmed.startswith("< "):
        trimmed = trimmed[2:].strip()
    normalized.append(trimmed)
text = "\n".join(normalized)
start = text.rfind("{\"code\"")
if start < 0:
    sys.exit(0)
depth = 0
in_string = False
escaped = False
for index in range(start, len(text)):
    char = text[index]
    if escaped:
        escaped = False
        continue
    if char == "\\":
        escaped = True
        continue
    if char == "\"":
        in_string = not in_string
        continue
    if in_string:
        continue
    if char == "{":
        depth += 1
    if char == "}":
        depth -= 1
        if depth == 0:
            sys.stdout.write(text[start:index + 1])
            sys.exit(0)
' 
}

get_http_status_code_from_yaak_output() {
  local line status reason
  while IFS= read -r line; do
    if [[ "$line" =~ ^\<[[:space:]]*HTTP/[0-9.]+[[:space:]]+([0-9]{3})(.*)$ ]]; then
      status="${BASH_REMATCH[1]}"
      reason="${BASH_REMATCH[2]}"
      printf '%s\t%s%s\n' "$status" "$status" "$reason"
      return
    fi
  done
  printf 'null\t\n'
}

get_yaak_id_by_name() {
  local lines="$1"
  local name="$2"
  local line
  line="$(printf '%s\n' "$lines" | grep -F " - " | grep -F "$name" | tail -1 || true)"
  if [[ -z "$line" ]]; then
    echo "Cannot find Yaak item named '$name'." >&2
    exit 1
  fi
  awk '{print $1}' <<< "$line"
}

get_yaak_workspace_id() {
  local workspaces
  workspaces="$(invoke_yaak_lines workspace list)"
  get_yaak_id_by_name "$workspaces" "$1"
}

get_yaak_environment_id() {
  local environments line
  environments="$(invoke_yaak_lines environment list "$1")"
  line="$(printf '%s\n' "$environments" | head -1)"
  if [[ -z "$line" ]]; then
    echo "No Yaak environment found in workspace $1." >&2
    exit 1
  fi
  awk '{print $1}' <<< "$line"
}

get_yaak_request_id() {
  local requests
  requests="$(invoke_yaak_lines request list "$1")"
  get_yaak_id_by_name "$requests" "$2"
}

# 前置条件：Variables 参数均为 key=value；后置条件：Yaak 环境变量被合并更新；不变量：未传入的变量保持原值。
set_yaak_environment_variables() {
  local environment_id="$1"
  shift
  local env_json new_vars merged updated_variables payload pair key value
  env_json="$(invoke_yaak_string environment show "$environment_id")"
  new_vars="{}"
  for pair in "$@"; do
    key="${pair%%=*}"
    value="${pair#*=}"
    new_vars="$(jq --arg key "$key" --arg value "$value" '.[$key] = $value' <<< "$new_vars")"
  done
  merged="$(jq --argjson new "$new_vars" '[.variables[]? | {(.name): (.value // "")}] | add // {} | . * $new' <<< "$env_json")"
  updated_variables="$(jq 'to_entries | sort_by(.key) | map({name: .key, value: (.value | tostring), enabled: true})' <<< "$merged")"
  payload="$(jq --argjson variables "$updated_variables" '{id: .id, workspaceId: .workspaceId, name: .name, variables: $variables}' -c <<< "$env_json")"
  invoke_yaak_lines environment update "--json=$payload" >/dev/null
}

get_json_field() {
  local json="$1"
  local field="$2"
  jq -r --arg field "$field" '.[$field] // ""' <<< "$json"
}

record_result() {
  local name="$1"
  local expected="$2"
  local actual="$3"
  local passed="false"
  IFS=',' read -ra expected_values <<< "$expected"
  for expected_code in "${expected_values[@]}"; do
    if [[ "$expected_code" == "$actual" ]]; then
      passed="true"
      break
    fi
  done
  printf '%s|%s|%s|%s\n' "$name" "$expected" "$actual" "$passed" >> "$TEST_RESULTS_FILE"
}

# 前置条件：WORKSPACE_ID 与 ENVIRONMENT_ID 已设置；后置条件：发送请求并记录结果；不变量：每次调用只记录一个测试结果。
send_yaak_request_json() {
  local name="$1"
  local expected="${2:-200}"
  local request_id show_json raw_output http_status status_code status_line resp_body resp actual_code passed expected_str
  request_id="$(get_yaak_request_id "$WORKSPACE_ID" "$name")"
  show_json="$(invoke_yaak_string request show "$request_id")"

  echo >&2
  printf '%0.s=' $(seq 1 60) >&2; echo >&2
  echo "  $name" >&2
  printf '%0.s-' $(seq 1 60) >&2; echo >&2
  printf '  %s %s\n' "$(jq -r '.method' <<< "$show_json")" "$(jq -r '.url' <<< "$show_json")" >&2
  jq -r '.headers[]? | select(.enabled) | "  > \(.name): \(.value)"' <<< "$show_json" >&2

  if [[ "$(jq -r '.body.text // empty' <<< "$show_json")" != "" ]]; then
    echo "  Body:" >&2
    jq -r '.body.text' <<< "$show_json" | sed 's/^/  /' >&2
  elif jq -e '.body.form[]? | select(.enabled)' <<< "$show_json" >/dev/null; then
    echo "  Body (form):" >&2
    jq -r '.body.form[]? | select(.enabled) | "    \(.name) = \(if .file then "[file: \(.file)]" else .value end)"' <<< "$show_json" >&2
  else
    echo "  Body: (none)" >&2
  fi

  raw_output="$(invoke_yaak_string request send "$request_id" -e "$ENVIRONMENT_ID" -v)"
  http_status="$(get_http_status_code_from_yaak_output <<< "$raw_output")"
  status_code="$(cut -f1 <<< "$http_status")"
  status_line="$(cut -f2- <<< "$http_status")"
  resp_body="$(get_response_json_from_yaak_output <<< "$raw_output")"

  printf '%0.s-' $(seq 1 60) >&2; echo >&2
  echo "  <- $status_line" >&2
  echo "  $resp_body" >&2

  if [[ -z "$resp_body" ]]; then
    resp="$(jq -n --argjson code "${status_code/null/0}" '{code: $code, message: "", data: {}}')"
  else
    resp="$resp_body"
  fi

  actual_code="$(jq -r '.code // empty' <<< "$resp")"
  passed="false"
  IFS=',' read -ra expected_values <<< "$expected"
  for expected_code in "${expected_values[@]}"; do
    if [[ "$expected_code" == "$actual_code" ]]; then
      passed="true"
      break
    fi
  done

  if [[ "$passed" == "true" ]]; then
    echo "  PASS (code=$actual_code)" >&2
  else
    expected_str="${expected//,/, }"
    echo "  FAIL (expected=$expected_str, actual=$actual_code, message=$(jq -r '.message // ""' <<< "$resp"))" >&2
  fi
  record_result "$name" "$expected" "$actual_code"
  printf '%s\n' "$resp"
}

send_yaak_request() {
  send_yaak_request_json "$1" "${2:-200}" >/dev/null
}

decode_mailhog_body() {
  python3 -c '
import html
import re
import sys

body = sys.stdin.read()
body = re.sub(r"=\r?\n", "", body)
body = re.sub(r"=([0-9A-Fa-f]{2})", lambda match: chr(int(match.group(1), 16)), body)
sys.stdout.write(html.unescape(body))
'
}

# 前置条件：MailHog API 可访问；后置条件：输出匹配收件人和用途的 token；不变量：超时前会轮询最新邮件。
get_mailhog_token() {
  local recipient="$1"
  local token_purpose="$2"
  local deadline token
  deadline=$((SECONDS + MAIL_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    token="$(
      curl -fsS "$MAILHOG_API_BASE/api/v2/messages" \
        | jq -r --arg recipient "$recipient" '.items // .Items // [] | sort_by(.Created) | reverse | .[] | select(((.Content.Headers.To // []) | join(",")) | contains($recipient)) | .Content.Body' \
        | decode_mailhog_body \
        | python3 -c 'import re, sys, urllib.parse; text=sys.stdin.read(); m=re.search(r"[?&]token=([^\"'\''&<>\s]+)", text); print(urllib.parse.unquote(m.group(1)) if m else "")' \
        | head -1
    )"
    if [[ -n "$token" ]]; then
      echo "  [MailHog] 获取到 $token_purpose token" >&2
      printf '%s\n' "$token"
      return
    fi
    sleep 1
  done
  echo "Cannot find $token_purpose token email for $recipient in MailHog." >&2
  exit 1
}

write_test_summary() {
  local total passed failed
  total="$(wc -l < "$TEST_RESULTS_FILE" | tr -d ' ')"
  passed="$(awk -F'|' '$4 == "true" { count++ } END { print count + 0 }' "$TEST_RESULTS_FILE")"
  failed=$((total - passed))
  echo
  printf '%0.s#' $(seq 1 60); echo
  echo "#  测试结果汇总 - 媒体下载鉴权"
  printf '%0.s#' $(seq 1 60); echo
  echo
  echo "  通过: $passed / $total"
  if (( failed > 0 )); then
    echo "  失败: $failed"
    echo
    echo "  失败的测试:"
    awk -F'|' '$4 != "true" { printf "    FAIL %s (expected=%s, actual=%s)\n", $1, $2, $3 }' "$TEST_RESULTS_FILE"
  elif (( total > 0 )); then
    echo "  全部通过!"
  fi
}

log ">>> 正在连接 Yaak 工作空间..."
WORKSPACE_ID="$(get_yaak_workspace_id "$WORKSPACE_NAME")"
ENVIRONMENT_ID="$(get_yaak_environment_id "$WORKSPACE_ID")"
log "Workspace: $WORKSPACE_ID"
log "Environment: $ENVIRONMENT_ID"

log ">>> 设置初始环境变量..."
set_yaak_environment_variables "$ENVIRONMENT_ID" \
  "baseUrl=$BASE_URL" \
  "testUserEmail=$TEST_USER_EMAIL" \
  "testUserPassword=$TEST_USER_PASSWORD" \
  "testPeerEmail=$TEST_PEER_EMAIL" \
  "testPeerPassword=$TEST_PEER_PASSWORD" \
  "adminUsername=$ADMIN_USERNAME" \
  "adminPassword=$ADMIN_PASSWORD" \
  "testUserId=$TEST_USER_ID" \
  "testPeerId=$TEST_PEER_ID" \
  "adminUserId=$ADMIN_USER_ID" \
  "merchantEmail=$MERCHANT_EMAIL" \
  "merchantPassword=$MERCHANT_PASSWORD" \
  "merchantNickname=$MERCHANT_NICKNAME" \
  "merchantName=$MERCHANT_NAME" \
  "teamName=$TEAM_NAME" \
  "outsiderEmail=$OUTSIDER_EMAIL" \
  "outsiderPassword=$MERCHANT_PASSWORD" \
  "outsiderNickname=$OUTSIDER_NICKNAME" \
  "avatarFile=$TEST_AVATAR_PATH" \
  "licenseFile=$TEST_LICENSE_PATH" \
  "merchantActivationToken=" \
  "outsiderActivationToken=" \
  "userAccessToken=" \
  "peerAccessToken=" \
  "adminAccessToken=" \
  "merchantAccessToken=" \
  "outsiderAccessToken=" \
  "merchantUserId=" \
  "friendRequestId=" \
  "privateConversationId=" \
  "teamId=" \
  "teamConversationId=" \
  "privateImageMediaId=" \
  "privateImageSignedUrl=" \
  "teamImageMediaId=" \
  "teamImageSignedUrl=" \
  "licenseMediaId=" \
  "licenseSignedUrl=" \
  "avatarSignedUrl=" \
  "tamperedSignedUrl=" \
  "missingSigUrl="

echo
printf '%0.s#' $(seq 1 60); echo
echo "#  00 登录与准备"
printf '%0.s#' $(seq 1 60); echo

log "正在登录 test_user..."
resp="$(send_yaak_request_json "00.01 登录 test_user")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  set_yaak_environment_variables "$ENVIRONMENT_ID" \
    "userAccessToken=$(jq -r '.data.tokens.accessToken' <<< "$resp")" \
    "testUserId=$(jq -r '.data.userId' <<< "$resp")"
  echo "  [env] userAccessToken 已保存"
fi

log "正在登录 test_peer..."
resp="$(send_yaak_request_json "00.02 登录 test_peer")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  set_yaak_environment_variables "$ENVIRONMENT_ID" \
    "peerAccessToken=$(jq -r '.data.tokens.accessToken' <<< "$resp")" \
    "testPeerId=$(jq -r '.data.userId' <<< "$resp")"
  echo "  [env] peerAccessToken 已保存"
fi

log "正在登录 admin..."
resp="$(send_yaak_request_json "00.03 登录 admin")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  set_yaak_environment_variables "$ENVIRONMENT_ID" "adminAccessToken=$(jq -r '.data.tokens.accessToken' <<< "$resp")"
  echo "  [env] adminAccessToken 已保存"
fi

log "正在注册商家..."
send_yaak_request "00.04 注册商家"

log "正在获取商家激活 token..."
merchant_token="$(get_mailhog_token "$MERCHANT_EMAIL" "商家激活")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "merchantActivationToken=$merchant_token"

log "正在激活商家..."
send_yaak_request "00.05 激活商家"

log "正在登录商家..."
resp="$(send_yaak_request_json "00.06 登录商家")"
merchant_login_ok="false"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  set_yaak_environment_variables "$ENVIRONMENT_ID" \
    "merchantAccessToken=$(jq -r '.data.tokens.accessToken' <<< "$resp")" \
    "merchantUserId=$(jq -r '.data.userId' <<< "$resp")"
  merchant_login_ok="true"
  echo "  [env] merchantAccessToken 已保存"
fi

log "正在建立私聊好友关系..."
resp="$(send_yaak_request_json "00.07 test_user 发送好友申请")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  friend_request_id="$(jq -r '.data.requestId // ""' <<< "$resp")"
  set_yaak_environment_variables "$ENVIRONMENT_ID" "friendRequestId=$friend_request_id"
  log "正在同意好友申请..."
  send_yaak_request "00.08 test_peer 同意好友申请"
fi

log "正在获取私聊会话..."
resp="$(send_yaak_request_json "00.09 获取私聊会话列表")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  conv_id="$(jq -r '.data.items[0].conversationId // .data.conversationId // ""' <<< "$resp")"
  set_yaak_environment_variables "$ENVIRONMENT_ID" "privateConversationId=$conv_id"
  echo "  [env] privateConversationId = $conv_id"
fi

echo
printf '%0.s#' $(seq 1 60); echo
echo "#  01 私聊图片鉴权"
printf '%0.s#' $(seq 1 60); echo

log "正在上传私聊图片..."
resp="$(send_yaak_request_json "01.01 上传私聊图片")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  media_id="$(jq -r '.data.mediaId // ""' <<< "$resp")"
  signed_url="$(jq -r '.data.url // .data.signedUrl // ""' <<< "$resp")"
  set_yaak_environment_variables "$ENVIRONMENT_ID" "privateImageMediaId=$media_id" "privateImageSignedUrl=$signed_url"
  echo "  [env] privateImageMediaId=$media_id"
fi

log "正在发送私聊图片消息（触发策略升级为 conversationMember）..."
resp="$(send_yaak_request_json "01.02 发送私聊图片消息")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  signed_url="$(jq -r '.data.image.signedUrl // ""' <<< "$resp")"
  if [[ -n "$signed_url" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" "privateImageSignedUrl=$signed_url"
    echo "  [env] privateImageSignedUrl 已刷新为 conversationMember 签名"
  fi
fi
log "1.1 私聊成员 test_peer 下载图片 => 200"
send_yaak_request "01.03 私聊成员 test_peer 下载图片" "200"
log "1.2 管理员下载私聊图片 => 200"
send_yaak_request "01.04 管理员下载私聊图片" "200"
log "1.3 匿名下载私聊图片 => 401"
send_yaak_request "01.05 匿名下载私聊图片" "401"
if [[ "$merchant_login_ok" == "true" ]]; then
  log "1.4 非成员商家下载私聊图片 => 403"
  send_yaak_request "01.06 非成员商家下载私聊图片" "403"
else
  skip_case "商家未成功登录，跳过非成员下载私聊图片用例。"
fi

echo
printf '%0.s#' $(seq 1 60); echo
echo "#  02 群聊图片鉴权"
printf '%0.s#' $(seq 1 60); echo

log "正在创建群聊小队..."
resp="$(send_yaak_request_json "02.01 创建群聊小队")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  team_id="$(jq -r '.data.teamId // ""' <<< "$resp")"
  chat_id="$(jq -r '.data.chatId // ""' <<< "$resp")"
  set_yaak_environment_variables "$ENVIRONMENT_ID" "teamId=$team_id" "teamConversationId=$chat_id"
  echo "  [env] teamId=$team_id, teamConversationId=$chat_id"
fi

log "正在 test_peer 加入小队..."
send_yaak_request "02.02 test_peer 加入小队"
log "正在上传群聊图片..."
resp="$(send_yaak_request_json "02.03 上传群聊图片")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  media_id="$(jq -r '.data.mediaId // ""' <<< "$resp")"
  signed_url="$(jq -r '.data.url // .data.signedUrl // ""' <<< "$resp")"
  set_yaak_environment_variables "$ENVIRONMENT_ID" "teamImageMediaId=$media_id" "teamImageSignedUrl=$signed_url"
  echo "  [env] teamImageMediaId=$media_id"
fi

log "正在发送群聊图片消息（触发策略升级为 conversationMember）..."
resp="$(send_yaak_request_json "02.04 发送群聊图片消息")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  signed_url="$(jq -r '.data.image.signedUrl // ""' <<< "$resp")"
  if [[ -n "$signed_url" ]]; then
    set_yaak_environment_variables "$ENVIRONMENT_ID" "teamImageSignedUrl=$signed_url"
    echo "  [env] teamImageSignedUrl 已刷新为 conversationMember 签名"
  fi
fi
log "2.1 群成员 test_peer 下载群聊图片 => 200"
send_yaak_request "02.05 群成员 test_peer 下载图片" "200"
log "2.2 管理员下载群聊图片 => 200"
send_yaak_request "02.06 管理员下载群聊图片" "200"

log "正在注册非成员用户..."
send_yaak_request "02.07 注册非成员用户"
log "正在获取非成员用户激活 token..."
outsider_token="$(get_mailhog_token "$OUTSIDER_EMAIL" "非成员用户激活")"
set_yaak_environment_variables "$ENVIRONMENT_ID" "outsiderActivationToken=$outsider_token"
send_yaak_request "02.08 激活非成员用户"
log "正在登录非成员用户..."
resp="$(send_yaak_request_json "02.09 登录非成员用户")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  set_yaak_environment_variables "$ENVIRONMENT_ID" "outsiderAccessToken=$(jq -r '.data.tokens.accessToken' <<< "$resp")"
fi
log "2.3 非成员用户下载群聊图片 => 403"
send_yaak_request "02.10 非成员用户下载群聊图片" "403"

echo
printf '%0.s#' $(seq 1 60); echo
echo "#  03 退出群聊后不可访问"
printf '%0.s#' $(seq 1 60); echo

log "test_peer 退出小队..."
send_yaak_request "03.01 test_peer 退出小队"
log "3.1 已退出的 test_peer 下载群聊图片 => 403"
send_yaak_request "03.02 已退出用户 test_peer 下载图片" "403"
log "3.2 仍在群中 test_user 下载群聊图片 => 200"
send_yaak_request "03.03 仍在群中 test_user 下载图片" "200"
log "3.3 管理员下载已退出群聊图片 => 200"
send_yaak_request "03.04 管理员下载已退出群聊图片" "200"

echo
printf '%0.s#' $(seq 1 60); echo
echo "#  04 商家资质鉴权"
printf '%0.s#' $(seq 1 60); echo

if [[ "$merchant_login_ok" != "true" ]]; then
  skip_case "商家未成功登录，跳过全部商家资质鉴权用例。"
else
  log "商家上传执照..."
  resp="$(send_yaak_request_json "04.01 商家上传执照")"
  if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
    media_id="$(jq -r '.data.mediaId // ""' <<< "$resp")"
    signed_url="$(jq -r '.data.url // .data.signedUrl // ""' <<< "$resp")"
    set_yaak_environment_variables "$ENVIRONMENT_ID" "licenseMediaId=$media_id" "licenseSignedUrl=$signed_url"
    echo "  [env] licenseMediaId=$media_id"
  fi
  log "4.1 商家本人下载执照 => 200"
  send_yaak_request "04.02 商家本人下载执照" "200"
  log "4.2 管理员下载商家执照 => 200"
  send_yaak_request "04.03 管理员下载商家执照" "200"
  log "4.3 test_user 非所有者下载商家执照 => 403"
  send_yaak_request "04.04 test_user 非所有者下载执照" "403"
  log "4.4 匿名下载商家执照 => 401"
  send_yaak_request "04.05 匿名下载商家执照" "401"
fi

echo
printf '%0.s#' $(seq 1 60); echo
echo "#  05 管理员全资源访问"
printf '%0.s#' $(seq 1 60); echo

log "上传测试头像..."
resp="$(send_yaak_request_json "05.01 上传测试头像")"
if [[ "$(jq -r '.code' <<< "$resp")" == "200" ]]; then
  signed_url="$(jq -r '.data.url // .data.signedUrl // ""' <<< "$resp")"
  set_yaak_environment_variables "$ENVIRONMENT_ID" "avatarSignedUrl=$signed_url"
  echo "  [env] avatarSignedUrl 已保存"
fi
log "5.1 管理员下载私聊图片 => 200"
send_yaak_request "05.02 管理员下载私聊图片" "200"
log "5.2 管理员下载群聊图片 => 200"
send_yaak_request "05.03 管理员下载群聊图片" "200"
log "5.3 管理员下载商家执照 => 200"
send_yaak_request "05.04 管理员下载商家执照" "200"
log "5.4 管理员下载公开头像 => 200"
send_yaak_request "05.05 管理员下载公开头像" "200"

echo
printf '%0.s#' $(seq 1 60); echo
echo "#  06 签名完整性"
printf '%0.s#' $(seq 1 60); echo

env_show="$(invoke_yaak_string environment show "$ENVIRONMENT_ID")"
signed_url_value="$(jq -r '.variables[]? | select(.name == "privateImageSignedUrl") | .value // ""' <<< "$env_show")"
if [[ -n "$signed_url_value" && "$signed_url_value" =~ \&sig=([^&]+) ]]; then
  orig_sig="${BASH_REMATCH[1]}"
  tampered_sig="${orig_sig%?}0"
  [[ "$tampered_sig" == "$orig_sig" ]] && tampered_sig="${orig_sig%?}1"
  tampered_url="${signed_url_value/&sig=$orig_sig/&sig=$tampered_sig}"
  missing_sig_url="$(sed -E 's/&sig=[^&]+//' <<< "$signed_url_value")"
  set_yaak_environment_variables "$ENVIRONMENT_ID" "tamperedSignedUrl=$tampered_url" "missingSigUrl=$missing_sig_url"
  echo "  [env] 已构造篡改签名 URL 和无签名 URL"
else
  skip_case "无法解析 signedUrl，跳过签名完整性测试。"
fi

log "6.1 有效签名下载 => 200"
send_yaak_request "06.01 有效签名下载" "200"
log "6.2 篡改签名下载 => 403"
send_yaak_request "06.02 篡改签名下载" "403"
log "6.3 缺少签名参数下载 => 403"
send_yaak_request "06.03 缺少签名参数下载" "403"
log "6.4 不存在媒体ID下载 => 404"
send_yaak_request "06.04 不存在媒体ID下载" "404"

write_test_summary

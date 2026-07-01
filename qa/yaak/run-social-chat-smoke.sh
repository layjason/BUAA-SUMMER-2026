#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
YAAK_DATA_DIR="${YAAK_DATA_DIR:-/tmp/mayoistar-social-chat-yaak}"

TEST_USER_EMAIL="test_user@mayoistar.qa"
TEST_USER_PASSWORD="4g9Pf6KNpw4rxe3NL7hij9l2"
TEST_PEER_EMAIL="test_peer@mayoistar.qa"
TEST_PEER_PASSWORD="1QL71Nz-b1aYcP5yzcTn4vSu"
ADMIN_USERNAME="admin"
ADMIN_PASSWORD="uMudtQCQ4ZJ9NKOYyYBtdxg5"

TEST_USER_ID="11111111-1111-1111-1111-111111111111"
TEST_PEER_ID="22222222-2222-2222-2222-222222222222"
ADMIN_USER_ID="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

mkdir -p "$YAAK_DATA_DIR"

current_test=""

# 前置条件：current_test 保存当前测试名称；后置条件：脚本异常退出时输出该测试不通过；不变量：成功测试必须通过 pass_test 清空 current_test。
finish_current_test_on_exit() {
  local exit_code=$?
  if [[ "$exit_code" -ne 0 && -n "$current_test" ]]; then
    echo "测试不通过: $current_test" >&2
  fi
}

# 前置条件：传入非空测试名称；后置条件：记录并输出正在运行的测试；不变量：同一时刻仅记录一个运行中的测试。
begin_test() {
  current_test="$1"
  echo "正在运行测试: $current_test"
}

# 前置条件：current_test 为已完成测试名称；后置条件：输出测试通过并清空当前测试；不变量：清空后不会被退出 trap 误报失败。
pass_test() {
  echo "测试通过: $current_test"
  current_test=""
}

trap finish_current_test_on_exit EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "缺少命令: $1" >&2
    exit 1
  fi
}

require_command yaak
require_command jq

create_workspace() {
  local output
  output=$(yaak --data-dir "$YAAK_DATA_DIR" workspace create --json '{"name":"MayoiStar Social Chat CLI Smoke"}')
  echo "$output" | awk '{print $3}'
}

create_environment() {
  local workspace_id="$1"
  jq -n \
    --arg workspaceId "$workspace_id" \
    --arg baseUrl "$BASE_URL" \
    --arg testUserEmail "$TEST_USER_EMAIL" \
    --arg testUserPassword "$TEST_USER_PASSWORD" \
    --arg testPeerEmail "$TEST_PEER_EMAIL" \
    --arg testPeerPassword "$TEST_PEER_PASSWORD" \
    --arg adminUsername "$ADMIN_USERNAME" \
    --arg adminPassword "$ADMIN_PASSWORD" \
    --arg testUserId "$TEST_USER_ID" \
    --arg testPeerId "$TEST_PEER_ID" \
    --arg adminUserId "$ADMIN_USER_ID" \
    '{
      workspaceId: $workspaceId,
      name: "Local",
      variables: [
        {name:"baseUrl", value:$baseUrl},
        {name:"testUserEmail", value:$testUserEmail},
        {name:"testUserPassword", value:$testUserPassword},
        {name:"testPeerEmail", value:$testPeerEmail},
        {name:"testPeerPassword", value:$testPeerPassword},
        {name:"adminUsername", value:$adminUsername},
        {name:"adminPassword", value:$adminPassword},
        {name:"testUserId", value:$testUserId},
        {name:"testPeerId", value:$testPeerId},
        {name:"adminUserId", value:$adminUserId},
        {name:"userAccessToken", value:""},
        {name:"peerAccessToken", value:""},
        {name:"adminAccessToken", value:""},
        {name:"friendRequestId", value:""},
        {name:"conversationId", value:""},
        {name:"messageId", value:""},
        {name:"reportId", value:""},
        {name:"chatImageMediaId", value:"media-placeholder"}
      ]
    }' > /tmp/mayoistar-yaak-env.json
  yaak --data-dir "$YAAK_DATA_DIR" environment create --json "$(cat /tmp/mayoistar-yaak-env.json)" | awk '{print $3}'
}

create_folder() {
  local workspace_id="$1"
  local name="$2"
  yaak --data-dir "$YAAK_DATA_DIR" folder create "$workspace_id" --name "$name" | awk '{print $3}'
}

create_request() {
  local workspace_id="$1"
  local folder_id="$2"
  local name="$3"
  local method="$4"
  local url="$5"
  local token_var="${6:-}"
  local body="${7:-}"

  local auth_type="null"
  local auth="{}"
  if [[ -n "$token_var" ]]; then
    auth_type='"bearer"'
    auth=$(jq -n --arg token "\${[ $token_var ]}" '{token:$token,prefix:"Bearer"}')
  fi

  jq -n \
    --arg workspaceId "$workspace_id" \
    --arg folderId "$folder_id" \
    --arg name "$name" \
    --arg method "$method" \
    --arg url "$url" \
    --argjson authenticationType "$auth_type" \
    --argjson authentication "$auth" \
    --arg body "$body" \
    '{
      workspaceId: $workspaceId,
      folderId: $folderId,
      name: $name,
      method: $method,
      url: $url,
      headers: [{name:"Content-Type", value:"application/json"}],
      authenticationType: $authenticationType,
      authentication: $authentication
    }
    + (if $body == "" then {} else {bodyType:"application/json", body:{text:$body}} end)' > /tmp/mayoistar-yaak-request.json

  yaak --data-dir "$YAAK_DATA_DIR" request create "$workspace_id" --json "$(cat /tmp/mayoistar-yaak-request.json)" | awk '{print $3}'
}

set_env_var() {
  local env_id="$1"
  local name="$2"
  local value="$3"
  yaak --data-dir "$YAAK_DATA_DIR" environment show "$env_id" \
    | jq --arg name "$name" --arg value "$value" '
      .variables = (
        if any(.variables[]; .name == $name)
        then [.variables[] | if .name == $name then .value = $value else . end]
        else .variables + [{name:$name, value:$value, enabled:true}]
        end
      )
    ' > /tmp/mayoistar-yaak-env-update.json
  yaak --data-dir "$YAAK_DATA_DIR" environment update --json "$(cat /tmp/mayoistar-yaak-env-update.json)" >/dev/null
}

send_json() {
  local request_id="$1"
  local env_id="$2"
  yaak --data-dir "$YAAK_DATA_DIR" request send -e "$env_id" "$request_id"
}

assert_code() {
  local response="$1"
  local expected="$2"
  local actual
  actual=$(echo "$response" | jq -r '.code')
  if [[ "$actual" != "$expected" ]]; then
    echo "响应码不符合预期: expected=$expected actual=$actual" >&2
    echo "$response" | jq . >&2
    exit 1
  fi
}

# 前置条件：response 是合法 JSON，filter 能定位单个标量字段；后置条件：字段字符串值等于 expected；不变量：不修改 Yaak 环境。
assert_jq_equals() {
  local response="$1"
  local filter="$2"
  local expected="$3"
  local actual
  actual=$(echo "$response" | jq -r "($filter) as \$value | if \$value == null then \"\" else \$value end")
  if [[ "$actual" != "$expected" ]]; then
    echo "响应内容不符合预期: filter=$filter expected=$expected actual=$actual" >&2
    echo "$response" | jq . >&2
    exit 1
  fi
}

# 前置条件：response 是合法 JSON，filter 能定位待检查字段；后置条件：字段存在且渲染后的字符串非空；不变量：不修改 Yaak 环境。
assert_jq_non_empty() {
  local response="$1"
  local filter="$2"
  local actual
  actual=$(echo "$response" | jq -r "($filter) as \$value | if \$value == null then \"\" else \$value end")
  if [[ -z "$actual" ]]; then
    echo "响应内容不能为空: filter=$filter" >&2
    echo "$response" | jq . >&2
    exit 1
  fi
}

# 前置条件：response 是合法 JSON，filter 是返回布尔语义的 jq 表达式；后置条件：表达式为真；不变量：不修改 Yaak 环境。
assert_jq_true() {
  local response="$1"
  local filter="$2"
  if ! echo "$response" | jq -e "$filter" >/dev/null; then
    echo "响应内容条件不成立: filter=$filter" >&2
    echo "$response" | jq . >&2
    exit 1
  fi
}

# 前置条件：response 使用 PageResult 响应结构；后置条件：分页字段与请求分页参数一致且计数字段为数字；不变量：不检查历史数据总量。
assert_page_result() {
  local response="$1"
  local expected_page="$2"
  local expected_page_size="$3"
  assert_jq_true "$response" '.data.items | type == "array"'
  assert_jq_equals "$response" ".data.page" "$expected_page"
  assert_jq_equals "$response" ".data.pageSize" "$expected_page_size"
  assert_jq_true "$response" '.data.total | type == "number"'
  assert_jq_true "$response" '.data.totalPages | type == "number"'
}

workspace_id=$(create_workspace)
environment_id=$(create_environment "$workspace_id")

auth_folder=$(create_folder "$workspace_id" "00 登录")
social_folder=$(create_folder "$workspace_id" "01 好友社群")
chat_folder=$(create_folder "$workspace_id" "02 一对一聊天")
report_folder=$(create_folder "$workspace_id" "03 举报后台")

login_user=$(create_request "$workspace_id" "$auth_folder" "登录 test_user" "POST" "${BASE_URL}/identity/auth/login" "" '{"email":"${[ testUserEmail ]}","password":"${[ testUserPassword ]}"}')
login_peer=$(create_request "$workspace_id" "$auth_folder" "登录 test_peer" "POST" "${BASE_URL}/identity/auth/login" "" '{"email":"${[ testPeerEmail ]}","password":"${[ testPeerPassword ]}"}')
login_admin=$(create_request "$workspace_id" "$auth_folder" "登录 admin" "POST" "${BASE_URL}/admin/auth/login" "" '{"username":"${[ adminUsername ]}","password":"${[ adminPassword ]}"}')

get_profile=$(create_request "$workspace_id" "$social_folder" "查看个人主页" "GET" "${BASE_URL}/social/profiles/${TEST_PEER_ID}" "userAccessToken")
create_friend=$(create_request "$workspace_id" "$social_folder" "发送好友申请" "POST" "${BASE_URL}/social/friend-requests" "userAccessToken" '{"targetUserId":"${[ testPeerId ]}","source":"profile","message":"Yaak CLI smoke"}')
accept_friend=$(create_request "$workspace_id" "$social_folder" "同意好友申请" "POST" "${BASE_URL}"'/social/friend-requests/${[ friendRequestId ]}/decision' "peerAccessToken" '{"accepted":true}')
list_friends=$(create_request "$workspace_id" "$social_folder" "好友列表" "GET" "${BASE_URL}/social/friends?page=1&pageSize=20" "userAccessToken")
update_remark=$(create_request "$workspace_id" "$social_folder" "更新好友备注" "PATCH" "${BASE_URL}/social/friends/${TEST_PEER_ID}" "userAccessToken" '{"remark":"QA 好友","groupTags":["测试","两人社群"]}')

list_conversations=$(create_request "$workspace_id" "$chat_folder" "会话列表" "GET" "${BASE_URL}/chat/conversations?page=1&pageSize=20" "userAccessToken")
send_text=$(create_request "$workspace_id" "$chat_folder" "发送文字消息" "POST" "${BASE_URL}"'/chat/conversations/${[ conversationId ]}/messages' "userAccessToken" '{"kind":"text","text":"Hello from Yaak CLI"}')
send_emoji=$(create_request "$workspace_id" "$chat_folder" "发送表情文本消息" "POST" "${BASE_URL}"'/chat/conversations/${[ conversationId ]}/messages' "userAccessToken" '{"kind":"text","text":"😀⭐"}')
mark_read=$(create_request "$workspace_id" "$chat_folder" "标记已读" "POST" "${BASE_URL}/chat/messages/read" "peerAccessToken" '{"messageIds":["${[ messageId ]}"]}')
forward_message=$(create_request "$workspace_id" "$chat_folder" "转发消息" "POST" "${BASE_URL}"'/chat/messages/${[ messageId ]}/forward' "userAccessToken" '{"targetConversationIds":["${[ conversationId ]}"]}')
recall_message=$(create_request "$workspace_id" "$chat_folder" "撤回消息" "POST" "${BASE_URL}"'/chat/messages/${[ messageId ]}/recall' "userAccessToken")

create_report=$(create_request "$workspace_id" "$report_folder" "举报用户" "POST" "${BASE_URL}/social/reports" "userAccessToken" '{"targetType":"user","targetId":"${[ testPeerId ]}","reason":"Yaak CLI smoke report"}')
admin_list_reports=$(create_request "$workspace_id" "$report_folder" "后台查询举报" "GET" "${BASE_URL}/admin/reports?targetType=user&targetId=${TEST_PEER_ID}&page=1&pageSize=20" "adminAccessToken")
admin_decide_report=$(create_request "$workspace_id" "$report_folder" "后台处理举报" "POST" "${BASE_URL}"'/admin/reports/${[ reportId ]}/decision' "adminAccessToken" '{"status":"resolved","handlingNote":"Yaak CLI smoke resolved"}')

echo "Yaak workspace: $workspace_id"
echo "Yaak environment: $environment_id"

begin_test "登录 test_user"
response=$(send_json "$login_user" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.userId' "$TEST_USER_ID"
assert_jq_equals "$response" '.data.kind' "personal"
assert_jq_equals "$response" '.data.accountStatus' "active"
assert_jq_non_empty "$response" '.data.tokens.accessToken'
assert_jq_non_empty "$response" '.data.tokens.refreshToken'
assert_jq_non_empty "$response" '.data.tokens.expiresAt'
set_env_var "$environment_id" "userAccessToken" "$(echo "$response" | jq -r '.data.tokens.accessToken')"
set_env_var "$environment_id" "testUserId" "$(echo "$response" | jq -r '.data.userId')"
pass_test

begin_test "登录 test_peer"
response=$(send_json "$login_peer" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.userId' "$TEST_PEER_ID"
assert_jq_equals "$response" '.data.kind' "personal"
assert_jq_equals "$response" '.data.accountStatus' "active"
assert_jq_non_empty "$response" '.data.tokens.accessToken'
assert_jq_non_empty "$response" '.data.tokens.refreshToken'
assert_jq_non_empty "$response" '.data.tokens.expiresAt'
set_env_var "$environment_id" "peerAccessToken" "$(echo "$response" | jq -r '.data.tokens.accessToken')"
set_env_var "$environment_id" "testPeerId" "$(echo "$response" | jq -r '.data.userId')"
pass_test

begin_test "登录 admin"
response=$(send_json "$login_admin" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.userId' "$ADMIN_USER_ID"
assert_jq_non_empty "$response" '.data.tokens.accessToken'
assert_jq_non_empty "$response" '.data.tokens.refreshToken'
assert_jq_non_empty "$response" '.data.tokens.expiresAt'
set_env_var "$environment_id" "adminAccessToken" "$(echo "$response" | jq -r '.data.tokens.accessToken')"
set_env_var "$environment_id" "adminUserId" "$(echo "$response" | jq -r '.data.userId')"
pass_test

begin_test "查看个人主页"
response=$(send_json "$get_profile" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.userId' "$TEST_PEER_ID"
assert_jq_equals "$response" '.data.nickname' "test_peer"
assert_jq_equals "$response" '.data.kind' "personal"
assert_jq_true "$response" '.data.reputationScore | type == "number"'
pass_test

begin_test "发送好友申请"
response=$(send_json "$create_friend" "$environment_id")
assert_code "$response" "200"
assert_jq_non_empty "$response" '.data.requestId'
assert_jq_equals "$response" '.data.requesterId' "$TEST_USER_ID"
assert_jq_equals "$response" '.data.targetUserId' "$TEST_PEER_ID"
assert_jq_equals "$response" '.data.source' "profile"
assert_jq_equals "$response" '.data.message' "Yaak CLI smoke"
assert_jq_equals "$response" '.data.status' "pending"
friend_request_id=$(echo "$response" | jq -r '.data.requestId')
set_env_var "$environment_id" "friendRequestId" "$(echo "$response" | jq -r '.data.requestId')"
pass_test

begin_test "同意好友申请"
response=$(send_json "$accept_friend" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.requestId' "$friend_request_id"
assert_jq_equals "$response" '.data.requesterId' "$TEST_USER_ID"
assert_jq_equals "$response" '.data.targetUserId' "$TEST_PEER_ID"
assert_jq_equals "$response" '.data.status' "accepted"
pass_test

begin_test "好友列表"
response=$(send_json "$list_friends" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "1" "20"
assert_jq_true "$response" ".data.items | any(.userId == \"$TEST_PEER_ID\" and .nickname == \"test_peer\")"
pass_test

begin_test "更新好友备注"
response=$(send_json "$update_remark" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.userId' "$TEST_PEER_ID"
assert_jq_equals "$response" '.data.nickname' "test_peer"
assert_jq_equals "$response" '.data.remark' "QA 好友"
assert_jq_true "$response" '.data.groupTags | index("测试") != null and index("两人社群") != null'
pass_test

begin_test "会话列表"
response=$(send_json "$list_conversations" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "1" "20"
assert_jq_true "$response" '.data.items | any(.kind == "friend" and (.conversationId // "") != "" and (.unreadCount | type == "number"))'
conversation_id=$(echo "$response" | jq -r '.data.items[0].conversationId // empty')
if [[ -z "$conversation_id" ]]; then
  echo "未找到好友会话，无法继续聊天测试。" >&2
  echo "$response" | jq . >&2
  exit 1
fi
set_env_var "$environment_id" "conversationId" "$conversation_id"
pass_test

begin_test "发送文字消息"
response=$(send_json "$send_text" "$environment_id")
assert_code "$response" "200"
assert_jq_non_empty "$response" '.data.messageId'
assert_jq_equals "$response" '.data.conversationId' "$conversation_id"
assert_jq_equals "$response" '.data.senderId' "$TEST_USER_ID"
assert_jq_equals "$response" '.data.kind' "text"
assert_jq_equals "$response" '.data.text' "Hello from Yaak CLI"
assert_jq_equals "$response" '.data.readStatus' "read"
assert_jq_equals "$response" '.data.recalled' "false"
set_env_var "$environment_id" "messageId" "$(echo "$response" | jq -r '.data.messageId')"
pass_test

begin_test "发送表情文本消息"
response=$(send_json "$send_emoji" "$environment_id")
assert_code "$response" "200"
assert_jq_non_empty "$response" '.data.messageId'
assert_jq_equals "$response" '.data.conversationId' "$conversation_id"
assert_jq_equals "$response" '.data.senderId' "$TEST_USER_ID"
assert_jq_equals "$response" '.data.kind' "text"
assert_jq_equals "$response" '.data.text' "😀⭐"
assert_jq_equals "$response" '.data.recalled' "false"
pass_test

begin_test "标记已读"
response=$(send_json "$mark_read" "$environment_id")
assert_code "$response" "200"
message_id=$(echo "$response" | jq -r '.data[0].messageId // empty')
assert_jq_true "$response" ".data | any(.messageId == \"$message_id\" and .readStatus == \"read\")"
pass_test

begin_test "转发消息"
response=$(send_json "$forward_message" "$environment_id")
assert_code "$response" "200"
assert_jq_true "$response" '.data | type == "array" and length == 1'
assert_jq_non_empty "$response" '.data[0].messageId'
assert_jq_true "$response" ".data[0].messageId != \"$message_id\""
assert_jq_equals "$response" '.data[0].conversationId' "$conversation_id"
assert_jq_equals "$response" '.data[0].senderId' "$TEST_USER_ID"
assert_jq_equals "$response" '.data[0].kind' "text"
assert_jq_equals "$response" '.data[0].text' "Hello from Yaak CLI"
assert_jq_equals "$response" '.data[0].recalled' "false"
pass_test

begin_test "撤回消息"
response=$(send_json "$recall_message" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.messageId' "$message_id"
assert_jq_equals "$response" '.data.conversationId' "$conversation_id"
assert_jq_equals "$response" '.data.senderId' "$TEST_USER_ID"
assert_jq_equals "$response" '.data.recalled' "true"
assert_jq_true "$response" '.data.text == null'
pass_test

begin_test "举报用户"
response=$(send_json "$create_report" "$environment_id")
assert_code "$response" "200"
assert_jq_non_empty "$response" '.data.reportId'
assert_jq_equals "$response" '.data.reporterUserId' "$TEST_USER_ID"
assert_jq_equals "$response" '.data.targetType' "user"
assert_jq_equals "$response" '.data.targetId' "$TEST_PEER_ID"
assert_jq_equals "$response" '.data.reason' "Yaak CLI smoke report"
assert_jq_equals "$response" '.data.status' "pending"
report_id=$(echo "$response" | jq -r '.data.reportId')
set_env_var "$environment_id" "reportId" "$(echo "$response" | jq -r '.data.reportId')"
pass_test

begin_test "后台查询举报"
response=$(send_json "$admin_list_reports" "$environment_id")
assert_code "$response" "200"
if ! echo "$response" | jq -e ".data.items | any(.reportId == \"$report_id\")" >/dev/null; then
  echo "后台举报列表未包含本次举报。" >&2
  echo "$response" | jq . >&2
  exit 1
fi
assert_page_result "$response" "1" "20"
assert_jq_true "$response" ".data.items | any(.reportId == \"$report_id\" and .targetType == \"user\" and .targetId == \"$TEST_PEER_ID\")"
pass_test

begin_test "后台处理举报"
response=$(send_json "$admin_decide_report" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.reportId' "$report_id"
assert_jq_equals "$response" '.data.reporterUserId' "$TEST_USER_ID"
assert_jq_equals "$response" '.data.targetType' "user"
assert_jq_equals "$response" '.data.targetId' "$TEST_PEER_ID"
assert_jq_equals "$response" '.data.status' "resolved"
assert_jq_equals "$response" '.data.handlingNote' "Yaak CLI smoke resolved"
assert_jq_non_empty "$response" '.data.handledAt'
pass_test

echo "Yaak CLI smoke completed."

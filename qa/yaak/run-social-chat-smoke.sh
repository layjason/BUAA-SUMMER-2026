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

response=$(send_json "$login_user" "$environment_id")
assert_code "$response" "200"
set_env_var "$environment_id" "userAccessToken" "$(echo "$response" | jq -r '.data.tokens.accessToken')"
set_env_var "$environment_id" "testUserId" "$(echo "$response" | jq -r '.data.userId')"

response=$(send_json "$login_peer" "$environment_id")
assert_code "$response" "200"
set_env_var "$environment_id" "peerAccessToken" "$(echo "$response" | jq -r '.data.tokens.accessToken')"
set_env_var "$environment_id" "testPeerId" "$(echo "$response" | jq -r '.data.userId')"

response=$(send_json "$login_admin" "$environment_id")
assert_code "$response" "200"
set_env_var "$environment_id" "adminAccessToken" "$(echo "$response" | jq -r '.data.tokens.accessToken')"
set_env_var "$environment_id" "adminUserId" "$(echo "$response" | jq -r '.data.userId')"

for request_id in "$get_profile"; do
  response=$(send_json "$request_id" "$environment_id")
  assert_code "$response" "200"
done

response=$(send_json "$create_friend" "$environment_id")
assert_code "$response" "200"
set_env_var "$environment_id" "friendRequestId" "$(echo "$response" | jq -r '.data.requestId')"

response=$(send_json "$accept_friend" "$environment_id")
assert_code "$response" "200"

for request_id in "$list_friends" "$update_remark"; do
  response=$(send_json "$request_id" "$environment_id")
  assert_code "$response" "200"
done

response=$(send_json "$list_conversations" "$environment_id")
assert_code "$response" "200"
conversation_id=$(echo "$response" | jq -r '.data.items[0].conversationId // empty')
if [[ -z "$conversation_id" ]]; then
  echo "未找到好友会话，无法继续聊天测试。" >&2
  echo "$response" | jq . >&2
  exit 1
fi
set_env_var "$environment_id" "conversationId" "$conversation_id"

response=$(send_json "$send_text" "$environment_id")
assert_code "$response" "200"
set_env_var "$environment_id" "messageId" "$(echo "$response" | jq -r '.data.messageId')"

response=$(send_json "$send_emoji" "$environment_id")
assert_code "$response" "200"

for request_id in "$mark_read" "$forward_message" "$recall_message"; do
  response=$(send_json "$request_id" "$environment_id")
  assert_code "$response" "200"
done

response=$(send_json "$create_report" "$environment_id")
assert_code "$response" "200"
set_env_var "$environment_id" "reportId" "$(echo "$response" | jq -r '.data.reportId')"

for request_id in "$admin_list_reports" "$admin_decide_report"; do
  response=$(send_json "$request_id" "$environment_id")
  assert_code "$response" "200"
done

echo "Yaak CLI smoke completed."

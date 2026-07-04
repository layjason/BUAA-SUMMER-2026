#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
YAAK_DATA_DIR="${YAAK_DATA_DIR:-/tmp/mayoistar-team-yaak}"

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

finish_current_test_on_exit() {
  local exit_code=$?
  if [[ "$exit_code" -ne 0 && -n "$current_test" ]]; then
    echo "测试不通过: $current_test" >&2
  fi
}

begin_test() {
  current_test="$1"
  echo "正在运行测试: $current_test"
}

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
  output=$(yaak --data-dir "$YAAK_DATA_DIR" workspace create --json '{"name":"MayoiStar Team Smoke"}')
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
        {name:"teamId", value:""},
        {name:"teamId2", value:""},
        {name:"teamId3", value:""},
        {name:"memberId", value:""},
        {name:"joinRequestId", value:""},
        {name:"pollId", value:""},
        {name:"pollOptionId", value:""},
        {name:"activityId", value:""},
        {name:"announcementId", value:""},
        {name:"mediaId", value:""}
      ]
    }' > /tmp/mayoistar-team-yaak-env.json
  yaak --data-dir "$YAAK_DATA_DIR" environment create --json "$(cat /tmp/mayoistar-team-yaak-env.json)" | awk '{print $3}'
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
    + (if $body == "" then {} else {bodyType:"application/json", body:{text:$body}} end)' > /tmp/mayoistar-team-yaak-request.json

  yaak --data-dir "$YAAK_DATA_DIR" request create "$workspace_id" --json "$(cat /tmp/mayoistar-team-yaak-request.json)" | awk '{print $3}'
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
    ' > /tmp/mayoistar-team-yaak-env-update.json
  yaak --data-dir "$YAAK_DATA_DIR" environment update --json "$(cat /tmp/mayoistar-team-yaak-env-update.json)" >/dev/null
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

assert_jq_true() {
  local response="$1"
  local filter="$2"
  if ! echo "$response" | jq -e "$filter" >/dev/null; then
    echo "响应内容条件不成立: filter=$filter" >&2
    echo "$response" | jq . >&2
    exit 1
  fi
}

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
TS=$(date +%s)

# ============================================================================
# 文件夹结构
# ============================================================================
auth_folder=$(create_folder "$workspace_id" "00 登录")
team_crud_folder=$(create_folder "$workspace_id" "01 小队CRUD")
team_join_folder=$(create_folder "$workspace_id" "02 加队与退出")
team_roles_folder=$(create_folder "$workspace_id" "03 角色管理")
team_chat_folder=$(create_folder "$workspace_id" "04 群聊功能")
team_activity_folder=$(create_folder "$workspace_id" "05 队内活动")
team_boundary_folder=$(create_folder "$workspace_id" "06 边界用例")

# ============================================================================
# 登录请求
# ============================================================================
login_user=$(create_request "$workspace_id" "$auth_folder" "登录 test_user" "POST" "${BASE_URL}/identity/auth/login" "" '{"email":"${[ testUserEmail ]}","password":"${[ testUserPassword ]}"}')
login_peer=$(create_request "$workspace_id" "$auth_folder" "登录 test_peer" "POST" "${BASE_URL}/identity/auth/login" "" '{"email":"${[ testPeerEmail ]}","password":"${[ testPeerPassword ]}"}')
login_admin=$(create_request "$workspace_id" "$auth_folder" "登录 admin" "POST" "${BASE_URL}/admin/auth/login" "" '{"username":"${[ adminUsername ]}","password":"${[ adminPassword ]}"}')

# ============================================================================
# 小队 CRUD 请求
# ============================================================================
create_public_team=$(create_request "$workspace_id" "$team_crud_folder" "燈创建MyGO!!!!!" "POST" "${BASE_URL}/social/teams" "userAccessToken" '{"name":"MyGO!!!!!-'"$TS"'","tags":["音楽","バンド"],"joinMode":"publicJoin","capacity":20,"description":"迷子でも、進み続ける。"}')
create_approval_team=$(create_request "$workspace_id" "$team_crud_folder" "そよ创建秘密CRYCHIC" "POST" "${BASE_URL}/social/teams" "userAccessToken" '{"name":"CRYCHIC-'"$TS"'","tags":["音楽","再結成"],"joinMode":"approvalRequired","capacity":5,"description":"もう一度、あの頃のように。"}')
create_dup_team=$(create_request "$workspace_id" "$team_crud_folder" "重名MyGO(负例)——世间只有一个MyGO" "POST" "${BASE_URL}/social/teams" "userAccessToken" '{"name":"MyGO!!!!!-'"$TS"'","tags":["a"],"joinMode":"publicJoin","capacity":10}')
search_by_keyword=$(create_request "$workspace_id" "$team_crud_folder" "搜索MyGO" "GET" "${BASE_URL}"'/social/teams?keyword=MyGO!!!!!&page=1&pageSize=20' "userAccessToken")
search_by_tag=$(create_request "$workspace_id" "$team_crud_folder" "按标签搜索小队" "GET" "${BASE_URL}/social/teams?tags=音楽&page=1&pageSize=20" "userAccessToken")
search_no_match=$(create_request "$workspace_id" "$team_crud_folder" "搜索无结果" "GET" "${BASE_URL}/social/teams?keyword=不存在的小队&page=1&pageSize=20" "userAccessToken")
get_team=$(create_request "$workspace_id" "$team_crud_folder" "获取小队详情" "GET" "${BASE_URL}"'/social/teams/${[ teamId ]}' "userAccessToken")
dissolve_team=$(create_request "$workspace_id" "$team_crud_folder" "解散小队" "DELETE" "${BASE_URL}"'/social/teams/${[ teamId ]}' "userAccessToken")
dissolve_by_non_leader=$(create_request "$workspace_id" "$team_crud_folder" "非队长解散(负例)" "DELETE" "${BASE_URL}"'/social/teams/${[ teamId ]}' "peerAccessToken")
list_team_members=$(create_request "$workspace_id" "$team_crud_folder" "成员列表" "GET" "${BASE_URL}"'/social/teams/${[ teamId ]}/members?page=1&pageSize=20' "userAccessToken")
get_point_ranks=$(create_request "$workspace_id" "$team_crud_folder" "积分榜" "GET" "${BASE_URL}"'/social/teams/${[ teamId ]}/points?page=1&pageSize=20' "userAccessToken")

# ============================================================================
# 加队与退出请求
# ============================================================================
join_public=$(create_request "$workspace_id" "$team_join_folder" "加入公开小队" "POST" "${BASE_URL}"'/social/teams/${[ teamId ]}/join' "peerAccessToken" '{"message":"我想加入你们!"}')
join_approval=$(create_request "$workspace_id" "$team_join_folder" "申请加入审核小队" "POST" "${BASE_URL}"'/social/teams/${[ teamId ]}/join' "peerAccessToken" '{"message":"请审核我"}')
join_dup=$(create_request "$workspace_id" "$team_join_folder" "重复加入(负例)" "POST" "${BASE_URL}"'/social/teams/${[ teamId ]}/join' "peerAccessToken" '{"message":"再来一次"}')
list_join_requests=$(create_request "$workspace_id" "$team_join_folder" "查看入队申请列表" "GET" "${BASE_URL}"'/social/teams/${[ teamId ]}/join-requests?status=pending&page=1&pageSize=20' "userAccessToken")
list_join_requests_by_non_admin=$(create_request "$workspace_id" "$team_join_folder" "非管理员查看申请(负例)" "GET" "${BASE_URL}"'/social/teams/${[ teamId ]}/join-requests?page=1&pageSize=20' "peerAccessToken")
accept_join=$(create_request "$workspace_id" "$team_join_folder" "同意入队申请" "POST" "${BASE_URL}"'/social/teams/${[ teamId ]}/join-requests/${[ joinRequestId ]}/decision' "userAccessToken" '{"accepted":true}')
reject_join=$(create_request "$workspace_id" "$team_join_folder" "拒绝入队申请" "POST" "${BASE_URL}"'/social/teams/${[ teamId ]}/join-requests/${[ joinRequestId ]}/decision' "userAccessToken" '{"accepted":false}')
leave_team=$(create_request "$workspace_id" "$team_join_folder" "退出小队" "POST" "${BASE_URL}"'/social/teams/${[ teamId ]}/leave' "peerAccessToken")
leader_leave=$(create_request "$workspace_id" "$team_join_folder" "队长退出(负例)" "POST" "${BASE_URL}"'/social/teams/${[ teamId ]}/leave' "userAccessToken")

# ============================================================================
# 角色管理请求
# ============================================================================
promote_to_admin=$(create_request "$workspace_id" "$team_roles_folder" "提升为管理员" "PATCH" "${BASE_URL}"'/social/teams/${[ teamId ]}/members/${[ memberId ]}/role' "userAccessToken" '{"role":"admin"}')
demote_to_member=$(create_request "$workspace_id" "$team_roles_folder" "降级为普通成员" "PATCH" "${BASE_URL}"'/social/teams/${[ teamId ]}/members/${[ memberId ]}/role' "userAccessToken" '{"role":"member"}')
transfer_leader=$(create_request "$workspace_id" "$team_roles_folder" "转让队长" "PATCH" "${BASE_URL}"'/social/teams/${[ teamId ]}/members/${[ memberId ]}/role' "userAccessToken" '{"role":"leader"}')
non_leader_change_role=$(create_request "$workspace_id" "$team_roles_folder" "非队长改角色(负例)" "PATCH" "${BASE_URL}"'/social/teams/${[ teamId ]}/members/${[ testUserId ]}/role' "peerAccessToken" '{"role":"admin"}')

# ============================================================================
# 群聊功能请求
# ============================================================================
publish_announcement=$(create_request "$workspace_id" "$team_chat_folder" "发布群公告" "POST" "${BASE_URL}"'/chat/teams/${[ teamId ]}/announcements' "userAccessToken" '{"content":"欢迎加入本小队！请遵守群规。"}')
publish_announcement_by_member=$(create_request "$workspace_id" "$team_chat_folder" "普通成员发公告(负例)" "POST" "${BASE_URL}"'/chat/teams/${[ teamId ]}/announcements' "peerAccessToken" '{"content":"越权发布"}')
mark_announcement_read=$(create_request "$workspace_id" "$team_chat_folder" "标记公告已读" "POST" "${BASE_URL}"'/chat/teams/${[ teamId ]}/announcements/${[ announcementId ]}/read' "peerAccessToken")
create_poll=$(create_request "$workspace_id" "$team_chat_folder" "创建群投票" "POST" "${BASE_URL}"'/chat/teams/${[ teamId ]}/polls' "userAccessToken" '{"title":"本周活动去哪？","options":["香山","植物园","奥森"],"deadline":"2099-12-31T23:59:59Z"}')
create_poll_single_option=$(create_request "$workspace_id" "$team_chat_folder" "单选项投票(负例)" "POST" "${BASE_URL}"'/chat/teams/${[ teamId ]}/polls' "userAccessToken" '{"title":"只有一个选项","options":["同意"]}')
vote_poll=$(create_request "$workspace_id" "$team_chat_folder" "投票" "POST" "${BASE_URL}"'/chat/teams/${[ teamId ]}/polls/${[ pollId ]}/votes' "peerAccessToken" '{"optionId":"${[ pollOptionId ]}"}')
vote_change=$(create_request "$workspace_id" "$team_chat_folder" "改票" "POST" "${BASE_URL}"'/chat/teams/${[ teamId ]}/polls/${[ pollId ]}/votes' "peerAccessToken" '{"optionId":"${[ pollOptionId ]}"}')
non_member_vote=$(create_request "$workspace_id" "$team_chat_folder" "非成员投票(负例)" "POST" "${BASE_URL}"'/chat/teams/${[ teamId ]}/polls/${[ pollId ]}/votes' "peerAccessToken" '{"optionId":"${[ pollOptionId ]}"}')
create_poll_expired=$(create_request "$workspace_id" "$team_chat_folder" "创建已过期投票" "POST" "${BASE_URL}"'/chat/teams/${[ teamId ]}/polls' "userAccessToken" '{"title":"过期投票","options":["A","B"],"deadline":"2020-01-01T00:00:00Z"}')

# ============================================================================
# 队内活动请求
# ============================================================================
create_team_activity=$(create_request "$workspace_id" "$team_activity_folder" "创建队内活动" "POST" "${BASE_URL}"'/social/teams/${[ teamId ]}/activities' "userAccessToken" '{"title":"小队登山日","tags":["登山","户外"],"startAt":"2099-07-15T08:00:00Z","endAt":"2099-07-15T16:00:00Z","location":{"point":{"longitude":116.397,"latitude":39.907},"city":"北京","address":"香山公园","placeName":"香山"},"introduction":"一起来爬山","safetyNotice":"注意安全","capacity":15,"registrationDeadline":"2099-07-14T12:00:00Z"}')
create_activity_by_member=$(create_request "$workspace_id" "$team_activity_folder" "普通成员创建活动(负例)" "POST" "${BASE_URL}"'/social/teams/${[ teamId ]}/activities' "peerAccessToken" '{"title":"越权活动","tags":["x"],"startAt":"2099-07-15T08:00:00Z","endAt":"2099-07-15T16:00:00Z","location":{"point":{"longitude":116.397,"latitude":39.907}},"introduction":"越权测试","safetyNotice":"注意安全","capacity":10,"registrationDeadline":"2099-07-14T12:00:00Z"}')
list_team_activities=$(create_request "$workspace_id" "$team_activity_folder" "队内活动列表" "GET" "${BASE_URL}"'/social/teams/${[ teamId ]}/activities?page=1&pageSize=20' "userAccessToken")
list_activities_by_non_member=$(create_request "$workspace_id" "$team_activity_folder" "非成员查看活动(负例)" "GET" "${BASE_URL}"'/social/teams/${[ teamId ]}/activities?page=1&pageSize=20' "peerAccessToken")
get_team_activity=$(create_request "$workspace_id" "$team_activity_folder" "队内活动详情" "GET" "${BASE_URL}"'/social/teams/${[ teamId ]}/activities/${[ activityId ]}' "userAccessToken")

# ============================================================================
# 边界用例请求
# ============================================================================
create_full_team=$(create_request "$workspace_id" "$team_boundary_folder" "创建满员小队(一人乐队)" "POST" "${BASE_URL}/social/teams" "userAccessToken" '{"name":"ソロバンド-'"$TS"'","tags":["ソロ"],"joinMode":"publicJoin","capacity":1,"description":"一人だけのバンド"}')
create_capacity_zero_team=$(create_request "$workspace_id" "$team_boundary_folder" "创建capacity=0(负例)" "POST" "${BASE_URL}/social/teams" "userAccessToken" '{"name":"ゼロ人バンド-'"$TS"'","tags":["テスト"],"joinMode":"publicJoin","capacity":0,"description":"誰もいないバンド"}')
get_nonexistent_team=$(create_request "$workspace_id" "$team_boundary_folder" "获取不存在小队" "GET" "${BASE_URL}/social/teams/00000000-0000-0000-0000-000000000000" "userAccessToken")
get_dissolved_team=$(create_request "$workspace_id" "$team_boundary_folder" "获取已解散小队" "GET" "${BASE_URL}"'/social/teams/${[ teamId ]}' "userAccessToken")
search_empty_tags=$(create_request "$workspace_id" "$team_boundary_folder" "搜索不存在的标签" "GET" "${BASE_URL}/social/teams?tags=不存在标签&page=1&pageSize=20" "userAccessToken")
join_full_team=$(create_request "$workspace_id" "$team_boundary_folder" "加入满员小队(负例)" "POST" "${BASE_URL}"'/social/teams/${[ teamId2 ]}/join' "peerAccessToken" '{"message":"让我进"}')
join_nonexistent_team=$(create_request "$workspace_id" "$team_boundary_folder" "加入不存在小队(负例)" "POST" "${BASE_URL}/social/teams/00000000-0000-0000-0000-000000000000/join" "peerAccessToken" '{"message":"hello"}')
search_disbanded=$(create_request "$workspace_id" "$team_boundary_folder" "搜索已解散小队(应为空)" "GET" "${BASE_URL}"'/social/teams?keyword=${[ teamId ]}&page=1&pageSize=20' "userAccessToken")
accept_already_processed=$(create_request "$workspace_id" "$team_boundary_folder" "重复处理申请(负例)" "POST" "${BASE_URL}"'/social/teams/${[ teamId ]}/join-requests/${[ joinRequestId ]}/decision' "userAccessToken" '{"accepted":true}')
join_after_dissolved=$(create_request "$workspace_id" "$team_boundary_folder" "加入已解散小队(负例)" "POST" "${BASE_URL}"'/social/teams/${[ teamId ]}/join' "peerAccessToken" '{"message":"还能加吗"}')
page_out_of_range=$(create_request "$workspace_id" "$team_boundary_folder" "超过范围的分页" "GET" "${BASE_URL}/social/teams?keyword=Yaak&page=999&pageSize=20" "userAccessToken")

echo "Yaak workspace: $workspace_id"
echo "Yaak environment: $environment_id"

# ============================================================================
# 00: 登录
# ============================================================================
begin_test "登录 test_user"
response=$(send_json "$login_user" "$environment_id")
assert_code "$response" "200"
assert_jq_non_empty "$response" '.data.tokens.accessToken'
set_env_var "$environment_id" "userAccessToken" "$(echo "$response" | jq -r '.data.tokens.accessToken')"
pass_test

begin_test "登录 test_peer"
response=$(send_json "$login_peer" "$environment_id")
assert_code "$response" "200"
assert_jq_non_empty "$response" '.data.tokens.accessToken'
set_env_var "$environment_id" "peerAccessToken" "$(echo "$response" | jq -r '.data.tokens.accessToken')"
pass_test

begin_test "登录 admin"
response=$(send_json "$login_admin" "$environment_id")
assert_code "$response" "200"
assert_jq_non_empty "$response" '.data.tokens.accessToken'
set_env_var "$environment_id" "adminAccessToken" "$(echo "$response" | jq -r '.data.tokens.accessToken')"
pass_test

# ============================================================================
# 01: 小队 CRUD
# ============================================================================
begin_test "燈创建MyGO!!!!!——迷子でも、進み続ける"
response=$(send_json "$create_public_team" "$environment_id")
assert_code "$response" "200"
assert_jq_non_empty "$response" '.data.teamId'
assert_jq_equals "$response" '.data.name' "MyGO!!!!!-$TS"
assert_jq_equals "$response" '.data.joinMode' "publicJoin"
assert_jq_equals "$response" '.data.capacity' "20"
assert_jq_equals "$response" '.data.memberCount' "1"
assert_jq_equals "$response" '.data.status' "active"
assert_jq_non_empty "$response" '.data.chatId'
assert_jq_true "$response" '.data.tags | index("音楽") != null and index("バンド") != null'
set_env_var "$environment_id" "teamId" "$(echo "$response" | jq -r '.data.teamId')"
pass_test

begin_test "そよ创建秘密CRYCHIC——もう一度、あの頃のように"
response=$(send_json "$create_approval_team" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.joinMode' "approvalRequired"
assert_jq_equals "$response" '.data.capacity' "5"
assert_jq_equals "$response" '.data.memberCount' "1"
set_env_var "$environment_id" "teamId3" "$(echo "$response" | jq -r '.data.teamId')"
pass_test

begin_test "重名MyGO应拒绝——世に一つだけのバンド"
response=$(send_json "$create_dup_team" "$environment_id")
assert_code "$response" "40008"
pass_test

begin_test "搜索MyGO——キーワードで探す"
response=$(send_json "$search_by_keyword" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "1" "20"
assert_jq_true "$response" ".data.items | length > 0"
assert_jq_true "$response" '.data.items | any(.name | startswith("MyGO"))'
pass_test

begin_test "按标签搜索——バンドで探す"
response=$(send_json "$search_by_tag" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "1" "20"
assert_jq_true "$response" '.data.items | any(.name | startswith("MyGO"))'
assert_jq_equals "$response" '.data.total' "$(echo "$response" | jq '.data.items | length')"
pass_test

begin_test "搜索无结果——你找不到不存在的バンド"
response=$(send_json "$search_no_match" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "1" "20"
assert_jq_equals "$response" '.data.total' "0"
pass_test

begin_test "获取MyGO详情——迷子でも進み続ける"
response=$(send_json "$get_team" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.teamId' "$(echo "$response" | jq -r '.data.teamId')"
assert_jq_equals "$response" '.data.status' "active"
assert_jq_true "$response" '.data.memberCount | type == "number"'
pass_test

begin_test "メンバーリストに燈（リーダー）がいる"
response=$(send_json "$list_team_members" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "1" "20"
assert_jq_true "$response" '.data.items | any(.role == "leader")'
assert_jq_true "$response" '.data.items | length == 1'
pass_test

begin_test "ポイントランキング——努力は報われる"
response=$(send_json "$get_point_ranks" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "1" "20"
assert_jq_true "$response" '.data.items | length >= 1'
assert_jq_equals "$response" '.data.items[0].rank' "1"
assert_jq_true "$response" '.data.items[0].points | type == "number"'
pass_test

# ============================================================================
# 02: 加队与退出
# ============================================================================

# 保存原始公开队 ID
PUBLIC_TEAM_ID=$(yaak --data-dir "$YAAK_DATA_DIR" environment show "$environment_id" | jq -r '.variables[] | select(.name=="teamId") | .value')

begin_test "楽奈加入MyGO——野良猫、バンドに参加"
response=$(send_json "$join_public" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.status' "accepted"
assert_jq_equals "$response" '.data.teamId' "$PUBLIC_TEAM_ID"
pass_test

begin_test "楽奈申请加入CRYCHIC——あの頃のように"
APPROVAL_TEAM_ID=$(yaak --data-dir "$YAAK_DATA_DIR" environment show "$environment_id" | jq -r '.variables[] | select(.name=="teamId3") | .value')
set_env_var "$environment_id" "teamId" "$APPROVAL_TEAM_ID"
response=$(send_json "$join_approval" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.status' "pending"
assert_jq_non_empty "$response" '.data.requestId'
set_env_var "$environment_id" "joinRequestId" "$(echo "$response" | jq -r '.data.requestId')"
pass_test

begin_test "重复加入应返回40012——もう入ってる"
set_env_var "$environment_id" "teamId" "$PUBLIC_TEAM_ID"
response=$(send_json "$join_dup" "$environment_id")
assert_code "$response" "40012"
pass_test

begin_test "そよ查看楽奈の入队申請"
set_env_var "$environment_id" "teamId" "$APPROVAL_TEAM_ID"
response=$(send_json "$list_join_requests" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "1" "20"
assert_jq_true "$response" '.data.items | length > 0'
assert_jq_equals "$response" '.data.items[0].status' "pending"
pass_test

begin_test "楽奈无权查看——部外者は口出し無用"
response=$(send_json "$list_join_requests_by_non_admin" "$environment_id")
assert_code "$response" "40020"
pass_test

begin_test "そよ同意——お帰り、CRYCHICへ"
response=$(send_json "$accept_join" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.status' "accepted"
pass_test

begin_test "重复处理申请——戻れない過去"
response=$(send_json "$accept_already_processed" "$environment_id")
assert_code "$response" "40014"
pass_test

set_env_var "$environment_id" "teamId" "$PUBLIC_TEAM_ID"

begin_test "愛音退出——一人で去る"
response=$(send_json "$leave_team" "$environment_id")
assert_code "$response" "200"
pass_test

begin_test "愛音再加入——やっぱりここにいたい"
response=$(send_json "$join_public" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.status' "accepted"
pass_test

begin_test "燈（隊長）退出应拒绝——リーダーは逃げられない"
response=$(send_json "$leader_leave" "$environment_id")
assert_code "$response" "40016"
pass_test

# ============================================================================
# 03: 角色管理
# ============================================================================

set_env_var "$environment_id" "memberId" "$TEST_PEER_ID"

begin_test "立希提升愛音为副队长"
response=$(send_json "$promote_to_admin" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.role' "admin"
pass_test

begin_test "愛音无权修改角色——立希だけが決められる"
response=$(send_json "$non_leader_change_role" "$environment_id")
assert_code "$response" "40020"
pass_test

begin_test "立希降级愛音——やっぱり普通でいい"
response=$(send_json "$demote_to_member" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.role' "member"
pass_test

# 转让队长请求 (用 peer token)
transfer_leader_back=$(create_request "$workspace_id" "$team_roles_folder" "转让队长回原主" "PATCH" "${BASE_URL}"'/social/teams/${[ teamId ]}/members/${[ memberId ]}/role' "peerAccessToken" '{"role":"leader"}')

# ... (在转让队长测试后)

begin_test "立希转让给愛音——新しいリーダー"
response=$(send_json "$transfer_leader" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.role' "leader"
pass_test

begin_test "立希现在是普通メンバー"
response=$(send_json "$list_team_members" "$environment_id")
assert_code "$response" "200"
assert_jq_true "$response" ".data.items | any(.userId == \"$TEST_USER_ID\" and .role == \"member\")"
pass_test

begin_test "立希取り戻す——やっぱり私のバンド"
set_env_var "$environment_id" "memberId" "$TEST_USER_ID"
response=$(send_json "$transfer_leader_back" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.role' "leader"
pass_test

# ============================================================================
# 04: 群聊功能
# ============================================================================
begin_test "立希发布群公告——今週の練習"
response=$(send_json "$publish_announcement" "$environment_id")
assert_code "$response" "200"
assert_jq_non_empty "$response" '.data.announcementId'
assert_jq_equals "$response" '.data.content' "欢迎加入本小队！请遵守群规。"
assert_jq_equals "$response" '.data.readByCurrentUser' "true"
set_env_var "$environment_id" "announcementId" "$(echo "$response" | jq -r '.data.announcementId')"
pass_test

begin_test "愛音越权发公告——勝手に決めないで"
response=$(send_json "$publish_announcement_by_member" "$environment_id")
assert_code "$response" "50011"
pass_test

begin_test "愛音标记公告已读——了解しました"
response=$(send_json "$mark_announcement_read" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.readByCurrentUser' "true"
pass_test

begin_test "立希创建合宿投票——みんなで決めよう"
response=$(send_json "$create_poll" "$environment_id")
assert_code "$response" "200"
assert_jq_non_empty "$response" '.data.pollId'
assert_jq_equals "$response" '.data.title' "本周活动去哪？"
assert_jq_true "$response" '.data.options | length == 3'
assert_jq_equals "$response" '.data.options[0].voteCount' "0"
set_env_var "$environment_id" "pollId" "$(echo "$response" | jq -r '.data.pollId')"
set_env_var "$environment_id" "pollOptionId" "$(echo "$response" | jq -r '.data.options[0].optionId')"
pass_test

begin_test "单选项投票拒绝——選択肢は2つ以上必要"
response=$(send_json "$create_poll_single_option" "$environment_id")
assert_code "$response" "400"
pass_test

begin_test "愛音投票选择軽井沢——合宿楽しみ！"
response=$(send_json "$vote_poll" "$environment_id")
assert_code "$response" "200"
assert_jq_true "$response" '.data.options[0].voteCount >= 1'
pass_test

begin_test "愛音改票——やっぱり箱根がいい"
set_env_var "$environment_id" "pollOptionId" "$(echo "$response" | jq -r '.data.options[1].optionId')"
response=$(send_json "$vote_change" "$environment_id")
assert_code "$response" "200"
assert_jq_true "$response" '.data.options[1].voteCount >= 1'
pass_test

# ============================================================================
# 05: 队内活动
# ============================================================================
begin_test "燈创建Live活動——迷子の叫び"
response=$(send_json "$create_team_activity" "$environment_id")
assert_code "$response" "200"
assert_jq_non_empty "$response" '.data.activityId'
assert_jq_equals "$response" '.data.title' "小队登山日"
set_env_var "$environment_id" "activityId" "$(echo "$response" | jq -r '.data.activityId')"
pass_test

begin_test "愛音创建活动拒绝——隊長だけが企画できる"
response=$(send_json "$create_activity_by_member" "$environment_id")
assert_code "$response" "40020"
pass_test

begin_test "查看Live活動一覧"
response=$(send_json "$list_team_activities" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "1" "20"
assert_jq_true "$response" '.data.items | length >= 1'
assert_jq_true "$response" '.data.items[0].title != null'
pass_test

begin_test "查看Live活動详情——初ワンマン"
response=$(send_json "$get_team_activity" "$environment_id")
assert_code "$response" "200"
assert_jq_equals "$response" '.data.activityId' "$(echo "$response" | jq -r '.data.activityId')"
assert_jq_equals "$response" '.data.title' "小队登山日"
pass_test

# ============================================================================
# 06: 边界用例
# ============================================================================
begin_test "获取不存在小队——存在しないバンド"
response=$(send_json "$get_nonexistent_team" "$environment_id")
assert_code "$response" "40009"
pass_test

begin_test "创建capacity=0——このバンドには誰もいない"
response=$(send_json "$create_capacity_zero_team" "$environment_id")
assert_code "$response" "400"
pass_test

begin_test "搜索不存在标签——届かない叫び"
response=$(send_json "$search_empty_tags" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "1" "20"
assert_jq_equals "$response" '.data.total' "0"
pass_test

begin_test "加入不存在小队——存在しない場所"
response=$(send_json "$join_nonexistent_team" "$environment_id")
assert_code "$response" "40009"
pass_test

begin_test "分页越界——ページの彼方へ"
response=$(send_json "$page_out_of_range" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "999" "20"
pass_test

# ============================================================================
# 解散与后续验证
# ============================================================================
begin_test "愛音无权解散——燈だけが終わらせられる"
response=$(send_json "$dissolve_by_non_leader" "$environment_id")
assert_code "$response" "40020"
pass_test

begin_test "燈解散小队——自分で終わらせる"
response=$(send_json "$dissolve_team" "$environment_id")
assert_code "$response" "200"
pass_test

begin_test "解散后搜索——もう戻れない"
response=$(send_json "$search_disbanded" "$environment_id")
assert_code "$response" "200"
assert_page_result "$response" "1" "20"
pass_test

begin_test "获取已解散小队——消えたCRYCHIC"
response=$(send_json "$get_dissolved_team" "$environment_id")
assert_code "$response" "40009"
pass_test

begin_test "加入已解散小队——終わったバンド"
response=$(send_json "$join_after_dissolved" "$environment_id")
assert_code "$response" "40009"
pass_test

echo ""
echo "========================================="
echo "  Yaak 小队管理测试全部通过"
echo "========================================="

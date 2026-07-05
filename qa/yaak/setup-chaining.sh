#!/usr/bin/env bash
set -euo pipefail
# 前置条件：已通过 yaak import 导入了 MayoiStar.SocialChat Postman 集合和环境。
# 后置条件：环境中的输出变量已设置为 response() 标签，后续按分组顺序执行即可自动串联。
# 用法: bash qa/yaak/setup-chaining.sh <WORKSPACE_ID>

WORKSPACE_ID="${1:?用法: bash setup-chaining.sh <WORKSPACE_ID>}"
ENV_NAME="MayoiStar Social Chat Local"

ENV_ID=$(yaak environment list "$WORKSPACE_ID" 2>&1 | jq -r ".[] | select(.name == \"$ENV_NAME\") | .id")
if [[ -z "$ENV_ID" ]]; then
  echo "未找到环境 '$ENV_NAME'，请先导入 MayoiStar.SocialChat.local.postman_environment.json" >&2
  exit 1
fi

yaak environment show "$ENV_ID" | jq \
  '.variables |= (
    map(
      if   .name == "userAccessToken"  then .value = "response(\"个人用户登录 test_user\", \"$.data.tokens.accessToken\")"
      elif .name == "testUserId"       then .value = "response(\"个人用户登录 test_user\", \"$.data.userId\")"
      elif .name == "peerAccessToken"  then .value = "response(\"个人用户登录 test_peer\", \"$.data.tokens.accessToken\")"
      elif .name == "testPeerId"       then .value = "response(\"个人用户登录 test_peer\", \"$.data.userId\")"
      elif .name == "adminAccessToken" then .value = "response(\"管理员登录 admin\", \"$.data.tokens.accessToken\")"
      elif .name == "adminUserId"      then .value = "response(\"管理员登录 admin\", \"$.data.userId\")"
      elif .name == "friendRequestId"  then .value = "response(\"发送好友申请 - 个人主页\", \"$.data.requestId\")"
      elif .name == "conversationId"   then .value = "response(\"会话列表\", \"$.data.items[0].conversationId\")"
      elif .name == "messageId"        then .value = "response(\"发送文字消息\", \"$.data.messageId\")"
      elif .name == "chatImageMediaId" then .value = "response(\"上传聊天图片\", \"$.data.mediaId\")"
      elif .name == "reportId"         then .value = "response(\"举报 test_peer\", \"$.data.reportId\")"
      else .
      end
    )
  )' > /tmp/yaak-chaining-env.json

yaak environment update --json "$(cat /tmp/yaak-chaining-env.json)"
echo "已设置 response() 请求链变量。现在按分组顺序执行请求即可自动串联。"

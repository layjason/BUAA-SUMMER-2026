# MayoiStar 好友社群与聊天 Yaak 测试

本目录保存好友社群（2 人）与一对一聊天 API 的 Yaak 调试资产。

## 文件

- `MayoiStar.SocialChat.postman_collection.json`：可导入 Yaak 的 Postman v2.1 集合。
- `MayoiStar.SocialChat.local.postman_environment.json`：本地 QA 环境变量，包含 V2 migration 中种子账号的明文测试密码。
- `run-social-chat-smoke.sh`：使用 Yaak CLI 创建临时 workspace，并执行主链路冒烟测试。
- `fixtures/chat-image.txt`：聊天图片上传请求的占位文件。

## 种子账号

这些账号由 `backend/src/main/resources/db/migration/V2__seed_qa_accounts.sql` 创建，只用于本地或测试环境。

- 个人用户：`test_user@mayoistar.qa`，昵称 `test_user`
- 好友用户：`test_peer@mayoistar.qa`，昵称 `test_peer`
- 管理员：`admin`

## 使用 Yaak 图形界面

1. 启动后端，并确认数据库已执行 V2 migration。
2. 在 Yaak 导入 `MayoiStar.SocialChat.postman_collection.json`。
3. 导入 `MayoiStar.SocialChat.local.postman_environment.json`。
4. 先执行 00 登录分组，将登录响应中的字段写入环境变量：
   - `data.tokens.accessToken` -> `userAccessToken`、`peerAccessToken`、`adminAccessToken`
   - `data.userId` -> `testUserId`、`testPeerId`、`adminUserId`
5. 执行好友申请后，将 `data.requestId` 写入 `friendRequestId`。
6. 执行会话列表后，将 `data.items[0].conversationId` 写入 `conversationId`。
7. 执行发送消息后，将 `data.messageId` 写入 `messageId`。
8. 执行举报用户后，将 `data.reportId` 写入 `reportId`。

## 使用 Yaak CLI

后端默认地址为 `http://127.0.0.1:8080`：

```bash
bash qa/yaak/run-social-chat-smoke.sh
```

如需指定后端地址或 Yaak 数据目录：

```bash
BASE_URL=http://127.0.0.1:8080 \
YAAK_DATA_DIR=/tmp/mayoistar-social-chat-yaak \
bash qa/yaak/run-social-chat-smoke.sh
```

脚本会创建新的 Yaak workspace，并依次调试：

- 登录 `test_user`、`test_peer`、`admin`
- 查看个人主页
- 发送并同意好友申请
- 查询好友列表、更新备注和分组
- 查询好友会话
- 发送文字与 Emoji 文本消息
- 标记已读、转发、撤回消息
- 举报用户，并由管理员查询和处理举报

## 注意事项

- 后端统一以 HTTP 200 返回业务响应，测试以响应体 `code` 判断成功或业务错误。
- 需求中的“表情”在当前 TypeSpec 中没有独立 `MessageKind`，按 Unicode Emoji 文本消息测试。
- 聊天图片上传请求保存在集合中；若后端仍返回占位 `media-placeholder` 且不持久化媒体文件，应作为实现问题记录，不在 QA 测试中绕过。
- 环境文件中的明文密码只用于 QA 种子账号，不得用于生产环境。

# MayoiStar Yaak 测试说明

- Version: 6 20260702 174739
  - 修正 Yaak 环境模板导入命令，环境导入必须显式指定 workspace
- Version: 5 20260702 172805
  - 补充 RustFS 对象存储依赖，身份文件上传与聊天图片上传测试需先启动本地对象存储
- Version: 4 20260701 114242
  - 合并身份接口与好友社群聊天接口的 Yaak 测试说明
- Version: 3 20260701 111200
  - 控制台输出完整请求/响应详情（方法、URL、请求头、请求体、响应状态、响应体）
  - README 补充第二个 JSON（环境模板）的导入说明
  - 修复测试管理员创建方式说明（V2 SQL 迁移）

本目录提供 MayoiStar 身份接口、好友社群（2 人）与一对一聊天 API 的 Yaak 调试和自动化测试资产。

## 身份接口测试

### 文件

- `MayoiStar.Identity.postman_collection.json`：Yaak 可导入的 Postman v2.1 集合（32 个请求）。
- `MayoiStar.Identity.local.postman_environment.json`：本地环境变量模板，不包含真实凭据。可在 Yaak 中导入作为环境变量起点。
- `start-backend-mailhog.ps1` / `start-backend-mailhog.sh`：使用 MailHog 邮件测试配置启动后端。
- `run-mailhog-smoke.ps1` / `run-mailhog-smoke.sh`：全量测试脚本，自动执行全部 32 个请求，控制台输出完整请求/响应详情。
- `test-avatar.png` / `test-license.png`：文件上传接口测试用占位图片。

### 初次使用步骤

1. 将 `backend/.env.mailhog.example` 复制为 `backend/.env`，按需修改数据库密码或端口。
2. 启动 Docker 依赖：

   ```bash
   docker compose --env-file .env -f docker-compose-local.yaml down -v
   docker compose --env-file .env -f docker-compose-local.yaml up -d postgres mailhog rustfs
   ```

   身份接口中的头像/执照上传，以及聊天图片上传测试依赖 RustFS。默认 S3 API 地址为 `http://localhost:9000`，控制台地址为 `http://localhost:9001`。

3. 在 Yaak 中导入集合与环境模板：

   ```bash
   yaak import qa/yaak/MayoiStar.Identity.postman_collection.json
   yaak workspace list
   yaak import --workspace-id <WORKSPACE_ID> qa/yaak/MayoiStar.Identity.local.postman_environment.json
   ```

   其中 `<WORKSPACE_ID>` 为第一条命令导入集合后对应的 workspace id，可通过 `yaak workspace list` 查看。

4. 启动后端（PowerShell 或 Bash）：

   ```powershell
   .\qa\yaak\start-backend-mailhog.ps1
   ```

   ```bash
   ./qa/yaak/start-backend-mailhog.sh
   ```

5. 运行全量测试：

   ```powershell
   .\qa\yaak\run-mailhog-smoke.ps1
   ```

   ```bash
   ./qa/yaak/run-mailhog-smoke.sh
   ```

> 首次启动后端时，Flyway V2 迁移会自动插入测试管理员。后续重复启动不再重复插入。若需重新创建管理员（比如数据库被重置），需要重启后端使其重新执行 V2 迁移（Flyway 会跳过已执行的迁移，但首次启动时若 V2 尚未执行则会执行）。

### 覆盖的接口测试

#### 00 公共接口

- `GET /identity/interest-tags`：获取兴趣标签列表
- `GET /identity/check-nickname`：检查昵称是否可用

#### 01 个人用户

- `POST /identity/auth/register`：个人用户注册
- `POST /identity/auth/activate/{token}`：激活个人账户
- `POST /identity/auth/login`：个人用户登录
- `GET /identity/me/profile`：获取个人资料
- `PATCH /identity/me/profile`：更新个人资料
- `POST /identity/auth/refresh`：刷新令牌
- `PATCH /identity/me/profile`：上传并关联头像
- `POST /identity/auth/logout`：个人用户登出
- `PATCH /identity/me/change-password`：修改密码（含错误旧密码场景）
- 重复邮箱注册、激活前登录、无令牌访问等错误场景

#### 02 商家用户

- `POST /identity/auth/register`：商家用户注册
- `POST /identity/auth/activate/{token}`：激活商家账户
- `POST /identity/auth/login`：商家用户登录
- `GET /identity/me/merchant-profile`：获取商家资料
- `PATCH /identity/me/merchant-profile`：更新商家资料
- `POST /identity/media/license`：上传商家执照
- `POST /identity/me/merchant-qualification`：提交商家资质
- 个人令牌访问商家资料的权限拒绝场景

#### 03 密码与激活

- `POST /identity/auth/reactivate`：重新发送激活邮件
- `POST /identity/auth/forgot-password`：发送密码重置邮件
- `POST /identity/auth/reset-password`：使用 token 重置密码
- 错误密码登录尝试场景

#### 04 管理员（占位）

- `POST /admin/auth/login`：管理员登录
- `GET /admin/users/{userId}/merchant-profile`：获取商家资料
- `POST /admin/users/{userId}/merchant-review`：审核商家资质（占位）

### 测试管理员

后端启动时，Flyway V2 迁移自动插入测试管理员：

- 用户名：`testadminyaak`
- 密码：`AdminPass123!`

### Token 与动态值

脚本自动处理以下流程：

- **激活 token**：脚本通过 MailHog API 轮询邮件正文中的 `token` 参数，自动写入 `activationToken`
- **登录 token**：脚本解析登录响应 JSON，提取 `data.tokens.accessToken` / `refreshToken` / `data.userId`
- **刷新 token**：刷新后脚本自动更新 `personalAccessToken` 和 `personalRefreshToken`
- **密码重置 token**：脚本从密码重置邮件中提取 reset token
- **文件上传 mediaId**：上传头像/执照后自动提取 `data.mediaId`

在 Yaak 中手动执行时，请自行把响应值写入环境变量（见下方手动变量映射）。

- `data.tokens.accessToken` -> `personalAccessToken` 或 `merchantAccessToken`
- `data.tokens.refreshToken` -> `personalRefreshToken` 或 `merchantRefreshToken`
- `data.userId` -> `personalUserId` 或 `merchantUserId`
- `data.mediaId` -> `avatarMediaId` 或 `licenseMediaId`

### 注意事项

- 商家注册接口只接收邮箱、密码、昵称；资质上传在激活登录后通过 `/identity/media/license` 和 `/identity/me/merchant-qualification` 完成。
- 商家审核接口目前返回占位数据。
- 后端统一错误响应使用 HTTP 200，业务成败通过响应体 `code` 判断。

## 好友社群与聊天测试

### 文件

- `MayoiStar.SocialChat.postman_collection.json`：可导入 Yaak 的 Postman v2.1 集合。
- `MayoiStar.SocialChat.local.postman_environment.json`：本地 QA 环境变量，包含 V2 migration 中种子账号的明文测试密码。
- `run-social-chat-tests.sh`：使用 Yaak CLI 创建临时 workspace，执行好友社群与聊天的完整集成测试（正向流程 + 边界用例）。
- `test-avatar.png`：聊天图片上传请求复用的 PNG 占位图片，可通过环境变量 `chatImageFile` 替换。

### 种子账号

这些账号由 `backend/src/main/resources/db/migration/V2__seed_qa_accounts.sql` 创建，只用于本地或测试环境。

- 个人用户：`test_user@mayoistar.qa`，昵称 `test_user`
- 好友用户：`test_peer@mayoistar.qa`，昵称 `test_peer`
- 管理员：`admin`

### 使用 Yaak 图形界面

1. 启动后端，并确认数据库已执行 V2 migration。
2. 导入 `MayoiStar.SocialChat.postman_collection.json`。
3. 导入 `MayoiStar.SocialChat.local.postman_environment.json` 到同一 workspace。
4. **设置请求链**：运行以下脚本，自动配置 `response()` 标签（也可在 GUI 中按 `Ctrl+Space` 手动设置）：

   ```bash
   bash qa/yaak/setup-chaining.sh <WORKSPACE_ID>
   ```

   这会设置以下变量自动从前置请求提取：
   - `userAccessToken`, `testUserId` ← 个人用户登录 test_user
   - `peerAccessToken`, `testPeerId` ← 个人用户登录 test_peer
   - `adminAccessToken`, `adminUserId` ← 管理员登录 admin
   - `friendRequestId` ← 发送好友申请 - 个人主页
   - `conversationId` ← 会话列表
   - `messageId` ← 发送文字消息
   - `chatImageMediaId` ← 上传聊天图片
   - `reportId` ← 举报 test_peer

5. 按分组顺序依次执行请求，变量自动填充。

### 使用 Yaak CLI

后端默认地址为 `http://127.0.0.1:8080`：

```bash
bash qa/yaak/run-social-chat-tests.sh
```

如需指定后端地址或 Yaak 数据目录：

```bash
BASE_URL=http://127.0.0.1:8080 \
YAAK_DATA_DIR=/tmp/mayoistar-social-chat-yaak \
bash qa/yaak/run-social-chat-tests.sh
```

脚本会创建新的 Yaak workspace，并依次调试：

**正向流程**：登录、个人主页、好友申请与同意、好友列表与备注、关注/取消关注、关注/粉丝列表、好友申请列表（发送/收到）、聊天会话与消息（文字/Emoji/图片/已读/转发/撤回）、举报与后台处理、删除好友、拉黑/取消拉黑。

**边界用例**：自我操作（关注/拉黑/申请/举报）、不存在用户（资料/举报）、重复操作（关注/申请/拉黑/取消关注/取消拉黑）、拒绝好友申请、已是好友时申请、按昵称搜索好友、分页验证。

### 注意事项

- 后端统一以 HTTP 200 返回业务响应，测试以响应体 `code` 判断成功或业务错误。
- 需求中的“表情”在当前 TypeSpec 中没有独立 `MessageKind`，按 Unicode Emoji 文本消息测试。
- 聊天图片上传请求会写入真实 `chatImageMediaId`，运行前请确认 RustFS 已启动。
- 环境文件中的明文密码只用于 QA 种子账号，不得用于生产环境。

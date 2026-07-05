# MayoiStar Yaak 测试说明

- Version: 13 20260704 145000
  - 新增个人二维码生成与扫描测试，mentionAll 权限校验单元测试已覆盖
- Version: 12 20260703 163000
  - 新增 Postman 集合和环境模板文件，run-media-auth-tests.ps1 重写为 workspace 模式
- Version: 11 20260703 160000
  - 新增媒体下载鉴权测试套件，覆盖私聊图片、群聊图片、退出群聊、商家资质、管理员全资源访问、签名完整性等场景
- Version: 10 20260703 153000
  - 统一 AI 测试脚本的 yaak 调用方式与服务检查风格，修复按名称查找请求的 bug
- Version: 9 20260703 150620
  - 新增 AI 图片分类接口测试集合，包含 Postman 集合、环境模板、启动脚本和全量自动化测试脚本
- Version: 8 20260703 005632
  - 补充 Redis 依赖说明，启动命令与可达性检查增加 Redis 服务
- Version: 7 20260702 180114
  - 修正未认证访问个人资料接口的自动化测试预期，未携带 token 应返回 401
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

本目录提供 MayoiStar 身份接口、好友社群（2 人）与一对一聊天 API、媒体下载鉴权的 Yaak 调试和自动化测试资产。

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
   docker compose --env-file .env -f docker-compose-local.yaml up -d postgres redis mailhog rustfs
   ```

   身份接口中的头像/执照上传，以及聊天图片上传测试依赖 RustFS。默认 S3 API 地址为 `http://localhost:9000`，控制台地址为 `http://localhost:9001`。

   Redis 用于媒体访问签名缓存与限流计数，后端需实际连接 Redis 实例。默认端口 `6379`，可通过 `.env` 中的 `DEV_REDIS_PORT` 和 `REDIS_PASSWORD` 配置。

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

## AI 图片分类测试

### 文件

- `MayoiStar.AI.postman_collection.json`：可导入 Yaak 的 Postman v2.1 集合（7 个请求，4 个分组）。
- `MayoiStar.AI.local.postman_environment.json`：本地环境变量模板，不包含真实凭据。可在 Yaak 中导入作为环境变量起点。
- `start-backend-with-clip.ps1` / `start-backend-with-clip.sh`：使用 CLIP 边车配置启动后端。
- `run-ai-tests.ps1` / `run-ai-tests.sh`：全量测试脚本，自动执行全部 7 个请求，控制台输出完整请求/响应详情。
- `test-avatar.png`：图片上传测试用占位图片（复用于 identity 和 social-chat 集合）。

### 使用步骤

1. 将 `backend/.env.mailhog.example` 复制为 `backend/.env`，按需修改数据库密码或端口。

2. 启动 Docker 依赖：

   ```bash
   docker compose --env-file .env -f docker-compose-local.yaml down -v
   docker compose --env-file .env -f docker-compose-local.yaml up -d postgres redis mailhog rustfs
   ```

   图片上传测试依赖 RustFS。默认 S3 API 地址为 `http://localhost:9000`，控制台地址为 `http://localhost:9001`。

   Redis 用于媒体访问签名缓存与限流计数，后端需实际连接 Redis 实例。默认端口 `6379`，可通过 `.env` 中的 `DEV_REDIS_PORT` 和 `REDIS_PASSWORD` 配置。

3. 启动 Python CLIP 边车服务：

   ```bash
   cd clip-service
   python main.py
   ```

   或使用 Docker（需 GPU）：

   ```bash
   cd clip-service
   docker compose up -d
   ```

   模型权重约 600MB，首次启动时自动下载。

4. 在 Yaak 中导入集合与环境模板：

   ```bash
   yaak import qa/yaak/MayoiStar.AI.postman_collection.json
   yaak workspace list
   yaak import --workspace-id <WORKSPACE_ID> qa/yaak/MayoiStar.AI.local.postman_environment.json
   ```

   其中 `<WORKSPACE_ID>` 为第一条命令导入集合后对应的 workspace id，可通过 `yaak workspace list` 查看。

5. 启动后端（PowerShell 或 Bash）：

   ```powershell
   .\qa\yaak\start-backend-with-clip.ps1
   ```

   ```bash
   ./qa/yaak/start-backend-with-clip.sh
   ```

   脚本会自动检查 S3、Redis 和 CLIP 服务是否可达。

6. 运行全量测试：

   ```powershell
   .\qa\yaak\run-ai-tests.ps1
   ```

   ```bash
   ./qa/yaak/run-ai-tests.sh
   ```

   如需指定后端地址或工作区名称：

   ```powershell
   .\qa\yaak\run-ai-tests.ps1 -WorkspaceName "MayoiStar AI" -BaseUrl "http://localhost:8080"
   ```

   ```bash
   WORKSPACE_NAME="MayoiStar AI" BASE_URL="http://localhost:8080" ./qa/yaak/run-ai-tests.sh
   ```

### Token 与动态值

脚本自动处理以下流程：

- **登录 token**：脚本解析登录响应 JSON，提取 `data.tokens.accessToken`，自动写入 `accessToken`
- **图片上传 mediaId**：上传后自动提取 `data.mediaId`，写入 `testImageMediaId` 和 `testImageMediaId2`

在 Yaak 中手动执行时，请自行把响应值写入环境变量（见下方手动变量映射）。

### 变量映射

| 响应字段                  | 环境变量            |
| ------------------------- | ------------------- |
| `data.tokens.accessToken` | `accessToken`       |
| `data.mediaId`（上传 1）  | `testImageMediaId`  |
| `data.mediaId`（上传 2）  | `testImageMediaId2` |

### 覆盖的接口测试

#### 00 登录

- `POST /identity/auth/login`：个人用户登录 test_user

#### 01 图片上传

- `POST /activities/media/images`：上传活动图片（2 张，获取分类所需的 mediaId）

#### 02 图片分类 - 正常流程

- `POST /ai/image-classifications`：分类单张图片（验证 `status=succeeded`、`suggestedTags` 为 5 类之一、`confidence` 在 [0,1]）
- `POST /ai/image-classifications`：分类多张图片（验证每张返回独立结果）

#### 03 图片分类 - 异常与边界

- `POST /ai/image-classifications`：空 `mediaIds` 列表（验证返回空 `items`）
- `POST /ai/image-classifications`：不存在的 `mediaId`（验证返回 30003 或 30001）
- `POST /ai/image-classifications`：未认证访问（验证返回 401）

### 注意事项

- AI 图片分类依赖 Python CLIP 边车服务，不启动时分类接口将返回 `AiServiceUnavailable`（30001）。
- 后端统一以 HTTP 200 返回业务响应，测试以响应体 `code` 判断成功或业务错误。
- 环境文件中的明文密码只用于 QA 种子账号，不得用于生产环境。

## 媒体下载鉴权测试

### 文件

- `MayoiStar.MediaAuth.postman_collection.json`：Yaak 可导入的 Postman v2.1 集合（约 30 个请求，6 个分组）。
- `MayoiStar.MediaAuth.local.postman_environment.json`：本地 QA 环境变量模板，预置种子账号及测试所需全部变量。
- `start-backend-media.ps1`：启动后端并检查媒体鉴权测试所需的所有依赖（S3、Redis、MailHog、测试素材文件）。
- `run-media-auth-tests.ps1`：全量测试脚本，使用已导入的 Yaak 集合自动执行全部测试用例。
- `test-avatar.png` / `test-license.png`：文件上传接口测试用占位图片（复用 identity 集合的素材）。

### 使用步骤

1. 确保 Docker 依赖已启动（postgres、redis、mailhog、rustfs），并已执行 V2 种子数据迁移。因脚本不幂等，需要清理数据库或重新构建 Docker 容器后再运行测试。

2. 在 Yaak 中导入集合与环境模板：

   ```bash
   yaak import qa/yaak/MayoiStar.MediaAuth.postman_collection.json
   yaak workspace list
   yaak import --workspace-id <WORKSPACE_ID> qa/yaak/MayoiStar.MediaAuth.local.postman_environment.json
   ```

   其中 `<WORKSPACE_ID>` 为第一条命令导入集合后对应的 workspace id。

3. 启动后端：

   ```powershell
   .\qa\yaak\start-backend-media.ps1
   ```

4. 运行全量测试：

   ```powershell
   .\qa\yaak\run-media-auth-tests.ps1
   ```

   如需指定后端地址或 MailHog 地址：

   ```powershell
   .\qa\yaak\run-media-auth-tests.ps1 -BaseUrl "http://localhost:8080" -MailHogApiBase "http://127.0.0.1:8025" -MailTimeoutSeconds 30
   ```

### 覆盖的测试场景

测试通过上传不同上下文中的媒体文件，提取其签名 URL，再以不同身份访问该 URL 来验证 `GET /media/{mediaId}` 的鉴权逻辑。

#### 00 登录与准备

- 使用种子账号登录 test_user、test_peer、admin
- 注册并激活一个新的商家用户
- test_user 与 test_peer 建立好友关系
- 获取私聊会话 ID

#### 01 私聊图片鉴权（conversationMember 策略）

- 1.1 会话成员 test_peer 下载图片 → `200`
- 1.2 管理员下载私聊图片 → `200`（管理员绕过鉴权）
- 1.3 匿名用户下载私聊图片 → `401`
- 1.4 非成员商家用户下载私聊图片 → `403`

#### 02 群聊图片鉴权（conversationMember 策略）

- 创建小队（自动生成群聊会话）
- test_peer 加入小队
- 在群聊中上传并发送图片（策略从 `owner` 升级为 `conversationMember`）
- 2.1 群成员 test_peer 下载图片 → `200`
- 2.2 管理员下载群聊图片 → `200`
- 2.3 非成员用户下载群聊图片 → `403`

#### 03 退出群聊后不可访问

- test_peer 退出小队（API: `POST /social/teams/{teamId}/leave`）
- 3.1 已退出的 test_peer 下载群聊图片 → `403`
- 3.2 仍在群中的 test_user 下载图片 → `200`
- 3.3 管理员下载已退出群聊的图片 → `200`

#### 04 商家资质鉴权（owner 策略）

- 商家上传执照（策略为 `owner`，scope 为商家 userId）
- 4.1 商家本人下载执照 → `200`
- 4.2 管理员下载商家执照 → `200`
- 4.3 test_user 非所有者下载商家执照 → `403`
- 4.4 匿名下载商家执照 → `401`

#### 05 管理员全资源访问

- 管理员以 `ROLE_admin` 角色绕过所有媒体访问策略检查
- 5.1 管理员下载私聊图片 → `200`
- 5.2 管理员下载群聊图片 → `200`
- 5.3 管理员下载商家执照 → `200`
- 5.4 管理员下载公开头像 → `200`

#### 06 签名完整性

- 媒体下载 URL 使用 HMAC-SHA256 签名保护，签名包含 `mediaId`、`accessVersion`、`policy`、`scope`
- 6.1 有效签名下载 → `200`
- 6.2 篡改签名（修改最后一位字符）→ `403`
- 6.3 缺少 `sig` 查询参数 → `403`
- 6.4 不存在的 mediaId → `404`

### 鉴权逻辑说明

| 访问策略             | 允许访问者                           | 拒绝访问者                                                  |
| -------------------- | ------------------------------------ | ----------------------------------------------------------- |
| `publicAccess`       | 任何人（需同时满足 `publicVisible`） | —                                                           |
| `owner`              | 上传者                               | 非上传者、匿名                                              |
| `conversationMember` | 当前会话成员                         | 非成员、已退出成员、匿名                                    |
| `teamMember`         | 当前小队成员                         | 非成员、匿名                                                |
| `activityOwner`      | 活动组织者                           | 非组织者、匿名                                              |
| `adminOnly`          | 仅管理员                             | 所有非管理员（管理员通过 `isAdmin()` 在策略检查前直接放行） |

**特殊规则**：

- 管理员（`ROLE_admin`）绕过所有策略检查，可访问任意媒体资源
- 未认证用户访问非公开资源返回 `401`
- 已认证但无权限的用户返回 `403`
- 媒体被软删除后返回 `404`（无 REST 端点触发，需通过 Service 层测试）
- 签名无效或版本不匹配返回 `403`

### Token 与动态值

脚本自动处理以下流程：

- **登录 token**：解析登录响应 JSON，提取 `data.tokens.accessToken`，自动写入各用户的 access token
- **激活 token**：通过 MailHog API 获取商家和非成员用户的激活 token
- **媒体签名 URL**：从上传响应中提取 `data.url`（或 `data.signedUrl`），用于后续下载测试
- **签名篡改 URL**：在测试阶段 06 中，自动解析 `privateImageSignedUrl` 中的 `sig` 参数并构造篡改版本和缺失签名版本

### 变量映射

在 Yaak 中手动执行时，请自行把响应值写入环境变量：

| 响应字段                                    | 环境变量                |
| ------------------------------------------- | ----------------------- |
| `data.tokens.accessToken`（test_user 登录） | `userAccessToken`       |
| `data.tokens.accessToken`（test_peer 登录） | `peerAccessToken`       |
| `data.tokens.accessToken`（admin 登录）     | `adminAccessToken`      |
| `data.tokens.accessToken`（商家登录）       | `merchantAccessToken`   |
| `data.userId`                               | `merchantUserId`        |
| `data.requestId`                            | `friendRequestId`       |
| `data.items[0].conversationId`              | `privateConversationId` |
| `data.teamId`                               | `teamId`                |
| `data.chatId`                               | `teamConversationId`    |
| `data.mediaId`（私聊上传）                  | `privateImageMediaId`   |
| `data.url`（私聊上传）                      | `privateImageSignedUrl` |
| `data.mediaId`（群聊上传）                  | `teamImageMediaId`      |
| `data.url`（群聊上传）                      | `teamImageSignedUrl`    |
| `data.mediaId`（执照上传）                  | `licenseMediaId`        |
| `data.url`（执照上传）                      | `licenseSignedUrl`      |
| `data.url`（头像上传）                      | `avatarSignedUrl`       |

### 注意事项

- 测试依赖 V2 种子数据中的 test_user、test_peer、admin 账号
- 商家用户和非成员用户由脚本自动注册并激活，需要 MailHog 可用
- 所有下载测试使用标准 HTTP 状态码（200/401/403/404），非业务 `code` 字段
- 媒体签名 URL 通过 HMAC-SHA256 保护，签名的生成和校验使用服务端密钥 `mayoistar.media.access.signing-secret`
- 聊天图片上传后策略从 `owner` 升级为 `conversationMember`，发生在发送消息时（`ChatService.sendMessage()` 中调用 `mediaAccessService.updateAccessPolicy()`）

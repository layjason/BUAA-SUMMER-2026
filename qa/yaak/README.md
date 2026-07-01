# MayoiStar 身份接口 Yaak 测试说明

- Version: 3 20260701 111200
  - 控制台输出完整请求/响应详情（方法、URL、请求头、请求体、响应状态、响应体）
  - README 补充第二个 JSON（环境模板）的导入说明
  - 修复测试管理员创建方式说明（V2 SQL 迁移）

本目录提供 MayoiStar 身份接口的 Yaak 全量自动化测试资产。

## 文件

- `MayoiStar.Identity.postman_collection.json`：Yaak 可导入的 Postman v2.1 集合（32 个请求）。
- `MayoiStar.Identity.local.postman_environment.json`：本地环境变量模板，不包含真实凭据。可在 Yaak 中导入作为环境变量起点。
- `start-backend-mailhog.ps1` / `start-backend-mailhog.sh`：使用 MailHog 邮件测试配置启动后端。
- `run-mailhog-smoke.ps1` / `run-mailhog-smoke.sh`：全量测试脚本，自动执行全部 32 个请求，控制台输出完整请求/响应详情。
- `test-avatar.png` / `test-license.png`：文件上传接口测试用占位图片。

## 初次使用步骤

1. 将 `backend/.env.mailhog.example` 复制为 `backend/.env`，按需修改数据库密码或端口。
2. 启动 Docker 依赖：

   ```bash
   docker compose --env-file .env -f docker-compose-local.yaml up -d postgres mailhog
   ```

3. 在 Yaak 中导入两个 JSON 文件：

   ```bash
   yaak import qa/yaak/MayoiStar.Identity.postman_collection.json
   yaak import qa/yaak/MayoiStar.Identity.local.postman_environment.json
   ```

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

> 首次启动后端时，Flyway V2 迁移会自动插入测试管理员。后续重复启动不再重复插入。若需重新创建管理员（比如数据库被重置），需要重启后端使其重新执行 V2 迁移（Flyway 会跳过已执行的迁移 — 但首次启动时若 V2 尚未执行则会执行）。

## 覆盖的接口测试

### 00 公共接口
- `GET /identity/interest-tags`：获取兴趣标签列表
- `GET /identity/check-nickname`：检查昵称是否可用

### 01 个人用户
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

### 02 商家用户
- `POST /identity/auth/register`：商家用户注册
- `POST /identity/auth/activate/{token}`：激活商家账户
- `POST /identity/auth/login`：商家用户登录
- `GET /identity/me/merchant-profile`：获取商家资料
- `PATCH /identity/me/merchant-profile`：更新商家资料
- `POST /identity/media/license`：上传商家执照
- `POST /identity/me/merchant-qualification`：提交商家资质
- 个人令牌访问商家资料的权限拒绝场景

### 03 密码与激活
- `POST /identity/auth/reactivate`：重新发送激活邮件
- `POST /identity/auth/forgot-password`：发送密码重置邮件
- `POST /identity/auth/reset-password`：使用 token 重置密码
- 错误密码登录尝试场景

### 04 管理员（占位）
- `POST /admin/auth/login`：管理员登录
- `GET /admin/users/{userId}/merchant-profile`：获取商家资料
- `POST /admin/users/{userId}/merchant-review`：审核商家资质（占位）

## 测试管理员

后端启动时，Flyway V2 迁移自动插入测试管理员：

- 用户名：`testadminyaak`
- 密码：`AdminPass123!`

## Token 与动态值

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

## 注意事项

- 商家注册接口只接收邮箱、密码、昵称；资质上传在激活登录后通过 `/identity/media/license` 和 `/identity/me/merchant-qualification` 完成。
- 商家审核接口目前返回占位数据。
- 后端统一错误响应使用 HTTP 200，业务成败通过响应体 `code` 判断。

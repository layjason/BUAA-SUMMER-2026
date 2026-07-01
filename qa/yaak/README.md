# MayoiStar 身份接口 Yaak 测试说明

- Version: 1 20260701 102350
  - 新增 bash 脚本，支持非 Windows 环境下运行

本目录提供 MayoiStar 身份接口的 Yaak 自动化测试资产。

## 文件

- `MayoiStar.Identity.postman_collection.json`：Yaak 可导入的 Postman v2.1 集合。
- `MayoiStar.Identity.local.postman_environment.json`：本地环境变量模板，不包含真实凭据。
- `start-backend-mailhog.ps1` / `start-backend-mailhog.sh`：使用 MailHog 邮件测试配置启动后端。
- `run-mailhog-smoke.ps1` / `run-mailhog-smoke.sh`：使用 Yaak CLI 和 MailHog 自动获取邮件 token 的冒烟测试脚本。

## 使用步骤

### 通用步骤

1. 将 `backend/.env.mailhog.example` 复制为 `backend/.env`，按需修改数据库密码或端口。
2. 在 `backend` 目录启动依赖：

   ```bash
   docker compose --env-file .env -f docker-compose-local.yaml up -d postgres mailhog
   ```

3. 在 Yaak 中导入集合文件，或使用 Yaak CLI 导入：

   ```bash
   yaak import qa/yaak/MayoiStar.Identity.postman_collection.json
   ```

### Windows (PowerShell)

4. 启动后端：

   ```powershell
   .\qa\yaak\start-backend-mailhog.ps1
   ```

5. 运行 MailHog 冒烟脚本：

   ```powershell
   .\qa\yaak\run-mailhog-smoke.ps1
   ```

### Linux / macOS (Bash)

4. 启动后端：

   ```bash
   ./qa/yaak/start-backend-mailhog.sh
   ```

5. 运行 MailHog 冒烟脚本：

   ```bash
   ./qa/yaak/run-mailhog-smoke.sh
   ```

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

## Token 与动态值

激活和重置密码 token 来自邮件链接。使用 MailHog 时，后端会把邮件发送到本地测试收件箱，Web 页面为 `http://127.0.0.1:8025`，API 为 `http://127.0.0.1:8025/api/v2/messages`。`run-mailhog-smoke.ps1` 会从 MailHog API 读取邮件正文中的 `token` 参数，并写回 Yaak 环境变量。

当前后端只保存 token 的 SHA-256 哈希，不能从数据库直接取原始 token 调用激活或重置接口。

在 Yaak 中手动执行时，请在登录、刷新、上传等请求后把响应值写入环境变量：

- `data.tokens.accessToken` -> `personalAccessToken` 或 `merchantAccessToken`
- `data.tokens.refreshToken` -> `personalRefreshToken` 或 `merchantRefreshToken`
- `data.userId` -> `personalUserId` 或 `merchantUserId`
- `data.mediaId` -> `avatarMediaId` 或 `licenseMediaId`

## 注意事项

- 商家注册接口只接收邮箱、密码、昵称；资质上传在激活登录后通过 `/identity/media/license` 和 `/identity/me/merchant-qualification` 完成。
- 商家审核接口目前返回占位数据。
- 后端统一错误响应使用 HTTP 200，业务成败通过响应体 `code` 判断。

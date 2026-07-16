# MayoiStar Web

迷星群聚后台管理 Web 前端，使用 React、Vite 和 TypeScript 实现，对应需求文档 §4 后台管理。

## 基本命令

```bash
pnpm install
pnpm dev
pnpm build
pnpm preview
pnpm test:unit
pnpm type-check
pnpm lint
pnpm format:check
```

## 环境变量

复制模板并编辑：

```bash
cp .env.example .env
```

| 变量                | 说明                                                                   |
| ------------------- | ---------------------------------------------------------------------- |
| `VITE_USE_MOCK`     | `false` 时请求真实后端；未设置或 `true` 时走本地 `mockDb`              |
| `VITE_API_BASE_URL` | API 根地址。本地联调与同域生产部署**留空**；跨域生产部署填后端完整 URL |

`VITE_*` 变量在 **`pnpm build` 时** 打入静态包。修改生产配置后需重新构建。

### 本地联调后端（推荐）

1. 启动后端 `http://localhost:8080`
2. `.env` 配置：

```env
VITE_USE_MOCK=false
VITE_API_BASE_URL=
```

3. `pnpm dev` → 访问 `http://localhost:3005`

`vite.config.ts` 中的 `server.proxy` 将 `/admin`、`/identity`、`/media` 转发到 `:8080`，浏览器请求同源，无需后端 CORS。

**管理员账号**（种子数据，非环境变量）：

| 迁移                                   | 用户名          |
| -------------------------------------- | --------------- |
| `backend/.../V3__seed_qa_accounts.sql` | `admin`         |
| `backend/.../V2__seed_test_data.sql`   | `testadminyaak` |

明文密码在团队 QA 环境配置中，不在仓库内。

**Mock 离线开发**（不连后端）：

```env
VITE_USE_MOCK=true
```

登录账号：`admin` / `admin123`

### 生产部署

`vite.config.ts` 里的 `server.proxy` **仅在 `pnpm dev` 时生效**，`pnpm build` 不会带入生产包，**无需为上线删除 proxy**。

#### 方案 A：同域反代（推荐）

前端静态资源与 API 共用同一域名，由 Nginx（或网关）转发：

```
https://admin.example.com/
  ├── /              → dist 静态文件
  ├── /admin/*       → 后端 :8080
  ├── /identity/*    → 后端 :8080
  └── /media/*       → 后端 :8080
```

构建前 `.env.production`（或 build 用的 `.env`）：

```env
VITE_USE_MOCK=false
VITE_API_BASE_URL=
```

前端继续请求相对路径 `/admin/...`、`/identity/...`、`/media/...`，与本地开发逻辑一致。

#### 方案 B：前后端不同域

```
前端：https://admin.example.com
后端：https://api.example.com
```

构建前设置：

```env
VITE_USE_MOCK=false
VITE_API_BASE_URL=https://api.example.com
```

需后端配置 CORS，或通过网关统一域名。私有媒体（商家执照等）同样依赖该基址拼接 `signedUrl`。

### 邮件桥接页（非 §4 后台功能）

同包部署的 `/activate`、`/forgot-password`、`/reset-password` 供用户邮件链接落地，调用 `/identity/*`。保留这些路由，在 Nginx 中应对 SPA 做 `try_files` 回退到 `index.html`。

## API 契约

接口路径、请求体和响应结构应与 `../../api-spec` 中的 TypeSpec 契约保持一致。

更新或校验前端 API 类型：

```bash
cd ../../api-spec
pnpm compile

cd ../frontend/mayoistar-web
pnpm api:generate
```

业务 DTO 应从 `src/api/generated/openapi.ts` 派生。Mock 数据可手工维护，但必须与生成类型的字段、枚举和分页结构一致。

## 实现要点

- **私有媒体**：商家执照、活动封面等通过 `AuthImage` 组件以 Bearer Token 拉取 blob，开发期依赖 `/media` 代理。
- **会话过期**：`client.ts` 在已登录请求收到 HTTP 401 时清除 token 并回到登录页（spec 无 admin refresh 端点）。
- **改密长度**：前端最小 8 位，与后端 `AdminChangePasswordRequest` 对齐（`src/constants/admin.ts`）。

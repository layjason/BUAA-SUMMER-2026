# 权限模型

## 更新日志

- Version: 1 20260630 195749
  - 初始版本：定义三种角色、JWT 映射关系、路由级和方法级权限控制策略、全端点权限矩阵

## 角色定义

系统定义三种角色，通过 Spring Security Authority 区分：

| 角色 | Authority | 来源 | 说明 |
|---|---|---|---|
| 管理员 | `ROLE_admin` | JWT `kind: "admin"` | 系统预置，通过单独的管理员登录接口获取 token |
| 个人用户 | `ROLE_personal` | JWT `kind: "personal"` | 通过 `/identity/auth/register/personal` 注册 |
| 商家用户 | `ROLE_merchant` | JWT `kind: "merchant"` | 通过 `/identity/auth/register/merchant` 注册 |

管理员与普通用户互斥：管理员使用独立的登录接口 `/admin/auth/login`，其 JWT token 中 `kind` 为 `"admin"`；普通用户的 `kind` 为 UserKind 枚举值。

## JWT 映射

`JwtAuthenticationFilter` 解析 JWT 的 `kind` claim 后注入 Spring Security Authority：

```
kind: "admin"    → ROLE_admin
kind: "personal" → ROLE_personal
kind: "merchant" → ROLE_merchant
```

管理员 JWT 不包含 `UserKind` 值，因此 `JwtService.getUserKind()` 对管理员令牌返回 `null`。

## 安全架构

权限控制分为两层：

### 第一层：路由级控制（SecurityFilterChain）

在 `SecurityConfiguration` 中定义 URL 模式的角色要求，由 Spring Security 过滤器链在请求到达 Controller 前执行。

```
请求 → JwtAuthenticationFilter（提取 JWT，设置 Authority）
    → SecurityFilterChain（URL 模式匹配，检查角色）
    → Controller
```

路由规则优先级（先匹配先生效）：

1. `PUBLIC_ENDPOINTS` → 无需认证
2. `/admin/**` → 需要 `ROLE_admin`
3. 商家专属端点 → 需要 `ROLE_merchant`
4. 其余 → 需要认证（任意角色）

### 第二层：方法级控制（@PreAuthorize）

通过 `@EnableMethodSecurity` 启用，在路由级控制之后执行，用于更细粒度的补充校验。

- `AdminController` 类级别 `@PreAuthorize("hasRole('admin')")`，`login` 方法覆盖为 `permitAll()`
- `getCurrentAdminId()` 内部额外检查 `ROLE_admin`，作为双重保险
- 操作级权限（如"调用方为草稿发起人"、"调用方为队长"等）由 Service 层业务逻辑负责，不在本层处理

## 权限矩阵

### Identity — `/identity`

| 端点 | 方法 | 角色要求 |
|---|---|---|
| `/identity/auth/register/personal` | POST | 公开 |
| `/identity/auth/register/merchant` | POST | 公开 |
| `/identity/auth/login` | POST | 公开 |
| `/identity/auth/activate` | POST | 公开 |
| `/identity/auth/activation-email` | POST | 公开 |
| `/identity/auth/refresh` | POST | 公开 |
| `/identity/auth/password-reset-email` | POST | 公开 |
| `/identity/auth/password-reset` | POST | 公开 |
| `/identity/auth/logout` | POST | 认证用户 |
| `/identity/me/password` | POST | 认证用户 |
| `/identity/me/profile` | GET | 认证用户 |
| `/identity/me/profile` | PATCH | 认证用户 |
| `/identity/interest-tags` | GET | 公开 |
| `/identity/nicknames/availability` | GET | 公开 |
| `/identity/media/avatar` | POST | 认证用户 |
| `/identity/me/merchant-profile` | GET | `ROLE_merchant` |
| `/identity/me/merchant-profile` | PATCH | `ROLE_merchant` |
| `/identity/me/merchant-qualification` | POST | `ROLE_merchant` |
| `/identity/media/license` | POST | `ROLE_merchant` |

### Activities — `/activities`

| 端点 | 方法 | 角色要求 |
|---|---|---|
| 全部端点 | 全部方法 | 认证用户 |

操作级权限（如草稿所有权、报名状态等）由 Service 层校验。

### Social — `/social`

| 端点 | 方法 | 角色要求 |
|---|---|---|
| 全部端点 | 全部方法 | 认证用户 |

操作级权限（如好友关系、队伍角色等）由 Service 层校验。

### Chat — `/chat`

| 端点 | 方法 | 角色要求 |
|---|---|---|
| 全部端点 | 全部方法 | 认证用户 |

操作级权限（如队伍成员身份等）由 Service 层校验。

### AI — `/ai`

| 端点 | 方法 | 角色要求 |
|---|---|---|
| 全部端点 | 全部方法 | 认证用户 |

### Admin — `/admin`

| 端点 | 方法 | 角色要求 |
|---|---|---|
| `/admin/auth/login` | POST | 公开 |
| `/admin/auth/password` | POST | `ROLE_admin` |
| `/admin/**`（其余全部） | 全部方法 | `ROLE_admin` |

## 设计决策

- **路由级为主、方法级为辅**：对能通过 URL 模式精确匹配的权限约束（如 `/admin/**`、商家专属端点），优先使用路由级控制，减少注解分散；对需要方法粒度的场景（如 AdminController 中 login 需排除），使用方法级注解。
- **双重保险**：`getCurrentAdminId()` 在已被路由级和方法级保护的前提下，额外进行 `ROLE_admin` 检查，防止未来配置变更导致的安全回退。
- **操作级权限不在此层处理**：TypeSpec 中描述的"调用方为草稿发起人"、"调用方为队长"等数据级权限属于业务逻辑层，由各 Service 方法内部校验，不通过 Spring Security 角色实现。

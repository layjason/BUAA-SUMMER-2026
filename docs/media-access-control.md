# 媒体权限与签名访问技术方案

## 更新日志

- Version: 2 20260703 150000
  - 移除签名 URL 过期时间（exp）和 URL 批量刷新机制；权限撤销仅依赖 accessVersion 和软删除状态
- Version: 1 20260702 230921
  - 初始版本：定义媒体签名 URL、访问策略、过期刷新、软删除失效、Redis 权限快照、限流和 CDN 适配方案

## 设计目标

迷星群聚（MayoiStar）的媒体资源不暴露长期裸 URL。所有媒体访问均通过后端签发的签名 URL 完成。

本方案需要同时满足：

- 公开资源无需登录即可访问，但必须携带签名，防止长期盗链和大规模外部消耗流量。
- 私有资源必须携带签名 URL、有效 JWT，并通过 ACL 权限校验。
- 权限撤销、软删除和可见性变化应尽快使旧 URL 失效。
- 下载路径应尽量避免访问关系数据库，优先使用 Redis/Valkey 中的访问快照和 ACL 快照。

## 核心概念

### 媒体标识

`mediaId` 是媒体资源的稳定标识，可长期保存和传递。客户端可以长期保存 `mediaId`，但不应将 `signedUrl` 视为永久地址。

### 签名 URL

签名 URL 是后端按当前资源状态和访问策略生成的访问地址：

```text
GET /media/{mediaId}?v=...&policy=...&scope=...&sig=...
```

参数含义：

| 参数 | 含义 |
|---|---|
| `v` | 媒体访问版本，即 `accessVersion` |
| `policy` | 访问策略 |
| `scope` | 访问作用域 |
| `sig` | 服务端 HMAC 签名 |

签名内容为：

```text
mediaId + v + policy + scope
```

签名只证明 URL 由服务端签发且参数未被篡改。私有资源仍必须依赖 JWT 确认当前访问者身份，签名 URL 本身不能替代用户身份。

### 访问版本

`accessVersion` 是媒体资源的访问控制版本。软删除、权限策略变化、可见性变化等会影响旧 URL 安全性的操作必须递增该版本。

下载时，URL 中的 `v` 必须等于 Redis 快照中的当前 `accessVersion`。若不一致，必须拒绝访问。

## 签发时机

### 上传阶段

上传阶段只生成稳定的 `mediaId` 和媒体元数据，不生成长期访问地址。

上传完成后，后端可返回 `mediaId` 和当前短期 `signedUrl`，但该 URL 仍是临时值，客户端不得长期依赖。

### 业务查询阶段

业务查询阶段是签名 URL 的主要签发时机。

例如活动详情、聊天消息列表、小队文件列表、用户资料等接口，在组装响应 DTO 时，为其中的媒体字段生成新的 `signedUrl`。

每次业务列表或详情查询都应返回新签发的 URL。

### 下载阶段

客户端下载文件时请求：

```text
GET /media/{mediaId}?v=...&policy=...&scope=...&sig=...
```

后端依次校验签名、访问版本、软删除状态、JWT 和 ACL 权限。校验通过后，后端从对象存储读取文件并返回二进制流。

### `public`

无需登录，仅校验签名、访问版本和软删除状态。

适用于公开头像、已发布活动图片等。

### `conversationMember`

需要登录。当前用户必须属于 `scope=conversationId` 对应会话。

适用于聊天图片和聊天文件。

### `teamMember`

需要登录。当前用户必须属于 `scope=teamId` 对应小队。

适用于小队文件和小队相册。

### `activityOwner`

需要登录。当前用户必须是 `scope=activityId` 对应活动创建者。

适用于未发布活动图片、活动草稿图片等。

### `owner`

需要登录。当前用户必须是上传者或资源所有者。

适用于商家资质、个人私有材料等。

### `adminOnly`

需要管理员身份。

适用于后台审核、治理、风控等场景。

管理员可访问所有未删除资源，但仍必须使用有效签名 URL。

## 软删除与失效

媒体软删除时必须执行：

1. 设置 `deletedAt`。
2. 递增 `accessVersion`。
3. 更新 Redis 媒体访问快照为 tombstone，或驱逐后立即重建 tombstone。

旧 URL 中的 `v` 与当前 `accessVersion` 不一致时立即失效。

已删除资源统一返回 404，避免暴露资源存在性。

对象存储中的实际文件可异步物理清理。软删除动作本身必须立即阻断访问，不依赖对象存储删除是否完成。

## Redis/Valkey 访问模型

### 媒体访问快照

Redis 保存媒体访问快照：

```text
media:access:{mediaId}
```

内容包括：

- `storagePath`
- `contentType`
- `fileName`
- `sizeBytes`
- `accessVersion`
- `deletedAt`
- `policy`
- `scope`

下载路径正常只读取该快照和对象存储，不访问关系数据库。

### ACL 快照

Redis 保存 ACL 快照：

```text
acl:conversation:{conversationId}:members
acl:team:{teamId}:members
acl:activity:{activityId}:owner
acl:media:{mediaId}:owner
```

对 `conversationMember` 和 `teamMember` 策略，不在每个媒体资源下重复存储成员列表，而是按会话或小队维护成员 Set。

下载校验时使用 `SISMEMBER` 或等价操作判断当前用户是否属于对应权限范围。

### 缓存未命中

Redis 未命中时允许从关系数据库回源并写回 Redis。

不存在的 `mediaId` 应写入短 TTL 空对象，例如 1 分钟，防止非法 ID 频繁穿透到数据库。

### 权限变化同步

以下操作必须同步更新 Redis ACL 或递增相关资源的 `accessVersion`：

- 用户退群或被移出会话。
- 小队成员变化。
- 活动创建者或活动可见性变化。
- 媒体软删除。
- 媒体访问策略变化。

## CDN 适配

公开资源可以使用 CDN。

私有资源默认不使用公开 CDN 缓存。

若私有资源经过 CDN：

- CDN 必须转发完整 query string。
- 鉴权应回源到业务服务，或使用边缘函数校验签名和 JWT。
- 响应头应避免公开缓存私有内容。

## 错误语义

| 场景 | 响应 |
|---|---|
| JWT 缺失或无效 | 401 Unauthorized |
| JWT 有效但无权限 | 403 Forbidden |
| 签名篡改 | 403 Forbidden |
| 版本不匹配 | 403 Forbidden 或 404 Not Found；资源已删除时优先 404 |
| 资源不存在或已软删除 | 404 Not Found |

## 客户端契约

客户端可以长期保存 `mediaId`。

客户端不应长期缓存 `signedUrl` 作为永久地址，应使用业务查询响应中的 `signedUrl` 展示媒体。

权限变更导致旧 URL 失效时，客户端应重新查询业务接口获取新的 `signedUrl`。

# 媒体权限与签名访问技术方案

## 更新日志

- Version: 6 20260704 003244
  - 新增「URL 生命周期与缓存策略」统一章节：按稳定公开 / 上传即定型 / 业务动作翻转 / 长期私有四类说明，并给出逐用途矩阵、缓存策略建议与失效信号总表，便于制定缓存策略
  - 修正上传阶段「初始访问策略为 owner」的笼统表述，补充头像、商家资质条目，标注 summaryImage、activityReviewImage 为未接入 / 待实现
  - 将访问策略定义从「签发时机」下提升为独立的「访问策略」章节
- Version: 5 20260704 000759
  - 明确活动图片（activityImage）访问策略生命周期：上传初态为 owner（仅上传者预览），绑定草稿升级为 activityOwner（仅组织者可见），审核通过/恢复上架升级为 publicAccess（公开），下架回退为 activityOwner；说明发布/下架递增 accessVersion 会使旧签名 URL 失效，客户端应重新查询活动详情获取当前 URL
- Version: 4 20260703 224845
  - 补充聊天图片上传后 URL 生命周期说明，明确上传 URL 在发送消息后因策略升级而失效
  - 群文件/相册删除统一使用软删除，递增 accessVersion 使旧 URL 立即失效
- Version: 3 20260703 030026
  - 明确签名 URL 的确定性：同资源同状态签发的 URL 完全相同；优化 CDN 适配、业务查询阶段和客户端契约的表述
- Version: 2 20260703 150000
  - 移除签名 URL 过期时间（exp）和 URL 批量刷新机制；权限撤销仅依赖 accessVersion 和软删除状态
- Version: 1 20260702 230921
  - 初始版本：定义媒体签名 URL、访问策略、过期刷新、软删除失效、Redis 权限快照、限流和 CDN 适配方案

## 设计目标

迷星群聚（MayoiStar）的媒体资源不暴露裸 URL。所有媒体访问均通过后端签发的长期签名 URL 完成。

本方案需要同时满足：

- 公开资源无需登录即可访问，但必须携带签名，防止盗链和大规模外部消耗流量。
- 私有资源必须携带签名 URL、有效 JWT，并通过 ACL 权限校验。
- 权限撤销、软删除和可见性变化应通过 `accessVersion` 递增尽快使旧 URL 失效。
- 下载路径应尽量避免访问关系数据库，优先使用 Redis/Valkey 中的访问快照和 ACL 快照。

## 核心概念

### 媒体标识

`mediaId` 是媒体资源的稳定标识，可长期保存和传递。`signedUrl` 在 `accessVersion` 不变时长期有效，但权限变更或软删除时可能失效，客户端不应将其视为绝对永久地址。

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

签名不包含时间等可变因素，同一资源在状态不变时签发的 URL 完全相同。此确定性使得 CDN 缓存和客户端缓存自然生效。

签名只证明 URL 由服务端签发且参数未被篡改。私有资源仍必须依赖 JWT 确认当前访问者身份，签名 URL 本身不能替代用户身份。

### 访问版本

`accessVersion` 是媒体资源的访问控制版本。软删除、权限策略变化、可见性变化等会影响旧 URL 安全性的操作必须递增该版本。

下载时，URL 中的 `v` 必须等于 Redis 快照中的当前 `accessVersion`。若不一致，必须拒绝访问。

## 签发时机

### 上传阶段

上传阶段生成稳定的 `mediaId` 和媒体元数据。初始访问策略取决于用途：公开直发型（如头像）上传即为 `publicAccess`；需要后续绑定业务对象的私有型（如聊天图片、活动图片、群文件）初始为 `owner`（`scope=上传者`），仅上传者可预览，待绑定或发送时再升级。各用途的完整演变见「URL 生命周期与缓存策略」。

- **头像**（`POST /identity/media/avatar`）：上传即为 `publicAccess`/`publicVisible`（`scope=""`），返回的 `signedUrl` 可长期使用并被公共 CDN 缓存，不会因业务动作升级；仅在头像被替换后软删除或资源软删除时因 `accessVersion` 递增而失效。

- **聊天图片**（`POST /chat/media/images`）：返回的 `signedUrl` 仅在上传者预览时有效，策略为 `owner`。上传不等于发送消息。发送消息时（`POST /chat/conversations/{conversationId}/messages`），`ChatService.sendMessage()` 将策略升级为 `conversationMember` 并递增 `accessVersion`，上传时返回的 `signedUrl` 立即因版本不匹配而失效。客户端应在消息发送后从**消息列表/详情接口**返回的 `ChatMessage.image.signedUrl` 获取新的会话签名 URL。

  注意：同一个 `mediaId` 仅能发送到一个会话，`sendMessage` 会将图片的访问策略绑定至该会话。若需将同一图片发送到多个会话，应先分别上传得到不同的 `mediaId`，或使用消息转发功能（转发会通过 `copyForScope` 创建独立的媒体副本）。

- **群文件**（`POST /chat/teams/{teamId}/files`）：上传 + 关联小队一步完成，策略在 `TeamService.uploadTeamFile()` 中即时升级为 `teamMember`。返回的 `signedUrl` 即为小队签名 URL，小队所有成员均可访问。

- **小队相册**（`POST /chat/teams/{teamId}/album-images`）：同群文件，上传后策略即时升级为 `teamMember`。

- **活动图片**（`POST /activities/media/images`）：上传时活动尚不存在，无法确定 `scope=activityId`，因此初始策略为 `owner`（`scope=上传者`），仅上传者可预览。绑定到活动草稿时（`ActivityDraftService.saveDraft/updateDraft`）升级为 `activityOwner`（`scope=activityId`），仅活动组织者可见；从草稿移除的图片会被软删除，旧签名 URL 立即失效。活动审核通过或下架后恢复上架时（`AdminActivityService.reviewActivity(approved)`、`restoreActivity`），图片升级为 `publicAccess`/`publicVisible`，任何人（含匿名）可经签名 URL 访问，并可被 CDN 缓存；活动被下架时（`takeDownActivity`）图片回退为 `activityOwner`，公开 URL 因 `accessVersion` 递增立即失效。

  每次策略翻转都会递增 `accessVersion`，使此前签发的 URL 失效。这是刻意设计：发布前只有组织者持有 URL，发布后所有查询入口（活动详情、我的活动、管理端详情）都会在查询阶段重新签名并返回当前有效的 URL，因此不会有人被旧 URL 卡住。客户端不应跨"活动状态变更"复用旧 URL，而应重新查询活动接口获取。

- **商家资质**（`POST /identity/media/license`）：上传即为 `owner`/`privateVisible`（`scope=上传者`），不会升级。可见者为上传者本人与管理员（管理员经身份短路放行），供用户查看自己的资质与管理员审核；URL 稳定但属敏感私有资料，不应进入公共缓存。

- **活动回顾图**（`POST /activities/media/review-images`，用途 `activityReviewImage`）：上传即为 `owner`/`privateVisible`。**当前其消费与策略升级路径尚未实现**（活动回顾/总结功能未完成），因此目前仅上传者与管理员可访问，暂无面向其他用户的公开逻辑。

- **总结图**（用途 `summaryImage`）：策略定义为 `publicAccess`/`publicVisible`，但**目前尚无上传端点接入**，属预留用途。

上传完成后，后端可返回 `mediaId` 和当前 `signedUrl`。

### 业务查询阶段

业务查询阶段是签名 URL 的主要签发时机。

例如活动详情、聊天消息列表、小队文件列表、用户资料等接口，在组装响应 DTO 时，为其中的媒体字段生成 `signedUrl`。签名是确定性的，同一资源状态下的 URL 相同，因此多次查询返回的 URL 一致。

### 下载阶段

客户端下载文件时请求：

```text
GET /media/{mediaId}?v=...&policy=...&scope=...&sig=...
```

后端依次校验签名、访问版本、软删除状态、JWT 和 ACL 权限。校验通过后，后端从对象存储读取文件并返回二进制流。

## 访问策略

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

## URL 生命周期与缓存策略

本章节从 URL 稳定性和可缓存性角度，统一说明各类媒体的生命周期，便于前端与网关制定缓存策略。核心原则：`signedUrl` 是当前资源状态的派生物，`accessVersion` 不变时 URL 完全稳定；任何会影响旧 URL 安全性的操作都会递增 `accessVersion`，使旧 URL 立即失效。

### 生命周期分类

- **A 稳定公开型**：`avatar`（`summaryImage` 未接入）。上传即 `publicAccess`/`publicVisible`，不会因业务动作升级，`accessVersion` 恒定，URL 稳定，可公共缓存。
- **B 上传即定型**：`teamFile`、`teamAlbum`。上传接口内一步升级到终态并直接返回终态 URL，对客户端而言 URL 稳定，但为私有资源，不进入公共缓存。
- **C 业务动作翻转型**：`chatImage`、`activityImage`。URL 会在发送消息、绑定草稿、审核发布、下架等业务动作后因 `accessVersion` 递增而失效，客户端必须按业务事件重新获取。
- **D 长期私有型**：`merchantLicense`、`activityReviewImage`（消费/升级路径待实现）。策略不升级，URL 稳定，但属私有/敏感资料，仅上传者本人与管理员可访问。

### 逐用途矩阵

| 用途 | 上传初态 (policy/visibility/scope) | 升级触发 | 终态 | 可见者 | 匿名 | accessVersion → URL 稳定性 | 公共 CDN | 客户端缓存建议 |
|---|---|---|---|---|---|---|---|---|
| `avatar` | publicAccess / public / "" | 无 | 同初态 | 任何人 | 是 | 恒定（仅软删除时 +1），稳定 | 可 | 长缓存 |
| `summaryImage` | publicAccess / public / "" | —（**未接入上传端点**） | — | — | — | — | — | 待实现 |
| `activityImage` | owner / private / 上传者 | 绑定草稿→activityOwner；通过/恢复→publicAccess；下架→activityOwner | 已发布：publicAccess/public/""；草稿或下架：activityOwner/private/activityId | 草稿=组织者；已发布=任何人 | 已发布=是 | 多次递增，**翻转失效** | 已发布可 | 以活动状态变更为失效信号重取 |
| `chatImage` | owner / private / 上传者 | 发送消息→conversationMember | conversationMember / private / conversationId | 会话成员 | 否 | 升级一次，上传 URL 失效 | 否 | 发送成功后取新 URL；私有缓存 |
| `teamFile` | owner / private / 上传者（瞬时） | 上传接口内→teamMember | teamMember / private / teamId | 小队成员 | 否 | 接口即返回终态，对客户端稳定 | 否 | 私有；退群/删除后失效 |
| `teamAlbum` | owner / private / 上传者（瞬时） | 上传接口内→teamMember | teamMember / private / teamId | 小队成员 | 否 | 接口即返回终态，对客户端稳定 | 否 | 私有；退群/删除后失效 |
| `merchantLicense` | owner / private / 上传者 | 无 | owner | 上传者本人 + 管理员 | 否 | 恒定，稳定 | 否（敏感） | 谨慎，不建议持久缓存 |
| `activityReviewImage` | owner / private / 上传者 | —（**消费/升级路径待实现**） | owner | 上传者 + 管理员 | 否 | 恒定，稳定 | 否 | 待实现 |

### 缓存策略建议

- **公开型（publicAccess + publicVisible）**：可设置较长缓存，例如 `Cache-Control: public, max-age=31536000, immutable`。由于 URL 随 `accessVersion` 变化，天然具备 cache-busting，无需手动清缓存。
- **私有型（owner / conversationMember / teamMember / activityOwner）**：使用 `Cache-Control: private` 或 `no-store`，禁止共享/公共缓存。若经 CDN，须转发完整 query string，并在边缘鉴权回源，避免缓存穿透泄露。
- **翻转型（chatImage / activityImage）**：不得跨业务状态复用旧 URL；客户端应以业务事件（消息发送成功、活动状态变更）为失效信号，重新查询业务接口获取当前 URL。
- **敏感型（merchantLicense）**：即使 URL 稳定，也不建议在客户端持久化缓存，防止敏感资料残留。

### 失效信号总表

区分两类"访问失败"，客户端应分别处理：

- **URL 失效（`accessVersion` 递增，旧 URL 立即返回 403/404）**：
  - 发送图片消息（`chatImage` → `conversationMember`）
  - 绑定/移除活动草稿图片（`activityImage` 升级 / 移除时软删除）
  - 活动审核通过、下架、恢复（`activityImage` 策略翻转）
  - 群文件 / 相册删除（软删除）
  - 媒体软删除
  - 媒体访问策略或可见性变化
- **权限撤销（ACL 变化，URL 不变但下载 403）**：
  - 用户退群、被移出会话
  - 小队成员变化
  - 提示：此类事件不改变 `accessVersion`，被缓存的私有资源可能突然不可访问，客户端需具备 403 回退能力（清除缓存并按当前权限重取）。

补充：消息转发经 `copyForScope` 生成**新的 `mediaId`**，属新资源新 URL，而非旧 URL 失效。

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

签名 URL 是确定性的，同一资源在同一 `accessVersion` 下 URL 不变，CDN 可自然按 URL 缓存。公开资源应设置较长的 CDN 缓存时间。

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

`signedUrl` 是确定性的长期地址，`accessVersion` 不变时始终有效。客户端可缓存并复用 `signedUrl`，无需每次业务查询都重新获取。权限变更或软删除导致 `accessVersion` 递增后旧 URL 立即失效，此时应重新查询业务接口获取新的 `signedUrl`。

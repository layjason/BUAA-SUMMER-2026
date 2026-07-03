# API 约定

## 更新日志

- Version: 14 20260702 171143
  - 媒体访问端点统一为 `GET /media/{mediaId}`，成功时固定返回 200 与文件二进制流；上传返回的 `MediaFile.url` 改为后端相对访问路径，原因是客户端不应依赖对象存储公开读地址
- Version: 13 20260701 172433
  - ChatRealtimeEvent 重构为 discriminated union：移除可选字段 message/conversationUnreadCount，改为按 kind 携带对应 payload 类型（MessageCreatedPayload/MessageRecalledPayload/MessageForwardedPayload）；friendRequestCreated 不再属于聊天实时事件枚举，原因是该事件通过 /queue/social-events 独立推送
- Version: 12 20260701 162400
  - 通知发送从 No-Op 占位升级为 STOMP over WebSocket 真实推送，ChatRealtimeEventKind 扩充为四种事件类型，群公告端点从占位响应接入 ChatService 业务逻辑，新增 team_announcement_reads 表追踪公告已读状态
- Version: 11 20260701 114500
  - 明确活动报名、取消报名、候补递补和候补确认属于受控状态流转，调用方必须通过公开 API 或后端统一服务入口执行，禁止绕过事务逻辑直接修改报名表状态字段
- Version: 10 20260630 152900
  - 后台管理补充用户详情、小队详情和通用举报接口；举报模型从仅支持用户举报用户扩展为可指向用户、小队、活动和消息，原因是后台需要查看小队相关举报并统一治理处理
- Version: 9 20260630 140150
  - 所有媒体上传接口统一由服务端根据调用端点自动设置媒体用途，客户端无需也不应传入 usage 字段
- Version: 8 20260630 094846
  - 商家资质提交接口改为通过 `licenseMediaIds` 引用已上传媒体，原因是统一文件上传接口与业务提交接口边界
- Version: 7 20260629 154647
  - 将群相册删除接口改为批量传入数组，与群文件批量删除接口保持一致
- Version: 6 20260629 152704
  - 将消息已读和群文件删除接口改为批量传入数组，减少客户端多次请求与接口粒度不一致问题
- Version: 5 20260629 152342
  - 增加聊天消息 WebSocket 占位端点约定，并补充群公告当前用户已读状态与标记已读接口
- Version: 4 20260629 124129
  - 活动地理筛选 URL 参数统一改为直接传入 longitude、latitude、distanceMeters，减少前端定位参数转换
- Version: 3 20260629 113015
  - 分页响应增加总页数字段，明确活动评价 Markdown 图片上传约定
- Version: 2 20260629 103507
  - 使用 TypeSpec 表达每个 API 响应可能出现的业务错误码与英文模板消息，并明确错误码分段规则
- Version: 1 20260628

API 请求/返回类型采用 TypeSpec 表达，[参见](https://typespec.io/)，定义在 `api-spec` 目录中，使用方法参见 `api-spec/README.md`。

对于所有 API，若无特别说明：

- HTTP 响应代码始终为 200，除非为二进制响应体
- 对于`GET`请求，请求体为空
  - 若含有“URL 参数”节，请注意相关参数需要**通过URL传送**，而不是通过请求体传送！URL参数的示例：`https://gw.buaa.edu.cn/srun_portal_success?ac_id=1&theme=buaa&srun_domain=`，其中传递了`ac_id`、`theme`、`srun_domain`三个参数
- 对于`POST`请求，请求体为 JSON
- 响应体为 JSON，且符合如下格式：

  ```typescript
  interface APIResponse<T> {
    code: number;
    message: string;
    data: T;
  }
  ```

  - 下文中的“响应**代码**”指的是`APIResponse`的`code`属性
  - 下文中的“响应**消息**”指的是`APIResponse`的`message`属性
  - 下文中的“响应**数据**”指的是`APIResponse`的`data`属性
  - TypeSpec 中使用 `APIResult<Data, BusinessErrors>` 表达响应联合类型；生成 OpenAPI 时会通过 `oneOf` 展开成功响应、通用错误响应和该接口声明的业务错误响应

“响应**代码**”类别：

- `<1000` 的响应代码为通用响应代码，所有请求通用
- `>=10000` 的响应代码为业务响应代码，依据不同服务和请求而不同
- 业务错误消息使用英文模板文案，`{}` 包裹的片段为占位符，例如 `Activity {activityId} is not visible`

通用响应代码表：

其中`{}`为占位符。

| 代码 | 可能的响应消息                                                     | 含义                                                                                                                                                         |
| ---- | ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 200  | `For Super Earth!`                                                 | 请求已被成功执行，可访问响应数据                                                                                                                             |
| 400  | `{reason}`                                                         | 请求存在错误，具体地：传入内容无法作为 JSON 解析；传入内容能作为 JSON 解析但不符合本文Schema定义的格式。若Schema符合，但语义不符合，不返回 400，而返回业务响应代码 |
| 401  | `Authentication is required`                                       | 请求未提供有效 Bearer Token，或 Token 已过期                                                                                                                 |
| 403  | `Permission is denied`                                             | 请求已认证，但调用方没有执行该操作的权限                                                                                                                     |
| 500  | `An internal server error has occurred` | 内部服务器错误                                                                                                                                               |

业务响应代码命名空间：

| 服务 | 响应代码范围 | TypeSpec 命名空间 |
| ---- | ------------ | ----------------- |
| 身份与资料 | `10000` 起 | `QujuApi.Errors.Identity` |
| 活动 | `20000` 起 | `QujuApi.Errors.Activities` |
| AI 辅助 | `30000` 起 | `QujuApi.Errors.Ai` |
| 好友社群 | `40000` 起 | `QujuApi.Errors.Social` |
| 即时通讯 | `50000` 起 | `QujuApi.Errors.Chat` |
| 后台管理 | `60000` 起 | `QujuApi.Errors.Admin` |

字段命名：camelCase

分页：

- 分页响应统一使用 `PageResult<T>`。
- `items` 表示当前页数据。
- `total` 表示匹配总数。
- `page` 表示当前页码。
- `pageSize` 表示每页数量。
- `totalPages` 表示匹配总页数。

地理位置筛选：

- 活动列表、搜索和地图点位等 GET 接口若按当前位置或地图中心点筛选，应通过 URL query 直接传入 `longitude`、`latitude`、`distanceMeters`。
- 创建活动仍使用 `LocationInfo` 表达活动地点，返回模型也保留 `LocationInfo` 以承载坐标、城市、详细地址和地点展示名称。

媒体上传与下载：

- 文件上传接口使用 `multipart/form-data`，仅需传入 `file` 字段，无需传入 `usage` 字段；服务端根据调用的端点自动设置媒体用途。
- 上传成功后返回的 `MediaFile.url` 字段由服务端自动填充为 `/media/{mediaId}`，前端可直接将该 URL 用于 `<img src>` 等展示场景。
- 通用媒体访问端点 `GET /media/{mediaId}` 成功时固定返回 HTTP 200 与原始二进制流，Content-Type 根据上传时的 MIME 类型设置，需登录后访问。
- 业务修改或提交接口不直接接收文件二进制，应引用上传接口返回的 `mediaId`；例如商家资质提交使用 `licenseMediaIds`。
- 活动评价正文使用 Markdown 时，正文内图片应先通过活动评价图片上传接口上传，取得返回 `MediaFile.url` 后再作为 Markdown 图片链接嵌入。

状态流转接口与服务边界：

- 涉及活动报名状态、名额占用、候补队列和候补确认窗口的变更，必须通过 TypeSpec 中声明的活动报名相关 API 执行；后端内部代码若不经 HTTP 调用，也必须复用统一的报名状态流转服务。
- 禁止绕过受控服务逻辑直接修改 `activity_registrations.status`、`activity_registrations.waiting_rank`、`activity_registrations.confirmation_deadline` 等字段；这些字段共同决定容量占用和候补顺序，直接改表会破坏事务锁、候补递补顺序和不超额报名的不变量。
- 当前受控流转包括：`POST /activities/{activityId}/registrations`、`POST /activities/{activityId}/registrations/cancel`、`POST /activities/{activityId}/waiting-confirmations`，以及后续可能实现的签到等报名状态流转接口。
- 活动容量判断必须将 `registered`、`waitingConfirmation`、`checkedIn` 视为占用名额；`waiting` 仅表示候补排队，不占用正式名额。

WebSocket：

- TypeSpec 中使用 `GET /chat/ws/messages` 作为聊天消息实时 WebSocket 端点标识，实际调用时客户端应向该路径发起 WebSocket Upgrade。
- 服务端通过 STOMP over WebSocket 提供消息代理，客户端使用 STOMP 协议连接。
- WebSocket 握手与 STOMP CONNECT 帧均使用与 JSON API 相同的 Bearer Token 鉴权。
- 连接建立后，客户端订阅 `/user/queue/chat-events` 接收 `ChatRealtimeEvent` 推送，支持事件类型：`messageCreated`、`messageRecalled`、`messageForwarded`。事件负载通过 `payload` 字段携带，类型由 `kind` 区分：`messageCreated`→`MessageCreatedPayload`（含 `message` 与 `conversationUnreadCount`），`messageRecalled`→`MessageRecalledPayload`（仅含 `message`），`messageForwarded`→`MessageForwardedPayload`（含 `message` 与 `conversationUnreadCount`）。所有 payload 字段均为必填，无 null 歧义。
- 订阅 `/user/queue/social-events` 接收 `friendRequestCreated` 事件（负载为 `FriendRequest` 模型）。
- 普通 JSON API 的统一响应包装、HTTP 响应代码固定为 200 等规则不适用于升级后的 WebSocket 数据帧。

日期与时间：

- 日期时间（精确到某一天的时分秒）处理：采用 ISO 8601 格式，例如：`2023-10-05T14:30:00Z`，JS 中请使用`new Date("2023-10-05T14:30:00Z")`处理
- 日期（精确到某一天）处理：采用 ISO 8601 格式，例如：`2023-10-05`，JS 中请使用`new Date("2023-10-05")`
- 时间（任意一天中的特定时刻）：采用从 00:00:00 开始的秒数

鉴权：使用 JWT，通过 Bearer Token 传递

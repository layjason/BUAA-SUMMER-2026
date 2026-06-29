# API 约定

## 更新日志

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

日期与时间：

- 日期时间（精确到某一天的时分秒）处理：采用 ISO 8601 格式，例如：`2023-10-05T14:30:00Z`，JS 中请使用`new Date("2023-10-05T14:30:00Z")`处理
- 日期（精确到某一天）处理：采用 ISO 8601 格式，例如：`2023-10-05`，JS 中请使用`new Date("2023-10-05")`
- 时间（任意一天中的特定时刻）：采用从 00:00:00 开始的秒数

鉴权：使用 JWT，通过 Bearer Token 传递

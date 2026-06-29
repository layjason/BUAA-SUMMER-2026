# 迷星群聚平台 API 契约

`api-spec/` 使用 TypeSpec 编写迷星群聚平台 API 契约，并生成 OpenAPI 文件供前端（Web）、后端、Android APP 共同使用。

## 基本命令

在 `api-spec/` 目录下执行：

```bash
pnpm install
pnpm compile
pnpm format:check
```

- `pnpm compile` 会根据 `main.tsp` 生成 `tsp-output/openapi.yaml`。
- `pnpm format:check` 用于检查 TypeSpec 文件格式。
- `pnpm format` 可自动格式化 TypeSpec 文件。

## 契约使用原则

- 以 TypeSpec 源文件为主，`tsp-output/openapi.yaml` 是生成产物。
- 接口说明、字段含义、枚举语义应优先写在 `.tsp` 文件的 `@doc` 中。
- JSON 接口统一返回 `APIResult<Data, BusinessErrors>`，生成的 OpenAPI 会以 `oneOf` 表达成功响应、通用错误响应和业务错误响应；二进制导出接口可直接返回文件流。
- GET 请求参数应通过 URL query 或 path 传递，不使用请求体。
- 需要登录的接口使用 Bearer Token。
- 文件上传接口使用 `multipart/form-data`，业务接口通过 `mediaId` 引用上传结果。

## 前端 Web 使用方式

Web 端可以把 `tsp-output/openapi.yaml` 作为类型和请求代码生成来源。

推荐流程：

1. 在提交前确认契约已更新：

   ```bash
   cd api-spec
   pnpm compile
   ```

2. 使用 OpenAPI 生成 TypeScript 类型或请求客户端。可选工具包括 `openapi-typescript`、`orval`、`openapi-fetch` 等。

3. 前端页面、状态管理和表单校验优先复用生成类型，避免手写重复 DTO。

4. 对请求封装层统一处理 `APIResult<Data, BusinessErrors>`：
   - `code === 200` 时读取 `data`。
   - `code !== 200` 时根据 OpenAPI 中枚举出的 `code` 和英文模板 `message` 展示错误或转换为业务错误。

5. 单元测试中使用生成类型构造 Mock 数据，确保 Mock 字段与契约一致。

## 后端 Spring Boot 使用方式

后端应将 TypeSpec 契约作为 Controller 入参、出参和接口路径的对齐依据。

推荐流程：

1. 后端实现接口前先查看对应 `.tsp` 文件，确认路径、HTTP 方法、请求体、query/path 参数和响应数据。

2. Controller 返回结构统一包装为 `APIResult<Data, BusinessErrors>` 对应的 JSON 结构，并保持 HTTP 状态码规则与 `docs/api-convention.md` 一致。

3. DTO 字段使用 camelCase，与契约字段保持一致；业务对象的校验状态仍按后端规范使用 Value Object 表达。

4. 文件上传接口使用 Spring MVC multipart 能力接收文件，并返回契约中的 `MediaFile` 结构。

5. 可通过 OpenAPI 生成服务端接口骨架或测试断言，但生成代码不应替代领域模型设计。

6. 后端测试建议覆盖：
   - Controller 路径、方法、query/path 参数绑定。
   - 请求体字段校验和业务响应代码。
   - multipart 上传接口。
   - 二进制导出接口。

## Android APP 使用方式

Android 客户端可以使用生成的 OpenAPI 文件生成 Kotlin/Java 网络模型，或手动对照契约编写 Retrofit 接口。

推荐流程：

1. 每次接口变更后拉取最新 `api-spec/`，确认 `tsp-output/openapi.yaml` 已由最新 TypeSpec 生成。

2. 使用 OpenAPI Generator 生成 Kotlin/Java DTO 和 API client，或将契约字段同步到手写 Retrofit 接口。

3. 网络层统一处理 `APIResult<Data, BusinessErrors>`：
   - 成功业务响应读取 `data`。
   - 业务失败根据 OpenAPI 中枚举出的 `code` 和英文模板 `message` 显示错误状态。
   - Bearer Token 在统一拦截器中添加。

4. 对地图、定位、图片上传等能力，按契约中的字段组织请求：
   - 地点使用 `LocationInfo` 与 `GeoPoint`。
   - 图片和文件先走 multipart 上传接口。
   - 创建活动、发消息、发布总结时只传 `mediaId`。

5. 客户端测试建议覆盖：
   - JSON 反序列化字段名与契约一致。
   - Token 过期、业务错误码、网络失败的统一处理。
   - 活动搜索 query 参数拼接。
   - multipart 上传请求体。

## Mock 与联调

- Web 和 Android 可基于 `tsp-output/openapi.yaml` 启动 Mock Server，用于后端接口完成前的页面开发。
- Mock 数据应遵循生成类型，不应随意增加或改名字段。
- Mock 业务错误时应使用接口响应 `oneOf` 中声明的 `code` 和 `message` 组合，不应跨服务混用错误码。
- 后端可使用 OpenAPI 契约做接口快照或契约测试，避免路径、字段、响应包装格式漂移。
- 发现契约与实现不一致时，应先更新 TypeSpec，再生成 OpenAPI，并同步三端改动。

## 变更流程

1. 修改对应领域的 `.tsp` 文件。
2. 补充或更新模型字段的 `@doc` 说明。
3. 执行：

   ```bash
   cd api-spec
   pnpm format
   pnpm compile
   pnpm format:check
   ```

4. 检查 `tsp-output/openapi.yaml` 是否符合预期。
5. 同步通知 Web、后端、Android 受影响的接口和字段。

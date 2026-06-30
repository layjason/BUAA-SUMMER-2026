# MayoiStar 身份接口 Yaak 测试说明

本目录提供当前 `feat-backend-identity` 分支可用的 Yaak 接口测试资产。

## 文件

- `MayoiStar.Identity.postman_collection.json`：Yaak 可导入的 Postman v2.1 集合。
- `MayoiStar.Identity.local.postman_environment.json`：本地环境变量模板，不包含真实凭据。

## 使用步骤

1. 启动后端：在 `backend` 目录运行 `mvn spring-boot:run`。
2. 在 Yaak 中导入集合文件。
3. 根据 Yaak 版本能力，导入或手动创建环境变量，变量名参考环境模板。
4. 在本地 `.env` 配置 SMTP、JWT、数据库等后端运行凭据，不要把真实密码、SMTP 授权码、管理员密码写入集合。
5. 按集合中的编号顺序执行请求。

## Token 与动态值

Yaak 当前更适合使用环境变量或请求链传递响应值。集合中保留了 Postman 测试脚本用于表达断言和变量提取意图，但在 Yaak 中可能不会执行。

在 Yaak 中执行时，请在登录、刷新、上传等请求后把响应值写入环境变量：

- `data.tokens.accessToken` -> `personalAccessToken` 或 `merchantAccessToken`
- `data.tokens.refreshToken` -> `personalRefreshToken` 或 `merchantRefreshToken`
- `data.userId` -> `personalUserId` 或 `merchantUserId`
- `data.mediaId` -> `avatarMediaId` 或 `licenseMediaId`

激活和重置密码 token 来自邮件链接；若本地 SMTP 不可用，可从本地测试数据库或后端日志辅助获取。

## 当前分支限制

- 当前契约中商家注册接口只接收邮箱、密码、昵称；商家图片上传在激活登录后通过 `/identity/media/license` 和 `/identity/me/merchant-qualification` 完成。
- 当前 `AdminController` 的商家审核接口仍返回占位数据，不能作为真实后台审核状态闭环的唯一依据。
- 当前身份资料接口只暴露 `reputationScore`，本集合仅校验该字段存在和默认值；举报扣分规则不在当前分支测试范围内。
- 后端统一错误响应使用 HTTP 200，业务成败通过响应体 `code` 判断。

## 建议记录

每次测试请记录：

- 请求名称和请求时间
- HTTP 状态码
- 响应体 `code`
- 关键字段，例如 `kind`、`accountStatus`、`qualificationStatus`、`reputationScore`
- 与预期不一致的响应体片段

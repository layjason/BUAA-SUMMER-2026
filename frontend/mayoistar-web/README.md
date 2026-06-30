# MayoiStar Web

迷星群聚后台管理 Web 前端，使用 React、Vite 和 TypeScript 实现。

## 基本命令

```bash
pnpm install
pnpm dev
pnpm test:unit
pnpm type-check
pnpm lint
pnpm format:check
```

## 环境变量

- `VITE_API_BASE_URL`：后端 API 基础地址。留空时请求当前源，适合通过 Vite 代理或同源部署。
- `VITE_USE_MOCK`：是否启用本地 Mock 数据。未设置或设置为 `true` 时启用，设置为 `false` 时请求后端。

## API 契约

接口路径、请求体和响应结构应与 `../../api-spec` 中的 TypeSpec 契约保持一致。

更新或校验前端 API 类型时，先重新生成 OpenAPI，再生成 TypeScript 契约类型：

```bash
cd ../../api-spec
pnpm compile

cd ../frontend/mayoistar-web
pnpm api:generate
```

业务 DTO 应从 `src/api/generated/openapi.ts` 派生，Mock 数据可以手工维护，但必须使用生成类型校验字段、枚举和分页结构。

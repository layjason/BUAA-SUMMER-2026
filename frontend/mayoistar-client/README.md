# MayoiStar Client

迷星群聚 App 用户端，使用 uni-app、Vue 3 和 TypeScript 实现。

## 开发

启动命令

```
pnpm install
pnpm dev:h5
```

默认启用内置 Mock Server，无需启动后端即可开发调试。
关闭 Mock 并连接真实后端：修改 `src/api/config.ts` 中 `USE_MOCK = false`。

## 检查

```
pnpm type-check
pnpm format:check
pnpm lint
pnpm test
```

## 构建

```
pnpm build:h5
pnpm preview:h5
```

## API 契约

接口类型从 `api-spec` 自动生成：

```
pnpm codegen
```

生成的 `src/api/types/schema.d.ts` 被 `api/modules` 和 `mock/` 共享，
Mock 服务器通过 `src/mock/schema-types.ts` 引用同一份类型，确保与规范一致。

# Backend repo

## 开发

启动命令

```
docker compose --env-file .env -f docker-compose-local.yaml down -v
docker compose --env-file .env -f docker-compose-local.yaml up -d postgres redis rustfs
mvn spring-boot:run
```

本地对象存储使用 RustFS。S3 API 默认端口为 `9000`，控制台默认端口为 `9001`；默认 bucket 与凭据由 `.env` 中的 `MAYOISTAR_S3_BUCKET`、`RUSTFS_ACCESS_KEY`、`RUSTFS_SECRET_KEY` 配置。

Redis 用于媒体访问快照缓存和限流计数，默认端口 `6379`，可通过 `.env` 中的 `DEV_REDIS_PORT` 和 `REDIS_PASSWORD` 配置。

## AI 图片分类

使用前需先启动 Python CLIP 边车服务（`../clip-service/`），否则 `/ai/image-classifications` 将返回 `AiServiceUnavailable`。

## AI 活动策划

`/ai/activity-plans` 使用兼容 OpenAI Chat Completions 的模型服务。需要在 `.env` 中配置
`MAYOISTAR_AI_ACTIVITY_PLANNING_ENDPOINT`、`MAYOISTAR_AI_ACTIVITY_PLANNING_MODEL` 和
`MAYOISTAR_AI_ACTIVITY_PLANNING_API_KEY`；未配置时接口返回 `AiServiceUnavailable`。

AI 接口按用户和操作维度执行分钟级限流，默认每分钟 20 次，可通过
`MAYOISTAR_AI_MAX_REQUESTS_PER_MINUTE` 调整。

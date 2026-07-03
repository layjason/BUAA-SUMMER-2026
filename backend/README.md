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

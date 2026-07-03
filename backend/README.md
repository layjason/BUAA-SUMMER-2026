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

图片分类功能依赖 Python CLIP 边车服务，需先启动该服务。

### 启动 CLIP 服务

参见 `../clip-service/README.md`。快速启动：

```bash
cd ../clip-service
uvicorn main:app --host 0.0.0.0 --port 8000
```

或使用 Docker（需 GPU）：

```bash
cd ../clip-service
docker compose up -d
```

### 配置

CLIP 服务端点通过以下环境变量配置：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MAYOISTAR_CLIP_ENDPOINT` | `http://localhost:8000` | CLIP 边车服务地址 |
| `MAYOISTAR_AI_CLIP_TIMEOUT_SECONDS` | `60` | 单次调用超时（秒） |
| `MAYOISTAR_AI_RATE_LIMIT_MAX_REQUESTS_PER_MINUTE` | `20` | 每分钟最大调用次数 |

### 不使用 AI 功能

若不启动 CLIP 服务，后端仍可正常启动，但调用 `/ai/image-classifications` 时将返回 `AiServiceUnavailable` 错误。

# Backend repo

## 开发

启动命令

```
docker compose --env-file .env -f docker-compose-local.yaml down -v
docker compose --env-file .env -f docker-compose-local.yaml up -d postgres rustfs
mvn spring-boot:run
```

本地对象存储使用 RustFS。S3 API 默认端口为 `9000`，控制台默认端口为 `9001`；默认 bucket 与凭据由 `.env` 中的 `MAYOISTAR_S3_BUCKET`、`RUSTFS_ACCESS_KEY`、`RUSTFS_SECRET_KEY` 配置。

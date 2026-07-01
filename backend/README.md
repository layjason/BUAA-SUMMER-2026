# Backend repo

## 开发

启动命令

```
docker compose --env-file .env -f docker-compose-local.yaml down -v
docker compose --env-file .env -f docker-compose-local.yaml up -d postgres
mvn spring-boot:run
```

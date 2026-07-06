# 部署指南

## 更新日志

- Version: 1 20260704 152641
  - 初始版本：确立 Java + Kafka + GPU 集群部署拓扑和步骤
- Version: 2 20260706 154940
  - 简化 GPU 部署流程：合并 .env.gpu.example 到 .env.example，改用 docker compose --scale 一键启动

---

## 1. 部署拓扑

```
┌── Java 服务器 (公网 IP) ────────────────────────────────────┐
│  Docker Compose 服务栈                                       │
│  ┌──────────┐ ┌────────┐ ┌────────┐ ┌──────────┐           │
│  │PostgreSQL│ │ Redis  │ │ RustFS │ │  Kafka   │           │
│  │  :5432   │ │ :6379  │ │ :9000  │ │:9092(外) │           │
│  └──────────┘ └────────┘ └────────┘ │:9093(内) │           │
│                                     └──────────┘           │
│  ┌────────────────────────────────────┐                    │
│  │      Java Backend (:8080)          │                    │
│  └────────────────────────────────────┘                    │
│  ┌──────────────────┐                                      │
│  │   Kafka UI       │  (可选，调试用端口 8081)             │
│  └──────────────────┘                                      │
├─────────────────────────────────────────────────────────────┤
│  对外暴露端口: 8080(后端), 9092(Kafka), 9000(RustFS)       │
└──────────────────────┬──────┬───────────────────────────────┘
                       │      │
             VPN / 专线（内网）
                       │      │
      ┌────────────────┘      └──────────────────┐
      ▼                                          ▼
┌─── GPU-1 ───────┐                    ┌─── GPU-2 ───────┐
│ → :9092 (Kafka) │                    │ → :9092 (Kafka) │
│ → :9000 (RustFS)│                    │ → :9000 (RustFS)│
│ 5 consumer 进程 │                    │ 5 consumer 进程 │
└─────────────────┘                    └─────────────────┘
              ┌─── GPU-3 ───────┐
              │ 5 consumer 进程 │
              └─────────────────┘
```

---

## 2. 前置条件

### Java 服务器

- Docker 24+ 已安装
- Docker Compose v2+ 已安装
- 端口 8080、9092、9000 可从客户端/VPN 访问
- 端口 8081（Kafka UI）仅限内网或 VPN 访问

### GPU 机器（3 台）

- Docker 24+ 已安装
- Docker Compose v2+ 已安装
- NVIDIA Container Toolkit 已安装（`nvidia-smi` 在宿主机可执行）
- 可通过 VPN/专线连接 Java 服务器的 9092（Kafka）和 9000（RustFS）

---

## 3. Java 服务器部署

### 3.1 配置环境变量

在 `backend/` 目录下创建 `.env.prod` 文件：

```bash
# === PostgreSQL ===
POSTGRES_DB=mayoistar
POSTGRES_USER=mayoistar
POSTGRES_PASSWORD=<强密码>

# === Redis ===
REDIS_PASSWORD=<Redis 密码>

# === RustFS / S3 ===
RUSTFS_ACCESS_KEY=<AK>
RUSTFS_SECRET_KEY=<SK>
MAYOISTAR_S3_BUCKET=mayoistar-media

# === Kafka 外部地址（GPU 机器通过 VPN 连接此地址） ===
KAFKA_EXTERNAL_HOST=<VPN_IP 或公网 IP>
KAFKA_EXTERNAL_PORT=9092

# === 后端配置 ===
MAYOISTAR_JWT_SECRET=<256-bit 密钥>
MAYOISTAR_MEDIA_SIGNING_SECRET=<签名密钥>
MAYOISTAR_MAIL_HOST=<SMTP 地址>
MAYOISTAR_MAIL_PORT=587
MAYOISTAR_MAIL_USERNAME=<SMTP 用户名>
MAYOISTAR_MAIL_PASSWORD=<SMTP 密码>
MAYOISTAR_MAIL_SENDER=<发件人邮箱>
MAYOISTAR_ACTIVATION_BASE_URL=https://yourdomain.com/activate
MAYOISTAR_PASSWORD_RESET_BASE_URL=https://yourdomain.com/reset-password

# === 阿里云内容审核（可选） ===
ALIBABA_CLOUD_ACCESS_KEY_ID=<AK>
ALIBABA_CLOUD_ACCESS_KEY_SECRET=<SK>

# === 对外访问 ===
MAYOISTAR_SERVER_PORT=8080
```

### 3.2 启动服务

```bash
cd backend
docker compose --env-file .env.prod up -d
```

### 3.3 验证

```bash
# 检查所有服务健康
docker compose ps

# 检查 Kafka
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
# 应看到: clip-classify-request, clip-classify-response, clip-classify-request-dlq

# 检查 Kafka UI
# 浏览器访问 http://<服务器IP>:8081
```

---

## 4. GPU 机器部署

### 4.1 配置环境变量

在 `clip-service/` 目录下复制并编辑环境变量文件：

```bash
cp .env.example .env
```

编辑 `.env` 文件，填入 Java 服务器的 VPN/专线 IP：

```bash
# 仅需修改这两个必填项
KAFKA_BOOTSTRAP_SERVERS=<VPN_IP>:9092
RUSTFS_ENDPOINT=http://<VPN_IP>:9000
```

其余配置（凭证、topic 名称等）有默认值，通常无需修改。如 RustFS 凭证与默认值不同，请同步修改。

### 4.2 启动 Consumer 进程

每台 GPU 机器通过 `--scale` 启动多个容器实例（共享同一 `group.id`，Kafka 自动负载均衡）：

```bash
cd clip-service
docker compose up -d --scale clip-classifier=5
```

### 4.3 验证

```bash
# 检查所有 GPU 容器
docker ps --filter "name=mayoistar-clip"

# 检查 Kafka consumer group
docker compose exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group mayoistar-clip-gpu \
  --describe
# 15 个 consumer 成员应均匀分配到 15 个 partition
```

---

## 5. VPN/专线要求

| 方向 | 源 | 目的 | 端口 | 协议 |
|------|----|------|------|------|
| GPU → Java | GPU 机器 | Java 服务器 VPN IP | 9092 | TCP (Kafka) |
| GPU → Java | GPU 机器 | Java 服务器 VPN IP | 9000 | TCP (RustFS S3 API) |
| 客户端 → Java | 前端/App | Java 服务器公网 IP | 8080 | TCP (HTTPS) |
| （可选）Kafka UI | 开发机 | Java 服务器 VPN IP | 8081 | TCP (HTTP) |

---

## 6. 监控部署（可选）

### 6.1 Prometheus

```bash
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus
```

### 6.2 Grafana

```bash
docker run -d \
  --name grafana \
  -p 3000:3000 \
  grafana/grafana
```

导入 `monitoring/dashboards/gpu-kafka.json` 面板。

---

## 7. 常见问题

### Q: Kafka consumer lag 不断增长

- 检查 GPU 机器是否正常工作（`nvidia-smi`）
- 确认 Kafka broker 端口在 VPN 中可达（`telnet <VPN_IP> 9092`）
- 检查 consumer group 成员数是否为 15

### Q: Python 服务下载图片失败

- 检查 `RUSTFS_ENDPOINT` 是否正确且可达
- 确认 `RUSTFS_ACCESS_KEY` 和 `RUSTFS_SECRET_KEY` 与 Java 服务器配置一致

### Q: 任务总是超时

- 检查 `mayoistar.ai.clip.request-timeout-seconds` 配置（默认 30 秒）
- 确认 GPU 服务已正确加载模型（首次启动需下载 ~600MB 模型，耗时较长）
- 确认模型缓存卷已持久化

### Q: 新增 GPU 机器后如何扩容

1. 增加 Kafka request topic 分区数：`kafka-topics --alter --topic clip-classify-request --partitions <N>`
2. 在新 GPU 机器上按 4.1-4.2 步骤部署
3. Kafka 自动触发 rebalance，新 consumer 分配到分区

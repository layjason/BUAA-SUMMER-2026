# Kafka 消息队列架构设计

## 更新日志

- Version: 1 20260704 120000
  - 初始版本：确立 Kafka 异步任务架构，替代同步 HTTP 调用 CLIP 服务的方案
- Version: 2 20260706 154940
  - 同步部署指南更新：环境变量模板统一为 .env.example

---

## 1. 背景与动机

原架构中，Java 后端通过同步 HTTP 调用 Python CLIP GPU 服务完成图片分类。该模式存在以下问题：

- **紧耦合**：Java 后端与 Python 服务生命周期绑定，任一方故障直接影响用户体验
- **不可扩展**：多 GPU 机器无法自然地负载均衡，需要单独引入反向代理层
- **无缓存**：已分类过的图片每次都需要重新调用 CLIP 推理，浪费 GPU 资源
- **无持久化**：分类结果不持久化，无法追溯历史分类

引入 Kafka 消息队列后：

- Java 后端发布分类任务到 Kafka，由 GPU 工作节点异步消费
- 分类结果持久化到数据库，支持按 mediaId 直接查询缓存
- 多 GPU 节点通过 Kafka Consumer Group 自动实现负载均衡
- 任务成功/失败通过 WebSocket 实时通知前端

---

## 2. 网络拓扑

### 整体架构

```
┌── Java 服务器 (公网 IP: X.X.X.X) ────────────────────────────┐
│                                                               │
│  Docker Network: mayoistar (bridge)                           │
│  ┌──────────┐ ┌────────┐ ┌────────┐ ┌──────────────────┐    │
│  │PostgreSQL│ │ Redis  │ │ RustFS │ │     Kafka        │    │
│  │  :5432   │ │ :6379  │ │  :9000 │ │  :9092 (外部监听) │    │
│  └──────────┘ └────────┘ └────────┘ └────────┬─────────┘    │
│       ↑          ↑          ↑               │                │
│  ┌────────────────────────────────────┐     │                │
│  │        Java Backend (:8080)        │─────┘                │
│  │  ┌──────────────────────────────┐  │                      │
│  │  │ KafkaProducer → request      │  │                      │
│  │  │ KafkaConsumer ← response     │  │                      │
│  │  │ ClassificationResultCache(DB)│  │                      │
│  │  │ ClipTaskResultStore(Redis)   │  │                      │
│  │  │ WebSocket → /queue/ai-events │  │                      │
│  │  └──────────────────────────────┘  │                      │
│  └────────────────────────────────────┘                      │
│                                                               │
│  Kafka 端口对外暴露：                                          │
│    INTERNAL listener: kafka:9093 (Docker 内部)                │
│    EXTERNAL listener: 0.0.0.0:9092 → VPN IP 可达             │
│  RustFS 端口对外暴露：                                        │
│    0.0.0.0:9000 → VPN IP 可达                                │
└──────────────────────────┬──────┬─────────────────────────────┘
                           │      │
                    VPN / 专线（内网可达）
                           │      │
         ┌─────────────────┘      └──────────────────┐
         ▼                                           ▼
┌──── GPU 机器 1 ───────┐              ┌──── GPU 机器 2 ───────┐
│ → Kafka:VPN_IP:9092  │              │ → Kafka:VPN_IP:9092  │
│ → RustFS:VPN_IP:9000  │              │ → RustFS:VPN_IP:9000  │
│ GPU: A100 (强算力)    │              │ GPU: RTX 4090 (弱算力) │
│ 5 Consumer 进程       │              │ 5 Consumer 进程       │
│ group: mayoistar-clip-gpu           │ group: mayoistar-clip-gpu
└───────────────────────┘              └───────────────────────┘
         ...                                         ...
              ┌──── GPU 机器 3 ───────┐
              │ 5 Consumer 进程       │
              │ group: mayoistar-clip-gpu
              └───────────────────────┘
```

### 网络连接要求

| 方向 | 端口 | 说明 |
|------|------|------|
| GPU 机器 → Java 服务器 | TCP 9092 | Kafka broker 外部监听地址（发任务 + 取结果） |
| GPU 机器 → Java 服务器 | TCP 9000 | RustFS S3 API（下载图片字节） |
| 前端 → Java 服务器 | TCP 8080 | REST API + WebSocket |

GPU 机器**不需要公网 IP**，只需通过 VPN/专线**出站**连接 Java 服务器的 9092 和 9000 端口。

---

## 3. Kafka Topic 设计

| Topic | 分区数 | 生产者 | 消费者 | 说明 |
|-------|--------|--------|--------|------|
| `clip-classify-request` | **15** | Java | Python GPU Workers | 分类任务请求，分区数 ≥ 消费者总数 (3×5=15) |
| `clip-classify-response` | 1 | Python | Java | 分类结果回执，单一 Java 消费者保证有序 |
| `clip-classify-request-dlq` | 1 | (自动) | (人工/告警) | 死信队列，3 次重试失败后进入 |

### 分区数与 Consumer 负载均衡

- **GPU 集群**：3 台机器，每台 5 个 consumer 进程，共 15 个 consumer
- **Consumer Group**：所有 consumer 使用相同的 `group.id = mayoistar-clip-gpu`
- **分区分配**：15 个分区均匀分配给 15 个 consumer（每 consumer 1 个分区）
- **容错**：任一 consumer 宕机，Kafka 自动将其分区 reassign 给其他 consumer
- **扩展性**：增加 GPU 机器时，只需增加分区数即可平行扩展

### 消息保留策略

- 正常消息保留 7 天（`retention.ms: 604800000`）
- DLQ 消息保留 30 天
- 生产者启用幂等性（`enable.idempotence: true`），防止网络重试导致重复消息

---

## 4. 消息格式

### Request 消息 (`clip-classify-request`)

```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "mediaIds": [
    "660e8400-e29b-41d4-a716-446655440001",
    "660e8400-e29b-41d4-a716-446655440002"
  ],
  "timestamp": "2026-07-04T10:30:00Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | UUID | 任务唯一标识，用于关联结果 |
| `mediaIds` | UUID[] | 需要分类的图片 mediaId 列表（已剔除 DB 中已缓存的） |
| `timestamp` | ISO 8601 | 任务创建时间，用于超时判断 |

> Kafka Key: `taskId` 字符串，保证同一任务的请求路由到同一分区

### Response 消息 (`clip-classify-response`)

```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "succeeded",
  "items": [
    {
      "mediaId": "660e8400-e29b-41d4-a716-446655440001",
      "category": "group_photo",
      "confidence": 0.8542
    }
  ],
  "errorMessage": null
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | UUID | 关联的任务 ID |
| `status` | string | `succeeded` 或 `failed` |
| `items` | object[] | 分类结果列表（仅 succeeded 时有值） |
| `errorMessage` | string | 错误信息（仅 failed 时有值） |

> Kafka Key: `taskId` 字符串

---

## 5. 数据流

### 5.1 分类任务提交流程

```
POST /ai/image-classifications  {mediaIds: [A, B, C]}
        │
        ├── 1. 查 ai_classification_results 表
        │      A → 已存在缓存 → 跳过
        │      B, C → 未缓存 → 进入队列
        │
        ├── 2. 生成 taskId，写入 task (Redis, TTL=30min)
        │      Key: clip:task:{taskId}
        │      Value: {status: "pending", mediaIds: [A,B,C], userId: "xxx"}
        │
        ├── 3. 发 Kafka "clip-classify-request"
        │      {taskId, mediaIds: [B, C]}  ← 仅未缓存的
        │
        └── 4. 返回 {taskId, status: "pending"} 给前端
```

### 5.2 GPU 消费与分类流程

```
Python Consumer (GPU机器)
        │
        ├── 1. poll Kafka "clip-classify-request"
        │
        ├── 2. 遍历 mediaIds
        │      │
        │      ├── GET RustFS {endpoint}/{bucket}/{mediaId}
        │      │    → 下载图片字节
        │      │
        │      └── 分类失败 → 单个 mediaId 标记为 error，
        │                     记录到 items 中 confidence=0.0
        │
        ├── 3. classify_batch(imageBytesList)
        │      → [{category, confidence}, ...]
        │
        └── 4. 回写 Kafka "clip-classify-response"
               {taskId, status: "succeeded"/"failed", items: [...], errorMessage}
```

### 5.3 结果收集与通知流程

```
Java Consumer (ClipResponseConsumer)
        │
        ├── 1. poll Kafka "clip-classify-response"
        │
        ├── 2. 写入 ai_classification_results 表
        │      (media_id, category, confidence, task_id, classified_at)
        │
        ├── 3. 更新 Redis task 状态
        │      clip:task:{taskId} → {status: "succeeded"/"failed"}
        │
        └── 4. WebSocket 通知
               → /user/{userId}/queue/ai-events
               {kind: "image_classification_completed", taskId, status}
```

### 5.4 单张图片查询（缓存命中）

```
GET /ai/image-classifications/media/{mediaId}
        │
        └── 1. 查 ai_classification_results 表
               → 命中 → 返回 {mediaId, category, confidence, classifiedAt}
               → 未命中 → 返回 30004 (AI_TASK_NOT_FOUND)
```

### 5.5 任务状态轮询

```
GET /ai/image-classifications/{taskId}
        │
        ├── 1. 查 Redis clip:task:{taskId}
        │      → pending → 返回 {status: "pending"}
        │      → succeeded → 查 DB 获取全部结果 → 返回完整结果
        │      → failed → 返回错误信息
        │      → 不存在 → 30004 (AI_TASK_NOT_FOUND)
        │
        └── 2. 超时检查：task 创建超过 30s
               → 标记 failed，返回 30005 (AI_TASK_TIMEOUT)
```

---

## 6. 数据库设计

### `ai_classification_results` 表

```sql
CREATE TABLE ai_classification_results (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    media_id UUID NOT NULL,
    category VARCHAR(50) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    classified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    task_id UUID,
    CONSTRAINT fk_ai_result_media FOREIGN KEY (media_id) REFERENCES media_files(id) ON DELETE CASCADE,
    CONSTRAINT uk_ai_result_media UNIQUE (media_id)
);

CREATE INDEX idx_ai_result_media_id ON ai_classification_results(media_id);
CREATE INDEX idx_ai_result_task_id ON ai_classification_results(task_id);
```

- `uk_ai_result_media`：每个 media 最多一条分类记录，保证"已分类过的图片直接返回"的语义
- `task_id`：关联任务用于追溯，非必填（方便手动录入）
- `ON DELETE CASCADE`：media 被删除时自动清理分类记录

---

## 7. 错误处理与容错

### 7.1 重试策略

| 层级 | 策略 | 说明 |
|------|------|------|
| Kafka Producer | `retries: 3, acks: all` | 确保消息不丢失 |
| Kafka Consumer | 3 次重试，指数退避 (1s/2s/4s) | 处理临时故障（网络抖动、S3 超时等） |
| DLQ | 3 次重试后进入 `clip-classify-request-dlq` | 需人工排查后手动重放 |

### 7.2 超时处理

- 任务超时时间：**30 秒**
- Java 后端 `ClipTaskResultStore` 检查 Redis 中 task 的创建时间
- 超过 30 秒未收到 response → 标记 `failed`，返回 `30005 (AI_TASK_TIMEOUT)`
- 超时后如果 response 迟来 → consumer 忽略（避免旧数据覆盖新状态）

### 7.3 故障场景

| 场景 | 影响 | 恢复方式 |
|------|------|----------|
| Kafka broker 宕机 | 分类任务积压，API 仍可返回 pending | 重启 Kafka，消息持久化在磁盘，不丢失 |
| GPU 机器全部宕机 | 任务永久 pending，30s 后标记 timeout | 重启 GPU 服务，consumer group 自动恢复消费 |
| Python 单个 media 下载失败 | 该 media 标记为失败 (confidence=0.0)，不影响其他 media | 正常，无缓存写入 |
| Python OOM/Crash | Consumer 心跳超时，Kafka 触发 rebalance | 自动重启，从上次 commit 的 offset 继续 |
| Java 后端重启 | 进行中的任务状态在 Redis 中保留（30min TTL），response consumer 快速恢复 | 正常 |

---

## 8. WebSocket 通知事件

### 事件定义

```typescript
interface AiTaskEvent {
  kind: "image_classification_completed";
  taskId: string;
  status: "succeeded" | "failed";
}
```

### 推送路径

```
/user/{userId}/queue/ai-events
```

### 推送时机

- 分类成功：所有 media 分类完成，结果写入 DB 后
- 分类失败：Python 返回 failed 或任务超时
- **不推送** pending 状态（前端通过轮询 GET 接口确认 pending 状态）

---

## 9. API 契约变更

### 9.1 POST /ai/image-classifications（修改）

| 项目 | 原 | 新 |
|------|-----|-----|
| 请求 | `{mediaIds: UUID[]}` | 不变 |
| 响应 | `{status, items: [...]}` | `{taskId: UUID, status: "pending" \| "succeeded"}` |
| 语义 | 同步等待分类完成 | 异步提交任务，通过 taskId 轮询结果 |

当所有 media 已有缓存时，直接返回 `{taskId, status: "succeeded"}`，前端可通过 taskId 立即查询结果。

### 9.2 GET /ai/image-classifications/{taskId}（新增）

轮询任务状态和结果。`status` 为 `pending` 时仅返回状态；`succeeded` 时返回完整分类列表。

### 9.3 GET /ai/image-classifications/media/{mediaId}（新增）

按媒体 ID 查询缓存的分类结果。命中返回完整信息（含 `classifiedAt`），未命中返回 `30004`。

### 9.4 新增错误码

| 错误码 | 名称 | 说明 |
|--------|------|------|
| 30004 | AI_TASK_NOT_FOUND | 分类任务不存在或已过期（含 media 未分类时） |
| 30005 | AI_TASK_TIMEOUT | 分类任务超时，GPU 服务可能在 30s 内未响应 |

---

## 10. 配置参数

### Java 后端 (`application.yaml` / 环境变量)

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker 地址 |
| `spring.kafka.consumer.group-id` | `mayoistar-backend` | Java 后端 consumer group (消费 response) |
| `spring.kafka.producer.retries` | `3` | 生产者重试次数 |
| `spring.kafka.producer.acks` | `all` | 等待所有 ISR 确认 |
| `spring.kafka.consumer.enable-auto-commit` | `false` | 手动提交 offset |
| `mayoistar.ai.clip.request-topic` | `clip-classify-request` | 请求 topic |
| `mayoistar.ai.clip.response-topic` | `clip-classify-response` | 响应 topic |
| `mayoistar.ai.clip.request-dlq-topic` | `clip-classify-request-dlq` | 死信 topic |
| `mayoistar.ai.clip.request-timeout-seconds` | `30` | 任务超时秒数 |
| `mayoistar.ai.clip.request-retry-max` | `3` | consumer 最大重试次数 |

### Python 服务 (环境变量)

| 参数 | 示例值 | 说明 |
|------|--------|------|
| `KAFKA_BOOTSTRAP_SERVERS` | `10.0.0.1:9092` | Kafka 地址（VPN IP） |
| `KAFKA_GROUP_ID` | `mayoistar-clip-gpu` | GPU consumer group ID |
| `KAFKA_REQUEST_TOPIC` | `clip-classify-request` | 消费的 topic |
| `KAFKA_RESPONSE_TOPIC` | `clip-classify-response` | 回写结果的 topic |
| `RUSTFS_ENDPOINT` | `http://10.0.0.1:9000` | S3 端点 |
| `RUSTFS_ACCESS_KEY` | `rustfsadmin` | S3 AK |
| `RUSTFS_SECRET_KEY` | `rustfsadmin` | S3 SK |
| `RUSTFS_BUCKET` | `mayoistar-media` | S3 桶名 |

### Kafka broker（`docker-compose.yaml`）

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `KAFKA_NUM_PARTITIONS` | `15` | 默认分区数（request topic 用） |
| `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR` | `1` | 单节点部署 |
| `KAFKA_LOG_RETENTION_HOURS` | `168` | 消息保留 7 天 |
| `KAFKA_ADVERTISED_LISTENERS` | `EXTERNAL://VPN_IP:9092` | 对外暴露地址 |

---

## 11. 监控

### Prometheus 指标

#### Java 后端
- `kafka_consumer_records_lag`：response topic 消费延迟
- `clip_task_submitted_total`：任务提交计数（按用户分）
- `clip_task_duration_seconds`：任务完成耗时分布
- `clip_task_cache_hit_total`：缓存命中计数

#### Python GPU 服务
- `clip_task_process_duration_seconds`：单 task 处理耗时（含 S3 下载 + 推理）
- `clip_task_processed_total`：处理任务计数
- `clip_task_errors_total`：处理失败计数（按错误类型分）
- `clip_batch_size`：每 task 的 media 数量分布

#### GPU 硬件
- `DCGM_FI_DEV_GPU_UTIL`：GPU 利用率
- `DCGM_FI_DEV_MEM_COPY_UTIL`：显存带宽利用率
- `DCGM_FI_DEV_FB_USED`：显存使用量

### Grafana 面板建议

| 面板 | 数据源 | 关键 Query |
|------|--------|------------|
| 任务吞吐 & 延迟 | clip_task_duration_seconds | p50/p95/p99 histogram |
| 缓存命中率 | clip_task_cache_hit_total / clip_task_submitted_total | rate |
| Consumer Lag | kafka_consumer_records_lag | per partition |
| GPU 利用率 | DCGM_FI_DEV_GPU_UTIL | per device |
| 错误率 | clip_task_errors_total | rate, by error type |

---

## 12. 部署检查清单

- [ ] Java 服务器 Docker 环境已安装
- [ ] Java 服务器开放端口：8080 (App)、9092 (Kafka)、9000 (RustFS S3)
- [ ] VPN/专线已配置，GPU 机器可 ping 通 Java 服务器的 VPN IP
- [ ] GPU 机器 Docker 环境已安装
- [ ] GPU 机器已安装 NVIDIA Container Toolkit (`nvidia-smi` 可用)
- [ ] GPU 机器可访问 `VPN_IP:9092` (Kafka) 和 `VPN_IP:9000` (RustFS)
- [ ] 模型缓存卷 `clip_model_cache` 已在 GPU 机器上创建
- [ ] 环境变量文件 `.env` 已按模板 `.env.example` 填写
- [ ] Prometheus + Grafana 已部署（可选，建议在 Java 服务器上）
- [ ] Kafka 外部监听地址 `ADVERTISED_LISTENERS` 已设置为 VPN IP

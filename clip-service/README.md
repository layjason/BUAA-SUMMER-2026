# MayoiStar CLIP 图片分类服务

基于 FastAPI + open_clip 的 CLIP 图片分类边车服务，使用 ViT-B-32 (laion2b_s34b_b79k) 模型对活动图片进行五类分类（合影/场地/过程记录/物资/成果展示）。

## 快速开始

### 生产部署（GPU + Docker，Kafka Consumer 模式）

确保已安装 [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html)。

```bash
cp .env.example .env
# 编辑 .env，填入 Java 服务器的 VPN/公网 IP
vim .env
# 启动 5 个消费者实例
docker compose up -d --scale clip-classifier=5
```

模型权重约 600MB，首次启动时自动下载，通过 `clip_model_cache` volume 持久化。

### 本地开发（CPU，HTTP API 模式）

```bash
cp .env.example .env
pip install -r requirements.txt
python main.py
```

或直接使用 uvicorn：

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MODE` | `fastapi` | 运行模式：`kafka`（生产）或 `fastapi`（调试） |
| `CLIP_HOST` | `0.0.0.0` | 服务监听地址 |
| `CLIP_PORT` | `8000` | 服务监听端口 |
| `KAFKA_BOOTSTRAP_SERVERS` | — | **必填**，Kafka broker 地址 |
| `RUSTFS_ENDPOINT` | — | **必填**，S3 兼容存储端点 |

### 手动构建

```bash
docker build -t mayoistar-clip:gpu .
docker run --gpus all -p 8000:8000 mayoistar-clip:gpu
```

## API

### `POST /classify`

对图片进行五类分类。

**请求体：**
```json
{
  "images": [
    "data:image/jpeg;base64,/9j/4AAQ...",
    "data:image/png;base64,iVBORw..."
  ]
}
```

**响应：**
```json
{
  "items": [
    { "category": "group_photo", "confidence": 0.8521 },
    { "category": "venue", "confidence": 0.7234 }
  ]
}
```

### `GET /health`

健康检查。

## 分类类别

| category | 中文含义 |
|----------|---------|
| `group_photo` | 合影 |
| `venue` | 场地 |
| `process` | 过程记录 |
| `supplies` | 物资 |
| `achievement` | 成果展示 |

## 资源消耗

| 指标 | 数值 |
|------|------|
| 模型参数量 | ~1.51 亿 |
| GPU 显存 | ~1-2 GB |
| 单张推理 (GPU) | ~10-50ms |
| 最小 GPU 要求 | 4GB 显存 |

CPU 推理亦可正常工作，单张延迟约 200-500ms。

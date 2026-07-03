# MayoiStar CLIP 图片分类服务

基于 FastAPI + open_clip 的 CLIP 图片分类边车服务，使用 ViT-B-32 (laion2b_s34b_b79k) 模型对活动图片进行五类分类（合影/场地/过程记录/物资/成果展示）。

## 快速开始

### CPU 运行

```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

### GPU 运行（Docker）

确保已安装 [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html)。

```bash
docker compose up -d
```

模型权重约 600MB，首次启动时自动下载，通过 `clip_model_cache` volume 持久化。

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

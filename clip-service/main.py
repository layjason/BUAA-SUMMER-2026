"""
CLIP 图片分类服务入口。

职责：支持两种运行模式：
  1. Kafka Consumer 模式（生产）：从 Kafka 消费分类请求，处理并回写结果
  2. FastAPI HTTP 模式（开发/调试）：提供 REST API 供直接调用

通过环境变量 MODE 控制（默认 fastapi）。
Kafka Consumer 模式下仍启动轻量 HTTP 服务暴露 /health 和 /metrics 端点。

环境变量：
    MODE             运行模式：fastapi | kafka（默认 fastapi）
    CLIP_HOST        服务监听地址，默认 0.0.0.0
    CLIP_PORT        服务监听端口，默认 8000
    METRICS_PORT     Prometheus metrics 端口，默认 9090
    HF_HOME          Hugging Face 模型缓存目录
"""
import base64
import logging
import os
import threading
from io import BytesIO
from typing import List

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from prometheus_client import generate_latest, CONTENT_TYPE_LATEST

from classifier import classify_batch, load_model

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger(__name__)

app = FastAPI(title="MayoiStar CLIP Classifier", version="2.0.0")
_consumer_thread_started = False


class ClassifyRequest(BaseModel):
    images: List[str]


class ClassifyItem(BaseModel):
    category: str
    confidence: float


class ClassifyResponse(BaseModel):
    items: List[ClassifyItem]


def _decode_data_uri(data_uri: str) -> bytes:
    """将 base64 Data URI 解码为字节数据。"""
    if ";base64," not in data_uri:
        raise HTTPException(status_code=400, detail="Invalid base64 data URI format")
    header, b64_part = data_uri.split(";base64,", 1)
    try:
        return base64.b64decode(b64_part)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Base64 decode failed: {e}")


@app.on_event("startup")
async def startup():
    """服务启动时加载 CLIP 模型并预计算文本特征。"""
    logger.info("正在启动 CLIP 分类服务...")
    load_model()
    _start_kafka_consumer_if_enabled()
    logger.info("CLIP 分类服务已就绪")


@app.get("/health")
async def health():
    """健康检查端点。"""
    return {"status": "ok"}


@app.get("/metrics")
async def metrics():
    """Prometheus metrics 端点。"""
    from fastapi.responses import Response
    from metrics import task_duration_seconds, task_processed_total, task_errors_total, task_batch_size
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.post("/classify", response_model=ClassifyResponse)
async def classify(request: ClassifyRequest):
    """
    对传入的图片列表进行分类。

    前置条件：请求体中 images 为 base64 Data URI 列表。
    后置条件：返回与输入顺序一致的分类结果。
    （Kafka Consumer 模式下通过 kafka_consumer.py 直接调用 classify_batch，
     此端点仅用于调试和兼容。）
    """
    if not request.images:
        return ClassifyResponse(items=[])

    try:
        image_bytes_list = [_decode_data_uri(uri) for uri in request.images]
    except HTTPException:
        raise
    except Exception as e:
        logger.error("图片解码失败: %s", e)
        raise HTTPException(status_code=400, detail="Image decode failed")

    try:
        results = classify_batch(image_bytes_list)
    except Exception as e:
        logger.error("图片分类失败: %s", e)
        raise HTTPException(status_code=500, detail="Image classification failed")

    return ClassifyResponse(items=[ClassifyItem(**r) for r in results])


def _start_kafka_consumer():
    """在独立线程中启动 Kafka Consumer。"""
    from kafka_consumer import run_consumer
    logger.info("启动 Kafka Consumer...")
    run_consumer()


def _start_kafka_consumer_if_enabled():
    """
    当 MODE=kafka 时启动 Kafka Consumer。

    前置条件：CLIP 模型已经完成加载。
    后置条件：Kafka 模式下仅启动一个后台 consumer 线程；HTTP 模式不启动 consumer。
    不变量：无论入口是 python main.py 还是 uvicorn main:app，启动行为一致。
    """
    global _consumer_thread_started

    mode = os.environ.get("MODE", "fastapi").lower()
    if mode != "kafka":
        logger.info("运行模式: FastAPI HTTP")
        return

    if _consumer_thread_started:
        logger.info("Kafka Consumer 已启动，跳过重复启动")
        return

    logger.info("运行模式: Kafka Consumer + HTTP (health/metrics)")
    consumer_thread = threading.Thread(target=_start_kafka_consumer, daemon=True)
    consumer_thread.start()
    _consumer_thread_started = True


if __name__ == "__main__":
    import uvicorn

    host = os.environ.get("CLIP_HOST", "0.0.0.0")
    port = int(os.environ.get("CLIP_PORT", "8000"))
    uvicorn.run(app, host=host, port=port)

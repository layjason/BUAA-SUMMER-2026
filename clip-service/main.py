"""
CLIP 图片分类 FastAPI 边车服务。

职责：接收 base64 编码的图片列表，返回 CLIP 分类结果。
"""
import base64
import logging
from io import BytesIO
from typing import List

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from classifier import classify_batch, load_model

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger(__name__)

app = FastAPI(title="MayoiStar CLIP Classifier", version="1.0.0")


class ClassifyRequest(BaseModel):
    images: List[str]  # base64 Data URI: "data:image/jpeg;base64,..."


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
    logger.info("CLIP 分类服务已就绪")


@app.get("/health")
async def health():
    """健康检查端点。"""
    return {"status": "ok"}


@app.post("/classify", response_model=ClassifyResponse)
async def classify(request: ClassifyRequest):
    """
    对传入的图片列表进行分类。

    前置条件：请求体中 images 为 base64 Data URI 列表。
    后置条件：返回与输入顺序一致的分类结果。
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

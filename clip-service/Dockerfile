# MayoiStar CLIP 图片分类服务
# 使用 NVIDIA CUDA 基础镜像，支持 GPU 加速推理
# 运行模式：
#   - 生产 (MODE=kafka)：Kafka Consumer + HTTP health/metrics
#   - 调试 (MODE=fastapi)：仅 HTTP API
# 若无需 GPU，可将基础镜像替换为 python:3.12-slim

FROM pytorch/pytorch:2.5.1-cuda12.4-cudnn9-runtime

WORKDIR /app

# 安装依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY classifier.py .
COPY main.py .
COPY s3_client.py .
COPY kafka_consumer.py .
COPY metrics.py .

# 开放端口
EXPOSE 8000

# 启动服务
# 默认 MODE=fastapi (兼容旧版)，生产环境通过环境变量 MODE=kafka 启动
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

# 默认命令（可被 docker compose / k8s 覆盖）
CMD MODE=${MODE:-fastapi} uvicorn main:app --host 0.0.0.0 --port 8000

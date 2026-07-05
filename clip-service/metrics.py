"""
CLIP 分类任务 Prometheus 指标。

职责：提供 task_duration_seconds、task_processed_total、task_errors_total 等指标，
供 Prometheus 抓取，Grafana 可视化。
"""
import time
from contextlib import contextmanager
from prometheus_client import Counter, Histogram, Gauge, generate_latest, CONTENT_TYPE_LATEST

# 任务处理耗时分布 (秒)
task_duration_seconds = Histogram(
    "clip_task_duration_seconds",
    "CLIP 分类任务处理耗时（含 S3 下载 + 推理）",
    buckets=[0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 20.0, 30.0],
)

# 处理成功计数
task_processed_total = Counter(
    "clip_task_processed_total",
    "CLIP 分类任务处理总数",
    ["status"],
)

# 处理错误计数（按错误类型分）
task_errors_total = Counter(
    "clip_task_errors_total",
    "CLIP 分类任务错误计数",
    ["error_type"],
)

# 当前 batch 中 media 数量
task_batch_size = Histogram(
    "clip_task_batch_size",
    "每次任务处理的 media 数量分布",
    buckets=[1, 2, 3, 5, 8, 10, 15, 20],
)


@contextmanager
def track_duration():
    """上下文管理器，自动记录任务耗时。"""
    start = time.time()
    try:
        yield
    finally:
        duration = time.time() - start
        task_duration_seconds.observe(duration)


def record_processed(status: str):
    """记录任务处理完成（按状态分类）。"""
    task_processed_total.labels(status=status).inc()


def record_error(error_type: str):
    """记录处理错误。"""
    task_errors_total.labels(error_type=error_type).inc()


def record_batch_size(size: int):
    """记录批次大小。"""
    task_batch_size.observe(size)

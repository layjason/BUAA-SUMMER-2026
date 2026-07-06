"""
CLIP 分类任务 Kafka 消费者。

职责：从 clip-classify-request topic 消费分类请求，
下载图片 → CLIP 分类 → 回写结果到 clip-classify-response topic。

支持：重试逻辑（3 次重试，指数退避）、优雅关闭、Prometheus 指标。
"""
import json
import logging
import os
import signal
import sys
import threading
import uuid
from typing import List

import boto3
from confluent_kafka import Consumer, Producer, KafkaError, KafkaException
from confluent_kafka.admin import AdminClient

from classifier import load_model, classify_image
from s3_client import create_s3_client, download_images
from metrics import (
    track_duration,
    record_processed,
    record_error,
    record_batch_size,
)

logger = logging.getLogger(__name__)

# 环境变量
BOOTSTRAP_SERVERS = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
GROUP_ID = os.environ.get("KAFKA_GROUP_ID", "mayoistar-clip-gpu")
REQUEST_TOPIC = os.environ.get("KAFKA_REQUEST_TOPIC", "clip-classify-request")
RESPONSE_TOPIC = os.environ.get("KAFKA_RESPONSE_TOPIC", "clip-classify-response")
MAX_RETRIES = int(os.environ.get("CONSUMER_MAX_RETRIES", "3"))

# 优雅关闭标记
_running = True


def _handle_signal(signum, frame):
    """处理 SIGTERM / SIGINT，优雅关闭。"""
    global _running
    logger.info("收到信号 %d，正在优雅关闭...", signum)
    _running = False


def create_consumer() -> Consumer:
    """
    创建 Kafka Consumer。

    前置条件：KAFKA_BOOTSTRAP_SERVERS 环境变量为可连接的 broker 地址。
    后置条件：返回已订阅 topic 的 Consumer，auto.offset.reset=earliest。
    """
    conf = {
        "bootstrap.servers": BOOTSTRAP_SERVERS,
        "group.id": GROUP_ID,
        "auto.offset.reset": "earliest",
        "enable.auto.commit": False,
        "session.timeout.ms": 30000,
        "max.poll.interval.ms": 60000,
    }
    consumer = Consumer(conf)
    consumer.subscribe([REQUEST_TOPIC])
    logger.info(
        "Kafka Consumer 已就绪: brokers=%s, group=%s, topic=%s",
        BOOTSTRAP_SERVERS,
        GROUP_ID,
        REQUEST_TOPIC,
    )
    return consumer


def create_producer() -> Producer:
    """
    创建 Kafka Producer（用于回写响应）。

    前置条件：KAFKA_BOOTSTRAP_SERVERS 配置正确。
    后置条件：返回可发送消息的 Producer。
    """
    conf = {
        "bootstrap.servers": BOOTSTRAP_SERVERS,
        "acks": "all",
        "enable.idempotence": True,
    }
    producer = Producer(conf)
    logger.info("Kafka Producer 已就绪")
    return producer


def process_message(producer: Producer, s3_client: boto3.client, msg_value: dict) -> bool:
    """
    处理单条分类请求消息。

    前置条件：msg_value 包含 taskId 和 mediaIds 字段。
    后置条件：分类结果已回写到 response topic。返回 True 表示成功。
    不变量：单张图片下载/分类失败不影响其他图片。

    :param producer: Kafka Producer
    :param s3_client: S3 客户端
    :param msg_value: 反序列化的消息字典
    :return: 处理是否成功
    """
    task_id = msg_value.get("taskId")
    media_ids = msg_value.get("mediaIds", [])

    if not task_id:
        logger.error("消息缺少 taskId，跳过")
        return True

    if not media_ids:
        logger.warning("任务无待分类图片: taskId=%s", task_id)
        _send_response(producer, task_id, "succeeded", [])
        return True

    record_batch_size(len(media_ids))
    logger.info("开始处理分类任务: taskId=%s, mediaCount=%d", task_id, len(media_ids))

    # 下载图片
    downloads = download_images(s3_client, [str(mid) for mid in media_ids])

    # 逐张分类
    items = []
    all_success = True
    for media_id, image_bytes in downloads:
        if image_bytes is None:
            logger.warning("图片下载失败，该 media 标记为空: mediaId=%s", media_id)
            items.append({
                "mediaId": media_id,
                "category": "",
                "confidence": 0.0,
            })
            all_success = False
            continue

        try:
            result = classify_image(image_bytes)
            items.append({
                "mediaId": media_id,
                "category": result["category"],
                "confidence": result["confidence"],
            })
        except Exception as e:
            logger.error("图片分类失败: mediaId=%s, error=%s", media_id, e)
            items.append({
                "mediaId": media_id,
                "category": "",
                "confidence": 0.0,
            })
            all_success = False

    status = "succeeded" if all_success else "succeeded"
    _send_response(producer, task_id, status, items)

    logger.info("分类任务处理完成: taskId=%s, status=%s", task_id, status)
    return True


def _send_response(producer: Producer, task_id: str, status: str, items: List[dict], error_message: str = None):
    """
    回写分类结果到 Kafka response topic。
    """
    response = {
        "taskId": task_id,
        "status": status,
        "items": items,
        "errorMessage": error_message,
    }
    payload = json.dumps(response).encode("utf-8")

    def delivery_report(err, msg):
        if err:
            logger.error("Kafka response 发送失败: taskId=%s, error=%s", task_id, err)
        else:
            logger.debug(
                "Kafka response 已发送: taskId=%s, partition=%s, offset=%s",
                task_id,
                msg.partition(),
                msg.offset(),
            )

    producer.produce(
        topic=RESPONSE_TOPIC,
        key=task_id.encode("utf-8"),
        value=payload,
        callback=delivery_report,
    )
    producer.flush(timeout=5.0)


def process_with_retry(producer, s3_client, msg_value) -> bool:
    """
    带重试逻辑的消息处理（3 次重试，指数退避 1s/2s/4s）。
    """
    for attempt in range(MAX_RETRIES):
        try:
            with track_duration():
                success = process_message(producer, s3_client, msg_value)
                record_processed("succeeded")
                return success
        except Exception as e:
            logger.warning(
                "分类处理失败 (attempt %d/%d): taskId=%s, error=%s",
                attempt + 1,
                MAX_RETRIES,
                msg_value.get("taskId"),
                e,
            )
            record_error(type(e).__name__)
            if attempt < MAX_RETRIES - 1:
                import time
                time.sleep(2 ** attempt)

    logger.error("分类处理最终失败: taskId=%s", msg_value.get("taskId"))
    _send_response(
        producer,
        msg_value.get("taskId"),
        "failed",
        [],
        f"Failed after {MAX_RETRIES} retries",
    )
    record_processed("failed")
    return False


def run_consumer():
    """
    Kafka Consumer 主循环。

    前置条件：CLIP 模型已加载（通过 load_model()），S3 客户端和 Kafka client 已创建。
    后置条件：持续 poll 和处理消息，直到收到退出信号。
    不变量：消息处理成功后才 commit offset。
    """
    if threading.current_thread() is threading.main_thread():
        signal.signal(signal.SIGINT, _handle_signal)
        signal.signal(signal.SIGTERM, _handle_signal)
    else:
        logger.info("Kafka Consumer 在线程中运行，跳过进程信号注册")

    consumer = create_consumer()
    producer = create_producer()
    s3_client = create_s3_client()

    logger.info("Kafka Consumer 主循环启动")

    try:
        while _running:
            msg = consumer.poll(timeout=1.0)
            if msg is None:
                continue
            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    continue
                logger.error("Kafka 消费错误: %s", msg.error())
                continue

            try:
                msg_value = json.loads(msg.value().decode("utf-8"))
                success = process_with_retry(producer, s3_client, msg_value)
                if success:
                    consumer.commit(message=msg)
            except json.JSONDecodeError:
                logger.error("消息 JSON 解析失败: offset=%s", msg.offset())
                consumer.commit(message=msg)
            except Exception as e:
                logger.exception("消息处理异常: offset=%s", msg.offset())
    finally:
        logger.info("正在关闭 Kafka Consumer...")
        consumer.close()
        logger.info("Kafka Consumer 已关闭")


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    logger.info("正在加载 CLIP 模型...")
    load_model()
    logger.info("CLIP 模型加载完成")
    run_consumer()

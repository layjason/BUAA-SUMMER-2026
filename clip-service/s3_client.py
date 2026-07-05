"""
S3 (RustFS) 客户端封装。

职责：从对象存储中按 mediaId 下载图片字节，供 CLIP 分类使用。
支持 S3 兼容 API（RustFS / MinIO 等）。
"""
import logging
import os
from io import BytesIO
from typing import List, Optional

import boto3
from botocore.client import Config as BotoConfig
from botocore.exceptions import ClientError

logger = logging.getLogger(__name__)


def create_s3_client() -> boto3.client:
    """
    创建 S3 (RustFS 兼容) 客户端。

    前置条件：环境变量 RUSTFS_ENDPOINT、RUSTFS_ACCESS_KEY、RUSTFS_SECRET_KEY 已配置。
    后置条件：返回可用的 boto3 S3 client。
    不变量：端点使用 path-style 寻址，禁用虚拟主机寻址。
    """
    endpoint = os.environ.get("RUSTFS_ENDPOINT", "http://localhost:9000")
    access_key = os.environ.get("RUSTFS_ACCESS_KEY", "rustfsadmin")
    secret_key = os.environ.get("RUSTFS_SECRET_KEY", "rustfsadmin")

    s3 = boto3.client(
        "s3",
        endpoint_url=endpoint,
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        config=BotoConfig(s3={"addressing_style": "path"}),
    )
    logger.info("S3 客户端已创建: endpoint=%s", endpoint)
    return s3


def download_images(s3_client: boto3.client, media_ids: List[str]) -> list:
    """
    下载多个图片的字节数据。

    前置条件：s3_client 为已认证的客户端，media_ids 为有效的对象键列表。
    后置条件：依序返回 (media_id, bytes_or_None) 的列表。
             单个下载失败时该项 bytes 为 None，不中断其他下载。
    不变量：不修改 S3 上的对象。

    :param s3_client: boto3 S3 客户端
    :param media_ids: 媒体文件 ID 列表（即 S3 对象键）
    :return: [(media_id, image_bytes), ...] 列表
    """
    bucket = os.environ.get("RUSTFS_BUCKET", "mayoistar-media")
    results = []

    for media_id in media_ids:
        try:
            response = s3_client.get_object(Bucket=bucket, Key=str(media_id))
            image_bytes = response["Body"].read()
            results.append((media_id, image_bytes))
            logger.debug("图片下载成功: mediaId=%s, size=%d bytes", media_id, len(image_bytes))
        except ClientError as e:
            logger.warning("图片下载失败: mediaId=%s, error=%s", media_id, e)
            results.append((media_id, None))
        except Exception as e:
            logger.error("图片下载异常: mediaId=%s, error=%s", media_id, e)
            results.append((media_id, None))

    return results

"""
CLIP 图片分类核心模块。

职责：加载 ViT-B-32 (laion2b_s34b_b79k) 模型，使用 Prompt Ensembling 对图片进行 5 类分类。
"""
import logging
from io import BytesIO
from typing import List

import open_clip
import torch
from PIL import Image

logger = logging.getLogger(__name__)

# 分类类别及其对应的 Prompt 集合（Prompt Ensembling）
CATEGORY_PROMPTS = {
    "group_photo": [
        "a group photo of people posing for the camera",
        "a group portrait of friends",
        "many people standing together looking at camera",
    ],
    "venue": [
        "a wide shot of activity venue",
        "empty outdoor event space",
        "scenery of the activity site",
        "landscape of the park or stadium",
    ],
    "process": [
        "action shot of people doing activities",
        "people hiking or playing sports",
        "candid photo of people in motion",
        "dynamic activity process",
    ],
    "supplies": [
        "close up of supplies and equipment",
        "food and drinks on table",
        "hiking gear or sports equipment",
        "event banners and supplies",
    ],
    "achievement": [
        "a photo of a trophy or certificate",
        "people celebrating at the finish line",
        "completed work or final achievement",
        "group cheering at the end",
    ],
}

# 类别展示顺序（保证结果一致性）
CATEGORY_NAMES = list(CATEGORY_PROMPTS.keys())

# 提前编码所有提示词文本特征
# 每个类别取各 prompt 特征向量的均值作为该类的代表特征
_model = None
_tokenizer = None
_preprocess = None
_text_features_by_category = None
_device = None


def _get_device() -> str:
    """获取最佳可用设备。"""
    if torch.cuda.is_available():
        return "cuda"
    if torch.backends.mps.is_available():
        return "mps"
    return "cpu"


def load_model():
    """
    加载 CLIP 模型并预计算所有类别的文本特征。

    前置条件：未加载模型。
    后置条件：模型、分词器、预处理函数和类别文本特征均已就绪。
    不变量：模型仅加载一次，全局共享。
    """
    global _model, _tokenizer, _preprocess, _text_features_by_category, _device
    if _model is not None:
        return

    _device = _get_device()
    logger.info("加载 CLIP 模型 ViT-B-32 (laion2b_s34b_b79k)，设备: %s", _device)

    _model, _, _preprocess = open_clip.create_model_and_transforms(
        "ViT-B-32", pretrained="laion2b_s34b_b79k"
    )
    _tokenizer = open_clip.get_tokenizer("ViT-B-32")

    if _device == "cuda":
        _model = _model.cuda()
    elif _device == "mps":
        _model = _model.to("mps")

    _model.eval()

    # 预计算所有 Prompt 的文本特征
    _text_features_by_category = {}
    with torch.no_grad():
        for category, prompts in CATEGORY_PROMPTS.items():
            tokenized = _tokenizer(prompts)
            if _device != "cpu":
                tokenized = tokenized.to(_device)
            features = _model.encode_text(tokenized)
            # 归一化后取均值作为该类别的代表特征
            features = features / features.norm(dim=-1, keepdim=True)
            avg_feature = features.mean(dim=0)
            avg_feature = avg_feature / avg_feature.norm(dim=-1, keepdim=True)
            _text_features_by_category[category] = avg_feature

    logger.info("CLIP 模型加载完成，已预计算 %d 个类别的文本特征", len(_text_features_by_category))


def classify_image(image_bytes: bytes) -> dict:
    """
    对单张图片进行分类。

    前置条件：模型已加载。
    后置条件：返回包含 category 和 confidence 的字典。
    不变量：不修改传入的图片数据。

    :param image_bytes: 图片字节数据（JPEG/PNG 格式）
    :return: {"category": "group_photo", "confidence": 0.85}
    """
    if _model is None:
        raise RuntimeError("模型尚未加载，请先调用 load_model()")

    image = Image.open(BytesIO(image_bytes)).convert("RGB")
    image_tensor = _preprocess(image).unsqueeze(0)
    if _device != "cpu":
        image_tensor = image_tensor.to(_device)

    with torch.no_grad():
        image_features = _model.encode_image(image_tensor)
        image_features = image_features / image_features.norm(dim=-1, keepdim=True)

        # 计算与各类别文本特征的余弦相似度
        category_features = torch.stack([_text_features_by_category[c] for c in CATEGORY_NAMES])
        logits = (100.0 * image_features @ category_features.T).softmax(dim=-1)

    best_idx = logits[0].argmax().item()
    return {
        "category": CATEGORY_NAMES[best_idx],
        "confidence": round(logits[0][best_idx].item(), 4),
    }


def classify_batch(images: List[bytes]) -> List[dict]:
    """
    批量分类多张图片。

    前置条件：模型已加载，images 中每个元素为图片字节数据。
    后置条件：返回与输入顺序一致的结果列表。
    不变量：不修改传入的图片数据。

    :param images: 图片字节数据列表
    :return: 分类结果列表
    """
    if not images:
        return []

    # 预处理所有图片并堆叠为 batch
    pil_images = [Image.open(BytesIO(img_bytes)).convert("RGB") for img_bytes in images]
    image_tensors = torch.stack([_preprocess(img) for img in pil_images])
    if _device != "cpu":
        image_tensors = image_tensors.to(_device)

    with torch.no_grad():
        image_features = _model.encode_image(image_tensors)
        image_features = image_features / image_features.norm(dim=-1, keepdim=True)

        category_features = torch.stack([_text_features_by_category[c] for c in CATEGORY_NAMES])
        logits = (100.0 * image_features @ category_features.T).softmax(dim=-1)

    best_indices = logits.argmax(dim=-1)
    best_confidences = logits.max(dim=-1).values

    results = []
    for i in range(len(images)):
        idx = best_indices[i].item()
        results.append({
            "category": CATEGORY_NAMES[idx],
            "confidence": round(best_confidences[i].item(), 4),
        })

    return results

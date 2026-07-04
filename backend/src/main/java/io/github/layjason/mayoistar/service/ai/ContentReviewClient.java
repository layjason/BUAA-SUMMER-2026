package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import java.util.List;

/**
 * 内容安全审核客户端。
 *
 * <p>类职责：定义文本和图片内容安全审核能力，隐藏具体供应商 SDK。
 *
 * <p>类不变量：实现不得在单元测试中真实调用外部 AI API。
 */
public interface ContentReviewClient {

    ContentReviewScanResult scanText(String content);

    ContentReviewScanResult scanImages(List<MediaFile> images);
}

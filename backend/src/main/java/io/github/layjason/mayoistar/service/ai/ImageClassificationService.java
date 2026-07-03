package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.api.ai.AiDtos.ImageClassificationResult;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.NonNull;

/**
 * 图片分类服务接口。
 *
 * <p>类职责：定义 AI 图片分类的业务契约，将上传的媒体图片按 5 个类别（合影、场地、过程记录、物资、成果展示）进行分类。
 */
public interface ImageClassificationService {

    /**
     * 对指定媒体文件进行分类。
     *
     * <p>前置条件：mediaIds 非空，每个 mediaId 对应的媒体文件已上传至 S3 且支持分类的图片格式。
     *
     * <p>后置条件：返回逐图片的分类结果；若某张图片不存在或不可用，则跳过并记录日志。
     *
     * <p>不变量：不修改媒体文件本身。
     *
     * @param mediaIds 待分类的媒体文件 ID 列表
     * @return 图片分类结果
     */
    ImageClassificationResult classifyImages(@NonNull List<UUID> mediaIds);
}

package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.api.ai.AiDtos.ClassifyTaskQueryResponse;
import io.github.layjason.mayoistar.api.ai.AiDtos.ClassifyTaskSubmitResponse;
import io.github.layjason.mayoistar.api.ai.AiDtos.MediaClassificationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.NonNull;

/**
 * 图片分类服务接口。
 *
 * <p>类职责：定义 AI 图片分类的异步业务契约。
 * 通过 Kafka 将分类任务提交给 GPU 集群，客户端轮询获取结果。
 *
 * <p>类不变量：已分类过的图片（ai_classification_results 表中存在记录）
 * 直接返回缓存结果，不重复调用 GPU 推理。
 */
public interface ImageClassificationService {

    /**
     * 提交图片分类任务。
     *
     * <p>前置条件：mediaIds 非空，每个 mediaId 对应的媒体文件已上传至 S3。
     *
     * <p>后置条件：已缓存的 media 直接标记完成；未缓存的发往 Kafka。
     * 返回 taskId 供客户端轮询结果。
     *
     * @param mediaIds 待分类的媒体文件 ID 列表
     * @param userId   发起任务的用户 ID（用于 WebSocket 通知）
     * @return 任务提交响应，含 taskId 和初始状态
     */
    ClassifyTaskSubmitResponse submitClassifyTask(@NonNull List<UUID> mediaIds, @NonNull String userId);

    /**
     * 查询分类任务结果。
     *
     * <p>前置条件：taskId 为 submitClassifyTask 返回的有效任务 ID。
     *
     * <p>后置条件：返回任务当前状态和结果。pending 时仅返回状态；
     * succeeded 时返回所有 media(含缓存命中)的分类结果；
     * 超过 30s 未完成则返回 timeout 状态。
     *
     * <p>不变量：不修改数据库或 Redis 中的任务状态。
     *
     * @param taskId 任务 ID
     * @return 任务查询响应
     */
    ClassifyTaskQueryResponse getClassifyTaskResult(@NonNull UUID taskId);

    /**
     * 按 mediaId 查询单个图片的分类缓存。
     *
     * <p>前置条件：mediaId 对应的媒体文件已上传。
     *
     * <p>后置条件：命中缓存时返回分类结果；未命中时返回 null。
     *
     * @param mediaId 媒体文件 ID
     * @return 分类结果，未分类时为 null
     */
    MediaClassificationResponse getClassificationByMediaId(@NonNull UUID mediaId);
}

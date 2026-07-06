package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.api.ai.AiDtos.ClassifyTaskQueryResponse;
import io.github.layjason.mayoistar.api.ai.AiDtos.ClassifyTaskSubmitResponse;
import io.github.layjason.mayoistar.api.ai.AiDtos.ImageClassificationItem;
import io.github.layjason.mayoistar.api.ai.AiDtos.MediaClassificationResponse;
import io.github.layjason.mayoistar.config.AiProperties;
import io.github.layjason.mayoistar.entity.ai.AiClassificationResult;
import io.github.layjason.mayoistar.service.ai.ClipTaskResultStore.TaskStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

/**
 * 图片分类服务实现（Kafka 异步模式）。
 *
 * <p>类职责：协调缓存检查、Kafka 消息发送、任务状态查询和结果组装。
 *
 * <p>类不变量：分类类别标签映射关系固定；任务超时由 AiProperties 中的配置决定。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "mayoistar.ai.clip.kafka-enabled", havingValue = "true", matchIfMissing = true)
public class ImageClassificationServiceImpl implements ImageClassificationService {

    /**
     * CLIP 分类标签到中文展示标签的映射。
     */
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "group_photo", "合影",
            "venue", "场地",
            "process", "过程记录",
            "supplies", "物资",
            "achievement", "成果展示");

    private final ClassificationResultCache resultCache;
    private final ClipTaskResultStore taskResultStore;
    private final KafkaClipProducer kafkaClipProducer;
    private final int requestTimeoutSeconds;

    public ImageClassificationServiceImpl(
            ClassificationResultCache resultCache,
            ClipTaskResultStore taskResultStore,
            KafkaClipProducer kafkaClipProducer,
            AiProperties aiProperties) {
        this.resultCache = resultCache;
        this.taskResultStore = taskResultStore;
        this.kafkaClipProducer = kafkaClipProducer;
        this.requestTimeoutSeconds = aiProperties.getClip().getRequestTimeoutSeconds();
    }

    /**
     * 提交图片分类任务。
     *
     * <p>前置条件：mediaIds 非空，userId 为当前认证用户。
     *
     * <p>后置条件：缓存命中的 media 不发送 Kafka；
     * 未缓存的 media 发往 Kafka；任务状态写入 Redis。
     *
     * @param mediaIds 待分类的媒体文件 ID 列表
     * @param userId   发起任务的用户 ID
     * @return 任务提交响应
     */
    @Override
    public ClassifyTaskSubmitResponse submitClassifyTask(@NonNull List<UUID> mediaIds, @NonNull String userId) {
        if (mediaIds.isEmpty()) {
            ClassifyTaskSubmitResponse response = new ClassifyTaskSubmitResponse();
            response.setTaskId(null);
            response.setStatus("succeeded");
            return response;
        }

        UUID taskId = UUID.randomUUID();

        // 筛出已缓存的 mediaId
        List<UUID> cachedMediaIds = resultCache.findCachedMediaIds(mediaIds);
        List<UUID> uncachedMediaIds =
                mediaIds.stream().filter(id -> !cachedMediaIds.contains(id)).toList();

        // 创建任务状态
        taskResultStore.createTask(taskId, userId, mediaIds);

        if (uncachedMediaIds.isEmpty()) {
            // 全部命中缓存，无需调用 GPU
            taskResultStore.markCompleted(taskId, "succeeded", null);
            log.info("分类任务全部命中缓存: taskId={}, mediaCount={}", taskId, mediaIds.size());
            ClassifyTaskSubmitResponse response = new ClassifyTaskSubmitResponse();
            response.setTaskId(taskId);
            response.setStatus("succeeded");
            return response;
        }

        // 发送未缓存的到 Kafka
        kafkaClipProducer.send(taskId, uncachedMediaIds);
        log.info(
                "分类任务已提交: taskId={}, total={}, cached={}, toClassify={}",
                taskId,
                mediaIds.size(),
                cachedMediaIds.size(),
                uncachedMediaIds.size());

        ClassifyTaskSubmitResponse response = new ClassifyTaskSubmitResponse();
        response.setTaskId(taskId);
        response.setStatus("pending");
        return response;
    }

    /**
     * 查询分类任务结果。
     *
     * <p>前置条件：taskId 由 submitClassifyTask 返回。
     *
     * <p>后置条件：返回当前任务状态。pending 时仅状态；succeeded 时含全部结果。
     *
     * @param taskId 任务 ID
     * @return 任务查询响应
     */
    @Override
    public ClassifyTaskQueryResponse getClassifyTaskResult(@NonNull UUID taskId) {
        TaskStatus status = taskResultStore.getStatus(taskId);
        if (status == null) {
            throw new io.github.layjason.mayoistar.exception.BusinessException(
                    io.github.layjason.mayoistar.exception.ErrorCodes.AI_TASK_NOT_FOUND,
                    "Classification task not found");
        }

        if ("pending".equals(status.getStatus())) {
            // 检查超时
            Instant createdAt = Instant.parse(status.getCreatedAt());
            int timeoutSeconds = requestTimeoutSeconds;
            if (Duration.between(createdAt, Instant.now()).getSeconds() > timeoutSeconds) {
                taskResultStore.markCompleted(taskId, "failed", "Task timed out after " + timeoutSeconds + " seconds");
                ClassifyTaskQueryResponse response = new ClassifyTaskQueryResponse();
                response.setStatus("timeout");
                response.setErrorMessage("Task timed out");
                return response;
            }

            ClassifyTaskQueryResponse response = new ClassifyTaskQueryResponse();
            response.setStatus("pending");
            return response;
        }

        if ("failed".equals(status.getStatus())) {
            ClassifyTaskQueryResponse response = new ClassifyTaskQueryResponse();
            response.setStatus("failed");
            response.setErrorMessage(status.getErrorMessage());
            return response;
        }

        // succeeded — 从 DB 获取该任务所有 mediaIds 的分类结果
        List<UUID> mediaIds = status.getMediaIds();
        List<AiClassificationResult> results = resultCache.findByMediaIds(mediaIds);

        List<ImageClassificationItem> items = new ArrayList<>();
        for (UUID mediaId : mediaIds) {
            ImageClassificationItem item = new ImageClassificationItem();
            item.setMediaId(mediaId);

            Optional<AiClassificationResult> matchingResult =
                    results.stream().filter(r -> r.getMediaId().equals(mediaId)).findFirst();

            if (matchingResult.isPresent()) {
                AiClassificationResult cr = matchingResult.get();
                String label = CATEGORY_LABELS.getOrDefault(cr.getCategory(), cr.getCategory());
                item.setSuggestedTags(List.of(label));
                item.setConfidence(cr.getConfidence());
            } else {
                item.setSuggestedTags(List.of());
                item.setConfidence(0.0);
            }

            items.add(item);
        }

        ClassifyTaskQueryResponse response = new ClassifyTaskQueryResponse();
        response.setStatus("succeeded");
        response.setItems(items);
        return response;
    }

    /**
     * 按 mediaId 查询单个图片的分类缓存。
     *
     * @param mediaId 媒体文件 ID
     * @return 分类结果，未分类时为 null
     */
    @Override
    public MediaClassificationResponse getClassificationByMediaId(@NonNull UUID mediaId) {
        Optional<AiClassificationResult> result = resultCache.findByMediaId(mediaId);
        if (result.isEmpty()) {
            return null;
        }

        AiClassificationResult cr = result.get();
        String label = CATEGORY_LABELS.getOrDefault(cr.getCategory(), cr.getCategory());

        MediaClassificationResponse response = new MediaClassificationResponse();
        response.setMediaId(cr.getMediaId());
        response.setSuggestedTags(List.of(label));
        response.setConfidence(cr.getConfidence());
        response.setClassifiedAt(cr.getClassifiedAt().toString());
        return response;
    }
}

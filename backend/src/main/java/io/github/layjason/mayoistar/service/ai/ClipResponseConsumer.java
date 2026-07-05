package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.api.ai.AiDtos.ImageClassificationCompletedEvent;
import io.github.layjason.mayoistar.entity.ai.AiClassificationResult;
import io.github.layjason.mayoistar.service.NotificationService;
import io.github.layjason.mayoistar.service.ai.ClipTaskResultStore.TaskStatus;
import io.github.layjason.mayoistar.service.ai.KafkaModels.ClipTaskItem;
import io.github.layjason.mayoistar.service.ai.KafkaModels.ClipTaskResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * CLIP 分类响应 Kafka 消费者。
 *
 * <p>类职责：消费 clip-classify-response topic 中的分类结果，
 * 将结果写入 ai_classification_results 表，更新 Redis 任务状态，
 * 并通过 WebSocket 通知前端。
 *
 * <p>类不变量：每条消息仅在 ACK 后提交 offset，处理失败时抛出异常触发重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean({ConcurrentKafkaListenerContainerFactory.class, StringRedisTemplate.class})
@ConditionalOnProperty(name = "mayoistar.ai.clip.kafka-enabled", havingValue = "true", matchIfMissing = true)
public class ClipResponseConsumer {

    private final ClassificationResultCache resultCache;
    private final ClipTaskResultStore taskResultStore;
    private final NotificationService notificationService;

    /**
     * 消费 CLIP 分类响应消息。
     *
     * <p>前置条件：Kafka topic clip-classify-response 中存在待消费消息。
     *
     * <p>后置条件：分类结果已写入 DB，任务状态已更新，WebSocket 通知已推送。
     *
     * @param record Kafka 消息记录
     * @param ack    手动确认
     */
    @KafkaListener(
            topics = "${mayoistar.ai.clip.response-topic:clip-classify-response}",
            containerFactory = "clipResponseKafkaListenerContainerFactory")
    public void onResponse(ConsumerRecord<String, ClipTaskResponse> record, Acknowledgment ack) {
        ClipTaskResponse response = record.value();
        UUID taskId = response.getTaskId();

        if (taskId == null) {
            log.warn("分类响应缺少 taskId，跳过: offset={}", record.offset());
            ack.acknowledge();
            return;
        }

        TaskStatus taskStatus = taskResultStore.getStatus(taskId);
        if (taskStatus == null) {
            log.warn("任务状态不存在或已过期，忽略迟来响应: taskId={}", taskId);
            ack.acknowledge();
            return;
        }

        try {
            if ("succeeded".equals(response.getStatus())) {
                handleSuccess(taskId, taskStatus, response);
            } else {
                handleFailure(taskId, taskStatus, response);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("分类响应处理失败，将重试: taskId={}", taskId, e);
            throw e;
        }
    }

    /**
     * 处理成功分类结果：写入 DB → 更新 Redis → 通知前端。
     */
    private void handleSuccess(UUID taskId, TaskStatus taskStatus, ClipTaskResponse response) {
        List<ClipTaskItem> items = response.getItems();
        if (items == null || items.isEmpty()) {
            log.warn("分类成功但结果为空: taskId={}", taskId);
            taskResultStore.markCompleted(taskId, "succeeded", null);
            notifyUser(taskStatus, taskId, "succeeded");
            return;
        }

        List<AiClassificationResult> entities = new ArrayList<>();
        for (ClipTaskItem item : items) {
            AiClassificationResult entity = AiClassificationResult.builder()
                    .mediaId(item.getMediaId())
                    .category(item.getCategory())
                    .confidence(item.getConfidence() != null ? item.getConfidence() : 0.0)
                    .taskId(taskId)
                    .build();
            entities.add(entity);
        }

        resultCache.saveBatch(entities);
        taskResultStore.markCompleted(taskId, "succeeded", null);
        notifyUser(taskStatus, taskId, "succeeded");
    }

    /**
     * 处理失败分类结果：仅更新 Redis 和通知前端，不写入 DB。
     */
    private void handleFailure(UUID taskId, TaskStatus taskStatus, ClipTaskResponse response) {
        String errorMessage = response.getErrorMessage() != null ? response.getErrorMessage() : "分类任务失败";
        log.warn("分类任务失败: taskId={}, error={}", taskId, errorMessage);
        taskResultStore.markCompleted(taskId, "failed", errorMessage);
        notifyUser(taskStatus, taskId, "failed");
    }

    /**
     * 通过 WebSocket 通知发起任务的前端用户。
     *
     * <p>前置条件：taskStatus.userId 为发起任务用户的身份标识。
     *
     * @param taskStatus 任务状态
     * @param taskId     任务 ID
     * @param status     完成状态
     */
    private void notifyUser(TaskStatus taskStatus, UUID taskId, String status) {
        String userId = taskStatus.getUserId();
        if (userId == null) {
            log.warn("任务 userId 为空，无法推送通知: taskId={}", taskId);
            return;
        }
        ImageClassificationCompletedEvent event = new ImageClassificationCompletedEvent();
        event.setKind("image_classification_completed");
        event.setTaskId(taskId);
        event.setStatus(status);
        notificationService.notifyImageClassificationCompleted(event, userId);
    }
}

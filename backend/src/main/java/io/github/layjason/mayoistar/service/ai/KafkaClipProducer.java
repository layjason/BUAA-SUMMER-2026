package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.config.AiProperties;
import io.github.layjason.mayoistar.service.ai.KafkaModels.ClipTaskRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * CLIP 分类请求 Kafka 生产者。
 *
 * <p>类职责：将分类任务请求发送到 clip-classify-request topic，
 * 供 Python GPU consumer group 消费。
 *
 * <p>不变量：message key 为 taskId 字符串，保证同一任务的后续消息路由到同一分区。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(KafkaTemplate.class)
@ConditionalOnProperty(name = "mayoistar.ai.clip.kafka-enabled", havingValue = "true", matchIfMissing = true)
public class KafkaClipProducer {

    private final KafkaTemplate<String, ClipTaskRequest> kafkaTemplate;
    private final AiProperties aiProperties;

    /**
     * 发送分类请求消息。
     *
     * <p>前置条件：mediaIds 非空，其中每个 mediaId 均无缓存。
     *
     * <p>后置条件：消息被异步发送到 Kafka，发送失败时记录错误日志。
     *
     * @param taskId   任务 ID
     * @param mediaIds 待分类的媒体文件 ID 列表
     */
    public void send(UUID taskId, List<UUID> mediaIds) {
        ClipTaskRequest request = ClipTaskRequest.builder()
                .taskId(taskId)
                .mediaIds(mediaIds)
                .timestamp(Instant.now().toString())
                .build();

        String topic = aiProperties.getClip().getRequestTopic();

        kafkaTemplate.send(topic, taskId.toString(), request).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Kafka 分类请求发送失败: taskId={}, topic={}", taskId, topic, ex);
            } else {
                SendResult<String, ClipTaskRequest> sendResult = result;
                log.info(
                        "Kafka 分类请求已发送: taskId={}, topic={}, partition={}, offset={}",
                        taskId,
                        topic,
                        sendResult.getRecordMetadata().partition(),
                        sendResult.getRecordMetadata().offset());
            }
        });
    }
}

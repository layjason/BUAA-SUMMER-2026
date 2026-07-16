package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.config.AiProperties;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.service.ai.KafkaModels.ClipTaskMedia;
import io.github.layjason.mayoistar.service.ai.KafkaModels.ClipTaskRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@ConditionalOnProperty(name = "mayoistar.ai.clip.kafka-enabled", havingValue = "true", matchIfMissing = true)
public class KafkaClipProducer {

    private final KafkaTemplate<String, ClipTaskRequest> kafkaTemplate;
    private final AiProperties aiProperties;
    private final MediaFileRepository mediaFileRepository;

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
        Map<UUID, MediaFile> mediaFileById = mediaFileRepository.findByMediaIdIn(mediaIds).stream()
                .collect(Collectors.toMap(MediaFile::getMediaId, Function.identity()));
        List<ClipTaskMedia> mediaFiles = mediaIds.stream()
                .map(mediaId -> {
                    MediaFile mediaFile = mediaFileById.get(mediaId);
                    if (mediaFile == null) {
                        log.warn("分类请求中的媒体文件不存在: taskId={}, mediaId={}", taskId, mediaId);
                        return ClipTaskMedia.builder().mediaId(mediaId).build();
                    }
                    return ClipTaskMedia.builder()
                            .mediaId(mediaId)
                            .storagePath(mediaFile.getStoragePath())
                            .build();
                })
                .toList();

        ClipTaskRequest request = ClipTaskRequest.builder()
                .taskId(taskId)
                .mediaIds(mediaIds)
                .mediaFiles(mediaFiles)
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

package io.github.layjason.mayoistar.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的 CLIP 分类任务状态存储。
 *
 * <p>类职责：追踪每个分类任务的状态（pending/succeeded/failed），
 * 支持任务超时检查和关联 mediaIds 查询。
 *
 * <p>不变量：Redis key 格式为 clip:task:{taskId}，TTL 为 30 分钟。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class ClipTaskResultStore {

    private static final String KEY_PREFIX = "clip:task:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 创建分类任务记录。
     *
     * <p>前置条件：taskId 全局唯一，userId 为当前认证用户。
     *
     * <p>后置条件：任务状态为 pending，Redis 中写入 TTL 30min。
     *
     * @param taskId   任务 ID
     * @param userId   发起任务的用户 ID
     * @param mediaIds 所有待分类的 mediaId（含缓存命中的）
     */
    public void createTask(UUID taskId, String userId, List<UUID> mediaIds) {
        TaskStatus status = TaskStatus.builder()
                .status("pending")
                .userId(userId)
                .mediaIds(mediaIds)
                .createdAt(Instant.now().toString())
                .build();
        store(taskId, status);
        log.info("分类任务已创建: taskId={}, userId={}, mediaCount={}", taskId, userId, mediaIds.size());
    }

    /**
     * 标记任务完成（成功或失败）。
     *
     * <p>前置条件：taskId 对应的任务状态为 pending。
     *
     * <p>后置条件：Redis 中任务状态更新为 succeeded 或 failed。
     *
     * @param taskId       任务 ID
     * @param status       完成状态
     * @param errorMessage 失败时的错误信息（成功时为 null）
     */
    public void markCompleted(UUID taskId, String status, @Nullable String errorMessage) {
        TaskStatus current = load(taskId);
        if (current == null) {
            log.warn("任务状态不存在，无法标记完成: taskId={}", taskId);
            return;
        }
        current.setStatus(status);
        current.setErrorMessage(errorMessage);
        current.setCompletedAt(Instant.now().toString());
        store(taskId, current);
        log.info("分类任务已完成: taskId={}, status={}", taskId, status);
    }

    /**
     * 查询任务状态。
     *
     * <p>前置条件：taskId 可能对应已完成或过期的任务。
     *
     * <p>后置条件：返回任务状态，若不存在则返回 null。
     *
     * @param taskId 任务 ID
     * @return 任务状态，null 表示不存在
     */
    @Nullable
    public TaskStatus getStatus(UUID taskId) {
        return load(taskId);
    }

    /**
     * 获取任务关联的完整 mediaIds 列表（含缓存命中的）。
     *
     * @param taskId 任务 ID
     * @return mediaId 列表
     */
    @Nullable
    public List<UUID> getMediaIds(UUID taskId) {
        TaskStatus status = load(taskId);
        return status != null ? status.getMediaIds() : null;
    }

    private void store(UUID taskId, TaskStatus status) {
        try {
            String json = objectMapper.writeValueAsString(status);
            redisTemplate.opsForValue().set(KEY_PREFIX + taskId, json, TTL);
        } catch (JsonProcessingException e) {
            log.error("任务状态序列化失败: taskId={}", taskId, e);
        }
    }

    @Nullable
    private TaskStatus load(UUID taskId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + taskId);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, TaskStatus.class);
        } catch (JsonProcessingException e) {
            log.error("任务状态反序列化失败: taskId={}", taskId, e);
            return null;
        }
    }

    /**
     * 分类任务状态 POJO，以 JSON 序列化存储在 Redis 中。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStatus {

        private String status;
        private String userId;
        private List<UUID> mediaIds;
        private String createdAt;
        private String completedAt;
        private String errorMessage;
    }
}

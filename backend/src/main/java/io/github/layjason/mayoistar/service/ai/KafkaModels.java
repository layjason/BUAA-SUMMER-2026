package io.github.layjason.mayoistar.service.ai;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka 消息 DTO 集合。
 *
 * <p>类职责：定义 Java 后端与 Python GPU 服务之间的 Kafka 消息模型。
 *
 * <p>类不变量：请求和响应均以 taskId 为关联键，保证消息可追溯。
 */
public final class KafkaModels {

    private KafkaModels() {}

    /**
     * Kafka 分类请求消息，发往 clip-classify-request topic。
     *
     * <p>前置条件：mediaIds 仅包含 ai_classification_results 中无缓存的 mediaId。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClipTaskRequest {

        /** 任务唯一标识，同时作为 Kafka message key */
        private UUID taskId;

        /** 待分类的媒体文件 ID 列表（已剔除缓存命中的） */
        private List<UUID> mediaIds;

        /** 任务创建时间 ISO-8601 字符串 */
        private String timestamp;
    }

    /**
     * 单张图片分类结果项，由 Python GPU 服务返回。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClipTaskItem {

        /** 图片媒体文件 ID */
        private UUID mediaId;

        /** CLIP 分类标签 */
        private String category;

        /** 分类置信度，范围 [0, 1] */
        private Double confidence;
    }

    /**
     * Kafka 分类响应消息，发往 clip-classify-response topic。
     *
     * <p>前置条件：status 为 "succeeded" 或 "failed"。
     * succeeded 时 items 非空；failed 时 errorMessage 非空。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClipTaskResponse {

        /** 关联的任务 ID */
        private UUID taskId;

        /** 任务状态：succeeded 或 failed */
        private String status;

        /** 分类结果列表（succeeded 时存在） */
        private List<ClipTaskItem> items;

        /** 错误信息（failed 时存在） */
        private String errorMessage;
    }
}

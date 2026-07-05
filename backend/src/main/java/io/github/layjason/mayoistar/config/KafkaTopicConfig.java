package io.github.layjason.mayoistar.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Topic 自动创建配置。
 *
 * <p>类职责：在应用启动时声明三个 Topic（请求、响应、死信），
 * 若 Topic 已存在则沿用已有配置。
 *
 * <p>不变量：request topic 分区数 15（匹配 3 台 GPU × 5 进程），
 * response 和 DLQ 各 1 个分区（单一消费者）。
 */
@Configuration
@RequiredArgsConstructor
public class KafkaTopicConfig {

    private final AiProperties aiProperties;

    /**
     * 图片分类请求 Topic。
     *
     * <p>后置条件：创建 15 分区的请求 Topic，GPU consumer group 中 15 个 consumer 各分配 1 个分区。
     *
     * @return NewTopic
     */
    @Bean
    public NewTopic clipClassifyRequestTopic() {
        return TopicBuilder.name(aiProperties.getClip().getRequestTopic())
                .partitions(15)
                .replicas(1)
                .build();
    }

    /**
     * 图片分类响应 Topic。
     *
     * <p>后置条件：创建单分区的响应 Topic，Java 后端单消费者保证处理有序。
     *
     * @return NewTopic
     */
    @Bean
    public NewTopic clipClassifyResponseTopic() {
        return TopicBuilder.name(aiProperties.getClip().getResponseTopic())
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * 图片分类请求死信 Topic。
     *
     * <p>后置条件：创建单分区的 DLQ Topic，3 次 retry 后消息进入此队列。
     *
     * @return NewTopic
     */
    @Bean
    public NewTopic clipClassifyRequestDlqTopic() {
        return TopicBuilder.name(aiProperties.getClip().getRequestDlqTopic())
                .partitions(1)
                .replicas(1)
                .build();
    }
}

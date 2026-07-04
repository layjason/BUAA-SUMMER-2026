package io.github.layjason.mayoistar.config;

import io.github.layjason.mayoistar.service.ai.KafkaModels.ClipTaskRequest;
import io.github.layjason.mayoistar.service.ai.KafkaModels.ClipTaskResponse;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka 生产者与消费者工厂配置。
 *
 * <p>类职责：创建类型安全的 KafkaTemplate（用于发送分类请求）和
 * ConcurrentKafkaListenerContainerFactory（用于消费分类响应）。
 *
 * <p>不变量：生产者确保 all-acks 和幂等性；消费者使用手动提交，禁止自动提交。
 */
@Configuration
@ConditionalOnProperty(name = "mayoistar.ai.clip.kafka-enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * CLIP 分类请求消息的 KafkaTemplate。
     */
    @Bean
    public KafkaTemplate<String, ClipTaskRequest> clipRequestKafkaTemplate() {
        return new KafkaTemplate<>(clipRequestProducerFactory());
    }

    /**
     * CLIP 分类请求的生产者工厂。
     */
    @Bean
    public ProducerFactory<String, ClipTaskRequest> clipRequestProducerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configs.put(ProducerConfig.RETRIES_CONFIG, 3);
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(configs);
    }

    /**
     * CLIP 分类响应的消费者工厂。
     */
    @Bean
    public ConsumerFactory<String, ClipTaskResponse> clipResponseConsumerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, "mayoistar-backend");
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ClipTaskResponse.class);
        configs.put(JsonDeserializer.TRUSTED_PACKAGES, "io.github.layjason.mayoistar.service.ai");
        return new DefaultKafkaConsumerFactory<>(configs);
    }

    /**
     * CLIP 分类响应消费的监听容器工厂。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ClipTaskResponse>
            clipResponseKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ClipTaskResponse> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(clipResponseConsumerFactory());
        factory.getContainerProperties()
                .setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}

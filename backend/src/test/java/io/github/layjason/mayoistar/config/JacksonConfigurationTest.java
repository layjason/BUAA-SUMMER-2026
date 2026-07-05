package io.github.layjason.mayoistar.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.service.media.MediaAccessDescriptor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JacksonConfiguration 单元测试。
 *
 * <p>类职责：验证主运行环境所需的 Jackson 2 ObjectMapper Bean 可以正确序列化/反序列化 java.time 类型。
 */
class JacksonConfigurationTest {

    /**
     * 验证 ObjectMapper Bean 创建。
     *
     * <p>前置条件：JacksonConfiguration 可被实例化。
     *
     * <p>后置条件：返回非空 ObjectMapper，供 SecurityErrorHandler 注入使用。
     */
    @Test
    @DisplayName("应创建 Jackson 2 ObjectMapper")
    void shouldCreateObjectMapper() {
        JacksonConfiguration configuration = new JacksonConfiguration();

        ObjectMapper objectMapper = configuration.objectMapper();

        assertThat(objectMapper).isNotNull();
    }

    /**
     * 验证 ObjectMapper 可序列化/反序列化含非空 Instant 的 MediaAccessDescriptor。
     *
     * <p>前置条件：ObjectMapper 实例可用，MediaAccessDescriptor 含非空 deletedAt。
     *
     * <p>后置条件：序列化后反序列化得到的 MediaAccessDescriptor 与原对象相等。
     */
    @Test
    @DisplayName("应正确序列化/反序列化含 Instant 字段的 MediaAccessDescriptor")
    void shouldSerializeAndDeserializeMediaAccessDescriptorWithInstant() throws JsonProcessingException {
        JacksonConfiguration configuration = new JacksonConfiguration();
        ObjectMapper objectMapper = configuration.objectMapper();
        UUID mediaId = UUID.randomUUID();
        Instant deletedAt = Instant.now();

        MediaAccessDescriptor original = new MediaAccessDescriptor(
                mediaId,
                "teamFile/user/file.jpg",
                "image/jpeg",
                "photo.jpg",
                1024L,
                MediaVisibility.publicVisible,
                MediaAccessPolicy.teamMember,
                "scope",
                1L,
                deletedAt,
                "12345678-1234-1234-1234-123456789012");

        String json = objectMapper.writeValueAsString(original);
        MediaAccessDescriptor deserialized = objectMapper.readValue(json, MediaAccessDescriptor.class);

        assertThat(deserialized).isEqualTo(original);
    }
}

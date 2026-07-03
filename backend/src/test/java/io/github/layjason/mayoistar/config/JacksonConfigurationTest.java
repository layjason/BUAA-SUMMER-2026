package io.github.layjason.mayoistar.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JacksonConfiguration 单元测试。
 *
 * <p>类职责：验证主运行环境所需的 Jackson 2 ObjectMapper Bean 可以被显式创建。
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
}

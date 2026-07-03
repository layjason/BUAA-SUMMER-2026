package io.github.layjason.mayoistar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 序列化配置。
 *
 * <p>类职责：为仍依赖 Jackson 2 API 的安全错误响应与第三方组件提供 ObjectMapper Bean。
 *
 * <p>不变量：应用上下文中始终存在一个 com.fasterxml.jackson.databind.ObjectMapper Bean。
 */
@Configuration
public class JacksonConfiguration {

    /**
     * 创建 Jackson 2 ObjectMapper。
     *
     * <p>前置条件：com.fasterxml.jackson.databind.ObjectMapper 位于运行时 classpath。
     *
     * <p>后置条件：Spring 容器可注入 ObjectMapper，SecurityErrorHandler 可正常启动。
     *
     * @return Jackson 2 ObjectMapper
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

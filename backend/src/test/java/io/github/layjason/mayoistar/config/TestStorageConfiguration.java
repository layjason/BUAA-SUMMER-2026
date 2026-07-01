package io.github.layjason.mayoistar.config;

import static org.mockito.Mockito.mock;

import io.github.layjason.mayoistar.service.storage.FileStorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * 测试对象存储配置。
 *
 * <p>类职责：在 test profile 下用 Mock FileStorageService 替换真实对象存储服务。
 *
 * <p>不变量：集成测试不连接 RustFS/S3，不创建真实 bucket，不写入外部对象存储。
 */
@TestConfiguration
@Profile("test")
public class TestStorageConfiguration {

    /**
     * 提供测试环境的对象存储 Mock。
     *
     * <p>前置条件：当前 Spring Profile 包含 test。
     *
     * <p>后置条件：所有注入 FileStorageService 的测试上下文均使用 Mock Bean。
     *
     * @return FileStorageService Mock
     */
    @Bean
    @Primary
    public FileStorageService testFileStorageService() {
        return mock(FileStorageService.class);
    }
}

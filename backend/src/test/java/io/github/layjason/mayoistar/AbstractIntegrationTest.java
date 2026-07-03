package io.github.layjason.mayoistar;

import io.github.layjason.mayoistar.config.TestSecurityConfiguration;
import io.github.layjason.mayoistar.config.TestStorageConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试抽象基类。
 *
 * <p>类职责：为所有 @SpringBootTest 集成测试提供统一注解配置。
 * 测试环境使用 H2 内存数据库与对象存储 Mock，不依赖外部数据库或 RustFS。
 *
 * <p>不变量：所有继承类共享 test profile、安全测试配置和对象存储 Mock。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestSecurityConfiguration.class, TestStorageConfiguration.class})
public abstract class AbstractIntegrationTest {}

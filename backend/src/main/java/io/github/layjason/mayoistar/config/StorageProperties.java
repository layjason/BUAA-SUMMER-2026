package io.github.layjason.mayoistar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * RustFS S3 兼容对象存储配置属性。
 *
 * <p>类职责：绑定 application.yaml 中 mayoistar.storage.s3 前缀的配置项。
 *
 * <p>不变量：所有字段均有默认值，确保本地开发环境可直接启动。
 */
@Data
@Validated
@ConfigurationProperties("mayoistar.storage.s3")
public class StorageProperties {

    private String endpoint = "http://localhost:9000";
    private String region = "us-east-1";
    private String accessKey = "rustfsadmin";
    private String secretKey = "rustfsadmin";
    private String bucket = "mayoistar-media";
}

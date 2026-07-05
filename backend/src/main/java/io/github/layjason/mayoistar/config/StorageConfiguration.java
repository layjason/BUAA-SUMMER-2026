package io.github.layjason.mayoistar.config;

import io.github.layjason.mayoistar.service.storage.FileStorageService;
import io.github.layjason.mayoistar.service.storage.S3FileStorageService;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * 文件存储自动配置。
 *
 * <p>类职责：根据 StorageProperties 创建 S3Client 和 S3FileStorageService Bean。
 *
 * <p>不变量：仅在启动时创建一次，所有 Bean 为单例。
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {

    /**
     * 创建指向 RustFS 的 S3Client。
     *
     * <p>前置条件：StorageProperties 已绑定有效配置。
     *
     * <p>后置条件：S3Client 配置为 path-style access，静态凭证认证，自定义 endpoint。
     *
     * @param properties S3 存储配置属性
     * @return 已配置的 S3Client 实例
     */
    @Bean
    public S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /**
     * 创建文件存储服务 Bean。
     *
     * <p>前置条件：S3Client 已创建。
     *
     * <p>后置条件：S3FileStorageService 已注入 bucket 和 endpoint 信息。
     *
     * @param s3Client   已配置的 S3 客户端
     * @param properties S3 存储配置属性
     * @return S3FileStorageService 实例
     */
    @Bean
    public FileStorageService fileStorageService(S3Client s3Client, StorageProperties properties) {
        return new S3FileStorageService(s3Client, properties.getBucket(), properties.getEndpoint());
    }
}

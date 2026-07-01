package io.github.layjason.mayoistar.service.storage;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 基于 RustFS（S3 兼容 API）的文件存储服务实现。
 *
 * <p>类职责：通过 AWS S3 SDK 与 RustFS 实例交互，实现文件的存储、读取、删除和公开 URL 生成。
 *
 * <p>不变量：bucket 在服务启动后保证存在；所有 key 在 bucket 内唯一。
 */
@Slf4j
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String endpoint;

    /**
     * @param s3Client 已配置指向 RustFS 的 S3 客户端
     * @param bucket   对象存储桶名称
     * @param endpoint RustFS S3 API 端点（用于构造公开 URL）
     */
    public S3FileStorageService(S3Client s3Client, String bucket, String endpoint) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.endpoint = endpoint;
    }

    /**
     * 确保 bucket 在服务启动后存在。
     *
     * <p>前置条件：S3Client 已就绪。
     *
     * <p>后置条件：bucket 存在，若已存在则记录日志但不抛出异常。
     */
    @PostConstruct
    void ensureBucketExists() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Bucket 创建成功: bucket={}", bucket);
        } catch (BucketAlreadyOwnedByYouException e) {
            log.info("Bucket 已存在: bucket={}", bucket);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>使用 PutObjectRequest 上传文件，不进行额外分片。
     */
    @Override
    public String store(String key, @NonNull InputStream data, @NonNull String contentType, long size) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(data, size));
        log.info("文件存储成功: bucket={}, key={}, size={}", bucket, key, size);
        return key;
    }

    /**
     * {@inheritDoc}
     *
     * <p>前置条件：key 对应的对象存在。不存在时抛出 NoSuchKeyException。
     */
    @Override
    public InputStream retrieve(@NonNull String key) {
        try {
            GetObjectRequest request =
                    GetObjectRequest.builder().bucket(bucket).key(key).build();
            return s3Client.getObject(request);
        } catch (NoSuchKeyException e) {
            log.warn("文件不存在: bucket={}, key={}", bucket, key);
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>基于公开读 bucket，直接拼接 endpoint + bucket + key 得到公开 URL。
     */
    @Override
    public String getPublicUrl(@NonNull String key) {
        String url = endpoint + "/" + bucket + "/" + key;
        log.debug("生成公开 URL: key={}, url={}", key, url);
        return url;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(@NonNull String key) {
        DeleteObjectRequest request =
                DeleteObjectRequest.builder().bucket(bucket).key(key).build();
        s3Client.deleteObject(request);
        log.info("文件删除成功: bucket={}, key={}", bucket, key);
    }
}

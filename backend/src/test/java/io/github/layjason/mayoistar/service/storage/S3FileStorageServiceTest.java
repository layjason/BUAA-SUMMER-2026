package io.github.layjason.mayoistar.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3FileStorageService s3FileStorageService;

    private static final String BUCKET = "test-bucket";
    private static final String ENDPOINT = "http://localhost:9000";

    @BeforeEach
    void setUp() {
        s3FileStorageService = new S3FileStorageService(s3Client, BUCKET, ENDPOINT);
    }

    @Nested
    @DisplayName("Bucket 初始化")
    class BucketInit {

        @Test
        @DisplayName("创建 bucket 成功")
        void shouldCreateBucket() {
            when(s3Client.createBucket(any(CreateBucketRequest.class))).thenReturn(null);

            s3FileStorageService.ensureBucketExists();

            verify(s3Client).createBucket(any(CreateBucketRequest.class));
        }

        @Test
        @DisplayName("bucket 已存在时不抛出异常")
        void shouldHandleBucketAlreadyExists() {
            when(s3Client.createBucket(any(CreateBucketRequest.class)))
                    .thenThrow(BucketAlreadyOwnedByYouException.class);

            s3FileStorageService.ensureBucketExists();
        }
    }

    @Nested
    @DisplayName("文件存储")
    class Store {

        @Test
        @DisplayName("成功存储文件")
        void shouldStoreFile() {
            String key = "test/file.png";
            byte[] data = "test-content".getBytes();
            InputStream stream = new ByteArrayInputStream(data);

            String result = s3FileStorageService.store(key, stream, "image/png", data.length);

            assertThat(result).isEqualTo(key);
            ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
            ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
            verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());
            assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(requestCaptor.getValue().key()).isEqualTo(key);
            assertThat(requestCaptor.getValue().contentType()).isEqualTo("image/png");
        }
    }

    @Nested
    @DisplayName("文件读取")
    class Retrieve {

        @Test
        @DisplayName("成功读取文件")
        void shouldRetrieveFile() {
            String key = "test/file.png";
            byte[] data = "test-content".getBytes();
            GetObjectResponse response = GetObjectResponse.builder().build();
            ResponseInputStream<GetObjectResponse> responseStream =
                    new ResponseInputStream<>(response, new ByteArrayInputStream(data));

            when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

            InputStream result = s3FileStorageService.retrieve(key);

            assertThat(result).isNotNull();
            verify(s3Client).getObject(any(GetObjectRequest.class));
        }

        @Test
        @DisplayName("文件不存在时抛出 NoSuchKeyException")
        void shouldThrowWhenFileNotFound() {
            String key = "nonexistent/file.png";
            when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.class);

            assertThatThrownBy(() -> s3FileStorageService.retrieve(key)).isInstanceOf(NoSuchKeyException.class);
        }
    }

    @Nested
    @DisplayName("公开 URL")
    class PublicUrl {

        @Test
        @DisplayName("生成正确的公开 URL")
        void shouldGeneratePublicUrl() {
            String key = "test/file.png";
            String url = s3FileStorageService.getPublicUrl(key);

            assertThat(url).isEqualTo(ENDPOINT + "/" + BUCKET + "/" + key);
        }
    }

    @Nested
    @DisplayName("文件删除")
    class Delete {

        @Test
        @DisplayName("成功删除文件")
        void shouldDeleteFile() {
            String key = "test/file.png";

            s3FileStorageService.delete(key);

            ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(requestCaptor.capture());
            assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(requestCaptor.getValue().key()).isEqualTo(key);
        }
    }
}

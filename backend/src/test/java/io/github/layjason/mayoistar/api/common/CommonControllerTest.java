package io.github.layjason.mayoistar.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommonController 单元测试")
class CommonControllerTest {

    @Mock
    private MediaFileRepository mediaFileRepository;

    @TempDir
    private Path uploadRoot;

    private CommonController controller;

    @BeforeEach
    void setUp() {
        controller = new CommonController(mediaFileRepository, uploadRoot.toString());
    }

    @Test
    @DisplayName("存在有效媒体文件时返回文件内容和正确的 Content-Type")
    void shouldReturnFileContentWhenMediaFileExists() throws Exception {
        String mediaId = UUID.randomUUID().toString();
        String storagePath = "avatars/test-user/" + mediaId + "_avatar.png";
        Path filePath = uploadRoot.resolve(storagePath).normalize();
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});

        MediaFile entity = MediaFile.builder()
                .mediaId(mediaId)
                .fileName("avatar.png")
                .contentType("image/png")
                .sizeBytes(4L)
                .usage(MediaUsage.avatar)
                .storagePath(storagePath)
                .uploadedBy("test-user")
                .uploadedAt(Instant.now())
                .build();

        when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(entity));

        ResponseEntity<Resource> response = controller.getMediaFile(mediaId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().contentLength()).isEqualTo(4L);
    }

    @Test
    @DisplayName("mediaId 不存在时返回 404")
    void shouldReturn404WhenMediaIdNotFound() {
        String mediaId = "nonexistent";
        when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.empty());

        ResponseEntity<Resource> response = controller.getMediaFile(mediaId);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("文件在磁盘上不存在时返回 404")
    void shouldReturn404WhenFileMissingOnDisk() {
        String mediaId = UUID.randomUUID().toString();
        String storagePath = "avatars/test-user/" + mediaId + "_missing.png";

        MediaFile entity = MediaFile.builder()
                .mediaId(mediaId)
                .fileName("missing.png")
                .contentType("image/png")
                .sizeBytes(0L)
                .usage(MediaUsage.avatar)
                .storagePath(storagePath)
                .uploadedBy("test-user")
                .uploadedAt(Instant.now())
                .build();

        when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(entity));

        ResponseEntity<Resource> response = controller.getMediaFile(mediaId);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("路径越界时返回 404")
    void shouldReturn404WhenPathTraversalDetected() {
        String mediaId = UUID.randomUUID().toString();
        String storagePath = "../etc/passwd";

        MediaFile entity = MediaFile.builder()
                .mediaId(mediaId)
                .fileName("malicious.txt")
                .contentType("text/plain")
                .sizeBytes(0L)
                .usage(MediaUsage.avatar)
                .storagePath(storagePath)
                .uploadedBy("test-user")
                .uploadedAt(Instant.now())
                .build();

        when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(entity));

        ResponseEntity<Resource> response = controller.getMediaFile(mediaId);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("返回 JPEG 文件时 Content-Type 为 image/jpeg")
    void shouldReturnJpegContentType() throws Exception {
        String mediaId = UUID.randomUUID().toString();
        String storagePath = "avatars/test-user/" + mediaId + "_photo.jpg";
        Path filePath = uploadRoot.resolve(storagePath).normalize();
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, new byte[] {(byte) 0xFF, (byte) 0xD8});

        MediaFile entity = MediaFile.builder()
                .mediaId(mediaId)
                .fileName("photo.jpg")
                .contentType("image/jpeg")
                .sizeBytes(2L)
                .usage(MediaUsage.activityImage)
                .storagePath(storagePath)
                .uploadedBy("test-user")
                .uploadedAt(Instant.now())
                .build();

        when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(entity));

        ResponseEntity<Resource> response = controller.getMediaFile(mediaId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
    }
}

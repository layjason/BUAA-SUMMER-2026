package io.github.layjason.mayoistar.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.service.MediaFileUploadService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("通用媒体访问控制器单元测试")
class CommonControllerTest {

    @Mock
    private MediaFileUploadService mediaFileUploadService;

    private MediaController controller;

    @BeforeEach
    void setUp() {
        controller = new MediaController(mediaFileUploadService);
    }

    @Test
    @DisplayName("存在有效媒体文件时返回文件内容和正确的 Content-Type")
    void shouldReturnFileContentWhenMediaFileExists() {
        UUID mediaId = UUID.randomUUID();
        MediaFile entity = buildMediaFile(mediaId, "image/png", 4L);

        when(mediaFileUploadService.getMediaFile(mediaId)).thenReturn(entity);
        when(mediaFileUploadService.retrieveContent(mediaId))
                .thenReturn(new ByteArrayInputStream(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}));

        ResponseEntity<InputStreamResource> response = controller.getMediaFile(mediaId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(4L);
        assertThat(response.getBody()).isNotNull();
        verify(mediaFileUploadService).getMediaFile(mediaId);
        verify(mediaFileUploadService).retrieveContent(mediaId);
    }

    @Test
    @DisplayName("返回 JPEG 文件时 Content-Type 为 image/jpeg")
    void shouldReturnJpegContentType() {
        UUID mediaId = UUID.randomUUID();
        MediaFile entity = buildMediaFile(mediaId, "image/jpeg", 2L);

        when(mediaFileUploadService.getMediaFile(mediaId)).thenReturn(entity);
        when(mediaFileUploadService.retrieveContent(mediaId))
                .thenReturn(new ByteArrayInputStream(new byte[] {(byte) 0xFF, (byte) 0xD8}));

        ResponseEntity<InputStreamResource> response = controller.getMediaFile(mediaId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
    }

    private MediaFile buildMediaFile(UUID mediaId, String contentType, long sizeBytes) {
        return MediaFile.builder()
                .mediaId(mediaId)
                .fileName("media.bin")
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .usage(MediaUsage.avatar)
                .storagePath("avatar/user/media.bin")
                .uploadedBy("test-user")
                .uploadedAt(Instant.now())
                .build();
    }
}

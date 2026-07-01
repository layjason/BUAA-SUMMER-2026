package io.github.layjason.mayoistar.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.service.MediaFileUploadService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * MediaController 单元测试。
 *
 * <p>类职责：验证媒体访问控制器只委托统一媒体服务，不直接依赖媒体仓库或底层对象存储。
 */
@DisplayName("MediaController")
class MediaControllerTest {

    private MediaFileUploadService mediaFileUploadService;
    private MediaController mediaController;

    @BeforeEach
    void setUp() {
        mediaFileUploadService = mock(MediaFileUploadService.class);
        mediaController = new MediaController(mediaFileUploadService);
    }

    @Test
    @DisplayName("有公开 URL 时返回 302 重定向")
    void shouldRedirectWhenPublicUrlExists() {
        UUID mediaId = UUID.randomUUID();
        MediaFile mediaFile = buildMediaFile(mediaId, "http://localhost:9000/bucket/avatar.png");
        when(mediaFileUploadService.getMediaFile(mediaId)).thenReturn(mediaFile);

        ResponseEntity<?> response = mediaController.getMediaFile(mediaId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("http://localhost:9000/bucket/avatar.png");
        verify(mediaFileUploadService).getMediaFile(mediaId);
        verifyNoMoreInteractions(mediaFileUploadService);
    }

    @Test
    @DisplayName("无公开 URL 时委托统一媒体服务读取文件流")
    void shouldRetrieveContentThroughMediaService() {
        UUID mediaId = UUID.randomUUID();
        MediaFile mediaFile = buildMediaFile(mediaId, null);
        InputStream inputStream = new ByteArrayInputStream("image-data".getBytes());

        when(mediaFileUploadService.getMediaFile(mediaId)).thenReturn(mediaFile);
        when(mediaFileUploadService.retrieveContent(mediaId)).thenReturn(inputStream);

        ResponseEntity<?> response = mediaController.getMediaFile(mediaId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(InputStreamResource.class);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        verify(mediaFileUploadService).getMediaFile(mediaId);
        verify(mediaFileUploadService).retrieveContent(mediaId);
        verifyNoMoreInteractions(mediaFileUploadService);
    }

    private MediaFile buildMediaFile(UUID mediaId, String url) {
        return MediaFile.builder()
                .mediaId(mediaId)
                .fileName("avatar.png")
                .contentType("image/png")
                .sizeBytes(100L)
                .usage(MediaUsage.avatar)
                .storagePath("avatar/user1/avatar.png")
                .url(url)
                .uploadedBy("user1")
                .uploadedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build();
    }
}

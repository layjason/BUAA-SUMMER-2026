package io.github.layjason.mayoistar.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.service.media.MediaAccessDescriptor;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * MediaController 单元测试。
 *
 * <p>类职责：验证媒体访问控制器只委托统一媒体访问服务，不直接依赖媒体仓库或底层对象存储。
 */
@DisplayName("MediaController")
class MediaControllerTest {

    private MediaAccessService mediaAccessService;
    private MediaController mediaController;

    @BeforeEach
    void setUp() {
        mediaAccessService = mock(MediaAccessService.class);
        mediaController = new MediaController(mediaAccessService);
    }

    @Test
    @DisplayName("签名校验通过时返回 200 文件流")
    void shouldReturnSignedStream() {
        UUID mediaId = UUID.randomUUID();
        MediaAccessDescriptor descriptor = buildDescriptor(mediaId);
        InputStream inputStream = new ByteArrayInputStream("image-data".getBytes());

        when(mediaAccessService.loadDescriptor(mediaId)).thenReturn(descriptor);
        when(mediaAccessService.openSignedContent(mediaId, 123L, 1L, MediaAccessPolicy.publicAccess, "", "sig", null))
                .thenReturn(inputStream);

        ResponseEntity<?> response =
                mediaController.getMediaFile(mediaId, 123L, 1L, MediaAccessPolicy.publicAccess, "", "sig", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(InputStreamResource.class);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isNull();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(100L);
        assertThat(response.getHeaders().getContentDisposition())
                .isEqualTo(ContentDisposition.inline().filename("avatar.png").build());
        verify(mediaAccessService).loadDescriptor(mediaId);
        verify(mediaAccessService)
                .openSignedContent(mediaId, 123L, 1L, MediaAccessPolicy.publicAccess, "", "sig", null);
        verifyNoMoreInteractions(mediaAccessService);
    }

    private MediaAccessDescriptor buildDescriptor(UUID mediaId) {
        return new MediaAccessDescriptor(
                mediaId,
                "avatar/user1/avatar.png",
                "image/png",
                "avatar.png",
                100L,
                MediaVisibility.publicVisible,
                MediaAccessPolicy.publicAccess,
                "",
                1L,
                null,
                "user1");
    }
}

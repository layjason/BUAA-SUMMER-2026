package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import io.github.layjason.mayoistar.service.storage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class MediaFileUploadServiceTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private MediaFileRepository mediaFileRepository;

    @Mock
    private MediaAccessService mediaAccessService;

    private MediaFileUploadService mediaFileUploadService;

    @BeforeEach
    void setUp() {
        mediaFileUploadService =
                new MediaFileUploadService(fileStorageService, mediaFileRepository, mediaAccessService);
        lenient().when(mediaAccessService.toSignedDto(any(MediaFile.class))).thenAnswer(invocation -> {
            MediaFile mediaFile = invocation.getArgument(0);
            CommonDtos.MediaFile dto = new CommonDtos.MediaFile();
            dto.setMediaId(mediaFile.getMediaId());
            dto.setFileName(mediaFile.getFileName());
            dto.setContentType(mediaFile.getContentType());
            dto.setSizeBytes(mediaFile.getSizeBytes());
            dto.setUsage(mediaFile.getUsage());
            dto.setVisibility(mediaFile.getVisibility());
            dto.setSignedUrl("/media/" + mediaFile.getMediaId() + "?sig=test");
            dto.setUploadedAt(mediaFile.getUploadedAt().toString());
            return dto;
        });
    }

    /**
     * 创建模拟的 MultipartFile。
     *
     * @param contentType MIME 类型
     * @param size        文件大小
     * @param filename    文件名
     * @return 模拟的 MultipartFile
     */
    private MultipartFile mockFile(String contentType, long size, String filename) {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.getContentType()).thenReturn(contentType);
        lenient().when(file.getSize()).thenReturn(size);
        lenient().when(file.getOriginalFilename()).thenReturn(filename);
        try {
            lenient().when(file.getInputStream()).thenReturn(mock(InputStream.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return file;
    }

    @Nested
    @DisplayName("文件类型校验")
    class FileTypeValidation {

        @Test
        @DisplayName("允许的图片类型通过校验")
        void shouldAcceptValidImageTypes() {
            MultipartFile file = mockFile("image/png", 100L, "test.png");

            when(fileStorageService.store(anyString(), any(InputStream.class), anyString(), anyLong()))
                    .thenReturn("test-key");

            CommonDtos.MediaFile result = mediaFileUploadService.upload("user1", file, MediaUsage.avatar);

            assertThat(result.getUsage()).isEqualTo(MediaUsage.avatar);
            assertThat(result.getContentType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("不允许的类型抛出 10013")
        void shouldRejectInvalidType() {
            MultipartFile file = mockFile("text/plain", 100L, "test.txt");

            assertThatThrownBy(() -> mediaFileUploadService.upload("user1", file, MediaUsage.avatar))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10013);
        }

        @Test
        @DisplayName("teamFile 允许所有类型")
        void shouldAcceptAnyTypeForTeamFile() {
            MultipartFile file = mockFile("application/pdf", 100L, "doc.pdf");

            when(fileStorageService.store(anyString(), any(InputStream.class), anyString(), anyLong()))
                    .thenReturn("test-key");

            CommonDtos.MediaFile result = mediaFileUploadService.upload("user1", file, MediaUsage.teamFile);

            assertThat(result.getContentType()).isEqualTo("application/pdf");
        }
    }

    @Nested
    @DisplayName("文件大小校验")
    class FileSizeValidation {

        @Test
        @DisplayName("超过大小限制抛出 10014")
        void shouldRejectOversizedFile() {
            long overLimit = 6 * 1024 * 1024L; // 6 MB, 超过头像的 5 MB
            MultipartFile file = mockFile("image/png", overLimit, "large.png");

            assertThatThrownBy(() -> mediaFileUploadService.upload("user1", file, MediaUsage.avatar))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10014);
        }

        @Test
        @DisplayName("在大小限制内的文件通过校验")
        void shouldAcceptFileWithinLimit() {
            MultipartFile file = mockFile("image/jpeg", 1 * 1024 * 1024L, "photo.jpg");

            when(fileStorageService.store(anyString(), any(InputStream.class), anyString(), anyLong()))
                    .thenReturn("test-key");

            CommonDtos.MediaFile result = mediaFileUploadService.upload("user1", file, MediaUsage.chatImage);

            assertThat(result.getContentType()).isEqualTo("image/jpeg");
        }
    }

    @Nested
    @DisplayName("元数据持久化")
    class MetadataPersistence {

        @Test
        @DisplayName("上传后正确保存 MediaFile 并设置签名访问元数据")
        void shouldSaveMediaFileWithSignedAccessMetadata() {
            MultipartFile file = mockFile("image/png", 500L, "avatar.png");

            when(fileStorageService.store(anyString(), any(InputStream.class), eq("image/png"), eq(500L)))
                    .thenReturn("avatar/user1/mock-id_avatar.png");

            CommonDtos.MediaFile result = mediaFileUploadService.upload("user1", file, MediaUsage.avatar);

            assertThat(result.getSignedUrl()).startsWith("/media/");
            assertThat(result.getFileName()).isEqualTo("avatar.png");
            assertThat(result.getSizeBytes()).isEqualTo(500L);
            assertThat(result.getUsage()).isEqualTo(MediaUsage.avatar);
            assertThat(result.getVisibility()).isEqualTo(MediaVisibility.publicVisible);

            ArgumentCaptor<MediaFile> captor = ArgumentCaptor.forClass(MediaFile.class);
            verify(mediaFileRepository).save(captor.capture());
            assertThat(captor.getValue().getVisibility()).isEqualTo(MediaVisibility.publicVisible);
            assertThat(captor.getValue().getAccessPolicy()).isEqualTo(MediaAccessPolicy.publicAccess);
            assertThat(captor.getValue().getAccessVersion()).isEqualTo(1L);
            assertThat(captor.getValue().getUploadedBy()).isEqualTo("user1");
            verify(fileStorageService, never()).getPublicUrl(anyString());
        }

        @Test
        @DisplayName("私有用途上传后设置 owner 策略")
        void shouldSavePrivateMediaWithOwnerPolicy() {
            MultipartFile file = mockFile("image/png", 500L, "chat.png");

            when(fileStorageService.store(anyString(), any(InputStream.class), eq("image/png"), eq(500L)))
                    .thenReturn("chat/user1/mock-id_chat.png");

            CommonDtos.MediaFile result = mediaFileUploadService.upload("user1", file, MediaUsage.chatImage);

            assertThat(result.getVisibility()).isEqualTo(MediaVisibility.privateVisible);
            ArgumentCaptor<MediaFile> captor = ArgumentCaptor.forClass(MediaFile.class);
            verify(mediaFileRepository).save(captor.capture());
            assertThat(captor.getValue().getAccessPolicy()).isEqualTo(MediaAccessPolicy.owner);
            assertThat(captor.getValue().getAccessScopeId()).isEqualTo("user1");
        }
    }

    @Nested
    @DisplayName("媒体文件读取")
    class MediaRetrieval {

        @Test
        @DisplayName("根据 mediaId 获取元数据")
        void shouldGetMediaFileMetadata() {
            UUID mediaId = UUID.randomUUID();
            MediaFile mediaFile = buildMediaFile(mediaId);
            when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(mediaFile));

            MediaFile result = mediaFileUploadService.getMediaFile(mediaId);

            assertThat(result).isSameAs(mediaFile);
            verifyNoInteractions(fileStorageService);
        }

        @Test
        @DisplayName("mediaId 不存在时抛出 404")
        void shouldThrow404WhenMetadataMissing() {
            UUID mediaId = UUID.randomUUID();
            when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mediaFileUploadService.getMediaFile(mediaId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(404);
        }

        @Test
        @DisplayName("从对象存储读取文件流")
        void shouldRetrieveContentFromStorage() {
            UUID mediaId = UUID.randomUUID();
            MediaFile mediaFile = buildMediaFile(mediaId);
            InputStream expectedStream = new ByteArrayInputStream("image-data".getBytes());

            when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(mediaFile));
            when(fileStorageService.retrieve("avatar/user1/avatar.png")).thenReturn(expectedStream);

            InputStream result = mediaFileUploadService.retrieveContent(mediaId);

            assertThat(result).isSameAs(expectedStream);
            verify(fileStorageService).retrieve("avatar/user1/avatar.png");
        }

        @Test
        @DisplayName("对象存储中文件不存在时抛出 404")
        void shouldThrow404WhenStorageObjectMissing() {
            UUID mediaId = UUID.randomUUID();
            MediaFile mediaFile = buildMediaFile(mediaId);

            when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(mediaFile));
            when(fileStorageService.retrieve("avatar/user1/avatar.png")).thenThrow(new RuntimeException("not found"));

            assertThatThrownBy(() -> mediaFileUploadService.retrieveContent(mediaId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(404);
        }
    }

    private MediaFile buildMediaFile(UUID mediaId) {
        return MediaFile.builder()
                .mediaId(mediaId)
                .fileName("avatar.png")
                .contentType("image/png")
                .sizeBytes(100L)
                .usage(MediaUsage.avatar)
                .storagePath("avatar/user1/avatar.png")
                .uploadedBy("user1")
                .uploadedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build();
    }
}

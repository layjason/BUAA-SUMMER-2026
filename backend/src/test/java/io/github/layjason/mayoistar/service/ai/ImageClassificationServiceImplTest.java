package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.ai.AiDtos.ImageClassificationResult;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.service.MediaFileUploadService;
import io.github.layjason.mayoistar.service.ai.ClipModels.ClipClassifyItem;
import io.github.layjason.mayoistar.service.ai.ClipModels.ClipClassifyResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ImageClassificationServiceImpl 单元测试。
 *
 * <p>类职责：验证图片分类的完整业务流程、边界条件和异常路径。
 *
 * <p>类不变量：测试仅使用 Mock 依赖，不连接真实 S3 或 CLIP 服务。
 */
@ExtendWith(MockitoExtension.class)
class ImageClassificationServiceImplTest {

    @Mock
    private MediaFileUploadService mediaFileUploadService;

    @Mock
    private ClipServiceClient clipServiceClient;

    private ImageClassificationServiceImpl service;

    /** 1x1 像素的 JPEG 最小有效数据，用于测试 */
    private static final byte[] MINIMAL_JPEG = new byte[] {
        (byte) 0xFF,
        (byte) 0xD8,
        (byte) 0xFF,
        (byte) 0xE0,
        0x00,
        0x10,
        0x4A,
        0x46,
        0x49,
        0x46,
        0x00,
        0x01,
        0x01,
        0x00,
        0x00,
        0x01,
        0x00,
        0x01,
        0x00,
        0x00,
        (byte) 0xFF,
        (byte) 0xDB,
        0x00,
        0x43,
        0x00,
        0x08,
        0x06,
        0x06,
        0x07,
        0x06,
        0x05,
        0x08,
        0x07,
        0x07,
        0x07,
        0x09,
        0x09,
        0x08,
        0x0A,
        0x0C,
        0x14,
        0x0D,
        0x0C,
        0x0B,
        0x0B,
        0x0C,
        0x19,
        0x12,
        0x13,
        0x0F,
        0x14,
        0x1D,
        0x1A,
        0x1F,
        0x1E,
        0x1D,
        0x1A,
        0x1C,
        0x1C,
        0x20,
        0x24,
        0x2E,
        0x27,
        0x20,
        0x22,
        0x2C,
        0x23,
        0x1C,
        0x1C,
        0x28,
        0x37,
        0x29,
        0x2C,
        0x30,
        0x31,
        0x34,
        0x34,
        0x34,
        0x1F,
        0x27,
        0x39,
        0x3D,
        0x38,
        0x32,
        0x3C,
        0x2E,
        0x33,
        0x34,
        0x32,
        (byte) 0xFF,
        (byte) 0xC0,
        0x00,
        0x0B,
        0x08,
        0x00,
        0x01,
        0x00,
        0x01,
        0x01,
        0x01,
        0x11,
        0x00,
        (byte) 0xFF,
        (byte) 0xC4,
        0x00,
        0x1F,
        0x00,
        0x00,
        0x01,
        0x05,
        0x01,
        0x01,
        0x01,
        0x01,
        0x01,
        0x01,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x01,
        0x02,
        0x03,
        0x04,
        0x05,
        0x06,
        0x07,
        0x08,
        0x09,
        0x0A,
        0x0B,
        (byte) 0xFF,
        (byte) 0xC4,
        0x00,
        0x1F,
        0x01,
        0x00,
        0x03,
        0x01,
        0x01,
        0x01,
        0x01,
        0x01,
        0x01,
        0x01,
        0x01,
        0x01,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x01,
        0x02,
        0x03,
        0x04,
        0x05,
        0x06,
        0x07,
        0x08,
        0x09,
        0x0A,
        0x0B,
        (byte) 0xFF,
        (byte) 0xDA,
        0x00,
        0x08,
        0x01,
        0x01,
        0x00,
        0x00,
        0x3F,
        0x00,
        0x37,
        (byte) 0xE7,
        (byte) 0xFF,
        (byte) 0xD9
    };

    @BeforeEach
    void setUp() {
        service = new ImageClassificationServiceImpl(mediaFileUploadService, clipServiceClient);
    }

    @Nested
    @DisplayName("正常分类流程")
    class NormalClassification {

        @Test
        @DisplayName("单张图片分类应返回正确类别和置信度")
        void shouldClassifySingleImage() {
            UUID mediaId = UUID.randomUUID();
            MediaFile mediaFile = createMediaFile(mediaId);

            when(mediaFileUploadService.getMediaFile(mediaId)).thenReturn(mediaFile);
            when(mediaFileUploadService.retrieveContent(mediaId)).thenReturn(inputStream());

            ClipClassifyResponse clipResponse = createClipResponse("group_photo", 0.85);
            when(clipServiceClient.classify(any())).thenReturn(clipResponse);

            ImageClassificationResult result = service.classifyImages(List.of(mediaId));

            assertThat(result.getStatus()).isEqualTo("succeeded");
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getMediaId()).isEqualTo(mediaId);
            assertThat(result.getItems().get(0).getSuggestedTags()).containsExactly("合影");
            assertThat(result.getItems().get(0).getConfidence()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("多张图片批���分类应返回逐张结果")
        void shouldClassifyMultipleImages() {
            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();
            MediaFile mediaFile = createMediaFile(mediaId1);

            when(mediaFileUploadService.getMediaFile(any(UUID.class))).thenReturn(mediaFile);
            when(mediaFileUploadService.retrieveContent(any(UUID.class))).thenReturn(inputStream());

            ClipClassifyItem item1 = new ClipClassifyItem();
            item1.setCategory("venue");
            item1.setConfidence(0.72);
            ClipClassifyItem item2 = new ClipClassifyItem();
            item2.setCategory("process");
            item2.setConfidence(0.60);
            ClipClassifyResponse clipResponse = new ClipClassifyResponse();
            clipResponse.setItems(List.of(item1, item2));

            when(clipServiceClient.classify(any())).thenReturn(clipResponse);

            ImageClassificationResult result = service.classifyImages(List.of(mediaId1, mediaId2));

            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getItems().get(0).getSuggestedTags()).containsExactly("场地");
            assertThat(result.getItems().get(1).getSuggestedTags()).containsExactly("过程记录");
        }

        @Test
        @DisplayName("所有 5 个类别应正确映射中文标签")
        void shouldMapAllCategoryLabels() {
            ClipClassifyItem groupItem = new ClipClassifyItem();
            groupItem.setCategory("group_photo");
            groupItem.setConfidence(0.9);
            ClipClassifyItem venueItem = new ClipClassifyItem();
            venueItem.setCategory("venue");
            venueItem.setConfidence(0.8);
            ClipClassifyItem processItem = new ClipClassifyItem();
            processItem.setCategory("process");
            processItem.setConfidence(0.7);
            ClipClassifyItem suppliesItem = new ClipClassifyItem();
            suppliesItem.setCategory("supplies");
            suppliesItem.setConfidence(0.6);
            ClipClassifyItem achievementItem = new ClipClassifyItem();
            achievementItem.setCategory("achievement");
            achievementItem.setConfidence(0.5);

            ClipClassifyResponse clipResponse = new ClipClassifyResponse();
            clipResponse.setItems(List.of(groupItem, venueItem, processItem, suppliesItem, achievementItem));

            UUID mediaId = UUID.randomUUID();
            MediaFile mediaFile = createMediaFile(mediaId);
            when(mediaFileUploadService.getMediaFile(any(UUID.class))).thenReturn(mediaFile);
            when(mediaFileUploadService.retrieveContent(any(UUID.class))).thenReturn(inputStream());
            when(clipServiceClient.classify(any())).thenReturn(clipResponse);

            List<UUID> fiveIds = List.of(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            ImageClassificationResult result = service.classifyImages(fiveIds);

            assertThat(result.getItems()).hasSize(5);
            assertThat(result.getItems().get(0).getSuggestedTags()).containsExactly("合影");
            assertThat(result.getItems().get(1).getSuggestedTags()).containsExactly("场地");
            assertThat(result.getItems().get(2).getSuggestedTags()).containsExactly("过程记录");
            assertThat(result.getItems().get(3).getSuggestedTags()).containsExactly("物资");
            assertThat(result.getItems().get(4).getSuggestedTags()).containsExactly("成果展示");
        }
    }

    @Nested
    @DisplayName("空输入处理")
    class EmptyInput {

        @Test
        @DisplayName("空 mediaIds 列表应返回空结果")
        void shouldReturnEmptyResultForEmptyInput() {
            ImageClassificationResult result = service.classifyImages(List.of());

            assertThat(result.getStatus()).isEqualTo("succeeded");
            assertThat(result.getItems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ErrorHandling {

        @Test
        @DisplayName("所有媒体文件不存在时应抛出 ImageMediaUnavailable 异常")
        void shouldThrowWhenAllMediaUnavailable() {
            UUID mediaId = UUID.randomUUID();
            when(mediaFileUploadService.getMediaFile(mediaId)).thenThrow(new BusinessException(404, "媒体文件不存在"));

            assertThatThrownBy(() -> service.classifyImages(List.of(mediaId)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(30003); // IMAGE_MEDIA_UNAVAILABLE
        }

        @Test
        @DisplayName("部分媒体文件不可用时应跳过并继续处理可用文件")
        void shouldSkipUnavailableMediaAndContinue() {
            UUID validId = UUID.randomUUID();
            UUID invalidId = UUID.randomUUID();
            MediaFile mediaFile = createMediaFile(validId);

            when(mediaFileUploadService.getMediaFile(invalidId)).thenThrow(new BusinessException(404, "媒体文件不存在"));
            when(mediaFileUploadService.getMediaFile(validId)).thenReturn(mediaFile);
            when(mediaFileUploadService.retrieveContent(validId)).thenReturn(inputStream());

            ClipClassifyResponse clipResponse = createClipResponse("supplies", 0.55);
            when(clipServiceClient.classify(any())).thenReturn(clipResponse);

            ImageClassificationResult result = service.classifyImages(List.of(invalidId, validId));

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getMediaId()).isEqualTo(validId);
            assertThat(result.getItems().get(0).getSuggestedTags()).containsExactly("物资");
        }

        @Test
        @DisplayName("CLIP 返回未知类别时直接使用原始值")
        void shouldUseRawCategoryForUnknownLabel() {
            UUID mediaId = UUID.randomUUID();
            MediaFile mediaFile = createMediaFile(mediaId);

            when(mediaFileUploadService.getMediaFile(mediaId)).thenReturn(mediaFile);
            when(mediaFileUploadService.retrieveContent(mediaId)).thenReturn(inputStream());

            ClipClassifyResponse clipResponse = createClipResponse("unknown_type", 0.30);
            when(clipServiceClient.classify(any())).thenReturn(clipResponse);

            ImageClassificationResult result = service.classifyImages(List.of(mediaId));

            assertThat(result.getItems().get(0).getSuggestedTags()).containsExactly("unknown_type");
        }
    }

    /**
     * 创建测试用的 MediaFile 实体。
     */
    private static MediaFile createMediaFile(UUID mediaId) {
        MediaFile file = new MediaFile();
        file.setMediaId(mediaId);
        file.setContentType("image/jpeg");
        file.setStoragePath("test/path/" + mediaId + "_test.jpg");
        return file;
    }

    /**
     * 创建测试用的输入流。
     */
    private static InputStream inputStream() {
        return new ByteArrayInputStream(MINIMAL_JPEG);
    }

    /**
     * 创建测试用的 CLIP 分类响应。
     */
    private static ClipClassifyResponse createClipResponse(String category, double confidence) {
        ClipClassifyItem item = new ClipClassifyItem();
        item.setCategory(category);
        item.setConfidence(confidence);
        ClipClassifyResponse response = new ClipClassifyResponse();
        response.setItems(List.of(item));
        return response;
    }
}

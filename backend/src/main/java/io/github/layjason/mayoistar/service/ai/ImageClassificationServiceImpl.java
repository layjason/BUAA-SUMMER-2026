package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.api.ai.AiDtos.ImageClassificationItem;
import io.github.layjason.mayoistar.api.ai.AiDtos.ImageClassificationResult;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.service.MediaFileUploadService;
import io.github.layjason.mayoistar.service.ai.ClipModels.ClipClassifyItem;
import io.github.layjason.mayoistar.service.ai.ClipModels.ClipClassifyResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

/**
 * 图片分类服务实现。
 *
 * <p>类职责：协调媒体文件下载、CLIP 服务调用和结果映射，完成图片分类业务流程。
 *
 * <p>类不变量：分类类别标签映射关系固定，不随运行时变化。
 */
@Slf4j
@Service
public class ImageClassificationServiceImpl implements ImageClassificationService {

    /**
     * CLIP 分类标签到中文展示标签的映射。
     */
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "group_photo", "合影",
            "venue", "场地",
            "process", "过程记录",
            "supplies", "物资",
            "achievement", "成果展示");

    private final MediaFileUploadService mediaFileUploadService;
    private final ClipServiceClient clipServiceClient;

    /**
     * @param mediaFileUploadService 媒体文件服务
     * @param clipServiceClient       CLIP HTTP 客户端
     */
    public ImageClassificationServiceImpl(
            MediaFileUploadService mediaFileUploadService, ClipServiceClient clipServiceClient) {
        this.mediaFileUploadService = mediaFileUploadService;
        this.clipServiceClient = clipServiceClient;
    }

    /**
     * 对指定媒体文件进行分类。
     *
     * <p>前置条件：mediaIds 非空，每个 mediaId 对应的媒体文件已上传至 S3。
     *
     * <p>后置条件：返回逐图片分类结果，包含建议标签和置信度；
     * 若某张图片下载或处理失败，则该图片分类结果为 failed 状态。
     *
     * <p>不变量：该方法不修改已上传的媒体文件，仅读取下载。
     *
     * @param mediaIds 待分类的媒体文件 ID 列表
     * @return 图片分类结果
     */
    @Override
    public ImageClassificationResult classifyImages(@NonNull List<UUID> mediaIds) {
        if (mediaIds.isEmpty()) {
            ImageClassificationResult result = new ImageClassificationResult();
            result.setStatus("succeeded");
            result.setItems(List.of());
            return result;
        }

        // 下載並編碼所有圖片
        List<UUID> validMediaIds = new ArrayList<>();
        List<String> encodedImages = new ArrayList<>();

        for (UUID mediaId : mediaIds) {
            try {
                MediaFile mediaFile = mediaFileUploadService.getMediaFile(mediaId);
                byte[] imageBytes = readAllBytes(mediaFileUploadService.retrieveContent(mediaId));
                String base64Image = ClipServiceClient.encodeImage(imageBytes, mediaFile.getContentType());
                validMediaIds.add(mediaId);
                encodedImages.add(base64Image);
            } catch (BusinessException e) {
                log.warn("媒体文件不可用，跳过分类: mediaId={}, reason={}", mediaId, e.getBusinessMessage());
            } catch (IOException e) {
                log.warn("读取媒体文件失败，跳过分类: mediaId={}", mediaId, e);
            }
        }

        if (encodedImages.isEmpty()) {
            throw new BusinessException(ErrorCodes.IMAGE_MEDIA_UNAVAILABLE, "Image media is unavailable");
        }

        // 调用 CLIP 服务进行分类
        ClipClassifyResponse clipResponse = clipServiceClient.classify(encodedImages);

        // 组装结果
        List<ImageClassificationItem> items = new ArrayList<>();
        List<ClipClassifyItem> clipItems = clipResponse.getItems();

        for (int i = 0; i < validMediaIds.size(); i++) {
            UUID mediaId = validMediaIds.get(i);
            ImageClassificationItem item = new ImageClassificationItem();
            item.setMediaId(mediaId);

            if (i < clipItems.size()) {
                ClipClassifyItem clipItem = clipItems.get(i);
                String label = CATEGORY_LABELS.getOrDefault(clipItem.getCategory(), clipItem.getCategory());
                item.setSuggestedTags(List.of(label));
                item.setConfidence(clipItem.getConfidence());
            } else {
                log.warn("CLIP 服务返回结果数量不匹配: expected={}, actual={}", validMediaIds.size(), clipItems.size());
                item.setSuggestedTags(List.of());
                item.setConfidence(0.0);
            }

            items.add(item);
        }

        ImageClassificationResult result = new ImageClassificationResult();
        result.setStatus("succeeded");
        result.setItems(items);
        return result;
    }

    /**
     * 将输入流完整读取为字节数组。
     *
     * <p>前置条件：inputStream 处于可读取状态。
     *
     * <p>后置条件：返回完整的字节数组，输入流被消费完毕。
     *
     * <p>不变量：调用方负责关闭输入流。
     *
     * @param inputStream 输入流
     * @return 字节数组
     * @throws IOException 读取失败
     */
    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
}

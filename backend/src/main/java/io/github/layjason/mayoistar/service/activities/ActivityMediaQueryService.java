package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.activities.ActivityImage;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 活动媒体查询服务。
 *
 * <p>类职责：集中读取活动图片关联、封面图和排序信息，避免不同活动查询入口各自实现媒体加载逻辑。
 *
 * <p>类不变量：只读取活动媒体数据，不修改任何持久化状态。
 */
@Service
@RequiredArgsConstructor
public class ActivityMediaQueryService {

    private final ActivityImageRepository activityImageRepository;
    private final MediaFileRepository mediaFileRepository;

    /**
     * 加载活动封面图。
     *
     * <p>前置条件：activityId 非空。
     *
     * <p>后置条件：返回 sortOrder 最小的活动图片对应媒体 DTO；无图片或媒体缺失时返回 null。
     *
     * <p>不变量：不修改活动图片和媒体文件数据。
     *
     * @param activityId 活动 ID
     * @return 活动封面图 DTO，可能为空
     */
    @Transactional(readOnly = true)
    public CommonDtos.MediaFile loadCoverImage(String activityId) {
        List<ActivityImage> activityImages = loadActivityImages(activityId);
        if (activityImages.isEmpty()) {
            return null;
        }
        String firstMediaId = activityImages.getFirst().getMediaId();
        return mediaFileRepository.findById(firstMediaId).map(this::toMediaFile).orElse(null);
    }

    /**
     * 加载活动图片媒体文件列表。
     *
     * <p>前置条件：activityId 非空。
     *
     * <p>后置条件：按活动图片 sortOrder 顺序返回可找到的媒体文件，忽略已不存在的媒体引用。
     *
     * <p>不变量：不修改活动图片和媒体文件数据。
     *
     * @param activityId 活动 ID
     * @return 活动图片媒体文件列表
     */
    @Transactional(readOnly = true)
    public List<MediaFile> loadMediaFiles(String activityId) {
        List<ActivityImage> activityImages = loadActivityImages(activityId);
        if (activityImages.isEmpty()) {
            return List.of();
        }
        List<String> mediaIds =
                activityImages.stream().map(ActivityImage::getMediaId).toList();
        Map<String, MediaFile> mediaFileMap = mediaFileRepository.findByMediaIdIn(mediaIds).stream()
                .collect(Collectors.toMap(MediaFile::getMediaId, mediaFile -> mediaFile));
        return mediaIds.stream().map(mediaFileMap::get).filter(Objects::nonNull).toList();
    }

    /**
     * 加载活动图片排序表。
     *
     * <p>前置条件：activityId 非空。
     *
     * <p>后置条件：返回 mediaId 到 sortOrder 的映射；无图片时返回空映射。
     *
     * <p>不变量：不修改活动图片数据。
     *
     * @param activityId 活动 ID
     * @return 媒体 ID 到排序值的映射
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> loadImageSortOrders(String activityId) {
        Map<String, Integer> sortOrderByMediaId = new LinkedHashMap<>();
        for (ActivityImage activityImage : loadActivityImages(activityId)) {
            sortOrderByMediaId.put(activityImage.getMediaId(), activityImage.getSortOrder());
        }
        return sortOrderByMediaId;
    }

    private List<ActivityImage> loadActivityImages(String activityId) {
        return activityImageRepository.findByActivityIdOrderBySortOrderAsc(activityId);
    }

    private CommonDtos.MediaFile toMediaFile(MediaFile mediaFile) {
        CommonDtos.MediaFile dto = new CommonDtos.MediaFile();
        dto.setMediaId(mediaFile.getMediaId());
        dto.setFileName(mediaFile.getFileName());
        dto.setContentType(mediaFile.getContentType());
        dto.setSizeBytes(mediaFile.getSizeBytes());
        dto.setUsage(mediaFile.getUsage());
        dto.setUrl(mediaFile.getUrl());
        dto.setUploadedAt(
                mediaFile.getUploadedAt() == null
                        ? null
                        : mediaFile.getUploadedAt().toString());
        return dto;
    }
}

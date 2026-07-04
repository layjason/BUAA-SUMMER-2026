package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import io.github.layjason.mayoistar.service.storage.FileStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 媒体文件上传统一服务。
 *
 * <p>类职责：封装文件校验、存储、读取和元数据持久化的通用流程，供各业务 Service 和 Controller 复用。
 *
 * <p>不变量：每种 MediaUsage 对应固定的文件类型白名单和大小上限。
 */
@Slf4j
@Service
public class MediaFileUploadService {

    private final FileStorageService fileStorageService;
    private final MediaFileRepository mediaFileRepository;
    private final MediaAccessService mediaAccessService;

    /**
     * 每种媒体用途允许的 MIME 类型集合。
     */
    private static final Map<MediaUsage, Set<String>> ALLOWED_TYPES = Map.ofEntries(
            Map.entry(MediaUsage.avatar, Set.of("image/jpeg", "image/png")),
            Map.entry(MediaUsage.merchantLicense, Set.of("image/jpeg", "image/png")),
            Map.entry(MediaUsage.chatImage, Set.of("image/jpeg", "image/png", "image/gif", "image/webp")),
            Map.entry(MediaUsage.teamFile, Set.of()), // 允许所有类型
            Map.entry(MediaUsage.teamAlbum, Set.of("image/jpeg", "image/png", "image/gif", "image/webp")),
            Map.entry(MediaUsage.activityImage, Set.of("image/jpeg", "image/png")),
            Map.entry(MediaUsage.activityReviewImage, Set.of("image/jpeg", "image/png")),
            Map.entry(MediaUsage.summaryImage, Set.of("image/jpeg", "image/png")));

    /**
     * 每种媒体用途的文件大小上限（字节）。
     */
    private static final Map<MediaUsage, Long> MAX_SIZES = Map.ofEntries(
            Map.entry(MediaUsage.avatar, 5L * 1024 * 1024), // 5 MB
            Map.entry(MediaUsage.merchantLicense, 10L * 1024 * 1024), // 10 MB
            Map.entry(MediaUsage.chatImage, 10L * 1024 * 1024), // 10 MB
            Map.entry(MediaUsage.teamFile, 50L * 1024 * 1024), // 50 MB
            Map.entry(MediaUsage.teamAlbum, 10L * 1024 * 1024), // 10 MB
            Map.entry(MediaUsage.activityImage, 10L * 1024 * 1024), // 10 MB
            Map.entry(MediaUsage.activityReviewImage, 10L * 1024 * 1024), // 10 MB
            Map.entry(MediaUsage.summaryImage, 10L * 1024 * 1024)); // 10 MB

    /**
     * @param fileStorageService  文件存储服务
     * @param mediaFileRepository 媒体文件数据访问
     */
    public MediaFileUploadService(
            FileStorageService fileStorageService,
            MediaFileRepository mediaFileRepository,
            MediaAccessService mediaAccessService) {
        this.fileStorageService = fileStorageService;
        this.mediaFileRepository = mediaFileRepository;
        this.mediaAccessService = mediaAccessService;
    }

    /**
     * 上传文件并返回媒体文件元数据 DTO。
     *
     * <p>前置条件：file 非空且可通过校验；usage 已定义类型/大小限制。
     *
     * <p>后置条件：文件已存入对象存储，元数据已持久化到 media_files 表，返回填充完整的 DTO。
     *
     * @param userId 上传者用户 ID
     * @param file   上传的文件
     * @param usage  媒体用途
     * @return 媒体文件元数据 DTO
     */
    @Transactional
    public CommonDtos.MediaFile upload(@NonNull String userId, @NonNull MultipartFile file, @NonNull MediaUsage usage) {
        validateFile(file, usage);

        UUID mediaId = UUID.randomUUID();
        String originalFilename =
                sanitizeForLog(Optional.ofNullable(file.getOriginalFilename()).orElse("file"));
        String contentType = file.getContentType();
        long size = file.getSize();

        String key = usage.name() + "/" + userId + "/" + mediaId.toString() + "_" + originalFilename;

        try (InputStream inputStream = file.getInputStream()) {
            fileStorageService.store(key, inputStream, contentType, size);
        } catch (IOException e) {
            log.error("文件上传失败: userId={}, usage={}", userId, usage, e);
            throw new RuntimeException("文件上传失败", e);
        }

        Instant now = Instant.now();

        MediaFile mediaFile = MediaFile.builder()
                .mediaId(mediaId)
                .fileName(originalFilename)
                .contentType(contentType)
                .sizeBytes(size)
                .usage(usage)
                .storagePath(key)
                .visibility(defaultVisibility(usage))
                .accessPolicy(defaultAccessPolicy(usage))
                .accessScopeId(defaultAccessScope(userId, usage))
                .accessVersion(1L)
                .uploadedBy(userId)
                .uploadedAt(now)
                .build();
        mediaFileRepository.save(mediaFile);

        log.info("文件上传成功: mediaId={}, userId={}, usage={}, key={}", mediaId, userId, usage, sanitizeForLog(key));

        return mediaAccessService.toSignedDto(mediaFile);
    }

    /**
     * 根据 mediaId 获取媒体文件元数据。
     *
     * <p>前置条件：mediaId 非空。
     *
     * <p>后置条件：若 mediaId 存在则返回对应元数据；否则抛出 BusinessException(404)。
     *
     * @param mediaId 媒体文件唯一标识
     * @return 媒体文件元数据实体
     */
    public MediaFile getMediaFile(@NonNull UUID mediaId) {
        return mediaFileRepository.findById(mediaId).orElseThrow(() -> new BusinessException(404, "媒体文件不存在"));
    }

    /**
     * 根据 mediaId 读取媒体文件内容。
     *
     * <p>前置条件：mediaId 对应有效媒体文件记录，且 storagePath 指向对象存储中的有效对象。
     *
     * <p>后置条件：返回对象存储中的文件输入流，由调用方负责关闭；若元数据或对象不存在则抛出 BusinessException(404)。
     *
     * <p>不变量：Controller 和业务服务不直接访问底层对象存储，统一由本服务完成读取。
     *
     * @param mediaId 媒体文件唯一标识
     * @return 文件输入流
     */
    public InputStream retrieveContent(@NonNull UUID mediaId) {
        MediaFile mediaFile = getMediaFile(mediaId);
        try {
            return fileStorageService.retrieve(mediaFile.getStoragePath());
        } catch (RuntimeException e) {
            log.warn("文件存储中不存在: mediaId={}, storagePath={}", mediaId, sanitizeForLog(mediaFile.getStoragePath()), e);
            throw new BusinessException(404, "媒体文件不存在");
        }
    }

    /**
     * 校验上传文件的类型和大小。
     *
     * <p>前置条件：file 非空。
     *
     * <p>后置条件：文件类型在允许列表中，大小不超过限制；否则抛出 BusinessException。
     *
     * @param file  上传的文件
     * @param usage 媒体用途
     */
    private void validateFile(MultipartFile file, MediaUsage usage) {
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new BusinessException(10013, "无法识别文件类型");
        }

        Set<String> allowed = ALLOWED_TYPES.getOrDefault(usage, Set.of());
        if (!allowed.isEmpty() && !allowed.contains(contentType)) {
            throw new BusinessException(10013, "不支持的图片格式");
        }

        Long maxSize = MAX_SIZES.get(usage);
        if (maxSize != null && file.getSize() > maxSize) {
            throw new BusinessException(10014, "文件大小超出限制");
        }
    }

    private static MediaVisibility defaultVisibility(MediaUsage usage) {
        return switch (usage) {
            // activityImage 上传时活动尚不存在，初态仅上传者可预览，绑定草稿/发布后再升级
            case avatar, summaryImage, activityReviewImage -> MediaVisibility.publicVisible;
            case merchantLicense, chatImage, teamFile, teamAlbum, activityImage -> MediaVisibility.privateVisible;
        };
    }

    private static MediaAccessPolicy defaultAccessPolicy(MediaUsage usage) {
        return switch (usage) {
            // activityImage 初态为 owner，绑定草稿升级为 activityOwner，审核通过后升级为 publicAccess
            case avatar, summaryImage, activityReviewImage -> MediaAccessPolicy.publicAccess;
            case merchantLicense, chatImage, teamFile, teamAlbum, activityImage -> MediaAccessPolicy.owner;
        };
    }

    private static String defaultAccessScope(String userId, MediaUsage usage) {
        return switch (usage) {
            case avatar, summaryImage, activityReviewImage -> "";
            case merchantLicense, chatImage, teamFile, teamAlbum, activityImage -> userId;
        };
    }

    private static String sanitizeForLog(String input) {
        return input.replace('\r', '_').replace('\n', '_');
    }
}

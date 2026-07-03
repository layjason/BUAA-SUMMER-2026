package io.github.layjason.mayoistar.service.media;

import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import java.time.Instant;
import java.util.UUID;

/**
 * 媒体访问快照。
 *
 * <p>类职责：承载下载路径所需的最小元数据，便于缓存层在不访问业务表的情况下完成签名和访问校验。
 *
 * <p>不变量：mediaId、storagePath、contentType、fileName、accessVersion、policy 均来自同一版本媒体记录。
 */
public record MediaAccessDescriptor(
        UUID mediaId,
        String storagePath,
        String contentType,
        String fileName,
        long sizeBytes,
        MediaVisibility visibility,
        MediaAccessPolicy policy,
        String scope,
        long accessVersion,
        Instant deletedAt,
        String uploadedBy) {}

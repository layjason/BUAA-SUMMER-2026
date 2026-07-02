package io.github.layjason.mayoistar.common;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import lombok.experimental.UtilityClass;

/**
 * 好友社群通用工具方法。
 *
 * <p>类职责：提供实体到 DTO 的公共转换方法，避免跨 Service 重复代码。
 *
 * <p>类不变量：所有方法为纯函数，不依赖外部状态。
 */
@UtilityClass
public class SocialUtils {

    /**
     * 将 MediaFile 实体转换为 MediaFile DTO。
     *
     * <p>前置条件：entity 非空。
     *
     * <p>后置条件：返回填充了所有非空字段的 DTO，url 根据 mediaId 自动生成。
     *
     * @param entity 媒体文件实体
     * @return 媒体文件 DTO
     */
    public CommonDtos.MediaFile toMediaFileDto(MediaFile entity) {
        CommonDtos.MediaFile dto = new CommonDtos.MediaFile();
        dto.setMediaId(entity.getMediaId());
        dto.setFileName(entity.getFileName());
        dto.setContentType(entity.getContentType());
        dto.setSizeBytes(entity.getSizeBytes());
        dto.setUsage(entity.getUsage());
        dto.setUrl(entity.getUrl());
        dto.setVisibility(entity.getVisibility());
        dto.setUploadedAt(entity.getUploadedAt().toString());
        return dto;
    }
}

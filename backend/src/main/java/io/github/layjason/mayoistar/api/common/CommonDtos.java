package io.github.layjason.mayoistar.api.common;

import io.github.layjason.mayoistar.entity.common.MediaUsage;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 跨领域通用响应 DTO 集合。
 *
 * <p>类职责：提供被多个领域 Controller 引用的响应模型。
 *
 * <p>类不变量：DTO 为纯数据载体，不包含业务逻辑。
 */
public final class CommonDtos {

    private CommonDtos() {}

    @Data
    public static class GeoPoint {
        private Double longitude;
        private Double latitude;
    }

    @Data
    public static class LocationInfo {
        private GeoPoint point;
        private String city;
        private String address;
        private String placeName;
    }

    @Data
    public static class MediaFile {
        private String mediaId;
        private String fileName;
        private String contentType;
        private Long sizeBytes;
        private MediaUsage usage;

        @Nullable
        private String url;

        @NotNull
        private String uploadedAt;
    }

    @Data
    public static class ImageTagConfirmation {
        private String mediaId;
        private List<String> tags;
    }
}

package io.github.layjason.mayoistar.api.common;

import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
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
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        private Double longitude;

        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        private Double latitude;
    }

    @Data
    public static class LocationInfo {
        @Valid
        private GeoPoint point;

        @Size(max = 100)
        private String city;

        @Size(max = 500)
        private String address;

        @Size(max = 200)
        private String placeName;
    }

    @Data
    public static class MediaFile {
        private UUID mediaId;
        private String fileName;
        private String contentType;
        private Long sizeBytes;
        private MediaUsage usage;

        @Nullable
        private String signedUrl;

        private MediaVisibility visibility;

        @NotNull
        private String uploadedAt;
    }

    @Data
    public static class ImageTagConfirmation {
        private UUID mediaId;
        private List<String> tags;
    }
}

package io.github.layjason.mayoistar.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SocialUtils 单元测试。
 *
 * <p>类职责：验证 MediaFile 实体到 DTO 的转换逻辑。
 */
class SocialUtilsTest {

    @Test
    @DisplayName("toMediaFileDto 应正确转换所有字段")
    void toMediaFileDtoMapsAllFields() {
        Instant now = Instant.now();
        MediaFile entity = MediaFile.builder()
                .mediaId("media-1")
                .fileName("avatar.png")
                .contentType("image/png")
                .sizeBytes(1024L)
                .usage(MediaUsage.avatar)
                .url("https://cdn.example.com/avatar.png")
                .uploadedAt(now)
                .build();

        CommonDtos.MediaFile dto = SocialUtils.toMediaFileDto(entity);

        assertThat(dto.getMediaId()).isEqualTo("media-1");
        assertThat(dto.getFileName()).isEqualTo("avatar.png");
        assertThat(dto.getContentType()).isEqualTo("image/png");
        assertThat(dto.getSizeBytes()).isEqualTo(1024L);
        assertThat(dto.getUsage()).isEqualTo(MediaUsage.avatar);
        assertThat(dto.getUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        assertThat(dto.getUploadedAt()).isEqualTo(now.toString());
    }
}

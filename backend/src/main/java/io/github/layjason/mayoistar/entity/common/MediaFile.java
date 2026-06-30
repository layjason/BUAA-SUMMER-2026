package io.github.layjason.mayoistar.entity.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 媒体文件元数据，记录上传文件的存储信息和用途。
 *
 * <p>业务对象通过 mediaId 引用媒体文件，不直接存储文件内容或存储凭据。
 */
@Entity
@Table(name = "media_files")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaFile {

    @Id
    @Column(name = "media_id", length = 36)
    private String mediaId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 50)
    private String usage;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    private String url;

    @Column(name = "uploaded_by", length = 36, nullable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}

package io.github.layjason.mayoistar.entity.common;

import io.github.layjason.mayoistar.entity.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
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
    @Column(name = "media_id", columnDefinition = "UUID")
    private UUID mediaId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 127)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MediaUsage usage;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private MediaVisibility visibility = MediaVisibility.privateVisible;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_policy", nullable = false, length = 50)
    @Builder.Default
    private MediaAccessPolicy accessPolicy = MediaAccessPolicy.owner;

    @Column(name = "access_scope_id", length = 100)
    private String accessScopeId;

    @Column(name = "access_version", nullable = false)
    @Builder.Default
    private Long accessVersion = 1L;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "uploaded_by", length = 36, nullable = false)
    private String uploadedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(name = "uploaded_at", nullable = false)
    @Builder.Default
    private Instant uploadedAt = Instant.now();

    @Column(name = "team_id", length = 36)
    private String teamId;
}

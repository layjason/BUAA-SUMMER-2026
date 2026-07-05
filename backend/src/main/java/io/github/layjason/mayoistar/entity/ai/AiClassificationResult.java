package io.github.layjason.mayoistar.entity.ai;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
 * AI 图片分类结果缓存实体。
 *
 * <p>类职责：持久化每张图片的 CLIP 分类结果，支持按 mediaId 直接查询以跳过重复推理。
 *
 * <p>类不变量：一个 mediaId 最多对应一条记录（主键约束）。
 */
@Entity
@Table(name = "ai_classification_results")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiClassificationResult {

    @Id
    @Column(name = "media_id", columnDefinition = "UUID", nullable = false)
    private UUID mediaId;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private Double confidence;

    @Column(name = "classified_at", nullable = false)
    @Builder.Default
    private Instant classifiedAt = Instant.now();

    @Column(name = "task_id", columnDefinition = "UUID")
    private UUID taskId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MediaFile mediaFile;
}

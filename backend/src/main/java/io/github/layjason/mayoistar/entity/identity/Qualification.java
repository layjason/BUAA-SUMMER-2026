package io.github.layjason.mayoistar.entity.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商家资质审核记录，与 MerchantProfile 一对一关联。
 *
 * <p>已通过的资质审核不可覆盖，被驳回后方可重新提交。
 */
@Entity
@Table(name = "qualifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Qualification {

    @Id
    @Column(name = "qualification_id", length = 36)
    private String qualificationId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "license_media_ids", columnDefinition = "text")
    private String licenseMediaIds;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "reviewer_id", length = 36)
    private String reviewerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

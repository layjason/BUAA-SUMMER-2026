package io.github.layjason.mayoistar.entity.social;

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
 * 用户举报，由用户提交并进入后台处理流程。
 */
@Entity
@Table(name = "user_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserReport {

    @Id
    @Column(name = "report_id", length = 36)
    private String reportId;

    @Column(name = "reporter_user_id", length = 36, nullable = false)
    private String reporterUserId;

    @Column(name = "target_user_id", length = 36, nullable = false)
    private String targetUserId;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "handling_note", columnDefinition = "text")
    private String handlingNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "handled_at")
    private Instant handledAt;
}

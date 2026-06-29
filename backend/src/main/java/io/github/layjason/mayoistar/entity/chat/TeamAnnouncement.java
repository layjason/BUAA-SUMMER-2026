package io.github.layjason.mayoistar.entity.chat;

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
 * 群公告，由队长或管理员在小队中发布。
 */
@Entity
@Table(name = "team_announcements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamAnnouncement {

    @Id
    @Column(name = "announcement_id", length = 36)
    private String announcementId;

    @Column(name = "team_id", length = 36, nullable = false)
    private String teamId;

    @Column(name = "publisher_id", length = 36, nullable = false)
    private String publisherId;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
}

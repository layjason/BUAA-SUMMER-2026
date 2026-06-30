package io.github.layjason.mayoistar.entity.chat;

import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.social.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * 群公告，由队长或管理员在小队中发布。
 */
@Entity
@Table(name = "team_announcements")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamAnnouncement {

    @Id
    @Column(name = "announcement_id", length = 36)
    private String announcementId;

    @Column(name = "team_id", length = 36, nullable = false)
    private String teamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Team team;

    @Column(name = "publisher_id", length = 36, nullable = false)
    private String publisherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User publisher;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
}

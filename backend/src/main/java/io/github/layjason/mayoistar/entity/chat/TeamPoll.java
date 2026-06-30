package io.github.layjason.mayoistar.entity.chat;

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
 * 群投票，由小队成员创建，选项至少两个。
 */
@Entity
@Table(name = "team_polls")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamPoll {

    @Id
    @Column(name = "poll_id", length = 36)
    private String pollId;

    @Column(name = "team_id", length = 36, nullable = false)
    private String teamId;

    @Column(nullable = false)
    private String title;

    private Instant deadline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

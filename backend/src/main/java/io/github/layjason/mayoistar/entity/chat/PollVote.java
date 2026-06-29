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
 * 投票记录，记录用户在投票中的选项选择。
 *
 * <p>同一用户对同一投票只能保留一个选择（后续投票覆盖前次选择）。
 */
@Entity
@Table(name = "poll_votes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PollVote {

    @Id
    @Column(name = "vote_id", length = 36)
    private String voteId;

    @Column(name = "poll_id", length = 36, nullable = false)
    private String pollId;

    @Column(name = "option_id", length = 36, nullable = false)
    private String optionId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;
}

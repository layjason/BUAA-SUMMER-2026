package io.github.layjason.mayoistar.entity.chat;

import io.github.layjason.mayoistar.entity.identity.User;
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
 * 投票记录，记录用户在投票中的选项选择。
 *
 * <p>同一用户对同一投票只能保留一个选择（后续投票覆盖前次选择）。
 */
@Entity
@Table(name = "poll_votes")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PollVote {

    @Id
    @Column(name = "vote_id", length = 36)
    private String voteId;

    @Column(name = "poll_id", length = 36, nullable = false)
    private String pollId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TeamPoll poll;

    @Column(name = "option_id", length = 36, nullable = false)
    private String optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PollOption option;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;
}

package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.PollVote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 投票记录数据访问层。
 *
 * <p>类职责：提供 PollVote 实体的 CRUD 及按投票、用户查询。
 */
public interface PollVoteRepository extends JpaRepository<PollVote, String> {

    Optional<PollVote> findByPollIdAndUserId(String pollId, String userId);

    List<PollVote> findByPollId(String pollId);

    List<PollVote> findByPollIdIn(List<String> pollIds);
}

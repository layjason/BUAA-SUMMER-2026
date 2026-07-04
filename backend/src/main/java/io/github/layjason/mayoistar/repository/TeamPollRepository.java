package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.TeamPoll;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 群投票数据访问层。
 *
 * <p>类职责：提供 TeamPoll 实体的 CRUD。
 */
public interface TeamPollRepository extends JpaRepository<TeamPoll, String> {

    Optional<TeamPoll> findByPollIdAndTeamId(String pollId, String teamId);

    Page<TeamPoll> findByTeamIdOrderByCreatedAtDesc(String teamId, Pageable pageable);
}

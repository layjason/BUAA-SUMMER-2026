package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.PollOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 投票选项数据访问层。
 *
 * <p>类职责：提供 PollOption 实体的 CRUD 及按投票查询。
 */
public interface PollOptionRepository extends JpaRepository<PollOption, String> {

    List<PollOption> findByPollId(String pollId);
}

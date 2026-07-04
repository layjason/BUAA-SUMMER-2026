package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.Team;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 小队数据访问层。
 *
 * <p>类职责：提供 Team 实体的 CRUD 及复杂筛选分页查询。
 */
public interface TeamRepository extends JpaRepository<Team, String>, JpaSpecificationExecutor<Team> {

    /**
     * 统计指定用户创建的小队数量。
     *
     * @param creatorId 创建者用户 ID
     * @return 小队数量
     */
    long countByCreatorId(String creatorId);

    /**
     * 按创建者分页查询小队。
     *
     * @param creatorId 创建者用户 ID
     * @param pageable  分页参数
     * @return 分页结果
     */
    Page<Team> findByCreatorId(String creatorId, Pageable pageable);

    /**
     * 通过群聊会话 ID 查找小队。
     *
     * @param chatId 群聊会话 ID
     * @return 小队信息（可能为空）
     */
    Optional<Team> findByChatId(String chatId);

    /**
     * 检查指定名称的小队是否存在。
     *
     * @param name 小队名称
     * @return 存在时返回 true
     */
    boolean existsByName(String name);
}

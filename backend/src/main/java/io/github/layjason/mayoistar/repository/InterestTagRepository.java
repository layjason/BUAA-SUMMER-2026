package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.identity.InterestTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 兴趣标签数据访问层。
 *
 * <p>类职责：提供 InterestTag 实体的 CRUD 及按名称查询。
 */
public interface InterestTagRepository extends JpaRepository<InterestTag, String> {

    Optional<InterestTag> findByName(String name);

    List<InterestTag> findAllByOrderByName();
}

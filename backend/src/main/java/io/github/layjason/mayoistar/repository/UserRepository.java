package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.identity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户账号数据访问层。
 *
 * <p>类职责：提供 User 实体的 CRUD 及按 email / nickname 查询。
 */
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}

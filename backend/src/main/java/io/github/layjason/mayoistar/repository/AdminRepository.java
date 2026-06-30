package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.admin.Admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 管理员数据访问层。
 *
 * <p>类职责：提供 Admin 实体的 CRUD 及按用户名查询。
 */
public interface AdminRepository extends JpaRepository<Admin, String> {

    Optional<Admin> findByUsername(String username);
}

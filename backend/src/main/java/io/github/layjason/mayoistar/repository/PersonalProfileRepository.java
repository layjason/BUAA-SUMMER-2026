package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 个人用户资料数据访问层。
 *
 * <p>类职责：提供 PersonalProfile 实体的 CRUD。
 */
public interface PersonalProfileRepository extends JpaRepository<PersonalProfile, String> {

    Optional<PersonalProfile> findByUserId(String userId);
}

package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.identity.MerchantProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 商家资料数据访问层。
 *
 * <p>类职责：提供 MerchantProfile 实体的 CRUD 及按用户 ID 查询。
 */
public interface MerchantProfileRepository extends JpaRepository<MerchantProfile, String> {

    Optional<MerchantProfile> findByUserId(String userId);
}

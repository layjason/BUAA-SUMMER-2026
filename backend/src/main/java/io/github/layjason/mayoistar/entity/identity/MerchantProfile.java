package io.github.layjason.mayoistar.entity.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商家资料，与 User 一对一关联。
 *
 * <p>商家昵称全平台唯一（与个人用户昵称共享唯一性约束）。
 */
@Entity
@Table(name = "merchant_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantProfile {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "merchant_nickname", unique = true, length = 50)
    private String merchantNickname;

    @Column(name = "avatar_media_id", length = 36)
    private String avatarMediaId;

    @Column(name = "interested_activity_fields", columnDefinition = "text")
    private String interestedActivityFields;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

package io.github.layjason.mayoistar.entity.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 个人用户资料，与 User 一对一关联。
 *
 * <p>昵称全平台唯一（与商家昵称共享唯一性约束）。兴趣标签以 JSON 数组形式存储。
 */
@Entity
@Table(name = "personal_profiles")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalProfile {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(name = "avatar_media_id", length = 36)
    private String avatarMediaId;

    @Column(length = 20)
    private String gender;

    @Column(length = 10)
    private String birthday;

    private String signature;

    @Column(name = "interest_tags", columnDefinition = "text")
    private String interestTags;

    @Column(name = "reputation_score", nullable = false)
    @Builder.Default
    private Integer reputationScore = 100;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

package io.github.layjason.mayoistar.entity.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 系统预定义兴趣标签，供用户在注册和编辑资料时选择。
 */
@Entity
@Table(name = "interest_tags")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterestTag {

    @Id
    @Column(name = "tag_id", length = 36)
    private String tagId;

    @Column(nullable = false, unique = true, length = 30)
    private String name;
}

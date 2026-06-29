package io.github.layjason.mayoistar.entity.chat;

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
 * 投票选项，属于一个群投票。
 */
@Entity
@Table(name = "poll_options")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PollOption {

    @Id
    @Column(name = "option_id", length = 36)
    private String optionId;

    @Column(name = "poll_id", length = 36, nullable = false)
    private String pollId;

    @Column(nullable = false)
    private String content;
}

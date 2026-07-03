package io.github.layjason.mayoistar.entity.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 小队与媒体文件的关联，记录小队拥有的群文件和相册图片。
 *
 * <p>类职责：解耦 media_files 表与小队业务，通过独立关联表建立小队到媒体的多对多单向关系。
 *
 * <p>不变量：team_id 关联 teams 表，media_id 关联 media_files 表。
 */
@Entity
@Table(name = "team_media_files")
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamMediaFile {

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @Column(name = "team_id", nullable = false, length = 36)
    private String teamId;

    @Column(name = "media_id", nullable = false, columnDefinition = "UUID")
    private UUID mediaId;
}

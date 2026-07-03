package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.social.TeamMediaFile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 小队媒体关联数据访问层。
 *
 * <p>类职责：管理 team_media_files 关联表的 CRUD，解耦 media_files 与小队业务。
 */
public interface TeamMediaFileRepository extends JpaRepository<TeamMediaFile, UUID> {

    Page<TeamMediaFile> findByTeamId(String teamId, Pageable pageable);

    List<TeamMediaFile> findByMediaIdInAndTeamId(Collection<UUID> mediaIds, String teamId);
}

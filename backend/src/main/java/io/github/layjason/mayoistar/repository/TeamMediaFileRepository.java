package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.social.TeamMediaFile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 小队媒体关联数据访问层。
 *
 * <p>类职责：管理 team_media_files 关联表的 CRUD，解耦 media_files 与小队业务。
 */
public interface TeamMediaFileRepository extends JpaRepository<TeamMediaFile, UUID> {

    Page<TeamMediaFile> findByTeamId(String teamId, Pageable pageable);

    List<TeamMediaFile> findByMediaIdInAndTeamId(Collection<UUID> mediaIds, String teamId);

    /**
     * 按小队和媒体用途分页查询关联记录，通过 JOIN media_files 按 usage 过滤，
     * 确保分页统计与实际返回 items 一致。
     */
    @Query("SELECT t FROM TeamMediaFile t JOIN MediaFile m ON t.mediaId = m.mediaId "
            + "WHERE t.teamId = :teamId AND m.usage = :usage")
    Page<TeamMediaFile> findByTeamIdAndMediaUsage(String teamId, MediaUsage usage, Pageable pageable);

    /**
     * 检查同小队中是否已存在相同文件名和用途的群文件。
     */
    @Query("SELECT COUNT(t) > 0 FROM TeamMediaFile t JOIN MediaFile m ON t.mediaId = m.mediaId "
            + "WHERE t.teamId = :teamId AND m.fileName = :fileName AND m.usage = :usage")
    boolean existsByTeamIdAndFileNameAndUsage(String teamId, String fileName, MediaUsage usage);
}

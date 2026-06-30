package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 媒体文件元数据数据访问层。
 *
 * <p>类职责：提供 MediaFile 实体的 CRUD 与按媒体 ID 批量查询能力。
 */
public interface MediaFileRepository extends JpaRepository<MediaFile, String> {

    List<MediaFile> findByMediaIdIn(Collection<String> mediaIds);
}

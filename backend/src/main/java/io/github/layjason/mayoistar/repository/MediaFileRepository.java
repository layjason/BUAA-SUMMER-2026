package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 媒体文件元数据数据访问层。
 *
 * <p>类职责：提供 MediaFile 实体的 CRUD 及批量查询。
 */
public interface MediaFileRepository extends JpaRepository<MediaFile, String> {

    /**
     * 根据媒体 ID 集合批量查询媒体文件。
     *
     * @param mediaIds 媒体 ID 集合，不为 null
     * @return 匹配的媒体文件列表，若无匹配则返回空列表
     */
    List<MediaFile> findByMediaIdIn(Collection<String> mediaIds);
}

package io.github.layjason.mayoistar.repository.ai;

import io.github.layjason.mayoistar.entity.ai.AiClassificationResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 分类结果数据访问层。
 *
 * <p>类职责：提供 AiClassificationResult 实体的 CRUD 和按 mediaId 批量查询。
 */
public interface AiClassificationResultRepository extends JpaRepository<AiClassificationResult, UUID> {

    /**
     * 按 mediaId 查询单个分类结果。
     *
     * @param mediaId 媒体文件 ID
     * @return 分类结果（若存在）
     */
    Optional<AiClassificationResult> findByMediaId(UUID mediaId);

    /**
     * 按 mediaId 列表批量查询分类结果。
     *
     * @param mediaIds 媒体文件 ID 列表
     * @return 匹配的分类结果列表
     */
    List<AiClassificationResult> findByMediaIdIn(Collection<UUID> mediaIds);

    /**
     * 按 taskId 查询该任务关联的所有分类结果。
     *
     * @param taskId 分类任务 ID
     * @return 分类结果列表
     */
    List<AiClassificationResult> findByTaskId(UUID taskId);
}

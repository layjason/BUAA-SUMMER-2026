package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.entity.ai.AiClassificationResult;
import io.github.layjason.mayoistar.repository.ai.AiClassificationResultRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分类结果缓存服务，封装对 ai_classification_results 表的读写逻辑。
 *
 * <p>类职责：提供按 mediaId 缓存查询、批量查找已缓存 mediaId、批量保存分类结果等能力。
 *
 * <p>类不变量：所有写操作均在事务中执行，确保 ai_classification_results 表的一致性和唯一性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationResultCache {

    private final AiClassificationResultRepository repository;

    /**
     * 按 mediaId 查询单个分类结果。
     *
     * <p>前置条件：mediaId 非 null。
     *
     * <p>后置条件：返回分类结果 Optional，未找到时为 Optional.empty()。
     *
     * @param mediaId 媒体文件 ID
     * @return 分类结果
     */
    @Transactional(readOnly = true)
    public Optional<AiClassificationResult> findByMediaId(UUID mediaId) {
        return repository.findByMediaId(mediaId);
    }

    /**
     * 从给定 mediaId 列表中筛选出已有缓存的 mediaId。
     *
     * <p>前置条件：mediaIds 非空。
     *
     * <p>后置条件：返回 mediaIds 中存在于 ai_classification_results 的子集。
     *
     * @param mediaIds 待检查的媒体文件 ID 列表
     * @return 已有缓存的 mediaId 列表
     */
    @Transactional(readOnly = true)
    public List<UUID> findCachedMediaIds(Collection<UUID> mediaIds) {
        return repository.findByMediaIdIn(mediaIds).stream()
                .map(AiClassificationResult::getMediaId)
                .toList();
    }

    /**
     * 按 mediaId 列表批量查询分类结果。
     *
     * @param mediaIds 媒体文件 ID 列表
     * @return 分类结果列表
     */
    @Transactional(readOnly = true)
    public List<AiClassificationResult> findByMediaIds(Collection<UUID> mediaIds) {
        return repository.findByMediaIdIn(mediaIds);
    }

    /**
     * 按 taskId 查询任务关联的所有分类结果。
     *
     * @param taskId 分类任务 ID
     * @return 分类结果列表
     */
    @Transactional(readOnly = true)
    public List<AiClassificationResult> findByTaskId(UUID taskId) {
        return repository.findByTaskId(taskId);
    }

    /**
     * 批量保存分类结果。
     *
     * <p>前置条件：results 中每个元素包含 mediaId、category、confidence、taskId。
     *
     * <p>后置条件：所有结果已持久化到 ai_classification_results 表。
     * 若某 mediaId 已有记录，则跳过不覆盖（唯一约束）。
     *
     * @param results 待保存的分类结果列表
     */
    @Transactional
    public void saveBatch(List<AiClassificationResult> results) {
        for (AiClassificationResult result : results) {
            if (repository.findByMediaId(result.getMediaId()).isPresent()) {
                log.debug("分类结果已存在，跳过: mediaId={}", result.getMediaId());
                continue;
            }
            repository.save(result);
        }
        log.info("分类结果已缓存: count={}", results.size());
    }
}

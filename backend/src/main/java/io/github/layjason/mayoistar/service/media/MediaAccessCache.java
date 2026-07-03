package io.github.layjason.mayoistar.service.media;

import java.util.Optional;
import java.util.UUID;

/**
 * 媒体访问快照缓存。
 *
 * <p>类职责：隔离媒体下载路径与具体缓存实现。生产环境可替换为 Redis/Valkey 实现，测试和单实例开发可使用内存实现。
 *
 * <p>不变量：同一 mediaId 的快照必须与当前 accessVersion 对齐；软删除后必须缓存 tombstone 或驱逐旧快照。
 */
public interface MediaAccessCache {

    Optional<MediaAccessDescriptor> get(UUID mediaId);

    void put(MediaAccessDescriptor descriptor);

    void evict(UUID mediaId);
}

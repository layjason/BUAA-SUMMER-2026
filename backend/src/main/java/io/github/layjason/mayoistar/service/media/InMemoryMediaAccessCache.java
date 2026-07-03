package io.github.layjason.mayoistar.service.media;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 内存媒体访问快照缓存。
 *
 * <p>类职责：为测试环境提供内存媒体访问快照缓存实现，无需 Redis 依赖。
 *
 * <p>不变量：写入同一 mediaId 会原子覆盖旧快照；删除时不会访问关系数据库。
 */
@Component
@Profile("test")
public class InMemoryMediaAccessCache implements MediaAccessCache {

    private final ConcurrentMap<UUID, MediaAccessDescriptor> descriptors = new ConcurrentHashMap<>();

    @Override
    public Optional<MediaAccessDescriptor> get(UUID mediaId) {
        return Optional.ofNullable(descriptors.get(mediaId));
    }

    @Override
    public void put(MediaAccessDescriptor descriptor) {
        descriptors.put(descriptor.mediaId(), descriptor);
    }

    @Override
    public void evict(UUID mediaId) {
        descriptors.remove(mediaId);
    }
}

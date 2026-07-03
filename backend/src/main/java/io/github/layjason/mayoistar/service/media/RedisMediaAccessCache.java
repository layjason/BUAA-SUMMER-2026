package io.github.layjason.mayoistar.service.media;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的媒体访问快照缓存。
 *
 * <p>类职责：在 Redis 中存储媒体访问快照，支持分布式环境下的缓存共享。
 *
 * <p>不变量：写入同一 mediaId 会原子覆盖旧快照；缓存条目设有 TTL；删除时驱逐对应键。
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisMediaAccessCache implements MediaAccessCache {

    private static final String KEY_PREFIX = "media:access:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 从 Redis 获取媒体访问快照。
     *
     * <p>前置条件：mediaId 非空。
     *
     * <p>后置条件：若 Redis 中存在有效快照则返回；否则返回空 Optional。
     *
     * @param mediaId 媒体标识
     * @return 媒体访问描述符，不存在或反序列化失败时返回 Optional.empty()
     */
    @Override
    public Optional<MediaAccessDescriptor> get(UUID mediaId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + mediaId);
        if (json == null) {
            log.debug("媒体访问快照缓存未命中: mediaId={}", mediaId);
            return Optional.empty();
        }
        try {
            MediaAccessDescriptor descriptor = objectMapper.readValue(json, MediaAccessDescriptor.class);
            return Optional.of(descriptor);
        } catch (JsonProcessingException e) {
            log.warn("媒体访问快照 JSON 反序列化失败: mediaId={}", mediaId, e);
            return Optional.empty();
        }
    }

    /**
     * 将媒体访问快照写入 Redis。
     *
     * <p>前置条件：descriptor 非空。
     *
     * <p>后置条件：快照以 JSON 形式存储在 Redis，带有 TTL。
     *
     * @param descriptor 媒体访问描述符
     */
    @Override
    public void put(MediaAccessDescriptor descriptor) {
        try {
            String json = objectMapper.writeValueAsString(descriptor);
            redisTemplate.opsForValue().set(KEY_PREFIX + descriptor.mediaId(), json, DEFAULT_TTL);
            log.debug("媒体访问快照已写入 Redis: mediaId={}", descriptor.mediaId());
        } catch (JsonProcessingException e) {
            log.error("媒体访问快照写入 Redis 失败: mediaId={}", descriptor.mediaId(), e);
        }
    }

    /**
     * 从 Redis 逐出媒体访问快照。
     *
     * <p>前置条件：mediaId 非空。
     *
     * <p>后置条件：对应 Redis 键被删除。
     *
     * @param mediaId 媒体标识
     */
    @Override
    public void evict(UUID mediaId) {
        redisTemplate.delete(KEY_PREFIX + mediaId);
        log.debug("媒体访问快照已从 Redis 逐出: mediaId={}", mediaId);
    }
}

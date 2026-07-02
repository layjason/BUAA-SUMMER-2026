package io.github.layjason.mayoistar.service.media;

import io.github.layjason.mayoistar.config.MediaAccessProperties;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 媒体签名刷新限流器。
 *
 * <p>类职责：在权限校验和签名生成前，对单用户或单 IP 的刷新请求频率和媒体数量进行限制。
 *
 * <p>前置条件：rateKey 已按 userId 或 clientIp 归一化，mediaCount 为去重后的媒体数量。
 *
 * <p>后置条件：未超过阈值时计数增加；超过阈值时抛出 429。
 *
 * <p>不变量：同一分钟窗口内请求数和媒体数均不得超过配置阈值。当 Redis 可用时使用 Redis 实现分布式限流，不可用时回退到内存实现。
 */
@Slf4j
@Component
public class MediaRefreshRateLimiter {

    private static final String LUA_CHECK_AND_INCREMENT = "local req_key = KEYS[1]\n"
            + "local media_key = KEYS[2]\n"
            + "local req_limit = tonumber(ARGV[1])\n"
            + "local media_limit = tonumber(ARGV[2])\n"
            + "local media_count = tonumber(ARGV[3])\n"
            + "local ttl = tonumber(ARGV[4])\n"
            + "local current_req = redis.call('INCR', req_key)\n"
            + "if current_req == 1 then\n"
            + "    redis.call('EXPIRE', req_key, ttl)\n"
            + "end\n"
            + "local current_media = redis.call('INCRBY', media_key, media_count)\n"
            + "if current_media == media_count then\n"
            + "    redis.call('EXPIRE', media_key, ttl)\n"
            + "end\n"
            + "return {current_req, current_media}";

    private final MediaAccessProperties properties;
    private final Clock clock = Clock.systemUTC();
    private final ConcurrentMap<String, WindowCounter> counters;

    @Nullable
    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<List<Long>> luaScript;

    /**
     * 构造限流器。
     *
     * <p>当 StringRedisTemplate 可用（非 test profile）时使用 Redis 实现分布式限流，否则回退到内存实现。
     *
     * @param properties 媒体访问配置
     * @param redisTemplateProvider Redis 模板提供者
     */
    public MediaRefreshRateLimiter(
            MediaAccessProperties properties, ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.properties = properties;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.counters = (this.redisTemplate == null) ? new ConcurrentHashMap<>() : null;
        if (this.redisTemplate != null) {
            DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_CHECK_AND_INCREMENT);
            script.setResultType((Class) List.class);
            this.luaScript = script;
        } else {
            this.luaScript = null;
        }
    }

    /**
     * 检查并记录一次批量刷新请求。
     *
     * <p>前置条件：rateKey 已按 userId 或 clientIp 归一化，mediaCount 为去重后的媒体数量。
     *
     * <p>后置条件：未超过阈值时计数增加；超过阈值时抛出 429。
     *
     * @param rateKey    限流键
     * @param mediaCount 本次刷新媒体数量
     */
    public void check(String rateKey, int mediaCount) {
        if (redisTemplate != null) {
            checkWithRedis(rateKey, mediaCount, redisTemplate, luaScript);
        } else {
            checkWithMemory(rateKey, mediaCount);
        }
    }

    private void checkWithRedis(
            String rateKey, int mediaCount, StringRedisTemplate redis, DefaultRedisScript<List<Long>> script) {
        long window = clock.instant().getEpochSecond() / 60;
        long ttl = 120;

        String reqKey = rateKey + ":" + window + ":requests";
        String mediaKey = rateKey + ":" + window + ":media";

        List<Long> results = redis.execute(
                script,
                List.of(reqKey, mediaKey),
                String.valueOf(properties.getRefreshRequestLimitPerMinute()),
                String.valueOf(properties.getRefreshMediaLimitPerMinute()),
                String.valueOf(mediaCount),
                String.valueOf(ttl));

        if (results == null || results.size() < 2) {
            log.warn("Redis Lua 脚本返回异常结果: rateKey={}", rateKey);
            return;
        }

        long currentReq = results.get(0);
        long currentMedia = results.get(1);

        if (currentReq > properties.getRefreshRequestLimitPerMinute()
                || currentMedia > properties.getRefreshMediaLimitPerMinute()) {
            log.warn("媒体签名刷新触发限流(Redis): rateKey={}, requests={}, mediaCount={}", rateKey, currentReq, currentMedia);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many media signed URL refreshes");
        }
    }

    private void checkWithMemory(String rateKey, int mediaCount) {
        long window = clock.instant().getEpochSecond() / 60;
        WindowCounter counter = counters.compute(rateKey, (ignored, current) -> {
            if (current == null || current.window() != window) {
                return new WindowCounter(window, 1, mediaCount);
            }
            return new WindowCounter(window, current.requests() + 1, current.mediaCount() + mediaCount);
        });

        if (counter.requests() > properties.getRefreshRequestLimitPerMinute()
                || counter.mediaCount() > properties.getRefreshMediaLimitPerMinute()) {
            log.warn(
                    "媒体签名刷新触发限流: rateKey={}, requests={}, mediaCount={}",
                    rateKey,
                    counter.requests(),
                    counter.mediaCount());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many media signed URL refreshes");
        }
    }

    private record WindowCounter(long window, int requests, int mediaCount) {}
}

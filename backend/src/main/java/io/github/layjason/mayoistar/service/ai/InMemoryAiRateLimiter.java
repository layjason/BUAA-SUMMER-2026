package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.config.AiProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 基于内存窗口计数的 AI 频率限制器。
 *
 * <p>类职责：消费 mayoistar.ai.rate-limit.max-requests-per-minute 配置，
 * 按用户与 AI 操作维度限制单进程内的每分钟调用次数。
 *
 * <p>类不变量：同一 key 的窗口计数通过 ConcurrentMap.compute 原子更新；不同用户和不同操作互不影响。
 */
@Slf4j
@Component
public class InMemoryAiRateLimiter implements AiRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final AiProperties properties;
    private final Clock clock;
    private final ConcurrentMap<RateLimitKey, WindowCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryAiRateLimiter(AiProperties properties) {
        this(properties, Clock.systemUTC());
    }

    private InMemoryAiRateLimiter(AiProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    static InMemoryAiRateLimiter withClock(AiProperties properties, Clock clock) {
        return new InMemoryAiRateLimiter(properties, clock);
    }

    /**
     * 尝试消费一次 AI 调用额度。
     *
     * <p>前置条件：userId 与 operation 非空。
     *
     * <p>后置条件：若当前分钟窗口仍有额度则计数加一并返回 true；否则保持计数不变并返回 false。
     *
     * <p>不变量：最大次数读取自 AiProperties，运行期配置变化会在下一次调用时生效。
     *
     * @param userId 当前登录用户标识
     * @param operation AI 操作名称
     * @return 是否成功消费调用额度
     */
    @Override
    public boolean tryAcquire(String userId, String operation) {
        Instant now = clock.instant();
        int maxRequests = Math.max(1, properties.getRateLimit().getMaxRequestsPerMinute());
        AtomicBoolean acquired = new AtomicBoolean(false);
        RateLimitKey key = new RateLimitKey(userId, operation);

        counters.compute(key, (ignored, current) -> nextCounter(current, now, maxRequests, acquired));
        cleanupExpired(now);

        if (!acquired.get()) {
            log.info("AI 调用触发频率限制: userId={}, operation={}, maxRequestsPerMinute={}", userId, operation, maxRequests);
        }
        return acquired.get();
    }

    private WindowCounter nextCounter(WindowCounter current, Instant now, int maxRequests, AtomicBoolean acquired) {
        if (current == null || isExpired(current, now)) {
            acquired.set(true);
            return new WindowCounter(now, 1);
        }
        if (current.count() >= maxRequests) {
            acquired.set(false);
            return current;
        }
        acquired.set(true);
        return new WindowCounter(current.windowStart(), current.count() + 1);
    }

    private void cleanupExpired(Instant now) {
        counters.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    private boolean isExpired(WindowCounter counter, Instant now) {
        return !counter.windowStart().plus(WINDOW).isAfter(now);
    }

    private record RateLimitKey(String userId, String operation) {}

    private record WindowCounter(Instant windowStart, int count) {}
}

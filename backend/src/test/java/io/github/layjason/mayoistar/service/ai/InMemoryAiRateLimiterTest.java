package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.config.AiProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * InMemoryAiRateLimiter 单元测试。
 *
 * <p>类职责：验证 AI 限流器会消费配置中的每分钟最大请求数，并按用户、操作与时间窗口隔离计数。
 */
class InMemoryAiRateLimiterTest {

    @Test
    @DisplayName("同一用户同一操作超过每分钟上限时应拒绝")
    void shouldRejectWhenSameUserAndOperationExceedsLimit() {
        AiProperties properties = new AiProperties();
        properties.getRateLimit().setMaxRequestsPerMinute(2);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-05T08:00:00Z"));
        InMemoryAiRateLimiter limiter = InMemoryAiRateLimiter.withClock(properties, clock);

        assertThat(limiter.tryAcquire("user-a", "activity-planning")).isTrue();
        assertThat(limiter.tryAcquire("user-a", "activity-planning")).isTrue();
        assertThat(limiter.tryAcquire("user-a", "activity-planning")).isFalse();
    }

    @Test
    @DisplayName("不同用户和不同操作应分别计数")
    void shouldIsolateByUserAndOperation() {
        AiProperties properties = new AiProperties();
        properties.getRateLimit().setMaxRequestsPerMinute(1);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-05T08:00:00Z"));
        InMemoryAiRateLimiter limiter = InMemoryAiRateLimiter.withClock(properties, clock);

        assertThat(limiter.tryAcquire("user-a", "activity-planning")).isTrue();
        assertThat(limiter.tryAcquire("user-a", "image-classification")).isTrue();
        assertThat(limiter.tryAcquire("user-b", "activity-planning")).isTrue();
        assertThat(limiter.tryAcquire("user-a", "activity-planning")).isFalse();
    }

    @Test
    @DisplayName("时间窗口过期后应重新允许调用")
    void shouldResetAfterOneMinuteWindow() {
        AiProperties properties = new AiProperties();
        properties.getRateLimit().setMaxRequestsPerMinute(1);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-05T08:00:00Z"));
        InMemoryAiRateLimiter limiter = InMemoryAiRateLimiter.withClock(properties, clock);

        assertThat(limiter.tryAcquire("user-a", "activity-planning")).isTrue();
        assertThat(limiter.tryAcquire("user-a", "activity-planning")).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("user-a", "activity-planning")).isTrue();
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

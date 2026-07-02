package io.github.layjason.mayoistar.service.media;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.config.MediaAccessProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("MediaRefreshRateLimiter")
class MediaRefreshRateLimiterTest {

    @Test
    @DisplayName("超过单用户每分钟请求次数限制时返回 429")
    void shouldRejectWhenRequestLimitExceeded() {
        MediaAccessProperties properties = new MediaAccessProperties();
        properties.setRefreshRequestLimitPerMinute(1);
        properties.setRefreshMediaLimitPerMinute(100);
        MediaRefreshRateLimiter limiter = new MediaRefreshRateLimiter(properties);

        limiter.check("rate:media-refresh:user:user1", 1);

        assertThatThrownBy(() -> limiter.check("rate:media-refresh:user:user1", 1))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(429);
    }
}

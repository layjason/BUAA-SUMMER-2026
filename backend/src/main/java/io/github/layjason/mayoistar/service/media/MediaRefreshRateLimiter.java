package io.github.layjason.mayoistar.service.media;

import io.github.layjason.mayoistar.config.MediaAccessProperties;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 媒体签名刷新限流器。
 *
 * <p>类职责：在权限校验和签名生成前，对单用户或单 IP 的刷新请求频率和媒体数量进行限制。
 *
 * <p>不变量：同一分钟窗口内请求数和媒体数均不得超过配置阈值；超过阈值时不执行后续签名逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaRefreshRateLimiter {

    private final MediaAccessProperties properties;
    private final Clock clock = Clock.systemUTC();
    private final ConcurrentMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

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

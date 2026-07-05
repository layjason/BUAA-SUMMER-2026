package io.github.layjason.mayoistar.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 活动功能配置属性。
 *
 * <p>类职责：绑定活动评价窗口等业务配置。
 *
 * <p>不变量：reviewWindowDays 必须为正整数。
 */
@Data
@Validated
@ConfigurationProperties("mayoistar.activity")
public class ActivityProperties {

    /**
     * 活动结束后评价窗口天数，默认 7 天。
     * 活动结束时间 + reviewWindowDays 之后，评价入口关闭。
     */
    private int reviewWindowDays = 7;
}

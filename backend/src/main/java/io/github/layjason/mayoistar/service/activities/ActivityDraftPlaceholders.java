package io.github.layjason.mayoistar.service.activities;

import java.time.Instant;

/**
 * 活动草稿内部占位值。
 *
 * <p>类职责：在数据库仍要求部分活动字段非空时，为草稿阶段尚未填写的字段提供不可提交的内部占位值。
 *
 * <p>类不变量：占位值只允许用于 reviewStatus=draft 或 changeRequired 的草稿；提交审核前必须被真实业务值替换。
 */
final class ActivityDraftPlaceholders {

    static final String TITLE = "";
    static final Instant REQUIRED_TIME = Instant.EPOCH;
    static final Integer CAPACITY = 0;

    private ActivityDraftPlaceholders() {}

    /**
     * 判断标题是否为草稿内部占位值。
     *
     * <p>前置条件：title 可为空。
     *
     * <p>后置条件：仅内部空字符串占位返回 true。
     *
     * <p>不变量：不修改传入值。
     *
     * @param title 活动标题
     * @return 是否为标题占位值
     */
    static boolean isTitlePlaceholder(String title) {
        return TITLE.equals(title);
    }

    /**
     * 判断时间是否为草稿内部占位值。
     *
     * <p>前置条件：instant 可为空。
     *
     * <p>后置条件：仅内部固定时间占位返回 true。
     *
     * <p>不变量：不修改传入值。
     *
     * @param instant 活动时间
     * @return 是否为时间占位值
     */
    static boolean isTimePlaceholder(Instant instant) {
        return REQUIRED_TIME.equals(instant);
    }

    /**
     * 判断容量是否为草稿内部占位值。
     *
     * <p>前置条件：capacity 可为空。
     *
     * <p>后置条件：仅内部容量占位返回 true。
     *
     * <p>不变量：不修改传入值。
     *
     * @param capacity 活动容量
     * @return 是否为容量占位值
     */
    static boolean isCapacityPlaceholder(Integer capacity) {
        return CAPACITY.equals(capacity);
    }
}

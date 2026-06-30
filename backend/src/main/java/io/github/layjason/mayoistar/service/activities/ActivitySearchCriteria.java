package io.github.layjason.mayoistar.service.activities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 活动搜索筛选条件。
 *
 * <p>类职责：承载活动搜索和地图点位接口传入的组合筛选参数。
 *
 * <p>类不变量：activityTypes 对外暴露不可变副本，其它字段为不可变值类型或只读引用。
 */
public final class ActivitySearchCriteria {

    private final String keyword;
    private final List<String> activityTypes;
    private final String city;
    private final Instant startAtFrom;
    private final Instant startAtTo;
    private final BigDecimal minFee;
    private final BigDecimal maxFee;
    private final Double latitude;
    private final Double longitude;
    private final Integer distanceMeters;
    private final Integer page;
    private final Integer pageSize;

    /**
     * 创建活动搜索筛选条件。
     *
     * <p>前置条件：所有字段均可为空；activityTypes 可为可变列表。
     *
     * <p>后置条件：创建不可变搜索条件对象，activityTypes 被复制为不可变列表。
     *
     * <p>不变量：费用字段仅表示筛选条件，不表示支付状态。
     */
    public ActivitySearchCriteria(
            String keyword,
            List<String> activityTypes,
            String city,
            Instant startAtFrom,
            Instant startAtTo,
            BigDecimal minFee,
            BigDecimal maxFee,
            Double latitude,
            Double longitude,
            Integer distanceMeters,
            Integer page,
            Integer pageSize) {
        this.keyword = keyword;
        this.activityTypes = activityTypes == null ? List.of() : List.copyOf(activityTypes);
        this.city = city;
        this.startAtFrom = startAtFrom;
        this.startAtTo = startAtTo;
        this.minFee = minFee;
        this.maxFee = maxFee;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceMeters = distanceMeters;
        this.page = page;
        this.pageSize = pageSize;
    }

    /**
     * 获取搜索关键词。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回原始关键词或 null。
     *
     * <p>不变量：不修改对象状态。
     */
    public String keyword() {
        return keyword;
    }

    /**
     * 获取活动类型筛选。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回不可变活动类型列表。
     *
     * <p>不变量：调用方无法通过返回值修改内部状态。
     */
    public List<String> activityTypes() {
        return activityTypes;
    }

    /**
     * 获取城市筛选。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回城市名称或 null。
     *
     * <p>不变量：不修改对象状态。
     */
    public String city() {
        return city;
    }

    /**
     * 获取活动开始时间下界。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回时间下界或 null。
     *
     * <p>不变量：Instant 为不可变对象。
     */
    public Instant startAtFrom() {
        return startAtFrom;
    }

    /**
     * 获取活动开始时间上界。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回时间上界或 null。
     *
     * <p>不变量：Instant 为不可变对象。
     */
    public Instant startAtTo() {
        return startAtTo;
    }

    /**
     * 获取最低费用筛选。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回最低费用或 null。
     *
     * <p>不变量：费用仅用于筛选。
     */
    public BigDecimal minFee() {
        return minFee;
    }

    /**
     * 获取最高费用筛选。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回最高费用或 null。
     *
     * <p>不变量：费用仅用于筛选。
     */
    public BigDecimal maxFee() {
        return maxFee;
    }

    /**
     * 获取中心点纬度。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回纬度或 null。
     *
     * <p>不变量：不校正坐标值。
     */
    public Double latitude() {
        return latitude;
    }

    /**
     * 获取中心点经度。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回经度或 null。
     *
     * <p>不变量：不校正坐标值。
     */
    public Double longitude() {
        return longitude;
    }

    /**
     * 获取距离半径。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回距离半径或 null。
     *
     * <p>不变量：单位固定为米。
     */
    public Integer distanceMeters() {
        return distanceMeters;
    }

    /**
     * 获取页码。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回页码或 null。
     *
     * <p>不变量：归一化由服务层完成。
     */
    public Integer page() {
        return page;
    }

    /**
     * 获取每页数量。
     *
     * <p>前置条件：对象已构造完成。
     *
     * <p>后置条件：返回每页数量或 null。
     *
     * <p>不变量：归一化由服务层完成。
     */
    public Integer pageSize() {
        return pageSize;
    }
}

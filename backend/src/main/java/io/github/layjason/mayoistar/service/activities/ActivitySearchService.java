package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.repository.activities.ActivityRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 活动搜索服务。
 *
 * <p>类职责：实现活动搜索、高级筛选和地图点位查询。
 *
 * <p>类不变量：仅返回审核通过且未下架的活动；费用仅用于筛选，不产生支付行为。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivitySearchService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final double EARTH_RADIUS_METERS = 6_371_000D;

    private final ActivityRepository activityRepository;
    private final ActivitySearchMapper mapper;

    /**
     * 搜索活动摘要。
     *
     * <p>前置条件：criteria 可为空字段；若提供距离筛选，latitude、longitude、distanceMeters 必须同时有效才生效。
     *
     * <p>后置条件：返回符合条件的审核通过且未下架活动分页结果。
     *
     * <p>不变量：查询不修改活动、报名或媒体数据。
     *
     * @param criteria 搜索条件
     * @return 活动摘要分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivitySummary> search(ActivitySearchCriteria criteria) {
        List<Activity> filtered = findFilteredActivities(criteria);
        int page = normalizedPage(criteria.page());
        int pageSize = normalizedPageSize(criteria.pageSize());
        PageSlice<Activity> slice = slice(filtered, page, pageSize);
        List<ActivityDtos.ActivitySummary> items =
                slice.items().stream().map(mapper::toSummary).toList();
        log.info(
                "活动搜索完成: keyword={}, city={}, activityTypes={}, total={}, page={}, pageSize={}",
                criteria.keyword(),
                criteria.city(),
                criteria.activityTypes(),
                filtered.size(),
                page,
                pageSize);
        return new PageResult<>(items, (long) filtered.size(), page, pageSize, totalPages(filtered.size(), pageSize));
    }

    /**
     * 查询地图活动点位。
     *
     * <p>前置条件：criteria 可为空字段；地图点位只返回具备完整经纬度的活动。
     *
     * <p>后置条件：返回符合搜索条件的活动点位列表，列表长度受 page/pageSize 控制。
     *
     * <p>不变量：查询不修改活动、报名或媒体数据。
     *
     * @param criteria 搜索条件
     * @return 活动地图点位列表
     */
    @Transactional(readOnly = true)
    public List<ActivityDtos.ActivityMapPoint> mapPoints(ActivitySearchCriteria criteria) {
        List<Activity> filtered = findFilteredActivities(criteria).stream()
                .filter(activity -> activity.getPointLon() != null && activity.getPointLat() != null)
                .toList();
        int page = normalizedPage(criteria.page());
        int pageSize = normalizedPageSize(criteria.pageSize());
        List<ActivityDtos.ActivityMapPoint> points = slice(filtered, page, pageSize).items().stream()
                .map(mapper::toMapPoint)
                .toList();
        log.info(
                "活动地图点位查询完成: city={}, distanceMeters={}, total={}, returned={}",
                criteria.city(),
                criteria.distanceMeters(),
                filtered.size(),
                points.size());
        return points;
    }

    /**
     * 执行数据库筛选和距离筛选。
     *
     * <p>前置条件：criteria 不为 null。
     *
     * <p>后置条件：返回已按开始时间升序排列的活动实体列表。
     *
     * <p>不变量：距离筛选在内存中完成，以避免依赖数据库方言函数。
     */
    private List<Activity> findFilteredActivities(ActivitySearchCriteria criteria) {
        List<Activity> activities = activityRepository.findAll(
                buildSpecification(criteria),
                Sort.by(Sort.Direction.ASC, "startAt").and(Sort.by("activityId")));
        if (!hasDistanceFilter(criteria)) {
            return activities;
        }
        return activities.stream()
                .filter(activity -> withinDistance(activity, criteria))
                .toList();
    }

    /**
     * 构造动态查询条件。
     *
     * <p>前置条件：criteria 不为 null。
     *
     * <p>后置条件：返回包含公开可见约束和所有可数据库下推筛选项的 Specification。
     *
     * <p>不变量：不会为无效或空白参数生成谓词。
     */
    private Specification<Activity> buildSpecification(ActivitySearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("reviewStatus"), ActivityReviewStatus.approved));
            predicates.add(builder.notEqual(root.get("runtimeStatus"), ActivityRuntimeStatus.takenDown));
            if (hasText(criteria.keyword())) {
                String keyword = likePattern(criteria.keyword());
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), keyword),
                        builder.like(builder.lower(root.get("introduction")), keyword),
                        builder.like(builder.lower(root.get("placeName")), keyword),
                        builder.like(builder.lower(root.get("address")), keyword)));
            }
            List<String> activityTypes = normalizedTexts(criteria.activityTypes());
            if (!activityTypes.isEmpty()) {
                predicates.add(activityTypes.stream()
                        .map(activityType -> builder.like(
                                builder.lower(root.get("tags").as(String.class)), likePattern(activityType)))
                        .reduce(builder::or)
                        .orElseThrow());
            }
            if (hasText(criteria.city())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("city")), criteria.city().trim().toLowerCase(Locale.ROOT)));
            }
            if (criteria.startAtFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("startAt"), criteria.startAtFrom()));
            }
            if (criteria.startAtTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("startAt"), criteria.startAtTo()));
            }
            addFeePredicates(criteria, predicates, root, builder);
            if (hasDistanceFilter(criteria)) {
                predicates.add(builder.isNotNull(root.get("pointLon")));
                predicates.add(builder.isNotNull(root.get("pointLat")));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 添加费用筛选谓词。
     *
     * <p>前置条件：predicates、root、builder 不为 null。
     *
     * <p>后置条件：根据最低和最高费用补充筛选条件。
     *
     * <p>不变量：费用为 null 的活动按免费处理，仅参与 maxFee 覆盖免费活动的筛选。
     */
    private void addFeePredicates(
            ActivitySearchCriteria criteria,
            List<Predicate> predicates,
            jakarta.persistence.criteria.Root<Activity> root,
            jakarta.persistence.criteria.CriteriaBuilder builder) {
        if (criteria.minFee() != null) {
            predicates.add(builder.greaterThanOrEqualTo(
                    builder.coalesce(root.get("feeAmount"), BigDecimal.ZERO), criteria.minFee()));
        }
        if (criteria.maxFee() != null) {
            predicates.add(builder.lessThanOrEqualTo(
                    builder.coalesce(root.get("feeAmount"), BigDecimal.ZERO), criteria.maxFee()));
        }
    }

    /**
     * 判断活动是否处于距离半径内。
     *
     * <p>前置条件：criteria 已通过 hasDistanceFilter，activity 可缺少经纬度。
     *
     * <p>后置条件：返回活动点位到中心点距离是否不超过筛选半径。
     *
     * <p>不变量：使用 Haversine 公式近似球面距离，不依赖数据库扩展。
     */
    private boolean withinDistance(Activity activity, ActivitySearchCriteria criteria) {
        if (activity.getPointLat() == null || activity.getPointLon() == null) {
            return false;
        }
        return distanceMeters(criteria.latitude(), criteria.longitude(), activity.getPointLat(), activity.getPointLon())
                <= criteria.distanceMeters();
    }

    /**
     * 计算两点距离。
     *
     * <p>前置条件：四个坐标均不为 null。
     *
     * <p>后置条件：返回两点球面距离，单位米。
     *
     * <p>不变量：不改变输入坐标。
     */
    private double distanceMeters(double fromLat, double fromLon, double toLat, double toLon) {
        double latDelta = Math.toRadians(toLat - fromLat);
        double lonDelta = Math.toRadians(toLon - fromLon);
        double fromLatRadians = Math.toRadians(fromLat);
        double toLatRadians = Math.toRadians(toLat);
        double halfChord = Math.sin(latDelta / 2D) * Math.sin(latDelta / 2D)
                + Math.cos(fromLatRadians) * Math.cos(toLatRadians) * Math.sin(lonDelta / 2D) * Math.sin(lonDelta / 2D);
        return EARTH_RADIUS_METERS * 2D * Math.atan2(Math.sqrt(halfChord), Math.sqrt(1D - halfChord));
    }

    /**
     * 判断距离筛选是否完整有效。
     *
     * <p>前置条件：criteria 不为 null。
     *
     * <p>后置条件：经纬度与正数半径同时存在时返回 true。
     *
     * <p>不变量：不校验坐标是否在地理范围内，异常坐标自然不会命中真实数据。
     */
    private boolean hasDistanceFilter(ActivitySearchCriteria criteria) {
        return criteria.latitude() != null
                && criteria.longitude() != null
                && criteria.distanceMeters() != null
                && criteria.distanceMeters() > 0;
    }

    /**
     * 截取分页数据。
     *
     * <p>前置条件：items 不为 null，page 与 pageSize 已归一化。
     *
     * <p>后置条件：返回当前页对应的不可变视图副本。
     *
     * <p>不变量：不修改原列表。
     */
    private PageSlice<Activity> slice(List<Activity> items, int page, int pageSize) {
        int fromIndex = Math.min((page - 1) * pageSize, items.size());
        int toIndex = Math.min(fromIndex + pageSize, items.size());
        return new PageSlice<>(List.copyOf(items.subList(fromIndex, toIndex)));
    }

    /**
     * 归一化页码。
     *
     * <p>前置条件：page 可为空或非法。
     *
     * <p>后置条件：返回不小于 1 的页码。
     *
     * <p>不变量：不写回输入条件。
     */
    private int normalizedPage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    /**
     * 归一化每页数量。
     *
     * <p>前置条件：pageSize 可为空或非法。
     *
     * <p>后置条件：返回 1 到 MAX_PAGE_SIZE 之间的每页数量。
     *
     * <p>不变量：不写回输入条件。
     */
    private int normalizedPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 计算总页数。
     *
     * <p>前置条件：pageSize 为正数。
     *
     * <p>后置条件：返回分页总页数，无数据时返回 0。
     *
     * <p>不变量：纯计算函数。
     */
    private int totalPages(int total, int pageSize) {
        return total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
    }

    /**
     * 生成 LIKE 模式。
     *
     * <p>前置条件：text 不为空白。
     *
     * <p>后置条件：返回小写且包裹百分号的匹配模式。
     *
     * <p>不变量：不处理 SQL 通配符转义，当前接口关键词按模糊匹配语义处理。
     */
    private String likePattern(String text) {
        return "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
    }

    /**
     * 判断文本是否有效。
     *
     * <p>前置条件：text 可为空。
     *
     * <p>后置条件：非空白文本返回 true。
     *
     * <p>不变量：纯计算函数。
     */
    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * 归一化多选文本。
     *
     * <p>前置条件：values 可为空，元素可包含逗号分隔值。
     *
     * <p>后置条件：返回去除空白项后的文本列表。
     *
     * <p>不变量：不去重，保留调用方传入顺序。
     */
    private List<String> normalizedTexts(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .flatMap(value -> List.of(value.split(",")).stream())
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .toList();
    }

    /**
     * 内部分页切片。
     *
     * <p>类职责：表达已经截取好的分页条目。
     *
     * <p>类不变量：items 不为 null。
     *
     * @param items 当前页条目
     */
    private record PageSlice<Item>(List<Item> items) {}
}

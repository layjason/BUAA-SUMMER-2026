package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.service.ActivitySearchService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首页活动信息流服务。
 *
 * <p>类职责：为首页 Feed 提供推荐、最新和附近三种 Tab 的分页查询，
 * 每种 Tab 使用不同的排序策略，并支持 ActivitySearchQuery 中的筛选条件。
 *
 * <p>类不变量：所有查询仅返回审核通过且未下架的活动。
 */
@Service
@Slf4j
public class ActivityFeedService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final double EARTH_RADIUS_METERS = 6_371_000D;
    /**
     * 附近 Tab 未指定距离半径时的默认预过滤半径（米）。
     *
     * <p>"附近"语义隐含有限距离，使用 50km 作为默认 bbox 半径，
     * 既可覆盖城市级范围，又避免全量加载全国数据。
     */
    private static final int DEFAULT_NEARBY_BBOX_RADIUS_METERS = 50_000;

    private final ActivityRepository activityRepository;
    private final ActivitySearchService activitySearchService;
    private final ActivityDtoMapper activityDtoMapper;
    private final ActivityMediaQueryService activityMediaQueryService;
    private final ActivityRegistrationCountService activityRegistrationCountService;
    private final EntityManager entityManager;

    public ActivityFeedService(
            ActivityRepository activityRepository,
            ActivitySearchService activitySearchService,
            ActivityDtoMapper activityDtoMapper,
            ActivityMediaQueryService activityMediaQueryService,
            ActivityRegistrationCountService activityRegistrationCountService,
            EntityManager entityManager) {
        this.activityRepository = activityRepository;
        this.activitySearchService = activitySearchService;
        this.activityDtoMapper = activityDtoMapper;
        this.activityMediaQueryService = activityMediaQueryService;
        this.activityRegistrationCountService = activityRegistrationCountService;
        this.entityManager = entityManager;
    }

    /**
     * 获取首页活动信息流。
     *
     * <p>前置条件：tab 为 recommended/latest/nearby 之一，或默认 recommended。
     * nearby 模式会应用 criteria 中的 distanceMeters 半径过滤。
     *
     * <p>后置条件：返回分页的 ActivitySummary，仅包含审核通过、未下架且满足所有筛选条件的活动。
     *
     * <p>不变量：不修改数据库，不修改传入参数。
     *
     * @param tab      信息流 Tab 类型
     * @param criteria 搜索条件，包含分页参数和距离筛选
     * @return 分页活动摘要
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivitySummary> getFeed(String tab, ActivitySearchService.SearchCriteria criteria) {
        int normalizedPage = normalizePage(criteria.page());
        int normalizedPageSize = normalizePageSize(criteria.pageSize());
        String feedTab = tab != null ? tab.replaceAll("[\\r\\n]", "_") : "recommended";

        log.debug(
                "获取首页信息流: tab={}, page={}, pageSize={}, keyword={}, city={}, activityTypes={}",
                feedTab,
                normalizedPage,
                normalizedPageSize,
                criteria.keyword(),
                criteria.city(),
                criteria.activityTypes());

        return switch (feedTab) {
            case "latest" -> getLatestFeed(criteria, normalizedPage, normalizedPageSize);
            case "nearby" -> getNearbyFeed(criteria, normalizedPage, normalizedPageSize);
            default -> getRecommendedFeed(criteria, normalizedPage, normalizedPageSize);
        };
    }

    /**
     * 获取"最新"Tab 信息流，按创建时间降序排列。
     *
     * <p>前置条件：criteria 非空，page 和 pageSize 已规范化。
     *
     * <p>后置条件：返回按 createdAt 降序排列、满足所有筛选条件的分页结果。
     *
     * <p>不变量：仅返回审核通过且未下架且满足搜索条件的活动。
     */
    private PageResult<ActivityDtos.ActivitySummary> getLatestFeed(
            ActivitySearchService.SearchCriteria criteria, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Activity> activityPage =
                activityRepository.findAll(activitySearchService.buildSpecification(criteria), pageable);
        Function<String, ActivityRegistrationCounts> countsProvider = loadCountsProvider(activityPage.getContent());
        return activityDtoMapper.toActivitySummaryPage(
                activityPage, activityMediaQueryService::loadCoverImage, countsProvider);
    }

    /**
     * 获取"附近"Tab 信息流，按距离用户位置升序排列并按 distanceMeters 过滤。
     *
     * <p>前置条件：criteria 非空，page 和 pageSize 已规范化。
     * 若无有效用户坐标，回退到按 createdAt 降序排序。
     *
     * <p>后置条件：返回按距离升序排列且仅在 distanceMeters（或默认半径）范围内的分页结果。
     *
     * <p>不变量：仅返回审核通过且未下架且满足搜索条件的活动。
     *
     * <p>实现说明：
     * <ul>
     *   <li>先在数据库层通过矩形边界框（bounding box）预过滤，大幅减少候选集；</li>
     *   <li>再在内存中对 bbox 结果做精确 Haversine 距离计算与排序；</li>
     *   <li>若无用户坐标，回退到按 createdAt 降序的 DB 分页。</li>
     * </ul>
     */
    private PageResult<ActivityDtos.ActivitySummary> getNearbyFeed(
            ActivitySearchService.SearchCriteria criteria, int page, int pageSize) {
        Double latitude = criteria.latitude();
        Double longitude = criteria.longitude();

        if (latitude == null || longitude == null) {
            log.debug("附近 Tab 无用户坐标，回退按最新排序");
            return getLatestFeed(criteria, page, pageSize);
        }

        Integer distanceMeters = criteria.distanceMeters();
        boolean hasDistanceFilter = distanceMeters != null;
        int bboxRadius = hasDistanceFilter ? distanceMeters : DEFAULT_NEARBY_BBOX_RADIUS_METERS;

        List<Activity> candidates = loadCandidatesWithBbox(criteria, latitude, longitude, bboxRadius);
        List<Activity> withCoordinates = candidates.stream()
                .filter(a -> a.getPointLat() != null && a.getPointLon() != null)
                .toList();

        List<Activity> sorted = new ArrayList<>(withCoordinates);
        sorted.sort(
                Comparator.comparingDouble(a -> distanceMeters(latitude, longitude, a.getPointLat(), a.getPointLon())));

        // 若指定了 distanceMeters，则按半径精确过滤（bbox 是矩形近似，需要二次过滤）
        List<Activity> filtered = sorted;
        if (hasDistanceFilter) {
            filtered = sorted.stream()
                    .filter(a ->
                            distanceMeters(latitude, longitude, a.getPointLat(), a.getPointLon()) <= distanceMeters)
                    .toList();
        }

        return paginateInMemory(filtered, page, pageSize);
    }

    /**
     * 获取"推荐"Tab 信息流，随机排序。
     *
     * <p>前置条件：criteria 非空，page 和 pageSize 已规范化。
     *
     * <p>后置条件：返回随机排序后、满足所有筛选条件的分页结果。
     *
     * <p>不变量：仅返回审核通过且未下架且满足搜索条件的活动。
     *
     * <p>实现说明：
     * <ul>
     *   <li>先通过投影查询仅加载 activityId（避免全量实体加载），在内存中 shuffle 后截取当前页；</li>
     *   <li>再通过 {@code findAllById} 按 ID 批量获取当前页的完整实体。</li>
     * </ul>
     */
    private PageResult<ActivityDtos.ActivitySummary> getRecommendedFeed(
            ActivitySearchService.SearchCriteria criteria, int page, int pageSize) {
        Specification<Activity> spec = activitySearchService.buildSpecification(criteria);
        List<String> allIds = loadActivityIds(spec);
        int total = allIds.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);

        Collections.shuffle(allIds);

        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= total) {
            return new PageResult<>(List.of(), (long) total, page, pageSize, totalPages);
        }
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<String> pageIds = allIds.subList(fromIndex, toIndex);

        List<Activity> pageActivities = activityRepository.findAllById(pageIds);
        // 恢复 shuffle 后的顺序（findAllById 可能不保持传入顺序）
        Map<String, Activity> activityById = new LinkedHashMap<>();
        for (Activity a : pageActivities) {
            activityById.put(a.getActivityId(), a);
        }
        List<Activity> ordered = new ArrayList<>(pageIds.size());
        for (String id : pageIds) {
            Activity a = activityById.get(id);
            if (a != null) {
                ordered.add(a);
            }
        }

        Function<String, ActivityRegistrationCounts> countsProvider = loadCountsProvider(ordered);
        List<ActivityDtos.ActivitySummary> items = ordered.stream()
                .map(activity -> activityDtoMapper.toActivitySummary(
                        activity, activityMediaQueryService::loadCoverImage, countsProvider))
                .toList();
        return new PageResult<>(items, (long) total, page, pageSize, totalPages);
    }

    /**
     * 对内存中的列表进行分页并映射为 DTO。
     *
     * <p>前置条件：activities 非空列表或空列表。
     *
     * <p>后置条件：返回指定页的 ActivitySummary 分页结果。
     *
     * <p>不变量：不修改输入列表。
     */
    private PageResult<ActivityDtos.ActivitySummary> paginateInMemory(
            List<Activity> activities, int page, int pageSize) {
        int total = activities.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= total) {
            return new PageResult<>(List.of(), (long) total, page, pageSize, totalPages);
        }
        int toIndex = Math.min(fromIndex + pageSize, total);
        Function<String, ActivityRegistrationCounts> countsProvider = loadCountsProvider(activities);
        List<ActivityDtos.ActivitySummary> items = activities.subList(fromIndex, toIndex).stream()
                .map(activity -> activityDtoMapper.toActivitySummary(
                        activity, activityMediaQueryService::loadCoverImage, countsProvider))
                .toList();
        return new PageResult<>(items, (long) total, page, pageSize, totalPages);
    }

    /**
     * 使用 Haversine 公式计算两点间的地表距离。
     *
     * <p>前置条件：所有参数均为有效经纬度。
     *
     * <p>后置条件：返回以米为单位的距离。
     *
     * <p>不变量：纯函数，不依赖外部状态。
     *
     * @param lat1 点 1 纬度
     * @param lon1 点 1 经度
     * @param lat2 点 2 纬度
     * @param lon2 点 2 经度
     * @return 距离（米）
     */
    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lonDistance / 2)
                        * Math.sin(lonDistance / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * 为活动列表构建报名计数查询函数。
     *
     * <p>前置条件：activities 非空列表或空列表。
     *
     * <p>后置条件：返回一个 Function，对于已统计的活动返回计数，未知活动返回零值。
     *
     * <p>不变量：纯函数，不修改输入。
     */
    private Function<String, ActivityRegistrationCounts> loadCountsProvider(List<Activity> activities) {
        Map<String, ActivityRegistrationCounts> countsByActivityId =
                activityRegistrationCountService.countByActivityIds(
                        activities.stream().map(Activity::getActivityId).toList());
        return activityId -> countsByActivityId.getOrDefault(activityId, ActivityRegistrationCounts.zero());
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 通过投影查询仅加载活动 ID，避免全量实体加载。
     *
     * <p>前置条件：spec 非空。
     *
     * <p>后置条件：返回所有匹配 spec 的活动 ID 列表。
     *
     * <p>不变量：只读，不修改数据库。
     *
     * @param spec JPA 查询条件
     * @return 活动 ID 列表
     */
    private List<String> loadActivityIds(Specification<Activity> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> cq = cb.createQuery(String.class);
        Root<Activity> root = cq.from(Activity.class);
        cq.select(root.get("activityId"));
        if (spec != null) {
            cq.where(spec.toPredicate(root, cq, cb));
        }
        TypedQuery<String> query = entityManager.createQuery(cq);
        return query.getResultList();
    }

    /**
     * 使用矩形边界框（bounding box）预过滤，仅加载候选区域内的活动。
     *
     * <p>前置条件：latitude、longitude、radiusMeters 均为有效值，criteria 非空。
     *
     * <p>后置条件：返回在 bbox 范围内且满足 criteria 所有非距离筛选条件的活动列表。
     *
     * <p>不变量：只读，不修改数据库。
     *
     * @param criteria     搜索条件（用于非距离筛选）
     * @param latitude     用户纬度
     * @param longitude    用户经度
     * @param radiusMeters bbox 半径（米）
     * @return bbox 范围内的活动列表
     */
    private List<Activity> loadCandidatesWithBbox(
            ActivitySearchService.SearchCriteria criteria, double latitude, double longitude, int radiusMeters) {
        double[] bbox = ActivitySearchService.computeBoundingBox(latitude, longitude, radiusMeters);
        Specification<Activity> baseSpec = activitySearchService.buildSpecification(criteria);
        Specification<Activity> bboxSpec = (root, query, cb) -> cb.and(
                cb.between(root.get("pointLat"), bbox[0], bbox[1]), cb.between(root.get("pointLon"), bbox[2], bbox[3]));
        return activityRepository.findAll(baseSpec.and(bboxSpec));
    }

}

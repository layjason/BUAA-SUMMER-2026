package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.service.activities.ActivityRegistrationCountService;
import io.github.layjason.mayoistar.service.activities.ActivityRegistrationCounts;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ActivitySearchService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final double EARTH_RADIUS_METERS = 6_371_000D;

    private final ActivityRepository activityRepository;
    private final ActivityRegistrationCountService activityRegistrationCountService;

    /**
     * 搜索活动。
     *
     * <p>前置条件：criteria 非空。
     *
     * <p>后置条件：当无距离筛选条件时使用数据库分页；有距离筛选条件时先查询全量候选集，
     * 在内存中完成距离过滤后再统一分页，确保 items、total、totalPages 与筛选结果一致。
     *
     * <p>不变量：仅返回审核通过且未下架的活动。
     *
     * @param criteria 搜索条件
     * @return 分页活动摘要
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivitySummary> search(SearchCriteria criteria) {
        int page = normalizePage(criteria.page());
        int pageSize = normalizePageSize(criteria.pageSize());
        if (hasDistanceCriteria(criteria)) {
            return searchWithDistanceFilter(criteria, page, pageSize);
        }
        return searchWithoutDistanceFilter(criteria, page, pageSize);
    }

    /**
     * 无距离筛选时使用数据库分页查询。
     */
    private PageResult<ActivityDtos.ActivitySummary> searchWithoutDistanceFilter(
            SearchCriteria criteria, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "startAt"));
        var result = activityRepository.findAll(buildSpecification(criteria), pageable);
        Map<String, ActivityRegistrationCounts> countsByActivityId =
                activityRegistrationCountService.countByActivityIds(result.getContent().stream()
                        .map(Activity::getActivityId)
                        .toList());
        List<ActivityDtos.ActivitySummary> items = result.getContent().stream()
                .map(activity -> toSummary(
                        activity,
                        countsByActivityId.getOrDefault(activity.getActivityId(), ActivityRegistrationCounts.zero())))
                .toList();
        return new PageResult<>(items, result.getTotalElements(), page, pageSize, result.getTotalPages());
    }

    /**
     * 有距离筛选时：先查询全量非距离候选集，内存中距离过滤后再统一分页，
     * 确保 total 和 items 与距离筛选结果一致。
     */
    private PageResult<ActivityDtos.ActivitySummary> searchWithDistanceFilter(
            SearchCriteria criteria, int page, int pageSize) {
        List<Activity> candidates = activityRepository.findAll(buildSpecification(criteria));
        List<Activity> withinDistance = candidates.stream()
                .filter(activity -> matchesDistance(activity, criteria))
                .toList();
        int total = withinDistance.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= total) {
            return new PageResult<>(List.of(), (long) total, page, pageSize, totalPages);
        }
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Activity> pageActivities = withinDistance.subList(fromIndex, toIndex);
        Map<String, ActivityRegistrationCounts> countsByActivityId =
                activityRegistrationCountService.countByActivityIds(
                        pageActivities.stream().map(Activity::getActivityId).toList());
        List<ActivityDtos.ActivitySummary> items = pageActivities.stream()
                .map(activity -> toSummary(
                        activity,
                        countsByActivityId.getOrDefault(activity.getActivityId(), ActivityRegistrationCounts.zero())))
                .toList();
        return new PageResult<>(items, (long) total, page, pageSize, totalPages);
    }

    /**
     * 判断搜索条件是否包含有效的距离筛选参数。
     *
     * <p>前置条件：criteria 非空。
     *
     * <p>后置条件：当经纬度和距离均非空时返回 true。
     *
     * @param criteria 搜索条件
     * @return 是否包含距离筛选
     */
    private boolean hasDistanceCriteria(SearchCriteria criteria) {
        return criteria.latitude() != null && criteria.longitude() != null && criteria.distanceMeters() != null;
    }

    /**
     * 构建活动搜索的 JPA Specification，包含可见性过滤和所有非距离搜索条件。
     *
     * <p>前置条件：criteria 非空。
     *
     * <p>后置条件：返回仅匹配审核通过、未下架且满足搜索条件的 Specification。
     *
     * <p>不变量：纯函数，不修改入参或外部状态。
     *
     * @param criteria 搜索条件
     * @return JPA Specification
     */
    public Specification<Activity> buildSpecification(SearchCriteria criteria) {
        return toSpecification(criteria);
    }

    @Transactional(readOnly = true)
    public List<ActivityDtos.ActivityMapPoint> mapPoints(SearchCriteria criteria) {
        return search(criteria).getItems().stream()
                .filter(summary -> summary.getLocation() != null)
                .filter(summary -> summary.getLocation().getPoint() != null)
                .filter(summary -> summary.getLocation().getPoint().getLongitude() != null)
                .filter(summary -> summary.getLocation().getPoint().getLatitude() != null)
                .map(this::toMapPoint)
                .toList();
    }

    private Specification<Activity> toSpecification(SearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("reviewStatus"), ActivityReviewStatus.approved));
            predicates.add(builder.notEqual(root.get("runtimeStatus"), ActivityRuntimeStatus.takenDown));

            if (StringUtils.hasText(criteria.keyword())) {
                String keyword = "%" + criteria.keyword().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), keyword),
                        builder.like(builder.lower(root.get("introduction")), keyword),
                        builder.like(builder.lower(root.get("placeName")), keyword)));
            }
            if (criteria.activityTypes() != null && !criteria.activityTypes().isEmpty()) {
                List<Predicate> tagPredicates = criteria.activityTypes().stream()
                        .filter(StringUtils::hasText)
                        .map(type -> builder.like(builder.lower(root.get("tags").as(String.class)), tagPattern(type)))
                        .toList();
                if (!tagPredicates.isEmpty()) {
                    predicates.add(builder.or(tagPredicates.toArray(Predicate[]::new)));
                }
            }
            if (StringUtils.hasText(criteria.city())) {
                predicates.add(builder.equal(root.get("city"), criteria.city().trim()));
            }
            Instant startAtFrom = parseInstant(criteria.startAtFrom());
            if (startAtFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("startAt"), startAtFrom));
            }
            Instant startAtTo = parseInstant(criteria.startAtTo());
            if (startAtTo != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("startAt"), startAtTo));
            }
            if (criteria.minFee() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        builder.coalesce(root.get("feeAmount"), BigDecimal.ZERO),
                        BigDecimal.valueOf(criteria.minFee())));
            }
            if (criteria.maxFee() != null) {
                predicates.add(builder.lessThanOrEqualTo(
                        builder.coalesce(root.get("feeAmount"), BigDecimal.ZERO),
                        BigDecimal.valueOf(criteria.maxFee())));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String tagPattern(String type) {
        return "%\"" + type.trim().toLowerCase() + "\"%";
    }

    @Nullable
    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private boolean matchesDistance(Activity activity, SearchCriteria criteria) {
        if (criteria.latitude() == null
                || criteria.longitude() == null
                || criteria.distanceMeters() == null
                || activity.getPointLat() == null
                || activity.getPointLon() == null) {
            return true;
        }
        return distanceMeters(criteria.latitude(), criteria.longitude(), activity.getPointLat(), activity.getPointLon())
                <= criteria.distanceMeters();
    }

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

    private ActivityDtos.ActivitySummary toSummary(Activity activity, ActivityRegistrationCounts counts) {
        ActivityDtos.ActivitySummary dto = new ActivityDtos.ActivitySummary();
        dto.setActivityId(activity.getActivityId());
        dto.setTitle(activity.getTitle());
        dto.setTags(activity.getTags() == null ? List.of() : activity.getTags());
        dto.setStartAt(formatInstant(activity.getStartAt()));
        dto.setEndAt(formatInstant(activity.getEndAt()));
        dto.setLocation(toLocation(activity));
        dto.setFeeAmount(activity.getFeeAmount() != null ? activity.getFeeAmount() : java.math.BigDecimal.ZERO);
        dto.setReviewStatus(activity.getReviewStatus());
        dto.setRuntimeStatus(activity.getRuntimeStatus());
        dto.setRegisteredCount(counts.registeredCount());
        dto.setOccupiedCount(counts.occupiedCount());
        dto.setCapacity(activity.getCapacity());
        return dto;
    }

    private CommonDtos.LocationInfo toLocation(Activity activity) {
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLongitude(activity.getPointLon());
        point.setLatitude(activity.getPointLat());

        CommonDtos.LocationInfo location = new CommonDtos.LocationInfo();
        location.setPoint(point);
        location.setCity(activity.getCity());
        location.setAddress(activity.getAddress());
        location.setPlaceName(activity.getPlaceName());
        return location;
    }

    private ActivityDtos.ActivityMapPoint toMapPoint(ActivityDtos.ActivitySummary summary) {
        ActivityDtos.ActivityMapPoint dto = new ActivityDtos.ActivityMapPoint();
        dto.setActivityId(summary.getActivityId());
        dto.setTitle(summary.getTitle());
        dto.setPoint(summary.getLocation().getPoint());
        dto.setRuntimeStatus(summary.getRuntimeStatus());
        dto.setStartAt(summary.getStartAt());
        return dto;
    }

    private String formatInstant(Instant value) {
        return value == null ? null : value.toString();
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

    public record SearchCriteria(
            String keyword,
            List<String> activityTypes,
            String city,
            String startAtFrom,
            String startAtTo,
            Double minFee,
            Double maxFee,
            Double latitude,
            Double longitude,
            Integer distanceMeters,
            Integer page,
            Integer pageSize) {
        public SearchCriteria {
            activityTypes = activityTypes == null ? null : List.copyOf(activityTypes);
        }

        @Override
        @Nullable
        public List<String> activityTypes() {
            return activityTypes == null ? null : List.copyOf(activityTypes);
        }
    }
}

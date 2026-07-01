package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivitySummary> search(SearchCriteria criteria) {
        int page = normalizePage(criteria.page());
        int pageSize = normalizePageSize(criteria.pageSize());
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "startAt"));
        var result = activityRepository.findAll(toSpecification(criteria), pageable);
        List<ActivityDtos.ActivitySummary> items = result.getContent().stream()
                .filter(activity -> matchesDistance(activity, criteria))
                .map(this::toSummary)
                .toList();
        return new PageResult<>(items, result.getTotalElements(), page, pageSize, result.getTotalPages());
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

    private ActivityDtos.ActivitySummary toSummary(Activity activity) {
        ActivityDtos.ActivitySummary dto = new ActivityDtos.ActivitySummary();
        dto.setActivityId(activity.getActivityId());
        dto.setTitle(activity.getTitle());
        dto.setTags(activity.getTags() == null ? List.of() : activity.getTags());
        dto.setStartAt(formatInstant(activity.getStartAt()));
        dto.setEndAt(formatInstant(activity.getEndAt()));
        dto.setLocation(toLocation(activity));
        dto.setFeeAmount(activity.getFeeAmount());
        dto.setReviewStatus(activity.getReviewStatus());
        dto.setRuntimeStatus(activity.getRuntimeStatus());
        dto.setRegisteredCount(0);
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
        public List<String> activityTypes() {
            return activityTypes == null ? null : List.copyOf(activityTypes);
        }
    }
}

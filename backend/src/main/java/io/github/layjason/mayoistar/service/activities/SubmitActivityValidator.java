package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 活动提交审核校验器。
 *
 * <p>类职责：在将活动从草稿提交为待审核状态前，校验所有必填字段是否齐全且合法。
 *
 * <p>类不变量：校验器仅做只读检查，不修改活动实体，不执行持久化操作。
 */
@Slf4j
@Component
public class SubmitActivityValidator {

    /**
     * 校验活动是否满足提交审核的条件。
     *
     * <p>前置条件：activity 非空，且属于当前调用者。
     *
     * <p>后置条件：所有必填字段通过校验时正常返回；任一字段不满足则抛出 BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE) 或 BusinessException(ErrorCodes.ACTIVITY_STATE_NOT_SUBMITTABLE)。
     *
     * <p>不变量：不修改传入的活动实体。
     *
     * @param activity 待提交的活动实体
     * @throws BusinessException 当活动不满足提交条件时
     */
    public void validateForSubmission(Activity activity) {
        validateReviewState(activity);
        validateTitle(activity);
        validateTags(activity);
        validateIntroduction(activity);
        validateSchedule(activity);
        validateLocation(activity);
        validateSafetyNotice(activity);
        validateCapacity(activity);
        validateRegistrationDeadline(activity);
        validateFee(activity);
        validateMinAge(activity);
    }

    /**
     * 校验活动审核状态是否允许提交。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：reviewStatus 为 draft 或 changeRequired 时通过，否则抛出 ErrorCodes.ACTIVITY_STATE_NOT_SUBMITTABLE。
     */
    private void validateReviewState(Activity activity) {
        switch (activity.getReviewStatus()) {
            case draft, changeRequired -> {
                /* 允许提交 */
            }
            case pending -> throw new BusinessException(ErrorCodes.ACTIVITY_STATE_NOT_SUBMITTABLE, "当前活动已在审核中，不能重复提交");
            case approved -> throw new BusinessException(ErrorCodes.ACTIVITY_STATE_NOT_SUBMITTABLE, "当前活动已审核通过，不能重复提交");
            case rejected ->
                throw new BusinessException(ErrorCodes.ACTIVITY_STATE_NOT_SUBMITTABLE, "当前活动已被驳回，请创建新活动后重新提交");
        }
    }

    /**
     * 校验活动标题。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：title 非空且非空白时通过，否则抛出 ErrorCodes.INVALID_ACTIVITY_SCHEDULE。
     */
    private void validateTitle(Activity activity) {
        if (activity.getTitle() == null || activity.getTitle().isBlank()) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动名称不能为空");
        }
    }

    /**
     * 校验活动标签。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：tags 非空且至少包含一个标签时通过，否则抛出 ErrorCodes.INVALID_ACTIVITY_SCHEDULE。
     */
    private void validateTags(Activity activity) {
        if (activity.getTags() == null || activity.getTags().isEmpty()) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动标签不能为空");
        }
    }

    /**
     * 校验活动简介。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：introduction 非空且非空白时通过，否则抛出 ErrorCodes.INVALID_ACTIVITY_SCHEDULE。
     */
    private void validateIntroduction(Activity activity) {
        if (activity.getIntroduction() == null || activity.getIntroduction().isBlank()) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动简介不能为空");
        }
    }

    /**
     * 校验活动时间安排。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：startAt 与 endAt 均非空，且 endAt 严格晚于 startAt 时通过，否则抛出 ErrorCodes.INVALID_ACTIVITY_SCHEDULE。
     */
    private void validateSchedule(Activity activity) {
        Instant startAt = activity.getStartAt();
        Instant endAt = activity.getEndAt();
        if (startAt == null) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动开始时间不能为空");
        }
        if (endAt == null) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动结束时间不能为空");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动结束时间必须晚于开始时间");
        }
    }

    /**
     * 校验活动地点信息完整性。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：经纬度、城市、地址四者均非空时通过，否则抛出 ErrorCodes.INVALID_ACTIVITY_SCHEDULE。
     */
    private void validateLocation(Activity activity) {
        if (activity.getPointLon() == null
                || activity.getPointLat() == null
                || activity.getCity() == null
                || activity.getCity().isBlank()
                || activity.getAddress() == null
                || activity.getAddress().isBlank()) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动地点信息不完整，请提供地图选点和城市、详细地址");
        }
    }

    /**
     * 校验安全须知。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：safetyNotice 非空且非空白时通过，否则抛出 ErrorCodes.INVALID_ACTIVITY_SCHEDULE。
     */
    private void validateSafetyNotice(Activity activity) {
        if (activity.getSafetyNotice() == null || activity.getSafetyNotice().isBlank()) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动安全须知不能为空");
        }
    }

    /**
     * 校验活动人数上限。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：capacity 非空且大于 0 时通过，否则抛出 ErrorCodes.INVALID_ACTIVITY_SCHEDULE。
     */
    private void validateCapacity(Activity activity) {
        if (activity.getCapacity() == null || activity.getCapacity() < 1) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动人数上限必须大于 0");
        }
    }

    /**
     * 校验报名截止时间。
     *
     * <p>前置条件：activity 已通过时间安排校验。
     *
     * <p>后置条件：registrationDeadline 非空且不晚于活动开始时间时通过，否则抛出 ErrorCodes.INVALID_ACTIVITY_SCHEDULE。
     */
    private void validateRegistrationDeadline(Activity activity) {
        Instant registrationDeadline = activity.getRegistrationDeadline();
        if (registrationDeadline == null) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "报名截止时间不能为空");
        }
        if (registrationDeadline.isAfter(activity.getStartAt())) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "报名截止时间不能晚于活动开始时间");
        }
    }

    /**
     * 校验费用字段一致性。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：feeAmount 为非负数或无费用时通过，否则抛出 ErrorCodes.INVALID_ACTIVITY_SCHEDULE。
     */
    private void validateFee(Activity activity) {
        if (activity.getFeeAmount() != null && activity.getFeeAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "活动费用不能为负数");
        }
    }

    /**
     * 校验最低年龄要求。
     *
     * <p>前置条件：activity 非空。
     *
     * <p>后置条件：minAge 为非负数时通过，否则抛出 ErrorCodes.INVALID_ACTIVITY_SCHEDULE。
     */
    private void validateMinAge(Activity activity) {
        if (activity.getMinAge() != null && activity.getMinAge() < 0) {
            throw new BusinessException(ErrorCodes.INVALID_ACTIVITY_SCHEDULE, "最低年龄要求不能为负数");
        }
    }
}

package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.RegistrationStatus;
import io.github.layjason.mayoistar.entity.social.TeamMember;
import io.github.layjason.mayoistar.entity.social.TeamPointChangeSource;
import io.github.layjason.mayoistar.entity.social.TeamPointRecord;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamPointRecordRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRegistrationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 小队积分服务实现。
 *
 * <p>类职责：提供积分原子增减、爽约批量扣分等功能。
 *
 * <p>不变量：成员积分不低于 0；每条积分变动 (source, referenceId) 全局唯一。
 */
@Slf4j
@Service
public class TeamPointServiceImpl implements TeamPointService {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamPointRecordRepository teamPointRecordRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;

    public TeamPointServiceImpl(
            TeamMemberRepository teamMemberRepository,
            TeamPointRecordRepository teamPointRecordRepository,
            ActivityRepository activityRepository,
            ActivityRegistrationRepository activityRegistrationRepository) {
        this.teamMemberRepository = teamMemberRepository;
        this.teamPointRecordRepository = teamPointRecordRepository;
        this.activityRepository = activityRepository;
        this.activityRegistrationRepository = activityRegistrationRepository;
    }

    /**
     * 为指定小队成员增减积分。
     *
     * <p>前置条件：成员在指定小队内；当扣分时，积分不低于 0 且 pointChange 为负数。
     *
     * <p>后置条件：成员积分已更新，积分变动流水已写入；若 (source, referenceId) 已有记录则跳过。
     *
     * @param teamId      小队 ID
     * @param userId      用户 ID
     * @param pointChange 积分变动量（正数为加，负数为减）
     * @param source      变动来源
     * @param referenceId 关联实体 ID（可为 null）
     * @param reason      变动原因
     */
    @Override
    @Transactional
    public void addPoints(
            String teamId, String userId, int pointChange, TeamPointChangeSource source,
            @Nullable String referenceId, String reason) {
        if (referenceId != null && teamPointRecordRepository.existsBySourceAndReferenceId(source, referenceId)) {
            log.info(
                    "积分变动流水已存在，跳过: teamId={}, userId={}, source={}, referenceId={}",
                    teamId, userId, source, referenceId);
            return;
        }

        TeamMember member = teamMemberRepository
                .findByTeamIdAndUserIdForUpdate(teamId, userId)
                .orElseThrow(() -> {
                    log.warn("成员不在小队中: teamId={}, userId={}", teamId, userId);
                    return new BusinessException(ErrorCodes.TEAM_MEMBER_NOT_FOUND, "成员不在该小队中");
                });

        int newPoints = member.getPoints() + pointChange;
        if (newPoints < 0) {
            log.warn(
                    "积分扣减后为负值: teamId={}, userId={}, currentPoints={}, pointChange={}",
                    teamId, userId, member.getPoints(), pointChange);
            throw new BusinessException(ErrorCodes.TEAM_POINTS_INSUFFICIENT, "积分不足以扣减");
        }

        member.setPoints(newPoints);
        teamMemberRepository.save(member);

        TeamPointRecord record = TeamPointRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .teamId(teamId)
                .userId(userId)
                .pointChange(pointChange)
                .reason(reason)
                .source(source)
                .referenceId(referenceId)
                .createdAt(Instant.now())
                .build();
        teamPointRecordRepository.save(record);

        log.info(
                "小队积分变动: teamId={}, userId={}, change={}, newPoints={}, source={}, reason={}",
                teamId, userId, pointChange, newPoints, source, reason);
    }

    /**
     * 批量处理活动爽约扣分。
     *
     * <p>前置条件：activityId 对应活动为小队活动、活动已结束。
     *
     * <p>后置条件：所有报名但未签到的成员积分被扣减 noShow 分。
     *
     * @param activityId 活动 ID
     */
    @Override
    @Transactional
    public void processNoShows(String activityId) {
        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> {
                    log.warn("活动不存在: activityId={}", activityId);
                    return new BusinessException(ErrorCodes.ACTIVITY_NOT_VISIBLE, "活动不存在");
                });

        if (activity.getTeamId() == null) {
            log.info("活动 {} 不是小队活动，跳过爽约扣分", activityId);
            return;
        }

        List<RegistrationStatus> noShowStatuses = List.of(
                RegistrationStatus.registered,
                RegistrationStatus.waitingConfirmation);
        var registrations =
                activityRegistrationRepository.findByActivityIdAndStatusIn(activityId, noShowStatuses);

        if (registrations.isEmpty()) {
            log.info("活动 {} 无爽约用户", activityId);
            return;
        }

        int processedCount = 0;
        for (var reg : registrations) {
            try {
                addPoints(
                        activity.getTeamId(),
                        reg.getUserId(),
                        NO_SHOW_POINTS,
                        TeamPointChangeSource.no_show,
                        reg.getRegistrationId(),
                        "爽约扣分");
                processedCount++;
            } catch (Exception e) {
                log.warn(
                        "爽约扣分失败: teamId={}, userId={}, registrationId={}",
                        activity.getTeamId(),
                        reg.getUserId(),
                        reg.getRegistrationId(),
                        e);
            }
        }

        log.info("活动 {} 爽约扣分完成，处理 {} 人", activityId, processedCount);
    }
}

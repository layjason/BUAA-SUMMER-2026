package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.ReportRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户信誉分服务。
 *
 * <p>类职责：根据举报记录计算并更新用户的信誉分。
 *
 * <p>评定标准：初始 100 分。被举报次数 × 5 扣分 + 被不同人举报 × 10 扣分。最低 0 分。低于 60 分禁止报名活动。
 *
 * <p>不变量：信誉分不低于 0。
 */
@Slf4j
@Service
public class ReputationService {

    /**
     * 禁止报名活动的信誉分阈值。
     */
    public static final int ACTIVITY_BAN_THRESHOLD = 60;

    /**
     * 初始信誉分。
     */
    public static final int DEFAULT_SCORE = 100;

    private final ReportRepository reportRepository;
    private final PersonalProfileRepository personalProfileRepository;

    /**
     * @param reportRepository          举报记录数据访问
     * @param personalProfileRepository 个人资料数据访问
     */
    public ReputationService(ReportRepository reportRepository, PersonalProfileRepository personalProfileRepository) {
        this.reportRepository = reportRepository;
        this.personalProfileRepository = personalProfileRepository;
    }

    /**
     * 重新计算并更新指定用户的信誉分。
     *
     * <p>前置条件：userId 对应用户存在且有 PersonalProfile。
     *
     * <p>后置条件：PersonalProfile.reputationScore 更新为计算后的值（不低于 0）。
     *
     * <p>计算公式：max(0, 100 - 被举报次数 × 5 - 唯一举报人数 × 10)。
     *
     * @param userId 用户 ID
     * @return 更新后的信誉分
     */
    @Transactional
    public int recalculateScore(String userId) {
        PersonalProfile profile = personalProfileRepository.findByUserId(userId).orElseThrow(() -> {
            log.warn("尝试为不存在的个人资料计算信誉分: userId={}", userId);
            return new IllegalArgumentException("Personal profile not found for user: " + userId);
        });

        long resolvedReportCount = reportRepository.countByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.user, userId, ReportStatus.resolved);

        long distinctReporterCount = reportRepository.countDistinctReporterByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.user, userId, ReportStatus.resolved);

        int newScore = Math.max(0, DEFAULT_SCORE - (int) resolvedReportCount * 5 - (int) distinctReporterCount * 10);

        int oldScore = profile.getReputationScore() != null ? profile.getReputationScore() : DEFAULT_SCORE;
        profile.setReputationScore(newScore);
        profile.setUpdatedAt(Instant.now());
        personalProfileRepository.save(profile);

        log.info(
                "用户信誉分更新: userId={}, oldScore={}, newScore={}, resolvedReports={}, distinctReporters={}",
                userId,
                oldScore,
                newScore,
                resolvedReportCount,
                distinctReporterCount);

        return newScore;
    }

    /**
     * 获取用户当前信誉分。
     *
     * <p>前置条件：userId 对应用户存在且有 PersonalProfile。
     *
     * <p>后置条件：返回当前信誉分，若未设置则返回默认值 100。
     *
     * @param userId 用户 ID
     * @return 当前信誉分
     */
    public int getCurrentScore(String userId) {
        return personalProfileRepository
                .findByUserId(userId)
                .map(profile -> profile.getReputationScore() != null ? profile.getReputationScore() : DEFAULT_SCORE)
                .orElse(DEFAULT_SCORE);
    }

    /**
     * 检查用户信誉分是否允许报名活动。
     *
     * <p>前置条件：userId 对应用户存在。
     *
     * <p>后置条件：返回 true 表示允许报名。
     *
     * @param userId 用户 ID
     * @return 是否允许报名活动
     */
    public boolean canRegisterForActivity(String userId) {
        return getCurrentScore(userId) >= ACTIVITY_BAN_THRESHOLD;
    }
}

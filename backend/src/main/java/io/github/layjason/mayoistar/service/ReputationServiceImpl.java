package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import io.github.layjason.mayoistar.entity.social.ReputationChangeSource;
import io.github.layjason.mayoistar.entity.social.ReputationRecord;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.ReportRepository;
import io.github.layjason.mayoistar.repository.ReputationRecordRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 信誉积分服务实现。
 *
 * <p>类职责：根据已处理举报计算用户当前信誉分，并保存积分变更流水用于审计。
 *
 * <p>不变量：当前信誉分不低于 0；流水不参与当前信誉分的累加计算。
 */
@Slf4j
@Service
public class ReputationServiceImpl implements ReputationService {

    private final ReputationRecordRepository reputationRecordRepository;
    private final PersonalProfileRepository personalProfileRepository;
    private final ReportRepository reportRepository;

    public ReputationServiceImpl(
            ReputationRecordRepository reputationRecordRepository,
            PersonalProfileRepository personalProfileRepository,
            ReportRepository reportRepository) {
        this.reputationRecordRepository = reputationRecordRepository;
        this.personalProfileRepository = personalProfileRepository;
        this.reportRepository = reportRepository;
    }

    /**
     * 重新计算并更新指定用户的信誉分。
     *
     * <p>前置条件：userId 对应用户存在且有 PersonalProfile。
     *
     * <p>后置条件：PersonalProfile.reputationScore 更新为 max(0, 100 - 已处理举报数 * 5 - 不同举报人数 * 10)。
     *
     * @param userId 用户 ID
     * @return 更新后的信誉分
     */
    @Override
    @Transactional
    public int recalculateScore(String userId) {
        var profile = personalProfileRepository.findByUserId(userId).orElseThrow(() -> {
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
                "用户信誉分重算完成: userId={}, oldScore={}, newScore={}, resolvedReports={}, distinctReporters={}",
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
     * <p>前置条件：userId 对应用户可以没有 PersonalProfile。
     *
     * <p>后置条件：返回当前信誉分，若未设置或资料不存在则返回默认值。
     *
     * @param userId 用户 ID
     * @return 当前信誉分
     */
    @Override
    public int getCurrentScore(String userId) {
        return personalProfileRepository
                .findByUserId(userId)
                .map(profile -> profile.getReputationScore() != null ? profile.getReputationScore() : DEFAULT_SCORE)
                .orElse(DEFAULT_SCORE);
    }

    /**
     * 检查用户信誉分是否允许报名活动。
     *
     * <p>前置条件：userId 对应用户可以没有 PersonalProfile。
     *
     * <p>后置条件：返回 true 表示当前信誉分达到报名阈值。
     *
     * @param userId 用户 ID
     * @return 是否允许报名活动
     */
    @Override
    public boolean canRegisterForActivity(String userId) {
        return getCurrentScore(userId) >= ACTIVITY_BAN_THRESHOLD;
    }

    /**
     * 保存积分变更流水。
     *
     * <p>前置条件：userId 对应有效用户。
     *
     * <p>后置条件：若 source 和 referenceId 尚无流水，则 ReputationRecord 已保存；当前信誉分不被流水累加修改。
     *
     * @param userId      用户 ID
     * @param scoreChange 积分变动量
     * @param source      变更来源
     * @param referenceId 关联实体 ID
     * @param reason      变更原因
     */
    @Override
    @Transactional
    public void recordScoreChange(
            String userId, int scoreChange, ReputationChangeSource source, String referenceId, String reason) {
        if (referenceId != null && reputationRecordRepository.existsBySourceAndReferenceId(source, referenceId)) {
            log.info("信誉积分流水已存在，跳过重复记录: source={}, referenceId={}", source, referenceId);
            return;
        }

        ReputationRecord record = ReputationRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .userId(userId)
                .scoreChange(scoreChange)
                .reason(reason)
                .source(source)
                .referenceId(referenceId)
                .createdAt(Instant.now())
                .build();
        reputationRecordRepository.save(record);

        log.info("信誉积分变更: userId={}, change={}, reason={}", userId, scoreChange, reason);
    }
}

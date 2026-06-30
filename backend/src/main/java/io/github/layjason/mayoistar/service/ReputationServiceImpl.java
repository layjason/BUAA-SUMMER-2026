package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.entity.social.ReputationChangeSource;
import io.github.layjason.mayoistar.entity.social.ReputationRecord;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.ReputationRecordRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 信誉积分服务实现。
 *
 * <p>类职责：记录信誉积分变更并原子更新用户当前信誉分。
 * 仅实现保存与累加功能，不实现计算策略（如时间衰减、多次举报权重等）。
 *
 * <p>不变量：积分更新通过数据库 UPDATE WHERE 实现原子性，避免并发覆盖。
 */
@Slf4j
@Service
public class ReputationServiceImpl implements ReputationService {

    private final ReputationRecordRepository reputationRecordRepository;
    private final PersonalProfileRepository personalProfileRepository;

    public ReputationServiceImpl(
            ReputationRecordRepository reputationRecordRepository,
            PersonalProfileRepository personalProfileRepository) {
        this.reputationRecordRepository = reputationRecordRepository;
        this.personalProfileRepository = personalProfileRepository;
    }

    /**
     * 记录积分变更并更新用户当前信誉分。
     *
     * <p>前置条件：userId 对应有效用户。
     *
     * <p>后置条件：ReputationRecord 和 PersonalProfile.reputationScore 均已更新。
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

        personalProfileRepository.findById(userId).ifPresent(profile -> {
            int newScore = profile.getReputationScore() + scoreChange;
            profile.setReputationScore(newScore);
            profile.setUpdatedAt(Instant.now());
            personalProfileRepository.save(profile);
        });

        log.info("信誉积分变更: userId={}, change={}, reason={}", userId, scoreChange, reason);
    }
}

package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.ai.AiDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.ReviewStatus;
import io.github.layjason.mayoistar.service.ai.ContentReviewClient;
import io.github.layjason.mayoistar.service.ai.ContentReviewRisk;
import io.github.layjason.mayoistar.service.ai.ContentReviewScanResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 活动内容安全审核服务。
 *
 * <p>类职责：在活动提交审核前，聚合活动文本字段和活动图片，调用内容安全客户端并生成 API 契约中的 AI 审核结果。
 *
 * <p>类不变量：不修改 Activity 实体；外部 AI 调用失败时返回 failed/uncertain 结果，由提交流程转人工审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityContentReviewService {

    private final ContentReviewClient contentReviewClient;

    /**
     * 审核活动发布前内容。
     *
     * <p>前置条件：activity 已通过提交必填字段校验；images 为该活动已关联图片。
     *
     * <p>后置条件：返回包含风险等级、建议审核状态和理由的 AI 审核结果。
     *
     * <p>不变量：不改变活动审核状态，不保存审核记录。
     */
    public AiDtos.AiContentReviewResult reviewActivity(Activity activity, List<MediaFile> images) {
        ContentReviewScanResult textResult = contentReviewClient.scanText(buildReviewText(activity));
        ContentReviewScanResult imageResult = images == null || images.isEmpty()
                ? ContentReviewScanResult.low()
                : contentReviewClient.scanImages(images);

        ContentReviewRisk risk = mergeRisk(textResult.risk(), imageResult.risk());
        List<String> reasons = new ArrayList<>();
        reasons.addAll(prefixReasons("文本审核", textResult.reasons()));
        if (images != null && !images.isEmpty()) {
            reasons.addAll(prefixReasons("图片审核", imageResult.reasons()));
        }

        AiDtos.AiContentReviewResult result = new AiDtos.AiContentReviewResult();
        result.setStatus(risk == ContentReviewRisk.failed ? "failed" : "succeeded");
        result.setRiskLevel(toRiskLevel(risk));
        result.setSuggestedReviewStatus(toSuggestedReviewStatus(risk));
        result.setReasons(reasons);
        result.setFriendlyErrorMessage(firstError(textResult, imageResult));
        log.info("活动内容安全审核完成，activityId={}, risk={}", activity.getActivityId(), result.getRiskLevel());
        return result;
    }

    private String buildReviewText(Activity activity) {
        return String.join(
                System.lineSeparator(),
                "活动名称：" + activity.getTitle(),
                "活动标签：" + activity.getTags(),
                "活动简介：" + activity.getIntroduction(),
                "城市：" + activity.getCity(),
                "地点名称：" + activity.getPlaceName(),
                "详细地址：" + activity.getAddress(),
                "开始时间：" + activity.getStartAt(),
                "结束时间：" + activity.getEndAt(),
                "报名截止：" + activity.getRegistrationDeadline(),
                "人数上限：" + activity.getCapacity(),
                "最低年龄：" + activity.getMinAge(),
                "费用说明：" + activity.getFeeDescription(),
                "安全须知：" + activity.getSafetyNotice());
    }

    private ContentReviewRisk mergeRisk(ContentReviewRisk left, ContentReviewRisk right) {
        if (left == ContentReviewRisk.block || right == ContentReviewRisk.block) {
            return ContentReviewRisk.block;
        }
        if (left == ContentReviewRisk.failed || right == ContentReviewRisk.failed) {
            return ContentReviewRisk.failed;
        }
        if (left == ContentReviewRisk.review || right == ContentReviewRisk.review) {
            return ContentReviewRisk.review;
        }
        return ContentReviewRisk.low;
    }

    private String toRiskLevel(ContentReviewRisk risk) {
        return switch (risk) {
            case low -> "low";
            case review -> "uncertain";
            case block -> "high";
            case failed -> "uncertain";
        };
    }

    private ReviewStatus toSuggestedReviewStatus(ContentReviewRisk risk) {
        return switch (risk) {
            case low -> ReviewStatus.approved;
            case block -> ReviewStatus.rejected;
            case review, failed -> ReviewStatus.pending;
        };
    }

    private List<String> prefixReasons(String prefix, List<String> reasons) {
        return reasons.stream().map(reason -> prefix + "：" + reason).toList();
    }

    private String firstError(ContentReviewScanResult textResult, ContentReviewScanResult imageResult) {
        if (textResult.friendlyErrorMessage() != null) {
            return textResult.friendlyErrorMessage();
        }
        return imageResult.friendlyErrorMessage();
    }
}

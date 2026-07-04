package io.github.layjason.mayoistar.service.ai;

import java.util.List;

/**
 * 内容安全底层扫描结果。
 *
 * <p>类职责：屏蔽具体 AI 供应商返回结构，向活动审核流程暴露统一风险等级和理由。
 *
 * <p>类不变量：reasons 始终为不可变非空列表；failed 风险仅表示调用失败，不表示内容本身违规。
 */
public record ContentReviewScanResult(ContentReviewRisk risk, List<String> reasons, String friendlyErrorMessage) {

    public ContentReviewScanResult {
        reasons = reasons == null || reasons.isEmpty() ? List.of("未发现内容安全风险") : List.copyOf(reasons);
    }

    public static ContentReviewScanResult low() {
        return new ContentReviewScanResult(ContentReviewRisk.low, List.of("未发现内容安全风险"), null);
    }

    public static ContentReviewScanResult failed(String friendlyErrorMessage) {
        return new ContentReviewScanResult(
                ContentReviewRisk.failed, List.of("AI 内容安全审核暂不可用，转入人工审核"), friendlyErrorMessage);
    }
}

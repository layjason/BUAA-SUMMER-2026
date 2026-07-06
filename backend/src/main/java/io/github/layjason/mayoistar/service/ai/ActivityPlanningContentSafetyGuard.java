package io.github.layjason.mayoistar.service.ai;

import io.github.layjason.mayoistar.api.ai.AiDtos;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 活动策划输出内容安全审核器。
 *
 * <p>类职责：复用内容安全审核客户端，对模型生成的活动策划文本做二次审核。
 *
 * <p>类不变量：只有低风险内容可以返回给调用方；审核失败、需人工复核或高风险均视为模型输出不可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityPlanningContentSafetyGuard {

    private final ContentReviewClient contentReviewClient;

    /**
     * 审核模型生成的活动策划文本。
     *
     * <p>前置条件：result 已通过结构与字段级校验。
     *
     * <p>后置条件：低风险时正常返回；非低风险时抛出 IllegalArgumentException。
     *
     * <p>不变量：不记录完整活动策划文本，不修改 result。
     *
     * @param result 活动策划结果
     */
    public void review(AiDtos.ActivityPlanningResult result) {
        ContentReviewScanResult scanResult = contentReviewClient.scanText(buildReviewText(result));
        if (scanResult.risk() != ContentReviewRisk.low) {
            log.warn("AI 活动策划输出内容安全审核未通过: risk={}", scanResult.risk());
            throw new IllegalArgumentException("AI activity planning output failed content safety review");
        }
    }

    private String buildReviewText(AiDtos.ActivityPlanningResult result) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        joiner.add("活动名称：" + result.getTitle());
        joiner.add("活动标签：" + result.getTags());
        joiner.add("活动简介：" + result.getIntroduction());
        joiner.add("安全须知：" + result.getSafetyNotice());
        return joiner.toString();
    }
}

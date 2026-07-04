package io.github.layjason.mayoistar.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.api.ai.AiDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 内容审核快照序列化器。
 *
 * <p>类职责：在实体中的 JSON 字符串与 API DTO 之间转换，避免活动服务直接依赖 JSON 细节。
 *
 * <p>类不变量：无法解析历史快照时返回 null，不影响活动详情主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiContentReviewSnapshotMapper {

    private final ObjectMapper objectMapper;

    public String toJson(AiDtos.AiContentReviewResult result) {
        if (result == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            log.warn("序列化 AI 内容审核快照失败", exception);
            return null;
        }
    }

    public AiDtos.AiContentReviewResult fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AiDtos.AiContentReviewResult.class);
        } catch (JsonProcessingException exception) {
            log.warn("解析 AI 内容审核快照失败", exception);
            return null;
        }
    }
}

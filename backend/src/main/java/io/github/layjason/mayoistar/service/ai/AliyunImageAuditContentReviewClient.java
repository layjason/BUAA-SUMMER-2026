package io.github.layjason.mayoistar.service.ai;

import com.aliyun.imageaudit20191230.models.ScanImageAdvanceRequest;
import com.aliyun.imageaudit20191230.models.ScanImageResponse;
import com.aliyun.imageaudit20191230.models.ScanImageResponseBody;
import com.aliyun.imageaudit20191230.models.ScanTextRequest;
import com.aliyun.imageaudit20191230.models.ScanTextResponse;
import com.aliyun.imageaudit20191230.models.ScanTextResponseBody;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import io.github.layjason.mayoistar.config.AiProperties;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.service.storage.FileStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 阿里云 ImageAudit 内容审核客户端。
 *
 * <p>类职责：使用 imageaudit20191230 SDK 接入阿里云文本审核 ScanText 与图片审核 ScanImageAdvance。
 *
 * <p>类不变量：AccessKey 仅来自配置/环境变量，不写入日志；凭据缺失或调用失败时返回 failed 结果，由上层转人工审核。
 */
@Slf4j
@Component
@Profile("!test")
public class AliyunImageAuditContentReviewClient implements ContentReviewClient {

    private final AiProperties.ContentReview properties;
    private final FileStorageService fileStorageService;
    private final com.aliyun.imageaudit20191230.Client client;

    public AliyunImageAuditContentReviewClient(AiProperties aiProperties, FileStorageService fileStorageService) {
        this.properties = aiProperties.getContentReview();
        this.fileStorageService = fileStorageService;
        this.client = createClient();
    }

    /**
     * 扫描活动文本内容。
     *
     * <p>前置条件：content 为活动发布前可审核文本；配置中包含阿里云 AccessKey。
     *
     * <p>后置条件：返回低风险、需复核、阻断或调用失败结果，不向上抛出供应商异常。
     *
     * <p>不变量：不会记录用户原始活动内容和 AccessKey。
     */
    @Override
    public ContentReviewScanResult scanText(String content) {
        if (client == null) {
            return ContentReviewScanResult.failed("阿里云内容审核凭据未配置");
        }
        ScanTextRequest scanTextRequest = new ScanTextRequest()
                .setLabels(properties.getTextLabels().stream()
                        .map(label -> new ScanTextRequest.ScanTextRequestLabels().setLabel(label))
                        .toList())
                .setTasks(List.of(new ScanTextRequest.ScanTextRequestTasks().setContent(content)));
        try {
            ScanTextResponse response = client.scanTextWithOptions(scanTextRequest, new RuntimeOptions());
            ContentReviewScanResult result = mapTextResponse(response);
            log.info("阿里云文本内容审核完成，risk={}", result.risk());
            return result;
        } catch (TeaException exception) {
            log.warn("阿里云文本内容审核失败，code={}", exception.getCode());
            return ContentReviewScanResult.failed("阿里云文本内容审核失败：" + exception.getCode());
        } catch (Exception exception) {
            log.warn("阿里云文本内容审核异常", exception);
            return ContentReviewScanResult.failed("阿里云文本内容审核暂不可用");
        }
    }

    /**
     * 扫描活动图片内容。
     *
     * <p>前置条件：images 为活动草稿关联图片，storagePath 可由 FileStorageService 读取。
     *
     * <p>后置条件：返回所有图片审核后的最高风险等级；图片流在调用结束后关闭。
     *
     * <p>不变量：不会将图片改为公开访问，不修改媒体文件元数据。
     */
    @Override
    public ContentReviewScanResult scanImages(List<MediaFile> images) {
        if (images == null || images.isEmpty()) {
            return ContentReviewScanResult.low();
        }
        if (client == null) {
            return ContentReviewScanResult.failed("阿里云内容审核凭据未配置");
        }

        List<InputStream> openedStreams = new ArrayList<>();
        try {
            List<ScanImageAdvanceRequest.ScanImageAdvanceRequestTask> tasks = new ArrayList<>();
            for (MediaFile image : images) {
                InputStream inputStream = fileStorageService.retrieve(image.getStoragePath());
                openedStreams.add(inputStream);
                tasks.add(new ScanImageAdvanceRequest.ScanImageAdvanceRequestTask()
                        .setDataId(image.getMediaId().toString())
                        .setImageTimeMillisecond(1L)
                        .setInterval(1)
                        .setMaxFrames(1)
                        .setImageURLObject(inputStream));
            }
            ScanImageAdvanceRequest request =
                    new ScanImageAdvanceRequest().setTask(tasks).setScene(List.copyOf(properties.getImageScenes()));
            ScanImageResponse response = client.scanImageAdvance(request, new RuntimeOptions());
            ContentReviewScanResult result = mapImageResponse(response);
            log.info("阿里云图片内容审核完成，imageCount={}, risk={}", images.size(), result.risk());
            return result;
        } catch (TeaException exception) {
            log.warn("阿里云图片内容审核失败，code={}", exception.getCode());
            return ContentReviewScanResult.failed("阿里云图片内容审核失败：" + exception.getCode());
        } catch (Exception exception) {
            log.warn("阿里云图片内容审核异常", exception);
            return ContentReviewScanResult.failed("阿里云图片内容审核暂不可用");
        } finally {
            closeStreams(openedStreams);
        }
    }

    private com.aliyun.imageaudit20191230.Client createClient() {
        if (isBlank(properties.getAccessKeyId()) || isBlank(properties.getAccessKeySecret())) {
            log.warn("阿里云内容审核凭据未配置，活动提交将转入人工审核");
            return null;
        }
        try {
            Config config = new Config()
                    .setAccessKeyId(properties.getAccessKeyId())
                    .setAccessKeySecret(properties.getAccessKeySecret());
            config.endpoint = properties.getEndpoint();
            return new com.aliyun.imageaudit20191230.Client(config);
        } catch (Exception exception) {
            log.warn("阿里云内容审核客户端初始化失败", exception);
            return null;
        }
    }

    private ContentReviewScanResult mapTextResponse(ScanTextResponse response) {
        List<ScanTextResponseBody.ScanTextResponseBodyDataElementsResults> results = response.getBody() == null
                        || response.getBody().getData() == null
                        || response.getBody().getData().getElements() == null
                ? List.of()
                : response.getBody().getData().getElements().stream()
                        .filter(element -> element.getResults() != null)
                        .flatMap(element -> element.getResults().stream())
                        .toList();
        return mapSuggestions(results.stream()
                .map(result -> new Suggestion(result.getSuggestion(), "文本", result.getLabel(), result.getRate()))
                .toList());
    }

    private ContentReviewScanResult mapImageResponse(ScanImageResponse response) {
        List<ScanImageResponseBody.ScanImageResponseBodyDataResultsSubResults> subResults = response.getBody() == null
                        || response.getBody().getData() == null
                        || response.getBody().getData().getResults() == null
                ? List.of()
                : response.getBody().getData().getResults().stream()
                        .filter(result -> result.getSubResults() != null)
                        .flatMap(result -> result.getSubResults().stream())
                        .toList();
        return mapSuggestions(subResults.stream()
                .map(result -> new Suggestion(
                        result.getSuggestion(), "图片", result.getScene() + "/" + result.getLabel(), result.getRate()))
                .toList());
    }

    private ContentReviewScanResult mapSuggestions(List<Suggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return ContentReviewScanResult.low();
        }
        ContentReviewRisk risk = suggestions.stream()
                .map(suggestion -> switch (String.valueOf(suggestion.suggestion())) {
                    case "block" -> ContentReviewRisk.block;
                    case "review" -> ContentReviewRisk.review;
                    default -> ContentReviewRisk.low;
                })
                .max(Comparator.comparingInt(this::riskWeight))
                .orElse(ContentReviewRisk.low);
        if (risk == ContentReviewRisk.low) {
            return ContentReviewScanResult.low();
        }
        List<String> reasons = suggestions.stream()
                .filter(suggestion -> !"pass".equals(suggestion.suggestion()))
                .map(suggestion -> "%s命中%s，处置建议=%s，置信度=%s"
                        .formatted(
                                suggestion.source(),
                                suggestion.label(),
                                suggestion.suggestion(),
                                suggestion.rate() == null ? "未知" : suggestion.rate()))
                .toList();
        return new ContentReviewScanResult(risk, reasons, null);
    }

    private int riskWeight(ContentReviewRisk risk) {
        return switch (risk) {
            case failed -> 3;
            case block -> 2;
            case review -> 1;
            case low -> 0;
        };
    }

    private void closeStreams(List<InputStream> streams) {
        for (InputStream stream : streams) {
            try {
                stream.close();
            } catch (IOException exception) {
                log.debug("关闭审核图片流失败", exception);
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Suggestion(String suggestion, String source, String label, Float rate) {}
}

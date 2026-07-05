package io.github.layjason.mayoistar.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.ai.AiDtos.ClassifyTaskQueryResponse;
import io.github.layjason.mayoistar.api.ai.AiDtos.ClassifyTaskSubmitResponse;
import io.github.layjason.mayoistar.api.ai.AiDtos.MediaClassificationResponse;
import io.github.layjason.mayoistar.config.AiProperties;
import io.github.layjason.mayoistar.entity.ai.AiClassificationResult;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.service.ai.ClipTaskResultStore.TaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageClassificationServiceImplTest {

    @Mock
    private ClassificationResultCache resultCache;

    @Mock
    private ClipTaskResultStore taskResultStore;

    @Mock
    private KafkaClipProducer kafkaClipProducer;

    @Mock
    private AiProperties aiProperties;

    private ImageClassificationServiceImpl service;

    private static final String USER_ID = "test-user-id";

    @BeforeEach
    void setUp() {
        AiProperties.Clip clipConfig = new AiProperties.Clip();
        clipConfig.setRequestTimeoutSeconds(30);
        clipConfig.setRequestTopic("clip-classify-request");
        clipConfig.setResponseTopic("clip-classify-response");
        when(aiProperties.getClip()).thenReturn(clipConfig);

        service = new ImageClassificationServiceImpl(resultCache, taskResultStore, kafkaClipProducer, aiProperties);
    }

    @Nested
    @DisplayName("任务提交")
    class SubmitTask {

        @Test
        @DisplayName("空 mediaIds 应直接返回成功")
        void shouldReturnSucceededForEmptyInput() {
            ClassifyTaskSubmitResponse result = service.submitClassifyTask(List.of(), USER_ID);

            assertThat(result.getStatus()).isEqualTo("succeeded");
        }

        @Test
        @DisplayName("全部命中缓存时应直接返回成功，不发 Kafka")
        void shouldReturnSucceededWhenAllCached() {
            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();

            when(resultCache.findCachedMediaIds(List.of(mediaId1, mediaId2))).thenReturn(List.of(mediaId1, mediaId2));

            ClassifyTaskSubmitResponse result = service.submitClassifyTask(List.of(mediaId1, mediaId2), USER_ID);

            assertThat(result.getStatus()).isEqualTo("succeeded");
            assertThat(result.getTaskId()).isNotNull();
            verify(taskResultStore).markCompleted(any(UUID.class), eq("succeeded"), eq(null));
        }

        @Test
        @DisplayName("部分未缓存时应返回 pending 并发送 Kafka")
        void shouldReturnPendingWhenPartiallyCached() {
            UUID cachedId = UUID.randomUUID();
            UUID uncachedId = UUID.randomUUID();

            when(resultCache.findCachedMediaIds(List.of(cachedId, uncachedId))).thenReturn(List.of(cachedId));

            ClassifyTaskSubmitResponse result = service.submitClassifyTask(List.of(cachedId, uncachedId), USER_ID);

            assertThat(result.getStatus()).isEqualTo("pending");
            assertThat(result.getTaskId()).isNotNull();
            verify(kafkaClipProducer).send(result.getTaskId(), List.of(uncachedId));
        }

        @Test
        @DisplayName("全部未缓存时应返回 pending 并发送 Kafka")
        void shouldReturnPendingWhenNoneCached() {
            UUID mediaId = UUID.randomUUID();

            when(resultCache.findCachedMediaIds(List.of(mediaId))).thenReturn(List.of());

            ClassifyTaskSubmitResponse result = service.submitClassifyTask(List.of(mediaId), USER_ID);

            assertThat(result.getStatus()).isEqualTo("pending");
            assertThat(result.getTaskId()).isNotNull();
            verify(kafkaClipProducer).send(result.getTaskId(), List.of(mediaId));
        }
    }

    @Nested
    @DisplayName("任务结果查询")
    class QueryTaskResult {

        @Test
        @DisplayName("任务不存在时应抛出 AI_TASK_NOT_FOUND 异常")
        void shouldThrowTaskNotFoundWhenMissing() {
            UUID taskId = UUID.randomUUID();
            when(taskResultStore.getStatus(taskId)).thenReturn(null);

            assertThatThrownBy(() -> service.getClassifyTaskResult(taskId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(30004);
        }

        @Test
        @DisplayName("pending 状态应返回 pending")
        void shouldReturnPending() {
            UUID taskId = UUID.randomUUID();
            TaskStatus status = TaskStatus.builder()
                    .status("pending")
                    .userId(USER_ID)
                    .mediaIds(List.of(UUID.randomUUID()))
                    .createdAt(Instant.now().toString())
                    .build();
            when(taskResultStore.getStatus(taskId)).thenReturn(status);

            ClassifyTaskQueryResponse result = service.getClassifyTaskResult(taskId);

            assertThat(result.getStatus()).isEqualTo("pending");
        }

        @Test
        @DisplayName("succeeded 状态应从 DB 加载分类结果")
        void shouldReturnSucceededWithResults() {
            UUID taskId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();
            TaskStatus status = TaskStatus.builder()
                    .status("succeeded")
                    .userId(USER_ID)
                    .mediaIds(List.of(mediaId))
                    .createdAt(Instant.now().toString())
                    .build();
            when(taskResultStore.getStatus(taskId)).thenReturn(status);

            AiClassificationResult entity = AiClassificationResult.builder()
                    .mediaId(mediaId)
                    .category("group_photo")
                    .confidence(0.85)
                    .taskId(taskId)
                    .build();
            when(resultCache.findByMediaIds(List.of(mediaId))).thenReturn(List.of(entity));

            ClassifyTaskQueryResponse result = service.getClassifyTaskResult(taskId);

            assertThat(result.getStatus()).isEqualTo("succeeded");
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getMediaId()).isEqualTo(mediaId);
            assertThat(result.getItems().get(0).getSuggestedTags()).containsExactly("合影");
            assertThat(result.getItems().get(0).getConfidence()).isEqualTo(0.85);
        }
    }

    @Nested
    @DisplayName("按 mediaId 查询缓存")
    class QueryByMediaId {

        @Test
        @DisplayName("命中缓存时应返回分类结果")
        void shouldReturnResultWhenCached() {
            UUID mediaId = UUID.randomUUID();
            AiClassificationResult entity = AiClassificationResult.builder()
                    .mediaId(mediaId)
                    .category("venue")
                    .confidence(0.72)
                    .classifiedAt(Instant.parse("2026-07-04T10:00:00Z"))
                    .build();
            when(resultCache.findByMediaId(mediaId)).thenReturn(Optional.of(entity));

            MediaClassificationResponse result = service.getClassificationByMediaId(mediaId);

            assertThat(result).isNotNull();
            assertThat(result.getMediaId()).isEqualTo(mediaId);
            assertThat(result.getSuggestedTags()).containsExactly("场地");
            assertThat(result.getConfidence()).isEqualTo(0.72);
        }

        @Test
        @DisplayName("未命中缓存时应返回 null")
        void shouldReturnNullWhenNotCached() {
            UUID mediaId = UUID.randomUUID();
            when(resultCache.findByMediaId(mediaId)).thenReturn(Optional.empty());

            MediaClassificationResponse result = service.getClassificationByMediaId(mediaId);

            assertThat(result).isNull();
        }
    }
}

package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.entity.social.Report;
import io.github.layjason.mayoistar.entity.social.ReportStatus;
import io.github.layjason.mayoistar.entity.social.ReportTargetType;
import io.github.layjason.mayoistar.entity.social.ReputationChangeSource;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ReportRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * ReportService 单元测试。
 *
 * <p>类职责：验证举报创建、用户查询、管理员查询与处理的业务逻辑和异常路径。
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReputationService reputationService;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(reportRepository, userRepository, reputationService);
    }

    @Nested
    @DisplayName("创建举报")
    class CreateReportTests {

        @Test
        @DisplayName("成功创建用户举报")
        void createUserReportSuccess() {
            when(userRepository.existsById("user-b")).thenReturn(true);

            var result = service.createReport("user-a", ReportTargetType.user, "user-b", "spam");

            assertThat(result.getReporterUserId()).isEqualTo("user-a");
            assertThat(result.getTargetType()).isEqualTo(ReportTargetType.user);
            assertThat(result.getStatus()).isEqualTo(ReportStatus.pending);

            ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
            verify(reportRepository).save(captor.capture());
            assertThat(captor.getValue().getReason()).isEqualTo("spam");
        }

        @Test
        @DisplayName("不能举报自己")
        void cannotReportSelf() {
            assertThatThrownBy(() -> service.createReport("user-a", ReportTargetType.user, "user-a", "spam"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40007);
        }

        @Test
        @DisplayName("目标用户不存在时抛出异常")
        void nonExistentUserThrowsException() {
            when(userRepository.existsById("user-x")).thenReturn(false);

            assertThatThrownBy(() -> service.createReport("user-a", ReportTargetType.user, "user-x", "spam"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(40007);
        }
    }

    @Nested
    @DisplayName("查询举报")
    class ListReportsTests {

        @Test
        @DisplayName("查询我的举报列表")
        void listMyReports() {
            Report r1 = Report.builder()
                    .reportId("rp-1")
                    .reporterUserId("user-a")
                    .targetType(ReportTargetType.user)
                    .targetId("user-b")
                    .reason("spam")
                    .status(ReportStatus.pending)
                    .createdAt(Instant.now())
                    .build();
            Page<Report> page = new PageImpl<>(List.of(r1), PageRequest.of(0, 20), 1);
            when(reportRepository.findByReporterUserIdOrderByCreatedAtDesc("user-a", PageRequest.of(0, 20)))
                    .thenReturn(page);

            var result = service.listMyReports("user-a", null, 1, 20);

            assertThat(result.getItems()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("管理员处理举报")
    class DecideReportTests {

        @Test
        @DisplayName("管理员首次处理用户举报为已解决时重算信誉分并记录流水")
        void decideReportResolved() {
            Report report = Report.builder()
                    .reportId("rp-1")
                    .reporterUserId("user-a")
                    .targetType(ReportTargetType.user)
                    .targetId("user-b")
                    .reason("spam")
                    .status(ReportStatus.pending)
                    .createdAt(Instant.now())
                    .build();
            when(reportRepository.findById("rp-1")).thenReturn(Optional.of(report));
            when(reputationService.getCurrentScore("user-b")).thenReturn(100);
            when(reputationService.recalculateScore("user-b")).thenReturn(85);

            var result = service.decideReport("rp-1", ReportStatus.resolved, "已核实，扣分处理");

            assertThat(result.getStatus()).isEqualTo(ReportStatus.resolved);
            assertThat(result.getHandlingNote()).isEqualTo("已核实，扣分处理");
            assertThat(result.getHandledAt()).isNotNull();
            verify(reputationService).recalculateScore("user-b");
            verify(reputationService)
                    .recordScoreChange("user-b", -15, ReputationChangeSource.report, "rp-1", "举报核实扣分: 已核实，扣分处理");
        }

        @Test
        @DisplayName("已解决举报重复处理为已解决时不重复记录流水")
        void resolvedReportDoesNotRecordDuplicateChange() {
            Report report = Report.builder()
                    .reportId("rp-1")
                    .reporterUserId("user-a")
                    .targetType(ReportTargetType.user)
                    .targetId("user-b")
                    .reason("spam")
                    .status(ReportStatus.resolved)
                    .createdAt(Instant.now())
                    .build();
            when(reportRepository.findById("rp-1")).thenReturn(Optional.of(report));
            when(reputationService.getCurrentScore("user-b")).thenReturn(85);
            when(reputationService.recalculateScore("user-b")).thenReturn(85);

            service.decideReport("rp-1", ReportStatus.resolved, "维持处理");

            verify(reputationService).recalculateScore("user-b");
            verify(reputationService, never())
                    .recordScoreChange(
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyInt(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anyString());
        }
    }
}

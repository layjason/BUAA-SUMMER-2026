package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.config.TestSecurityConfiguration;
import io.github.layjason.mayoistar.config.TestStorageConfiguration;
import io.github.layjason.mayoistar.entity.chat.TeamAnnouncementRead;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.Team;
import io.github.layjason.mayoistar.entity.social.TeamJoinMode;
import io.github.layjason.mayoistar.entity.social.TeamMember;
import io.github.layjason.mayoistar.entity.social.TeamMemberRole;
import io.github.layjason.mayoistar.entity.social.TeamStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.TeamAnnouncementReadRepository;
import io.github.layjason.mayoistar.repository.TeamAnnouncementRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import({TestSecurityConfiguration.class, TestStorageConfiguration.class})
class ChatServiceAnnouncementTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private TeamAnnouncementRepository teamAnnouncementRepository;

    @Autowired
    private TeamAnnouncementReadRepository teamAnnouncementReadRepository;

    private User leader;
    private User member;
    private Team team;
    private String teamId;

    @BeforeEach
    void setUp() {
        leader = createUser("leader@test.test", "隊長");
        member = createUser("member@test.test", "隊員");

        teamId = UUID.randomUUID().toString();
        team = Team.builder()
                .teamId(teamId)
                .name("テスト小隊")
                .tags(List.of())
                .joinMode(TeamJoinMode.publicJoin)
                .capacity(10)
                .status(TeamStatus.active)
                .creatorId(leader.getUserId())
                .leaderId(leader.getUserId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        teamRepository.save(team);

        teamMemberRepository.save(TeamMember.builder()
                .memberId(UUID.randomUUID().toString())
                .teamId(teamId)
                .userId(leader.getUserId())
                .role(TeamMemberRole.leader)
                .points(0)
                .joinedAt(Instant.now())
                .build());
        teamMemberRepository.save(TeamMember.builder()
                .memberId(UUID.randomUUID().toString())
                .teamId(teamId)
                .userId(member.getUserId())
                .role(TeamMemberRole.member)
                .points(0)
                .joinedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("队长发布公告成功")
    void publishAnnouncementByLeader() {
        var result = chatService.publishAnnouncement(teamId, leader.getUserId(), "重要なお知らせ");

        assertThat(result.getAnnouncementId()).isNotNull();
        assertThat(result.getTeamId()).isEqualTo(teamId);
        assertThat(result.getPublisherId()).isEqualTo(leader.getUserId());
        assertThat(result.getContent()).isEqualTo("重要なお知らせ");
        assertThat(result.getReadByCurrentUser()).isTrue();

        List<TeamAnnouncementRead> reads =
                teamAnnouncementReadRepository.findByAnnouncementId(result.getAnnouncementId());
        assertThat(reads).hasSize(2);
        assertThat(reads.stream()
                        .filter(r -> r.getUserId().equals(leader.getUserId()))
                        .findFirst()
                        .orElseThrow()
                        .getReadAt())
                .isNotNull();
    }

    @Test
    @DisplayName("普通成员发布公告拒绝")
    void publishAnnouncementByMemberRejected() {
        assertThatThrownBy(() -> chatService.publishAnnouncement(teamId, member.getUserId(), "こっそり"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("非成员发布公告拒绝")
    void publishAnnouncementByNonMemberRejected() {
        User outsider = createUser("outsider@test.test", "部外者");

        assertThatThrownBy(() -> chatService.publishAnnouncement(teamId, outsider.getUserId(), "侵略"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("membership");
    }

    @Test
    @DisplayName("标记公告已读成功")
    void markAnnouncementReadSuccess() {
        var announcement = chatService.publishAnnouncement(teamId, leader.getUserId(), "お知らせ");

        var result = chatService.markAnnouncementRead(teamId, announcement.getAnnouncementId(), member.getUserId());

        assertThat(result.getReadByCurrentUser()).isTrue();
        assertThat(result.getAnnouncementId()).isEqualTo(announcement.getAnnouncementId());

        var read = teamAnnouncementReadRepository
                .findByAnnouncementIdAndUserId(announcement.getAnnouncementId(), member.getUserId())
                .orElseThrow();
        assertThat(read.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("标记公告已读 - 公告不存在抛异常")
    void markAnnouncementReadNotFound() {
        assertThatThrownBy(() -> chatService.markAnnouncementRead(teamId, "nonexistent", leader.getUserId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not visible");
    }

    @Test
    @DisplayName("标记公告已读 - 非成员抛异常")
    void markAnnouncementReadNonMember() {
        var announcement = chatService.publishAnnouncement(teamId, leader.getUserId(), "お知らせ");
        User outsider = createUser("outsider2@test.test", "部外者2");

        assertThatThrownBy(() -> chatService.markAnnouncementRead(
                        teamId, announcement.getAnnouncementId(), outsider.getUserId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("membership");
    }

    @Test
    @DisplayName("分页获取群公告列表，按发布时间降序")
    void listAnnouncements() {
        chatService.publishAnnouncement(teamId, leader.getUserId(), "第一報");
        chatService.publishAnnouncement(teamId, leader.getUserId(), "第二報");

        var result = chatService.listAnnouncements(teamId, member.getUserId(), 1, 20);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getContent()).isEqualTo("第二報");
        assertThat(result.getItems().get(1).getContent()).isEqualTo("第一報");
    }

    @Test
    @DisplayName("获取单条公告详情")
    void getAnnouncement() {
        var published = chatService.publishAnnouncement(teamId, leader.getUserId(), "詳細のお知らせ");

        var result = chatService.getAnnouncement(teamId, published.getAnnouncementId(), member.getUserId());

        assertThat(result.getAnnouncementId()).isEqualTo(published.getAnnouncementId());
        assertThat(result.getContent()).isEqualTo("詳細のお知らせ");
        assertThat(result.getReadByCurrentUser()).isFalse();
    }

    @Test
    @DisplayName("发布者编辑公告成功")
    void updateAnnouncementByPublisher() {
        var published = chatService.publishAnnouncement(teamId, leader.getUserId(), "元の内容");

        var result =
                chatService.updateAnnouncement(teamId, published.getAnnouncementId(), leader.getUserId(), "変更後の内容");

        assertThat(result.getContent()).isEqualTo("変更後の内容");
    }

    @Test
    @DisplayName("非发布者/非管理员编辑公告拒绝")
    void updateAnnouncementByNonPublisherRejected() {
        var published = chatService.publishAnnouncement(teamId, leader.getUserId(), "元の内容");

        assertThatThrownBy(() -> chatService.updateAnnouncement(
                        teamId, published.getAnnouncementId(), member.getUserId(), "書き換え"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("发布者删除公告成功，已读记录级联删除")
    void deleteAnnouncement() {
        var published = chatService.publishAnnouncement(teamId, leader.getUserId(), "削除予定");

        chatService.deleteAnnouncement(teamId, published.getAnnouncementId(), leader.getUserId());

        assertThat(teamAnnouncementRepository.findById(published.getAnnouncementId()))
                .isEmpty();
        assertThat(teamAnnouncementReadRepository.findByAnnouncementId(published.getAnnouncementId()))
                .isEmpty();
    }

    @Test
    @DisplayName("非发布者/非管理员删除公告拒绝")
    void deleteAnnouncementByNonPublisherRejected() {
        var published = chatService.publishAnnouncement(teamId, leader.getUserId(), "消さないで");

        assertThatThrownBy(
                        () -> chatService.deleteAnnouncement(teamId, published.getAnnouncementId(), member.getUserId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not allowed");
    }

    private User createUser(String email, String nickname) {
        User user = User.builder()
                .userId(UUID.randomUUID().toString())
                .email(email)
                .nickname(nickname)
                .passwordHash("hash")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return userRepository.save(user);
    }
}

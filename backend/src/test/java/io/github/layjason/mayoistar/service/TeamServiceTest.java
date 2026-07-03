package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.config.TestSecurityConfiguration;
import io.github.layjason.mayoistar.config.TestStorageConfiguration;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.TeamJoinMode;
import io.github.layjason.mayoistar.entity.social.TeamJoinRequestStatus;
import io.github.layjason.mayoistar.entity.social.TeamMemberRole;
import io.github.layjason.mayoistar.entity.social.TeamStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.TeamJoinRequestRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestSecurityConfiguration.class, TestStorageConfiguration.class})
@Transactional
class TeamServiceTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private TeamJoinRequestRepository teamJoinRequestRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Nested
    @DisplayName("小队创建")
    class CreateTeam {

        private User tomori;

        @BeforeEach
        void setUp() {
            tomori = createUser("tomori@mygo.band", "燈");
        }

        @Test
        @DisplayName("燈创建CRYCHIC，自动成为队长且有群聊")
        void createTeamSuccess() {
            SocialDtos.TeamCreateRequest request = createRequest("CRYCHIC", TeamJoinMode.publicJoin);

            var result = teamService.createTeam(tomori.getUserId(), request);

            assertThat(result.getTeamId()).isNotNull();
            assertThat(result.getName()).isEqualTo("CRYCHIC");
            assertThat(result.getTags()).containsExactly("音楽", "バンド");
            assertThat(result.getJoinMode()).isEqualTo(TeamJoinMode.publicJoin);
            assertThat(result.getCapacity()).isEqualTo(20);
            assertThat(result.getMemberCount()).isEqualTo(1);
            assertThat(result.getStatus()).isEqualTo(TeamStatus.active);
            assertThat(result.getCreatorId()).isEqualTo(tomori.getUserId());
            assertThat(result.getLeaderId()).isEqualTo(tomori.getUserId());
            assertThat(result.getChatId()).isNotNull();

            var member = teamMemberRepository
                    .findByTeamIdAndUserId(result.getTeamId(), tomori.getUserId())
                    .orElseThrow();
            assertThat(member.getRole()).isEqualTo(TeamMemberRole.leader);
        }

        @Test
        @DisplayName("创建重名CRYCHIC应拒绝——世间只有一个CRYCHIC")
        void createTeamDuplicateName() {
            SocialDtos.TeamCreateRequest request = createRequest("CRYCHIC", TeamJoinMode.publicJoin);
            teamService.createTeam(tomori.getUserId(), request);

            assertThatThrownBy(() -> teamService.createTeam(tomori.getUserId(), request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already taken");
        }
    }

    @Nested
    @DisplayName("小队搜索与详情")
    class SearchAndDetail {

        private User anon;
        private String teamId;

        @BeforeEach
        void setUp() {
            anon = createUser("anon@mygo.band", "愛音");
            SocialDtos.TeamCreateRequest request = createRequest("MyGO!!!!!", TeamJoinMode.publicJoin);
            var team = teamService.createTeam(anon.getUserId(), request);
            teamId = team.getTeamId();
        }

        @Test
        @DisplayName("愛音按关键词搜索MyGO")
        void searchByKeyword() {
            var result = teamService.searchTeams("MyGO", null, 1, 20);
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getName()).isEqualTo("MyGO!!!!!");
        }

        @Test
        @DisplayName("愛音按标签搜索乐队")
        void searchByTags() {
            var result = teamService.searchTeams(null, List.of("音楽"), 1, 20);
            assertThat(result.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("不匹配的标签返回空——你找不到不属于你的乐队")
        void searchByTagsNoMatch() {
            var result = teamService.searchTeams(null, List.of("プログラミング"), 1, 20);
            assertThat(result.getItems()).isEmpty();
        }

        @Test
        @DisplayName("获取MyGO详情")
        void getTeamDetail() {
            var result = teamService.getTeam(teamId);
            assertThat(result.getName()).isEqualTo("MyGO!!!!!");
            assertThat(result.getMemberCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("解散后不出现在搜索——CRYCHIC不会回来了")
        void dissolvedTeamNotInSearch() {
            teamService.dissolveTeam(teamId, anon.getUserId());
            var result = teamService.searchTeams("MyGO", null, 1, 20);
            assertThat(result.getItems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("加入小队")
    class JoinTeam {

        private User tomori;
        private User raana;
        private String publicTeamId;
        private String approvalTeamId;

        @BeforeEach
        void setUp() {
            tomori = createUser("tomori2@mygo.band", "燈");
            raana = createUser("raana@mygo.band", "楽奈");

            SocialDtos.TeamCreateRequest publicReq = createRequest("MyGO!!!!! 練習隊", TeamJoinMode.publicJoin);
            publicTeamId = teamService.createTeam(tomori.getUserId(), publicReq).getTeamId();

            SocialDtos.TeamCreateRequest approvalReq = createRequest("CRYCHIC 再結成", TeamJoinMode.approvalRequired);
            approvalTeamId =
                    teamService.createTeam(tomori.getUserId(), approvalReq).getTeamId();
        }

        @Test
        @DisplayName("楽奈直接加入公开练习队——野良猫归队")
        void joinPublicTeam() {
            SocialDtos.JoinTeamRequest joinReq = new SocialDtos.JoinTeamRequest();
            joinReq.setMessage("私もやる");

            var result = teamService.joinTeam(publicTeamId, raana.getUserId(), joinReq);

            assertThat(result.getStatus()).isEqualTo(TeamJoinRequestStatus.accepted);
            var member = teamMemberRepository
                    .findByTeamIdAndUserId(publicTeamId, raana.getUserId())
                    .orElseThrow();
            assertThat(member.getRole()).isEqualTo(TeamMemberRole.member);
        }

        @Test
        @DisplayName("楽奈申请加入CRYCHIC再結成——需要灯审核")
        void joinApprovalTeam() {
            SocialDtos.JoinTeamRequest joinReq = new SocialDtos.JoinTeamRequest();
            joinReq.setMessage("CRYCHICに戻りたい");

            var result = teamService.joinTeam(approvalTeamId, raana.getUserId(), joinReq);

            assertThat(result.getStatus()).isEqualTo(TeamJoinRequestStatus.pending);
            assertThat(result.getMessage()).isEqualTo("CRYCHICに戻りたい");
        }

        @Test
        @DisplayName("重复加入应拒绝——你已经在了")
        void joinAlreadyMemberRejected() {
            SocialDtos.JoinTeamRequest joinReq = new SocialDtos.JoinTeamRequest();
            teamService.joinTeam(publicTeamId, raana.getUserId(), joinReq);

            assertThatThrownBy(() -> teamService.joinTeam(publicTeamId, raana.getUserId(), joinReq))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already a team member");
        }
    }

    @Nested
    @DisplayName("退出小队")
    class LeaveTeam {

        private User taki;
        private User anon;
        private String teamId;

        @BeforeEach
        void setUp() {
            taki = createUser("taki@mygo.band", "立希");
            anon = createUser("anon2@mygo.band", "愛音");

            SocialDtos.TeamCreateRequest req = createRequest("MyGO!!!!! 合宿", TeamJoinMode.publicJoin);
            teamId = teamService.createTeam(taki.getUserId(), req).getTeamId();

            SocialDtos.JoinTeamRequest joinReq = new SocialDtos.JoinTeamRequest();
            teamService.joinTeam(teamId, anon.getUserId(), joinReq);
        }

        @Test
        @DisplayName("愛音退出——普通人可以离开")
        void memberLeaveSuccess() {
            teamService.leaveTeam(teamId, anon.getUserId());

            assertThat(teamMemberRepository.findByTeamIdAndUserId(teamId, anon.getUserId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("立希退出应拒绝——鼓手不能擅自离队")
        void leaderLeaveRejected() {
            assertThatThrownBy(() -> teamService.leaveTeam(teamId, taki.getUserId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("leader cannot leave");
        }
    }

    @Nested
    @DisplayName("入队申请审核")
    class JoinRequestManagement {

        private User soyo;
        private User raana;
        private String teamId;

        @BeforeEach
        void setUp() {
            soyo = createUser("soyo@mygo.band", "そよ");
            raana = createUser("raana2@mygo.band", "楽奈");

            SocialDtos.TeamCreateRequest req = createRequest("CRYCHIC 内緒の楽団", TeamJoinMode.approvalRequired);
            teamId = teamService.createTeam(soyo.getUserId(), req).getTeamId();

            SocialDtos.JoinTeamRequest joinReq = new SocialDtos.JoinTeamRequest();
            joinReq.setMessage("ギター、やる");
            teamService.joinTeam(teamId, raana.getUserId(), joinReq);
        }

        @Test
        @DisplayName("そよ查看楽奈的入队申请")
        void listJoinRequests() {
            var result = teamService.listTeamJoinRequests(teamId, soyo.getUserId(), null, 1, 20);
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getUserId()).isEqualTo(raana.getUserId());
        }

        @Test
        @DisplayName("そよ同意楽奈入队——欢迎帰隊")
        void acceptJoinRequest() {
            var requests =
                    teamService.listTeamJoinRequests(teamId, soyo.getUserId(), TeamJoinRequestStatus.pending, 1, 20);
            String requestId = requests.getItems().get(0).getRequestId();

            var result = teamService.decideTeamJoinRequest(teamId, requestId, soyo.getUserId(), true);

            assertThat(result.getStatus()).isEqualTo(TeamJoinRequestStatus.accepted);
            assertThat(teamMemberRepository.findByTeamIdAndUserId(teamId, raana.getUserId()))
                    .isPresent();
        }

        @Test
        @DisplayName("そよ拒绝——このバンド、いらない")
        void rejectJoinRequest() {
            var requests =
                    teamService.listTeamJoinRequests(teamId, soyo.getUserId(), TeamJoinRequestStatus.pending, 1, 20);
            String requestId = requests.getItems().get(0).getRequestId();

            var result = teamService.decideTeamJoinRequest(teamId, requestId, soyo.getUserId(), false);

            assertThat(result.getStatus()).isEqualTo(TeamJoinRequestStatus.rejected);
        }
    }

    @Nested
    @DisplayName("角色管理")
    class RoleManagement {

        private User taki;
        private User anon;
        private String teamId;

        @BeforeEach
        void setUp() {
            taki = createUser("taki2@mygo.band", "立希");
            anon = createUser("anon3@mygo.band", "愛音");

            SocialDtos.TeamCreateRequest req = createRequest("MyGO!!!!! 定期公演", TeamJoinMode.publicJoin);
            teamId = teamService.createTeam(taki.getUserId(), req).getTeamId();

            SocialDtos.JoinTeamRequest joinReq = new SocialDtos.JoinTeamRequest();
            teamService.joinTeam(teamId, anon.getUserId(), joinReq);
        }

        @Test
        @DisplayName("立希提升愛音为副队长（管理员）")
        void promoteToAdmin() {
            var result =
                    teamService.updateTeamMemberRole(teamId, anon.getUserId(), taki.getUserId(), TeamMemberRole.admin);

            assertThat(result.getRole()).isEqualTo(TeamMemberRole.admin);
        }

        @Test
        @DisplayName("立希将队长转让给燈——新しいリーダー")
        void transferLeadership() {
            var result =
                    teamService.updateTeamMemberRole(teamId, anon.getUserId(), taki.getUserId(), TeamMemberRole.leader);

            assertThat(result.getRole()).isEqualTo(TeamMemberRole.leader);

            var oldLeader = teamMemberRepository
                    .findByTeamIdAndUserId(teamId, taki.getUserId())
                    .orElseThrow();
            assertThat(oldLeader.getRole()).isEqualTo(TeamMemberRole.member);
        }

        @Test
        @DisplayName("愛音无权修改角色——只有队长能做决定")
        void nonLeaderChangeRoleRejected() {
            assertThatThrownBy(() -> teamService.updateTeamMemberRole(
                            teamId, anon.getUserId(), anon.getUserId(), TeamMemberRole.admin))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only the team leader");
        }
    }

    @Nested
    @DisplayName("解散小队")
    class DissolveTeam {

        private User tomori;
        private User anon;
        private String teamId;

        @BeforeEach
        void setUp() {
            tomori = createUser("tomori3@mygo.band", "燈");
            anon = createUser("anon4@mygo.band", "愛音");

            SocialDtos.TeamCreateRequest req = createRequest("もう一つのCRYCHIC", TeamJoinMode.publicJoin);
            teamId = teamService.createTeam(tomori.getUserId(), req).getTeamId();
        }

        @Test
        @DisplayName("燈解散小队——自分で終わらせる")
        void dissolveByLeader() {
            teamService.dissolveTeam(teamId, tomori.getUserId());

            var team = teamRepository.findById(teamId).orElseThrow();
            assertThat(team.getStatus()).isEqualTo(TeamStatus.dissolved);
        }

        @Test
        @DisplayName("愛音无权解散——只有创造者才能结束")
        void dissolveByNonLeaderRejected() {
            SocialDtos.JoinTeamRequest joinReq = new SocialDtos.JoinTeamRequest();
            teamService.joinTeam(teamId, anon.getUserId(), joinReq);

            assertThatThrownBy(() -> teamService.dissolveTeam(teamId, anon.getUserId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Only the team leader");
        }
    }

    @Nested
    @DisplayName("积分榜")
    class PointRanks {

        private User tomori;
        private String teamId;

        @BeforeEach
        void setUp() {
            tomori = createUser("tomori4@mygo.band", "燈");

            SocialDtos.TeamCreateRequest req = createRequest("MyGO!!!!! ランキング", TeamJoinMode.publicJoin);
            teamId = teamService.createTeam(tomori.getUserId(), req).getTeamId();
        }

        @Test
        @DisplayName("燈查看积分榜——努力は報われる")
        void getPointRanks() {
            var result = teamService.getTeamPointRanks(teamId, 1, 20);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getRank()).isEqualTo(1);
            assertThat(result.getItems().get(0).getNickname()).isEqualTo("燈");
            assertThat(result.getItems().get(0).getPoints()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("群文件与相册上传")
    class TeamFileAndAlbumUpload {

        private User tomori;
        private User raana;
        private String teamId;

        @BeforeEach
        void setUp() {
            tomori = createUser("tomori5@mygo.band", "燈");
            raana = createUser("raana3@mygo.band", "楽奈");

            SocialDtos.TeamCreateRequest req = createRequest("CRYCHIC ファイル共有", TeamJoinMode.publicJoin);
            teamId = teamService.createTeam(tomori.getUserId(), req).getTeamId();
        }

        @Test
        @DisplayName("燈上传群文件——访问策略更新为 teamMember，签名 URL 包含 group 签名")
        void uploadTeamFileUpdatesAccessPolicy() {
            MediaFile file = persistMediaFile(
                    UUID.randomUUID(), tomori.getUserId(), MediaUsage.teamFile, MediaAccessPolicy.owner);

            var result = teamService.uploadTeamFile(teamId, tomori.getUserId(), file.getMediaId());

            assertThat(result).isNotNull();
            assertThat(result.getSignedUrl()).contains("policy=teamMember");
            assertThat(result.getSignedUrl()).contains("scope=" + teamId);

            var updated = mediaFileRepository.findById(file.getMediaId()).orElseThrow();
            assertThat(updated.getAccessPolicy()).isEqualTo(MediaAccessPolicy.teamMember);
            assertThat(updated.getAccessScopeId()).isEqualTo(teamId);
            assertThat(updated.getAccessVersion()).isEqualTo(2L);
        }

        @Test
        @DisplayName("非成员上传群文件应拒绝")
        void nonMemberUploadTeamFileRejected() {
            MediaFile file = persistMediaFile(
                    UUID.randomUUID(), raana.getUserId(), MediaUsage.teamFile, MediaAccessPolicy.owner);

            assertThatThrownBy(() -> teamService.uploadTeamFile(teamId, raana.getUserId(), file.getMediaId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("membership is required");
        }

        @Test
        @DisplayName("成员引用他人的文件上传应拒绝")
        void uploadTeamFileNotOwnedByMemberRejected() {
            MediaFile file = persistMediaFile(
                    UUID.randomUUID(), raana.getUserId(), MediaUsage.teamFile, MediaAccessPolicy.owner);

            assertThatThrownBy(() -> teamService.uploadTeamFile(teamId, tomori.getUserId(), file.getMediaId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Media file not found");
        }

        @Test
        @DisplayName("燈上传相册图片——访问策略更新为 teamMember，usage 更新为 teamAlbum")
        void uploadTeamAlbumImageUpdatesAccessPolicy() {
            MediaFile file = persistMediaFile(
                    UUID.randomUUID(), tomori.getUserId(), MediaUsage.chatImage, MediaAccessPolicy.owner);

            var result = teamService.uploadTeamAlbumImage(teamId, tomori.getUserId(), file.getMediaId());

            assertThat(result).isNotNull();
            assertThat(result.getSignedUrl()).contains("policy=teamMember");
            assertThat(result.getSignedUrl()).contains("scope=" + teamId);

            var updated = mediaFileRepository.findById(file.getMediaId()).orElseThrow();
            assertThat(updated.getUsage()).isEqualTo(MediaUsage.teamAlbum);
            assertThat(updated.getAccessPolicy()).isEqualTo(MediaAccessPolicy.teamMember);
            assertThat(updated.getAccessScopeId()).isEqualTo(teamId);
            assertThat(updated.getAccessVersion()).isEqualTo(2L);
        }
    }

    private MediaFile persistMediaFile(UUID mediaId, String userId, MediaUsage usage, MediaAccessPolicy policy) {
        MediaFile file = MediaFile.builder()
                .mediaId(mediaId)
                .fileName("test.txt")
                .contentType("text/plain")
                .sizeBytes(100L)
                .usage(usage)
                .storagePath("test/" + mediaId + "_test.txt")
                .accessPolicy(policy)
                .accessScopeId(userId)
                .accessVersion(1L)
                .uploadedBy(userId)
                .uploadedAt(Instant.now())
                .build();
        return mediaFileRepository.save(file);
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

    private SocialDtos.TeamCreateRequest createRequest(String name, TeamJoinMode joinMode) {
        SocialDtos.TeamCreateRequest req = new SocialDtos.TeamCreateRequest();
        req.setName(name);
        req.setTags(List.of("音楽", "バンド"));
        req.setJoinMode(joinMode);
        req.setCapacity(20);
        req.setDescription("一生、バンドをやろう。");
        return req;
    }
}

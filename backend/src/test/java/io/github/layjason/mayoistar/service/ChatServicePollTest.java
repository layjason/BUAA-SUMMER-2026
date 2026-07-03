package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.Team;
import io.github.layjason.mayoistar.entity.social.TeamJoinMode;
import io.github.layjason.mayoistar.entity.social.TeamMember;
import io.github.layjason.mayoistar.entity.social.TeamMemberRole;
import io.github.layjason.mayoistar.entity.social.TeamStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.PollOptionRepository;
import io.github.layjason.mayoistar.repository.PollVoteRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamPollRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatServicePollTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private TeamPollRepository teamPollRepository;

    @Autowired
    private PollOptionRepository pollOptionRepository;

    @Autowired
    private PollVoteRepository pollVoteRepository;

    @Nested
    @DisplayName("创建投票")
    class CreatePoll {

        private User leader;
        private String teamId;

        @BeforeEach
        void setUp() {
            leader = createUser("poll-leader@test.test", "投票队长");

            teamId = UUID.randomUUID().toString();
            Team team = Team.builder()
                    .teamId(teamId)
                    .name("投票小隊")
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
        }

        @Test
        @DisplayName("队长创建投票成功")
        void createPollSuccess() {
            var request = new ChatDtos.TeamPollCreateRequest();
            request.setTitle("周末去哪");
            request.setOptions(List.of("爬山", "聚餐", "看电影"));

            var result = chatService.createPoll(teamId, leader.getUserId(), request);

            assertThat(result.getPollId()).isNotNull();
            assertThat(result.getTitle()).isEqualTo("周末去哪");
            assertThat(result.getOptions()).hasSize(3);
            assertThat(result.getOptions().get(0).getVoteCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("选项少于2个应拒绝")
        void createPollTooFewOptions() {
            var request = new ChatDtos.TeamPollCreateRequest();
            request.setTitle("只有一个选项");
            request.setOptions(List.of("同意"));

            assertThatThrownBy(() -> chatService.createPoll(teamId, leader.getUserId(), request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("At least two");
        }

        @Test
        @DisplayName("非成员创建投票应拒绝")
        void createPollNonMember() {
            User outsider = createUser("outsider-poll@test.test", "外部者");
            var request = new ChatDtos.TeamPollCreateRequest();
            request.setTitle("非法投票");
            request.setOptions(List.of("A", "B"));

            assertThatThrownBy(() -> chatService.createPoll(teamId, outsider.getUserId(), request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("membership");
        }
    }

    @Nested
    @DisplayName("参与投票")
    class VotePoll {

        private User leader;
        private User member;
        private String teamId;
        private String pollId;

        @BeforeEach
        void setUp() {
            leader = createUser("vote-leader@test.test", "投票创立者");
            member = createUser("vote-member@test.test", "投票成员");

            teamId = UUID.randomUUID().toString();
            Team team = Team.builder()
                    .teamId(teamId)
                    .name("投票小队2")
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

            var createReq = new ChatDtos.TeamPollCreateRequest();
            createReq.setTitle("午餐选择");
            createReq.setOptions(List.of("拉面", "寿司"));
            var poll = chatService.createPoll(teamId, leader.getUserId(), createReq);
            pollId = poll.getPollId();
        }

        @Test
        @DisplayName("成员投票成功")
        void votePollSuccess() {
            var options = pollOptionRepository.findByPollId(pollId);
            String optionId = options.get(0).getOptionId();

            var voteReq = new ChatDtos.VotePollRequest();
            voteReq.setOptionId(optionId);

            var result = chatService.votePoll(teamId, pollId, member.getUserId(), voteReq);

            assertThat(result.getOptions().get(0).getVoteCount()).isEqualTo(1);
            assertThat(result.getOptions().get(1).getVoteCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("重复投票覆盖前次选择")
        void votePollOverride() {
            var options = pollOptionRepository.findByPollId(pollId);

            var voteReq1 = new ChatDtos.VotePollRequest();
            voteReq1.setOptionId(options.get(0).getOptionId());
            chatService.votePoll(teamId, pollId, member.getUserId(), voteReq1);

            var voteReq2 = new ChatDtos.VotePollRequest();
            voteReq2.setOptionId(options.get(1).getOptionId());
            var result = chatService.votePoll(teamId, pollId, member.getUserId(), voteReq2);

            assertThat(result.getOptions().get(0).getVoteCount()).isEqualTo(0);
            assertThat(result.getOptions().get(1).getVoteCount()).isEqualTo(1);
        }
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

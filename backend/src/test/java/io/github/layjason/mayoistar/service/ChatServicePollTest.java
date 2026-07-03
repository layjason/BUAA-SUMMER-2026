package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.config.TestSecurityConfiguration;
import io.github.layjason.mayoistar.config.TestStorageConfiguration;
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

    private User taki;
    private User anon;
    private String teamId;
    private String pollId;

    @BeforeEach
    void setUp() {
        taki = createUser("taki@mygo.band", "立希");
        anon = createUser("anon@mygo.band", "愛音");

        teamId = UUID.randomUUID().toString();
        Team team = Team.builder()
                .teamId(teamId)
                .name("MyGO!!!!! 合宿計画")
                .tags(List.of())
                .joinMode(TeamJoinMode.publicJoin)
                .capacity(10)
                .status(TeamStatus.active)
                .creatorId(taki.getUserId())
                .leaderId(taki.getUserId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        teamRepository.save(team);

        teamMemberRepository.save(TeamMember.builder()
                .memberId(UUID.randomUUID().toString())
                .teamId(teamId)
                .userId(taki.getUserId())
                .role(TeamMemberRole.leader)
                .points(0)
                .joinedAt(Instant.now())
                .build());
        teamMemberRepository.save(TeamMember.builder()
                .memberId(UUID.randomUUID().toString())
                .teamId(teamId)
                .userId(anon.getUserId())
                .role(TeamMemberRole.member)
                .points(0)
                .joinedAt(Instant.now())
                .build());

        var createReq = new ChatDtos.TeamPollCreateRequest();
        createReq.setTitle("合宿の行き先");
        createReq.setOptions(List.of("軽井沢", "箱根"));
        var poll = chatService.createPoll(teamId, taki.getUserId(), createReq);
        pollId = poll.getPollId();
    }

    @Test
    @DisplayName("立希创建练习时间投票——みんなで決めよう")
    void createPollSuccess() {
        var request = new ChatDtos.TeamPollCreateRequest();
        request.setTitle("今週の練習はいつ？");
        request.setOptions(List.of("月曜日", "水曜日", "金曜日"));

        var result = chatService.createPoll(teamId, taki.getUserId(), request);

        assertThat(result.getPollId()).isNotNull();
        assertThat(result.getTitle()).isEqualTo("今週の練習はいつ？");
        assertThat(result.getOptions()).hasSize(3);
        assertThat(result.getOptions().get(0).getVoteCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("选项只有一个应拒绝——選択肢は2つ以上必要")
    void createPollTooFewOptions() {
        var request = new ChatDtos.TeamPollCreateRequest();
        request.setTitle("只有一个选项");
        request.setOptions(List.of("同意"));

        assertThatThrownBy(() -> chatService.createPoll(teamId, taki.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("At least two");
    }

    @Test
    @DisplayName("非成员创建投票应拒绝——部外者は口出し無用")
    void createPollNonMember() {
        User outsider = createUser("stranger@mygo.band", "部外者");
        var request = new ChatDtos.TeamPollCreateRequest();
        request.setTitle("部外者の投票");
        request.setOptions(List.of("A", "B"));

        assertThatThrownBy(() -> chatService.createPoll(teamId, outsider.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("membership");
    }

    @Test
    @DisplayName("愛音投票选择軽井沢——合宿楽しみ！")
    void votePollSuccess() {
        var options = pollOptionRepository.findByPollId(pollId);
        String optionId = options.get(0).getOptionId();

        var voteReq = new ChatDtos.VotePollRequest();
        voteReq.setOptionId(optionId);

        var result = chatService.votePoll(teamId, pollId, anon.getUserId(), voteReq);

        assertThat(result.getOptions().get(0).getVoteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("愛音改票——やっぱり箱根の方がいいかも")
    void votePollOverride() {
        var options = pollOptionRepository.findByPollId(pollId);

        var voteReq1 = new ChatDtos.VotePollRequest();
        voteReq1.setOptionId(options.get(0).getOptionId());
        chatService.votePoll(teamId, pollId, anon.getUserId(), voteReq1);

        var voteReq2 = new ChatDtos.VotePollRequest();
        voteReq2.setOptionId(options.get(1).getOptionId());
        var result = chatService.votePoll(teamId, pollId, anon.getUserId(), voteReq2);

        assertThat(result.getOptions().get(0).getVoteCount()).isEqualTo(0);
        assertThat(result.getOptions().get(1).getVoteCount()).isEqualTo(1);
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

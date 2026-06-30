package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.entity.social.FriendRequestSource;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FriendshipSearchIntegrationTest {

    @Autowired
    private FriendshipService friendshipService;

    @Autowired
    private FriendRequestService friendRequestService;

    @Autowired
    private UserRepository userRepository;

    private User tomori;
    private User anon;
    private User rana;

    @BeforeEach
    void setUp() {
        tomori = createUser("tomori@mygo.test", "燈");
        anon = createUser("anon@mygo.test", "愛音");
        rana = createUser("rana@mygo.test", "楽奈");

        makeFriends(tomori, anon);
        makeFriends(tomori, rana);
    }

    @Test
    @DisplayName("按昵称搜索好友应只返回匹配的好友")
    void searchFriendByNickname() {
        var result = friendshipService.listFriends(tomori.getUserId(), 1, 20, "音");

        assertThat(result.getItems()).as("搜索'音'应只返回愛音").hasSize(1);
        assertThat(result.getItems().getFirst().getNickname()).as("搜索结果应为愛音").isEqualTo("愛音");
    }

    @Test
    @DisplayName("不传搜索词应返回所有好友")
    void listAllFriendsWhenNoKeyword() {
        var result = friendshipService.listFriends(tomori.getUserId(), 1, 20, null);

        assertThat(result.getItems()).as("不传搜索词应返回全部好友").hasSize(2);
    }

    @Test
    @DisplayName("搜索不存在的昵称应返回空列表")
    void searchNonExistentNicknameReturnsEmpty() {
        var result = friendshipService.listFriends(tomori.getUserId(), 1, 20, "海鈴");

        assertThat(result.getItems()).as("搜索不存在昵称应返回空").isEmpty();
    }

    private void makeFriends(User userA, User userB) {
        var result = friendRequestService.createFriendRequest(
                userA.getUserId(), userB.getUserId(), FriendRequestSource.profile, "交个朋友");
        friendRequestService.decideFriendRequest(userB.getUserId(), result.getRequestId(), true);
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

package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.config.JwtProperties;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.SecurityToken;
import io.github.layjason.mayoistar.entity.identity.TokenType;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.MerchantProfileRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.SecurityTokenRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PersonalProfileRepository personalProfileRepository;

    @Mock
    private MerchantProfileRepository merchantProfileRepository;

    @Mock
    private SecurityTokenRepository securityTokenRepository;

    @Mock
    private MailService mailService;

    private PasswordEncoder passwordEncoder;

    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        jwtService = new JwtService(
                new JwtProperties("test-secret-key-that-is-at-least-256-bits-long-for-hs256", 3600, 2592000));
        authService = new AuthService(
                userRepository,
                personalProfileRepository,
                merchantProfileRepository,
                securityTokenRepository,
                passwordEncoder,
                jwtService,
                mailService);
    }

    @Nested
    @DisplayName("注册个人用户")
    class RegisterPersonal {

        @Test
        @DisplayName("成功注册并发送激活邮件")
        void shouldRegisterAndSendActivationEmail() {
            var request = new IdentityDtos.PersonalRegisterRequest();
            request.setEmail("test@example.com");
            request.setPassword("password123");
            request.setNickname("testuser");

            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.existsByNickname("testuser")).thenReturn(false);

            authService.registerPersonal(request);

            verify(userRepository).save(any(User.class));
            verify(personalProfileRepository).save(any());
            verify(mailService).sendActivationEmail(anyString(), anyString());
        }

        @Test
        @DisplayName("邮箱已注册时抛出 10001")
        void shouldThrowWhenEmailExists() {
            var request = new IdentityDtos.PersonalRegisterRequest();
            request.setEmail("test@example.com");
            request.setPassword("password123");
            request.setNickname("testuser");

            when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.registerPersonal(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10001);
        }

        @Test
        @DisplayName("昵称已占用时抛出 10002")
        void shouldThrowWhenNicknameExists() {
            var request = new IdentityDtos.PersonalRegisterRequest();
            request.setEmail("test@example.com");
            request.setPassword("password123");
            request.setNickname("testuser");

            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.existsByNickname("testuser")).thenReturn(true);

            assertThatThrownBy(() -> authService.registerPersonal(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10002);
        }
    }

    @Nested
    @DisplayName("登录")
    class Login {

        @Test
        @DisplayName("成功登录返回令牌对")
        void shouldLoginSuccessfully() {
            var request = new IdentityDtos.EmailPasswordRequest();
            request.setEmail("test@example.com");
            request.setPassword("password123");

            User user = User.builder()
                    .userId(UUID.randomUUID().toString())
                    .email("test@example.com")
                    .nickname("testuser")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .kind(UserKind.personal)
                    .accountStatus(AccountStatus.active)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            IdentityDtos.LoginResult result = authService.login(request);

            assertThat(result.getUserId()).isEqualTo(user.getUserId());
            assertThat(result.getKind()).isEqualTo(UserKind.personal);
            assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.active);
            assertThat(result.getTokens().getAccessToken()).isNotBlank();
            assertThat(result.getTokens().getRefreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("密码错误时抛出 10003")
        void shouldThrowOnWrongPassword() {
            var request = new IdentityDtos.EmailPasswordRequest();
            request.setEmail("test@example.com");
            request.setPassword("wrongpassword");

            User user = User.builder()
                    .userId(UUID.randomUUID().toString())
                    .email("test@example.com")
                    .nickname("testuser")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .kind(UserKind.personal)
                    .accountStatus(AccountStatus.active)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10003);
        }

        @Test
        @DisplayName("未激活账号登录时抛出 10004")
        void shouldThrowOnInactiveAccount() {
            var request = new IdentityDtos.EmailPasswordRequest();
            request.setEmail("test@example.com");
            request.setPassword("password123");

            User user = User.builder()
                    .userId(UUID.randomUUID().toString())
                    .email("test@example.com")
                    .nickname("testuser")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .kind(UserKind.personal)
                    .accountStatus(AccountStatus.inactive)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10004);
        }

        @Test
        @DisplayName("封禁账号登录时抛出 10005")
        void shouldThrowOnBannedAccount() {
            var request = new IdentityDtos.EmailPasswordRequest();
            request.setEmail("test@example.com");
            request.setPassword("password123");

            User user = User.builder()
                    .userId(UUID.randomUUID().toString())
                    .email("test@example.com")
                    .nickname("testuser")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .kind(UserKind.personal)
                    .accountStatus(AccountStatus.banned)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10005);
        }
    }

    @Nested
    @DisplayName("账号激活")
    class ActivateAccount {

        @Test
        @DisplayName("成功激活账号")
        void shouldActivateAccount() {
            String userId = UUID.randomUUID().toString();
            String rawToken = JwtService.generateRandomToken();
            String tokenHash = JwtService.hashToken(rawToken);

            User user = User.builder()
                    .userId(userId)
                    .email("test@example.com")
                    .nickname("testuser")
                    .passwordHash("hash")
                    .kind(UserKind.personal)
                    .accountStatus(AccountStatus.inactive)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            SecurityToken token = SecurityToken.builder()
                    .tokenId(UUID.randomUUID().toString())
                    .userId(userId)
                    .tokenHash(tokenHash)
                    .tokenType(TokenType.activation)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .createdAt(Instant.now())
                    .used(false)
                    .revoked(false)
                    .build();

            when(securityTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            authService.activateAccount(rawToken);

            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.active);
            assertThat(user.getActivatedAt()).isNotNull();
            assertThat(token.getUsed()).isTrue();
        }

        @Test
        @DisplayName("无效令牌抛出 10006")
        void shouldThrowOnInvalidToken() {
            String rawToken = JwtService.generateRandomToken();
            String tokenHash = JwtService.hashToken(rawToken);

            when(securityTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.activateAccount(rawToken))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10006);
        }

        @Test
        @DisplayName("已激活账号抛出 10012")
        void shouldThrowOnAlreadyActive() {
            String userId = UUID.randomUUID().toString();
            String rawToken = JwtService.generateRandomToken();
            String tokenHash = JwtService.hashToken(rawToken);

            User user = User.builder()
                    .userId(userId)
                    .email("test@example.com")
                    .nickname("testuser")
                    .passwordHash("hash")
                    .kind(UserKind.personal)
                    .accountStatus(AccountStatus.active)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            SecurityToken token = SecurityToken.builder()
                    .tokenId(UUID.randomUUID().toString())
                    .userId(userId)
                    .tokenHash(tokenHash)
                    .tokenType(TokenType.activation)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .createdAt(Instant.now())
                    .used(false)
                    .revoked(false)
                    .build();

            when(securityTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.activateAccount(rawToken))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(10012);
        }
    }

    @Nested
    @DisplayName("发送密码重置邮件")
    class SendPasswordResetEmail {

        @Test
        @DisplayName("存在用户时发送邮件")
        void shouldSendEmailWhenUserExists() {
            User user = User.builder()
                    .userId(UUID.randomUUID().toString())
                    .email("test@example.com")
                    .nickname("testuser")
                    .passwordHash("hash")
                    .kind(UserKind.personal)
                    .accountStatus(AccountStatus.active)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            authService.sendPasswordResetEmail("test@example.com");

            verify(mailService).sendPasswordResetEmail(anyString(), anyString());
        }

        @Test
        @DisplayName("用户不存在时静默返回")
        void shouldSilentlyReturnWhenUserNotFound() {
            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            authService.sendPasswordResetEmail("nonexistent@example.com");

            verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString());
        }
    }
}

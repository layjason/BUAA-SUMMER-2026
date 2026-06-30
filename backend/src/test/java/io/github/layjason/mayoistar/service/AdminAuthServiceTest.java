package io.github.layjason.mayoistar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.admin.AdminDtos;
import io.github.layjason.mayoistar.config.JwtProperties;
import io.github.layjason.mayoistar.entity.admin.Admin;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.AdminRepository;
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
class AdminAuthServiceTest {

    @Mock
    private AdminRepository adminRepository;

    private PasswordEncoder passwordEncoder;

    private JwtService jwtService;

    private AdminAuthService adminAuthService;

    private final String adminId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        jwtService = new JwtService(
                new JwtProperties("test-secret-key-that-is-at-least-256-bits-long-for-hs256", 3600, 2592000));
        adminAuthService = new AdminAuthService(adminRepository, passwordEncoder, jwtService);
    }

    @Nested
    @DisplayName("管理员登录")
    class Login {

        @Test
        @DisplayName("成功登录返回令牌对")
        void shouldLoginSuccessfully() {
            AdminDtos.AdminLoginRequest request = new AdminDtos.AdminLoginRequest();
            request.setUsername("admin");
            request.setPassword("password123");

            Admin admin = Admin.builder()
                    .adminId(adminId)
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            AdminDtos.AdminLoginResponse result = adminAuthService.login(request);

            assertThat(result.getUserId()).isEqualTo(adminId);
            assertThat(result.getTokens().getAccessToken()).isNotBlank();
            assertThat(result.getTokens().getRefreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("密码错误抛出 60000")
        void shouldThrowOnWrongPassword() {
            AdminDtos.AdminLoginRequest request = new AdminDtos.AdminLoginRequest();
            request.setUsername("admin");
            request.setPassword("wrong");

            Admin admin = Admin.builder()
                    .adminId(adminId)
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> adminAuthService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60000);
        }

        @Test
        @DisplayName("用户名不存在抛出 60000")
        void shouldThrowOnUnknownUsername() {
            AdminDtos.AdminLoginRequest request = new AdminDtos.AdminLoginRequest();
            request.setUsername("unknown");
            request.setPassword("password123");

            when(adminRepository.findByUsername("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminAuthService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60000);
        }
    }

    @Nested
    @DisplayName("修改密码")
    class ChangePassword {

        @Test
        @DisplayName("成功修改密码")
        void shouldChangePassword() {
            Admin admin = Admin.builder()
                    .adminId(adminId)
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("oldpassword"))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));

            adminAuthService.changePassword(adminId, "oldpassword", "newpassword");

            assertThat(passwordEncoder.matches("newpassword", admin.getPasswordHash()))
                    .isTrue();
        }

        @Test
        @DisplayName("旧密码错误抛出 60001")
        void shouldThrowOnWrongOldPassword() {
            Admin admin = Admin.builder()
                    .adminId(adminId)
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("correctpassword"))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> adminAuthService.changePassword(adminId, "wrongpassword", "newpassword"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(60001);
        }
    }
}

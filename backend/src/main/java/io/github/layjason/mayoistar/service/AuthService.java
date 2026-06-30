package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.MerchantProfile;
import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
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
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务。
 *
 * <p>类职责：处理注册、登录、激活、密码管理、令牌刷新和登出等认证流程。
 *
 * <p>不变量：密码只保存加盐哈希，不保存明文；激活/重置令牌仅存哈希。
 */
@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PersonalProfileRepository personalProfileRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final SecurityTokenRepository securityTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;

    /**
     * 邮件发送冷却时间（秒），激活邮件和密码重置邮件共用。
     */
    private static final long EMAIL_COOLDOWN_SECONDS = 60;

    /**
     * 登录失败最大次数，达到后账号被锁定。
     */
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    /**
     * 登录锁定持续时间（秒）。
     */
    private static final long LOGIN_LOCK_DURATION_SECONDS = 900;

    /**
     * 登录失败窗口（秒），窗口外的旧失败不计入锁定判断。
     */
    private static final long LOGIN_ATTEMPT_WINDOW_SECONDS = 900;

    /**
     * @param userRepository             用户数据访问
     * @param personalProfileRepository  个人资料数据访问
     * @param merchantProfileRepository  商家资料数据访问
     * @param securityTokenRepository    令牌数据访问
     * @param passwordEncoder            密码编码器
     * @param jwtService                 JWT 服务
     * @param mailService                邮件服务
     */
    public AuthService(
            UserRepository userRepository,
            PersonalProfileRepository personalProfileRepository,
            MerchantProfileRepository merchantProfileRepository,
            SecurityTokenRepository securityTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            MailService mailService) {
        this.userRepository = userRepository;
        this.personalProfileRepository = personalProfileRepository;
        this.merchantProfileRepository = merchantProfileRepository;
        this.securityTokenRepository = securityTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailService = mailService;
    }

    /**
     * 注册个人用户。
     *
     * <p>前置条件：email 未注册，nickname 全平台唯一。
     *
     * <p>后置条件：创建 User (inactive) + PersonalProfile，生成激活令牌并存储哈希，发送激活邮件。
     *
     * @param request 注册请求
     */
    @Transactional
    public void registerPersonal(IdentityDtos.PersonalRegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(10001, "Email is already registered");
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(10002, "Nickname is unavailable");
        }

        String userId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        User user = User.builder()
                .userId(userId)
                .email(request.getEmail())
                .nickname(request.getNickname())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.inactive)
                .createdAt(now)
                .updatedAt(now)
                .build();
        user = userRepository.save(user);

        PersonalProfile profile = PersonalProfile.builder()
                .user(user)
                .reputationScore(100)
                .updatedAt(now)
                .build();
        personalProfileRepository.save(profile);

        String activationToken = JwtService.generateRandomToken();
        saveToken(userId, activationToken, TokenType.activation);

        mailService.sendActivationEmail(request.getEmail(), activationToken);
        log.info("个人用户注册成功: userId={}, email={}", userId, request.getEmail());
    }

    /**
     * 注册商家用户。
     *
     * <p>前置条件：email 未注册，nickname 全平台唯一。
     *
     * <p>后置条件：创建 User (inactive) + MerchantProfile，生成激活令牌并存储哈希，发送激活邮件。
     *
     * @param request 注册请求
     */
    @Transactional
    public void registerMerchant(IdentityDtos.MerchantRegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(10001, "Email is already registered");
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(10002, "Nickname is unavailable");
        }

        String userId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        User user = User.builder()
                .userId(userId)
                .email(request.getEmail())
                .nickname(request.getNickname())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .kind(UserKind.merchant)
                .accountStatus(AccountStatus.inactive)
                .createdAt(now)
                .updatedAt(now)
                .build();
        user = userRepository.save(user);

        MerchantProfile profile =
                MerchantProfile.builder().user(user).updatedAt(now).build();
        merchantProfileRepository.save(profile);

        String activationToken = JwtService.generateRandomToken();
        saveToken(userId, activationToken, TokenType.activation);

        mailService.sendActivationEmail(request.getEmail(), activationToken);
        log.info("商家用户注册成功: userId={}, email={}", userId, request.getEmail());
    }

    /**
     * 邮箱密码登录。
     *
     * <p>前置条件：email 已注册且账号状态为 active，未被锁定。
     *
     * <p>后置条件：返回 LoginResult（含 userId、kind、accountStatus、tokenPair）。登录成功时重置失败计数与锁定状态；密码错误时递增失败计数。
     *
     * @param request 登录请求
     * @return 登录结果
     */
    @Transactional
    public IdentityDtos.LoginResult login(IdentityDtos.EmailPasswordRequest request) {
        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(10003, "Email or password is invalid"));

        if (isLocked(user)) {
            log.warn("锁定账号尝试登录: userId={}", user.getUserId());
            throw new BusinessException(10003, "Email or password is invalid");
        }

        resetLoginWindowIfExpired(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordLoginFailure(user);
            throw new BusinessException(10003, "Email or password is invalid");
        }

        if (user.getAccountStatus() == AccountStatus.inactive) {
            throw new BusinessException(10004, "Account is inactive");
        }
        if (user.getAccountStatus() == AccountStatus.banned) {
            throw new BusinessException(10005, "Account is banned");
        }

        resetLoginState(user);

        String accessToken = jwtService.generateAccessToken(user.getUserId(), user.getKind());
        String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getKind());
        saveToken(user.getUserId(), refreshToken, TokenType.refresh);

        IdentityDtos.TokenPair tokenPair = new IdentityDtos.TokenPair();
        tokenPair.setAccessToken(accessToken);
        tokenPair.setRefreshToken(refreshToken);
        var claims = jwtService.parseToken(accessToken);
        tokenPair.setExpiresAt(jwtService.getExpiresAt(claims));

        IdentityDtos.LoginResult result = new IdentityDtos.LoginResult();
        result.setUserId(user.getUserId());
        result.setKind(user.getKind());
        result.setAccountStatus(user.getAccountStatus());
        result.setTokens(tokenPair);

        log.info("用户登录成功: userId={}", user.getUserId());
        return result;
    }

    /**
     * 激活账号。
     *
     * <p>前置条件：token 有效、未过期、未使用，且对应用户状态为 inactive。
     *
     * <p>后置条件：用户 accountStatus 变为 active，activatedAt 设为当前时间，token 标记为 used。
     *
     * @param token 激活令牌
     */
    @Transactional
    public void activateAccount(String token) {
        String tokenHash = JwtService.hashToken(token);
        SecurityToken securityToken = securityTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(10006, "Activation token is invalid"));

        if (Boolean.TRUE.equals(securityToken.getUsed())) {
            throw new BusinessException(10006, "Activation token is invalid");
        }
        if (Boolean.TRUE.equals(securityToken.getRevoked())) {
            throw new BusinessException(10006, "Activation token is invalid");
        }
        if (securityToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(10006, "Activation token is invalid");
        }

        User user = userRepository
                .findById(securityToken.getUserId())
                .orElseThrow(() -> new BusinessException(10006, "Activation token is invalid"));
        if (user.getAccountStatus() != AccountStatus.inactive) {
            throw new BusinessException(10012, "Account is already active");
        }

        user.setAccountStatus(AccountStatus.active);
        user.setActivatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        securityToken.setUsed(true);
        securityTokenRepository.save(securityToken);

        log.info("账号激活成功: userId={}", user.getUserId());
    }

    /**
     * 重新发送激活邮件。
     *
     * <p>前置条件：邮箱对应账号存在且状态为 inactive。
     *
     * <p>后置条件：生成新激活令牌，作废旧令牌，发送新激活邮件。若在冷却期内则拒绝发送。
     *
     * @param email 账号邮箱
     */
    @Transactional
    public void resendActivationEmail(String email) {
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new BusinessException(10011, "Email is not registered"));

        if (user.getAccountStatus() == AccountStatus.active) {
            throw new BusinessException(10012, "Account is already active");
        }
        if (user.getAccountStatus() == AccountStatus.banned) {
            throw new BusinessException(10005, "Account is banned");
        }

        checkEmailCooldown(
                user.getUserId(), TokenType.activation, 10015, "Activation email resend rate limit has been exceeded");

        securityTokenRepository.revokeAllByUserIdAndTokenType(user.getUserId(), TokenType.activation);

        String activationToken = JwtService.generateRandomToken();
        saveToken(user.getUserId(), activationToken, TokenType.activation);
        mailService.sendActivationEmail(email, activationToken);

        log.info("激活邮件已重发: userId={}", user.getUserId());
    }

    /**
     * 发送密码重置邮件。
     *
     * <p>前置条件：email 对应的账号存在（不向调用方暴露是否存在）。
     *
     * <p>后置条件：若账号存在且不在冷却期内，生成密码重置令牌并发送邮件；若不存在或在冷却期内，静默返回。
     *
     * @param email 账号邮箱
     */
    @Transactional
    public void sendPasswordResetEmail(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getAccountStatus() == AccountStatus.banned) {
                return;
            }
            if (isEmailCooldownActive(user.getUserId(), TokenType.password_reset)) {
                log.info("密码重置邮件在冷却期内，静默返回: userId={}", user.getUserId());
                return;
            }
            securityTokenRepository.revokeAllByUserIdAndTokenType(user.getUserId(), TokenType.password_reset);
            String resetToken = JwtService.generateRandomToken();
            saveToken(user.getUserId(), resetToken, TokenType.password_reset);
            mailService.sendPasswordResetEmail(email, resetToken);
            log.info("密码重置邮件已发送: userId={}", user.getUserId());
        });
        // 无论邮箱是否存在，均返回成功（避免信息泄露）
    }

    /**
     * 通过令牌重置密码。
     *
     * <p>前置条件：token 有效、未过期、未使用、未吊销，对应账号未被封禁。
     *
     * <p>后置条件：密码更新为新密码的哈希，旧刷新令牌全部吊销，重置令牌标记为 used。
     *
     * @param token       密码重置令牌
     * @param newPassword 新密码
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String tokenHash = JwtService.hashToken(token);
        SecurityToken securityToken = securityTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(10017, "Password reset token is invalid"));

        if (Boolean.TRUE.equals(securityToken.getUsed())) {
            throw new BusinessException(10017, "Password reset token is invalid");
        }
        if (Boolean.TRUE.equals(securityToken.getRevoked())) {
            throw new BusinessException(10017, "Password reset token is invalid");
        }
        if (securityToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(10017, "Password reset token is invalid");
        }

        User user = userRepository
                .findById(securityToken.getUserId())
                .orElseThrow(() -> new BusinessException(10017, "Password reset token is invalid"));
        if (user.getAccountStatus() == AccountStatus.banned) {
            throw new BusinessException(10005, "Account is banned");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        securityToken.setUsed(true);
        securityTokenRepository.save(securityToken);

        securityTokenRepository.revokeAllByUserIdAndTokenType(user.getUserId(), TokenType.refresh);

        log.info("密码重置成功: userId={}", user.getUserId());
    }

    /**
     * 修改密码（已登录用户）。
     *
     * <p>前置条件：userId 对应用户未被封禁，oldPassword 正确。
     *
     * <p>后置条件：密码更新为新密码的哈希，旧刷新令牌全部吊销。
     *
     * @param userId      当前用户 ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    @Transactional
    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(10003, "Email or password is invalid"));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(10016, "Old password is invalid");
        }
        if (user.getAccountStatus() == AccountStatus.banned) {
            throw new BusinessException(10005, "Account is banned");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        securityTokenRepository.revokeAllByUserIdAndTokenType(userId, TokenType.refresh);

        log.info("密码修改成功: userId={}", userId);
    }

    /**
     * 刷新访问令牌。
     *
     * <p>前置条件：refreshToken 有效、对应用户未被封禁。
     *
     * <p>后置条件：返回新的 TokenPair，旧刷新令牌吊销。
     *
     * @param refreshToken 刷新令牌
     * @return 新令牌对
     */
    @Transactional
    public IdentityDtos.TokenPair refreshToken(String refreshToken) {
        var claims = jwtService.parseToken(refreshToken);
        if (claims == null || !"refresh".equals(claims.get("type", String.class))) {
            throw new BusinessException(10007, "Refresh token is invalid");
        }

        String userId = jwtService.getUserId(claims);
        UserKind kind = jwtService.getUserKind(claims);

        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(10007, "Refresh token is invalid"));
        if (user.getAccountStatus() == AccountStatus.banned) {
            throw new BusinessException(10005, "Account is banned");
        }

        String tokenHash = JwtService.hashToken(refreshToken);
        SecurityToken securityToken = securityTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(10007, "Refresh token is invalid"));

        if (Boolean.TRUE.equals(securityToken.getUsed())) {
            throw new BusinessException(10007, "Refresh token is invalid");
        }
        if (Boolean.TRUE.equals(securityToken.getRevoked())) {
            throw new BusinessException(10007, "Refresh token is invalid");
        }
        if (securityToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(10007, "Refresh token is invalid");
        }

        securityTokenRepository.revokeAllByUserIdAndTokenType(userId, TokenType.refresh);

        String newAccessToken = jwtService.generateAccessToken(userId, kind);
        String newRefreshToken = jwtService.generateRefreshToken(userId, kind);
        saveToken(userId, newRefreshToken, TokenType.refresh);

        IdentityDtos.TokenPair tokenPair = new IdentityDtos.TokenPair();
        tokenPair.setAccessToken(newAccessToken);
        tokenPair.setRefreshToken(newRefreshToken);
        var accessClaims = jwtService.parseToken(newAccessToken);
        tokenPair.setExpiresAt(jwtService.getExpiresAt(accessClaims));

        log.info("令牌刷新成功: userId={}", userId);
        return tokenPair;
    }

    /**
     * 登出。
     *
     * <p>前置条件：userId 对应有效用户。
     *
     * <p>后置条件：该用户所有 refresh 令牌被吊销。
     *
     * @param userId 用户 ID
     */
    @Transactional
    public void logout(String userId) {
        securityTokenRepository.revokeAllByUserIdAndTokenType(userId, TokenType.refresh);
        log.info("用户登出成功: userId={}", userId);
    }

    /**
     * 检查邮件发送是否在冷却期内，若在冷却期内则抛出指定业务异常。
     *
     * <p>前置条件：userId、tokenType、errorCode、errorMessage 均非空。
     *
     * <p>后置条件：若最近一次该类型令牌在冷却期内创建，抛出 BusinessException。
     *
     * @param userId       用户 ID
     * @param tokenType    令牌类型
     * @param errorCode    限流错误码
     * @param errorMessage 限流错误消息
     */
    private void checkEmailCooldown(String userId, TokenType tokenType, int errorCode, String errorMessage) {
        securityTokenRepository
                .findFirstByUserIdAndTokenTypeOrderByCreatedAtDesc(userId, tokenType)
                .ifPresent(lastToken -> {
                    if (lastToken
                            .getCreatedAt()
                            .plusSeconds(EMAIL_COOLDOWN_SECONDS)
                            .isAfter(Instant.now())) {
                        throw new BusinessException(errorCode, errorMessage);
                    }
                });
    }

    /**
     * 检查邮件发送是否在冷却期内。
     *
     * <p>前置条件：userId 和 tokenType 非空。
     *
     * <p>后置条件：返回 true 表示处于冷却期内。
     *
     * @param userId    用户 ID
     * @param tokenType 令牌类型
     * @return 是否在冷却期内
     */
    private boolean isEmailCooldownActive(String userId, TokenType tokenType) {
        return securityTokenRepository
                .findFirstByUserIdAndTokenTypeOrderByCreatedAtDesc(userId, tokenType)
                .map(lastToken -> lastToken
                        .getCreatedAt()
                        .plusSeconds(EMAIL_COOLDOWN_SECONDS)
                        .isAfter(Instant.now()))
                .orElse(false);
    }

    /**
     * 检查账号是否处于锁定状态。
     *
     * <p>前置条件：user 非空。
     *
     * <p>后置条件：返回 true 表示账号当前被锁定。
     *
     * @param user 用户实体
     * @return 是否锁定
     */
    private boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }

    /**
     * 若失败窗口已过期，重置登录失败计数。
     *
     * <p>前置条件：user 非空。
     *
     * <p>后置条件：若 lastFailedLoginAt + 窗口 < now，则 loginAttempts 重置为 0。
     *
     * @param user 用户实体
     */
    private void resetLoginWindowIfExpired(User user) {
        if (user.getLastFailedLoginAt() != null
                && user.getLastFailedLoginAt()
                        .plusSeconds(LOGIN_ATTEMPT_WINDOW_SECONDS)
                        .isBefore(Instant.now())) {
            user.setLoginAttempts(0);
        }
    }

    /**
     * 记录一次登录失败，达到上限时锁定账号。
     *
     * <p>前置条件：user 非空，密码不匹配。
     *
     * <p>后置条件：loginAttempts + 1，若达到上限则设置 lockedUntil。
     *
     * @param user 用户实体
     */
    private void recordLoginFailure(User user) {
        user.setLoginAttempts(user.getLoginAttempts() + 1);
        user.setLastFailedLoginAt(Instant.now());
        if (user.getLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plusSeconds(LOGIN_LOCK_DURATION_SECONDS));
            log.warn("账号已被锁定: userId={}", user.getUserId());
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    /**
     * 登录成功后重置失败计数与锁定状态。
     *
     * <p>前置条件：user 非空，登录已验证通过。
     *
     * <p>后置条件：loginAttempts 重置为 0，lockedUntil 与 lastFailedLoginAt 置为 null。
     *
     * @param user 用户实体
     */
    private void resetLoginState(User user) {
        user.setLoginAttempts(0);
        user.setLastFailedLoginAt(null);
        user.setLockedUntil(null);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    /**
     * 存储令牌的加盐哈希。
     *
     * <p>前置条件：userId、rawToken、tokenType 均非空。
     *
     * <p>后置条件：数据库中存在一条 SecurityToken 记录，其中 tokenHash = SHA-256(rawToken)。
     *
     * @param userId    关联用户 ID
     * @param rawToken  原始令牌
     * @param tokenType 令牌类型
     */
    private void saveToken(String userId, String rawToken, TokenType tokenType) {
        long expireSeconds = tokenType == TokenType.refresh
                ? jwtService.getRefreshTokenExpireSeconds()
                : 86400L; // activation / password_reset 默认 24 小时

        SecurityToken token = SecurityToken.builder()
                .tokenId(UUID.randomUUID().toString())
                .userId(userId)
                .tokenHash(JwtService.hashToken(rawToken))
                .tokenType(tokenType)
                .expiresAt(Instant.now().plusSeconds(expireSeconds))
                .createdAt(Instant.now())
                .used(false)
                .revoked(false)
                .build();
        securityTokenRepository.save(token);
    }
}

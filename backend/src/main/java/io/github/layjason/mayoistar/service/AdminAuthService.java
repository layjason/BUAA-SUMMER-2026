package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.admin.AdminDtos;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.entity.admin.Admin;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.AdminRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员认证服务。
 *
 * <p>类职责：处理管理员登录、密码修改。管理员账号由系统预置，不提供注册。
 *
 * <p>不变量：密码只保存加盐哈希，不保存明文。
 */
@Slf4j
@Service
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * @param adminRepository 管理员数据访问
     * @param passwordEncoder 密码编码器
     * @param jwtService      JWT 服务
     */
    public AdminAuthService(AdminRepository adminRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 管理员用户名密码登录。
     *
     * <p>前置条件：username 存在且密码匹配。
     *
     * <p>后置条件：返回 AdminLoginResponse（含 adminId、tokenPair）。
     *
     * @param request 登录请求
     * @return 登录结果
     */
    @Transactional(readOnly = true)
    public AdminDtos.AdminLoginResponse login(AdminDtos.AdminLoginRequest request) {
        Admin admin = adminRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(60000, "Admin username or password is invalid"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new BusinessException(60000, "Admin username or password is invalid");
        }

        String accessToken = jwtService.generateAdminAccessToken(admin.getAdminId());
        String refreshToken = jwtService.generateAdminRefreshToken(admin.getAdminId());

        IdentityDtos.TokenPair tokenPair = new IdentityDtos.TokenPair();
        tokenPair.setAccessToken(accessToken);
        tokenPair.setRefreshToken(refreshToken);
        var claims = jwtService.parseToken(accessToken);
        tokenPair.setExpiresAt(jwtService.getExpiresAt(claims));

        AdminDtos.AdminLoginResponse result = new AdminDtos.AdminLoginResponse();
        result.setUserId(admin.getAdminId());
        result.setTokens(tokenPair);

        log.info("管理员登录成功: adminId={}, username={}", admin.getAdminId(), admin.getUsername());
        return result;
    }

    /**
     * 管理员修改密码。
     *
     * <p>前置条件：adminId 对应用户存在，oldPassword 正确。
     *
     * <p>后置条件：密码更新为新哈希。
     *
     * @param adminId     管理员 ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    @Transactional
    public void changePassword(String adminId, String oldPassword, String newPassword) {
        Admin admin =
                adminRepository.findById(adminId).orElseThrow(() -> new BusinessException(401, "Admin not found"));

        if (!passwordEncoder.matches(oldPassword, admin.getPasswordHash())) {
            throw new BusinessException(60001, "Old password is invalid");
        }

        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        adminRepository.save(admin);

        log.info("管理员密码修改成功: adminId={}", adminId);
    }
}

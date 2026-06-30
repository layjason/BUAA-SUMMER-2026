package io.github.layjason.mayoistar.config;

import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器。
 *
 * <p>类职责：从请求头 Authorization: Bearer <token> 中提取 JWT，校验并设置 SecurityContext。
 *
 * <p>不变量：校验失败的请求不清空已有的 SecurityContext（允许匿名访问公共端点）。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    /**
     * 过滤请求，提取并校验 JWT。
     *
     * <p>前置条件：请求可能携带或不携带 Authorization 头。
     *
     * <p>后置条件：若 token 有效，设置 SecurityContext 中的认证信息；若无效或无 token，继续过滤器链（匿名访问）。
     *
     * @param request      HTTP 请求
     * @param response     HTTP 响应
     * @param filterChain  过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = jwtService.parseToken(token);
            if (claims != null) {
                String userId = jwtService.getUserId(claims);

                if (jwtService.isAdmin(claims)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_admin")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    UserKind kind = jwtService.getUserKind(claims);
                    if (kind != null) {
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + kind.name())));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}

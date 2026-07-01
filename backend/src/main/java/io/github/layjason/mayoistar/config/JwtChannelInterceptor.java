package io.github.layjason.mayoistar.config;

import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.service.JwtService;
import io.jsonwebtoken.Claims;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * STOMP 连接鉴权拦截器，从 STOMP CONNECT 帧的 Authorization 头中提取并校验 JWT。
 *
 * <p>类职责：在 STOMP CONNECT 阶段校验 Bearer Token，设置 simpUser 主身份。
 *
 * <p>不变量：校验失败时拒绝连接，校验成功时 principal 为 userId 字符串。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    /**
     * 拦截 STOMP CONNECT 帧，校验 Bearer Token。
     *
     * <p>前置条件：CONNECT 帧携带 Authorization: Bearer <token> 头。
     *
     * <p>后置条件：校验通过则设置 simpUser 为 authenticated principal；失败则拒绝连接。
     *
     * @param message STOMP 消息
     * @param channel 消息通道
     * @return 处理后的消息
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            log.warn("STOMP CONNECT 缺少 Authorization 头");
            throw new org.springframework.messaging.simp.stomp.StompConversionException(
                    "Authorization header is required");
        }

        String authHeader = authHeaders.getFirst();
        if (!authHeader.startsWith("Bearer ")) {
            log.warn(
                    "STOMP CONNECT Authorization 格式无效: {}", authHeader.substring(0, Math.min(10, authHeader.length())));
            throw new org.springframework.messaging.simp.stomp.StompConversionException("Bearer token is required");
        }

        String token = authHeader.substring(7);
        Claims claims = jwtService.parseToken(token);
        if (claims == null) {
            log.warn("STOMP CONNECT JWT 校验失败");
            throw new org.springframework.messaging.simp.stomp.StompConversionException("Invalid token");
        }

        String userId = jwtService.getUserId(claims);
        Principal principal;
        if (jwtService.isAdmin(claims)) {
            principal = new UsernamePasswordAuthenticationToken(
                    userId, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_admin")));
        } else {
            UserKind kind = jwtService.getUserKind(claims);
            if (kind == null) {
                log.warn("STOMP CONNECT JWT 缺少用户类型: userId={}", userId);
                throw new org.springframework.messaging.simp.stomp.StompConversionException(
                        "Invalid token: missing user kind");
            }
            principal = new UsernamePasswordAuthenticationToken(
                    userId, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + kind.name())));
        }

        accessor.setUser(principal);
        log.debug("STOMP 连接认证成功: userId={}", userId);
        return message;
    }
}

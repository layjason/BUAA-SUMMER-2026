package io.github.layjason.mayoistar.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket / STOMP 消息代理配置。
 *
 * <p>类职责：启用 STOMP over WebSocket，注册端点并配置简单消息代理。
 *
 * <p>不变量：客户端通过 /chat/ws/messages 端点发起 WebSocket 升级，
 * 使用 Bearer Token 完成鉴权，服务端向 /user 目标推送一对一事件。
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Profile("!test")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    /**
     * 注册 STOMP 端点，允许任意来源接入。
     *
     * <p>前置条件：Spring WebSocket 已自动配置。
     *
     * <p>后置条件：暴露 /chat/ws/messages SockJS 端点。
     *
     * @param registry STOMP 端点注册器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/chat/ws/messages").setAllowedOriginPatterns("*");
    }

    /**
     * 配置消息代理：/topic 用于广播，/queue 用于点对点（经 /user 前缀路由）。
     *
     * <p>前置条件：STOMP 端点已注册。
     *
     * <p>后置条件：外部代理启用 /topic + /queue，/app 为应用路由前缀。
     *
     * @param registry 消息代理注册器
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * 配置客户端入站通道拦截器，注入 JWT 鉴权拦截器。
     *
     * <p>前置条件：JwtChannelInterceptor Bean 存在。
     *
     * <p>后置条件：每个 STOMP CONNECT 帧均经过 JWT 校验。
     *
     * @param registration 通道注册器
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}

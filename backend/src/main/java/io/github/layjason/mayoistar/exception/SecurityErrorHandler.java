package io.github.layjason.mayoistar.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.api.common.ApiErrorResponse;
import io.github.layjason.mayoistar.api.common.EmptyData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Spring Security 认证/授权失败的统一错误处理器。
 *
 * <p>类职责：将 Spring Security 过滤器链中抛出的认证失败和授权失败转换为符合 API 约定的错误响应。
 *
 * <p>不变量：所有安全错误响应均返回恰当的 HTTP 状态码（401/403），响应体使用 ApiErrorResponse 格式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * 处理未认证请求。
     *
     * <p>前置条件：请求未携带有效认证凭据，Spring Security 过滤器链触发 AuthenticationException。
     *
     * <p>后置条件：返回 HTTP 401，响应体为 code=401、message="Authentication is required" 的 ApiErrorResponse。
     *
     * @param request       HTTP 请求
     * @param response      HTTP 响应
     * @param authException 认证异常
     * @throws IOException 写入响应体时 IO 异常
     */
    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        log.warn("未认证访问: {} {}，异常: {}", request.getMethod(), request.getRequestURI(), authException.getMessage());
        writeErrorResponse(response, 401, "Authentication is required");
    }

    /**
     * 处理已认证但无权限的请求。
     *
     * <p>前置条件：请求已通过认证但用户角色或权限不足以访问目标资源。
     *
     * <p>后置条件：返回 HTTP 403，响应体为 code=403、message="Permission is denied" 的 ApiErrorResponse。
     *
     * @param request               HTTP 请求
     * @param response              HTTP 响应
     * @param accessDeniedException 授权异常
     * @throws IOException 写入响应体时 IO 异常
     */
    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.warn(
                "无权限访问: {} {}，异常: {}",
                request.getMethod(),
                request.getRequestURI(),
                accessDeniedException.getMessage());
        writeErrorResponse(response, 403, "Permission is denied");
    }

    /**
     * 向 HTTP 响应写入统一格式的 API 错误响应。
     *
     * <p>前置条件：status 为合理的 HTTP 错误状态码，message 为非空错误消息。
     *
     * <p>后置条件：response 已设置状态码、Content-Type 和 JSON 响应体。
     *
     * <p>不变量：不修改 request 状态。
     *
     * @param response HTTP 响应
     * @param status   HTTP 状态码
     * @param message  错误消息
     * @throws IOException 写入响应体时 IO 异常
     */
    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        ApiErrorResponse<EmptyData> body = new ApiErrorResponse<>(status, message, new EmptyData());
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

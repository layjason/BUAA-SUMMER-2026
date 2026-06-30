package io.github.layjason.mayoistar.service.activities;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 解析当前请求的调用者标识。
 *
 * <p>类职责：在认证体系尚未接入前，从请求头中提取活动草稿接口使用的调用者 ID，避免业务服务直接依赖
 * Servlet API。
 *
 * <p>类不变量：仅解析请求上下文中的只读信息，不创建用户、不持久化任何状态。
 */
@Component
public class RequestActorResolver {

    public static final String USER_ID_HEADER = "X-MayoiStar-User-Id";

    /**
     * 读取当前请求头中的调用者 ID。
     *
     * <p>前置条件：调用发生在 HTTP 请求线程内；若未处于 Web 请求上下文则返回空。
     *
     * <p>后置条件：当请求头包含非空白的用户 ID 时返回该值，否则返回空。
     *
     * <p>不变量：该方法只做字符串解析，不进行认证、授权或数据库访问。
     *
     * @return 当前请求的调用者 ID
     */
    public Optional<String> resolveCurrentUserId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return Optional.empty();
        }
        String userId = servletRequestAttributes.getRequest().getHeader(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(userId);
    }
}

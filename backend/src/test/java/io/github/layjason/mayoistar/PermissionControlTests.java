package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 权限控制集成测试。
 *
 * <p>类职责：验证路由级（SecurityFilterChain）和方法级（@PreAuthorize）权限控制正确执行。
 *
 * <p>不变量：不测试 Service 层业务逻辑，仅验证 HTTP 层面的角色访问控制。
 */
@AutoConfigureMockMvc
@DisplayName("权限控制")
class PermissionControlTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("公开端点")
    class PublicEndpoints {

        @Test
        @DisplayName("无认证访问公开端点应返回 200")
        void publicEndpointWithoutAuth_shouldSucceed() throws Exception {
            mockMvc.perform(get("/identity/interest-tags")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("管理员登录接口无认证可访问，缺少请求体时返回 HTTP 200 + 业务码 400")
        void adminLoginWithoutAuth_shouldSucceed() throws Exception {
            mockMvc.perform(post("/admin/auth/login")
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(result -> {
                        String body = result.getResponse().getContentAsString();
                        assert body.contains("\"code\":400") || body.contains("\"code\": 400");
                    });
        }

        @Test
        @DisplayName("错误端点无认证不应被安全链二次改写为 401")
        void errorEndpointWithoutAuth_shouldNotBeInterceptedBySecurity() throws Exception {
            mockMvc.perform(get("/error"))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .isNotEqualTo(401));
        }
    }

    @Nested
    @DisplayName("管理员端点")
    class AdminEndpoints {

        @Test
        @DisplayName("个人用户访问管理员端点应返回 403")
        @WithMockUser(
                username = "personal-user",
                roles = {"personal"})
        void adminEndpointWithPersonalRole_shouldBeForbidden() throws Exception {
            mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("商家用户访问管理员端点应返回 403")
        @WithMockUser(
                username = "merchant-user",
                roles = {"merchant"})
        void adminEndpointWithMerchantRole_shouldBeForbidden() throws Exception {
            mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("管理员访问管理员端点应返回非 403")
        @WithMockUser(
                username = "admin-user",
                roles = {"admin"})
        void adminEndpointWithAdminRole_shouldNotBeForbidden() throws Exception {
            mockMvc.perform(get("/admin/users")).andExpect(status().is2xxSuccessful());
        }
    }

    @Nested
    @DisplayName("管理员端点 — 方法级注解")
    class AdminMethodSecurity {

        @Test
        @DisplayName("个人用户访问管理员端点（路由+方法级双重校验）应返回 403")
        @WithMockUser(
                username = "personal-user",
                roles = {"personal"})
        void adminEndpointWithMethodSecurity_shouldBeForbidden() throws Exception {
            mockMvc.perform(get("/admin/teams")).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("商家端点")
    class MerchantEndpoints {

        @Test
        @DisplayName("个人用户访问商家专属端点应返回 403")
        @WithMockUser(
                username = "personal-user",
                roles = {"personal"})
        void merchantEndpointWithPersonalRole_shouldBeForbidden() throws Exception {
            mockMvc.perform(get("/identity/me/merchant-profile")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("个人用户访问营业执照上传应返回 403")
        @WithMockUser(
                username = "personal-user",
                roles = {"personal"})
        void merchantLicenseWithPersonalRole_shouldBeForbidden() throws Exception {
            mockMvc.perform(post("/identity/media/license")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("管理员访问商家端点应返回 403")
        @WithMockUser(
                username = "admin-user",
                roles = {"admin"})
        void merchantEndpointWithAdminRole_shouldBeForbidden() throws Exception {
            mockMvc.perform(get("/identity/me/merchant-profile")).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("认证用户共用端点")
    class AuthenticatedEndpoints {

        @Test
        @DisplayName("个人用户访问活动接口应返回非 403")
        @WithMockUser(
                username = "personal-user",
                roles = {"personal"})
        void activityEndpointWithPersonalRole_shouldNotBeForbidden() throws Exception {
            mockMvc.perform(get("/activities/feed")).andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("商家用户访问活动接口应返回非 403")
        @WithMockUser(
                username = "merchant-user",
                roles = {"merchant"})
        void activityEndpointWithMerchantRole_shouldNotBeForbidden() throws Exception {
            mockMvc.perform(get("/activities/feed")).andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("个人用户访问社交接口应返回非 403")
        @WithMockUser(
                username = "personal-user",
                roles = {"personal"})
        void socialEndpointWithPersonalRole_shouldNotBeForbidden() throws Exception {
            mockMvc.perform(get("/social/teams")).andExpect(status().is2xxSuccessful());
        }

        @Test
        @DisplayName("商家用户访问社交接口应返回非 403")
        @WithMockUser(
                username = "merchant-user",
                roles = {"merchant"})
        void socialEndpointWithMerchantRole_shouldNotBeForbidden() throws Exception {
            mockMvc.perform(get("/social/teams")).andExpect(status().is2xxSuccessful());
        }
    }
}

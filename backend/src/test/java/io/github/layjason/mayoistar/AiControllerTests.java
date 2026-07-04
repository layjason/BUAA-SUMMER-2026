package io.github.layjason.mayoistar;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.layjason.mayoistar.config.TestSecurityConfiguration;
import io.github.layjason.mayoistar.config.TestStorageConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestSecurityConfiguration.class, TestStorageConfiguration.class})
class AiControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generateActivityPlanShouldReturnFailedWhenProviderNotConfigured() throws Exception {
        mockMvc.perform(post("/ai/activity-plans")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "周末羽毛球",
                                  "activityType": "运动",
                                  "city": "北京",
                                  "expectedParticipants": 12,
                                  "additionalRequirements": "适合新手"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.tags.length()").value(0))
                .andExpect(jsonPath("$.data.friendlyErrorMessage").value("AI 活动策划接口尚未配置"));
    }

    @Test
    void generateActivityPlanShouldRejectBlankTopic() throws Exception {
        mockMvc.perform(post("/ai/activity-plans")
                        .with(SecurityMockMvcRequestPostProcessors.user("user-a")
                                .roles("personal"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}

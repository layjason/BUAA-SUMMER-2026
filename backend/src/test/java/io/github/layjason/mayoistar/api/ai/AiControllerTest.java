package io.github.layjason.mayoistar.api.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import io.github.layjason.mayoistar.common.SecurityUtils;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.service.ai.ImageClassificationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AiControllerTest {

    @Test
    void getClassifyTaskResultShouldReportUnavailableWhenServiceMissing() {
        ObjectProvider<ImageClassificationService> imageClassificationServiceProvider = new ObjectProvider<>() {
            @Override
            public ImageClassificationService getObject() {
                return null;
            }

            @Override
            public ImageClassificationService getIfAvailable() {
                return null;
            }
        };
        AiController controller = new AiController(
                new DefaultApiResponseFactory(), imageClassificationServiceProvider, mock(SecurityUtils.class));

        assertThatThrownBy(() -> controller.getClassifyTaskResult(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCodes.AI_SERVICE_UNAVAILABLE);
    }
}

package io.github.layjason.mayoistar.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.config.MailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MailProperties mailProperties;

    @Mock
    private TemplateEngine templateEngine;

    private MailService mailService;

    private static final String ACTIVATION_BASE = "http://localhost:3005/activate";
    private static final String RESET_BASE = "http://localhost:3005/reset-password";
    private static final String SENDER = "noreply@mayoistar.example.com";
    private static final String TO = "user@example.com";
    private static final String TOKEN = "test-token";
    private static final String DUMMY_HTML = "<html><body>test</body></html>";

    @BeforeEach
    void setUp() {
        when(mailProperties.activationLinkBase()).thenReturn(ACTIVATION_BASE);
        when(mailProperties.passwordResetLinkBase()).thenReturn(RESET_BASE);
        when(mailProperties.sender()).thenReturn(SENDER);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        when(templateEngine.process(any(String.class), any(Context.class))).thenReturn(DUMMY_HTML);

        mailService = new MailService(mailSender, mailProperties, templateEngine);
    }

    @Test
    @DisplayName("发送激活邮件时调用 TemplateEngine 渲染模板并发送")
    void shouldSendActivationEmail() {
        mailService.sendActivationEmail(TO, TOKEN);

        verify(templateEngine).process(eq("mail/activation-email"), any(Context.class));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("发送密码重置邮件时调用 TemplateEngine 渲染模板并发送")
    void shouldSendPasswordResetEmail() {
        mailService.sendPasswordResetEmail(TO, TOKEN);

        verify(templateEngine).process(eq("mail/password-reset-email"), any(Context.class));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("SMTP 发送失败时不影响注册或重置流程")
    void shouldNotThrowWhenSmtpSendFails() {
        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> mailService.sendActivationEmail(TO, TOKEN));
    }
}

package io.github.layjason.mayoistar.service;

import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MailProperties mailProperties;

    private MailService mailService;

    private static final String ACTIVATION_BASE = "http://localhost:5173/activate";
    private static final String RESET_BASE = "http://localhost:5173/reset-password";
    private static final String SENDER = "noreply@mayoistar.example.com";
    private static final String TO = "user@example.com";
    private static final String TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        when(mailProperties.activationLinkBase()).thenReturn(ACTIVATION_BASE);
        when(mailProperties.passwordResetLinkBase()).thenReturn(RESET_BASE);
        when(mailProperties.sender()).thenReturn(SENDER);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        mailService = new MailService(mailSender, mailProperties);
    }

    @Test
    @DisplayName("发送激活邮件时调用 JavaMailSender.send")
    void shouldSendActivationEmail() {
        mailService.sendActivationEmail(TO, TOKEN);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("发送密码重置邮件时调用 JavaMailSender.send")
    void shouldSendPasswordResetEmail() {
        mailService.sendPasswordResetEmail(TO, TOKEN);

        verify(mailSender).send(any(MimeMessage.class));
    }
}

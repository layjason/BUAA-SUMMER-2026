package io.github.layjason.mayoistar.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 测试环境邮件模拟配置。
 *
 * <p>类职责：在 test profile 中提供模拟 JavaMailSender，避免测试中发送真实邮件。
 *
 * <p>不变量：仅在 test profile 生效，Mockito 模拟的发送器不执行任何网络操作。
 */
@Configuration
@Profile("test")
public class TestMailConfiguration {

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = mock(MimeMessage.class);
        when(sender.createMimeMessage()).thenReturn(message);
        return sender;
    }
}

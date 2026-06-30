package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件服务。
 *
 * <p>类职责：发送激活邮件和密码重置邮件。凭据通过 Spring Boot 邮件自动配置从环境变量注入。
 *
 * <p>不变量：不记录邮件内容，不暴露 SMTP 凭据。
 */
@Slf4j
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    /**
     * @param mailSender     Spring Boot 自动配置的邮件发送器
     * @param mailProperties 邮件配置属性
     */
    public MailService(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    /**
     * 发送账号激活邮件。
     *
     * <p>前置条件：to 为有效的邮箱地址，activationToken 为由 {@link JwtService#generateRandomToken} 生成的随机令牌。
     *
     * <p>后置条件：邮件已提交到 SMTP 服务器。若发送失败，记录日志但不抛异常。
     *
     * @param to              收件邮箱
     * @param activationToken 激活令牌
     */
    public void sendActivationEmail(String to, String activationToken) {
        String link = mailProperties.activationLinkBase() + "?token=" + activationToken;
        String subject = "激活您的迷星群聚账号";
        String body = """
                <p>感谢您注册迷星群聚！</p>
                <p>请点击以下链接激活您的账号：</p>
                <p><a href="{{LINK}}">{{LINK}}</a></p>
                <p>此链接仅可使用一次，请勿转发给他人。</p>
                """.replace("{{LINK}}", link);

        sendHtmlEmail(to, subject, body);
    }

    /**
     * 发送密码重置邮件。
     *
     * <p>前置条件：to 为有效的邮箱地址，resetToken 为由 {@link JwtService#generateRandomToken} 生成的随机令牌。
     *
     * <p>后置条件：邮件已提交到 SMTP 服务器。若发送失败，记录日志但不抛异常。
     *
     * @param to         收件邮箱
     * @param resetToken 密码重置令牌
     */
    public void sendPasswordResetEmail(String to, String resetToken) {
        String link = mailProperties.passwordResetLinkBase() + "?token=" + resetToken;
        String subject = "重置您的迷星群聚密码";
        String body = """
                <p>您请求了密码重置。</p>
                <p>请点击以下链接重置密码：</p>
                <p><a href="{{LINK}}">{{LINK}}</a></p>
                <p>此链接仅可使用一次，若您未请求此操作，请忽略此邮件。</p>
                """.replace("{{LINK}}", link);

        sendHtmlEmail(to, subject, body);
    }

    /**
     * 发送 HTML 格式邮件。
     *
     * <p>前置条件：to、subject、htmlBody 均非空。
     *
     * <p>后置条件：邮件已提交到 SMTP。若发送失败，以 WARN 级别记录但不影响调用方流程。
     *
     * @param to       收件邮箱
     * @param subject  邮件主题
     * @param htmlBody HTML 正文
     */
    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailProperties.sender());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("邮件已发送: to={}, subject={}", to, subject);
        } catch (MessagingException e) {
            log.warn("邮件发送失败: to={}, subject={}, error={}", to, subject, e.getMessage());
        }
    }
}

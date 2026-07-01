package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * 邮件服务。
 *
 * <p>类职责：发送激活邮件和密码重置邮件。凭据通过 Spring Boot 邮件自动配置从环境变量注入。邮件 HTML 通过 Thymeleaf 模板渲染。
 *
 * <p>不变量：不记录邮件内容，不暴露 SMTP 凭据。
 */
@Slf4j
@Service
public class MailService {

    private static final String LOGO_CID = "logo";
    private static final String LOGO_PATH = "logo.png";

    private static final String ACTIVATION_TEMPLATE = "mail/activation-email";
    private static final String PASSWORD_RESET_TEMPLATE = "mail/password-reset-email";

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final TemplateEngine templateEngine;

    /**
     * @param mailSender     Spring Boot 自动配置的邮件发送器
     * @param mailProperties 邮件配置属性
     * @param templateEngine Thymeleaf 模板引擎
     */
    public MailService(JavaMailSender mailSender, MailProperties mailProperties, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.templateEngine = templateEngine;
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

        Context context = new Context();
        context.setVariable("link", link);
        context.setVariable("titleText", "激活您的迷星群聚账号");
        context.setVariable("bodyText", "感谢您注册迷星群聚！请点击下方按钮激活您的账号，开始探索身边的精彩活动与社群。");
        context.setVariable("buttonText", "激 活 账 号");
        context.setVariable("hintExtraText", "此链接 24 小时内有效，仅可使用一次。若您未注册此账号，请忽略此邮件。");

        String html = templateEngine.process(ACTIVATION_TEMPLATE, context);
        sendHtmlEmail(to, "激活您的迷星群聚账号", html);
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

        Context context = new Context();
        context.setVariable("link", link);
        context.setVariable("titleText", "重置您的迷星群聚密码");
        context.setVariable("bodyText", "我们收到了您重置密码的请求。请点击下方按钮设置新密码。");
        context.setVariable("buttonText", "重 置 密 码");
        context.setVariable("hintExtraText", "此链接 24 小时内有效，仅可使用一次。若您未请求此操作，请忽略此邮件，您的密码将保持不变。");

        String html = templateEngine.process(PASSWORD_RESET_TEMPLATE, context);
        sendHtmlEmail(to, "重置您的迷星群聚密码", html);
    }

    /**
     * 发送 HTML 格式邮件，内嵌 logo 图片。
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

            ClassPathResource logo = new ClassPathResource(LOGO_PATH);
            helper.addInline(LOGO_CID, logo);

            mailSender.send(message);
            log.info("邮件已发送: to={}, subject={}", to, subject);
        } catch (MessagingException e) {
            log.warn("邮件发送失败: to={}, subject={}, error={}", to, subject, e.getMessage());
        }
    }
}

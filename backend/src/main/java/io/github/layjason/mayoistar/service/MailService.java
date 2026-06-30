package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
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

    private static final String LOGO_CID = "logo";
    private static final String LOGO_PATH = "logo.png";

    private static final String BRAND_COLOR = "#6C5CE7";
    private static final String BG_COLOR = "#F5F3FF";
    private static final String CARD_BG = "#FFFFFF";
    private static final String TEXT_COLOR = "#2D3436";
    private static final String SUBTEXT_COLOR = "#636E72";

    private static final String EMAIL_WRAPPER = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0;padding:0;background-color:${bg};font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,'PingFang SC','Microsoft YaHei',sans-serif;">
            <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:${bg};">
              <tr>
                <td align="center" style="padding:40px 16px;">
                  <table width="480" cellpadding="0" cellspacing="0" style="max-width:480px;width:100%%;background-color:${cardBg};border-radius:16px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08);">
                    <tr>
                      <td align="center" style="padding:36px 32px 0 32px;">
                        <img src="cid:${logoCid}" alt="迷星群聚" width="56" height="56" style="display:block;border:0;border-radius:14px;">
                      </td>
                    </tr>
                    <tr>
                      <td align="center" style="padding:24px 32px 0 32px;">
                        ${titleBlock}
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:16px 32px 0 32px;font-size:15px;line-height:1.7;color:${textColor};">
                        ${bodyBlock}
                      </td>
                    </tr>
                    <tr>
                      <td align="center" style="padding:24px 32px 0 32px;">
                        ${buttonBlock}
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:16px 32px 0 32px;">
                        ${hintBlock}
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:32px 32px 24px 32px;border-top:1px solid #E8E8E8;margin-top:24px;">
                        <p style="margin:0;font-size:12px;color:${subtextColor};text-align:center;">迷星群聚 &mdash; 发现身边的精彩</p>
                      </td>
                    </tr>
                  </table>
                  <p style="margin-top:20px;font-size:12px;color:${subtextColor};">此邮件由系统自动发送，请勿回复</p>
                </td>
              </tr>
            </table>
            </body>
            </html>
            """.replace("${bg}", BG_COLOR)
            .replace("${cardBg}", CARD_BG)
            .replace("${textColor}", TEXT_COLOR)
            .replace("${subtextColor}", SUBTEXT_COLOR)
            .replace("${logoCid}", LOGO_CID);

    private static final String BUTTON_STYLE = """
            <a href="${link}" target="_blank" style="display:inline-block;padding:14px 48px;background-color:${brand};color:#FFFFFF;font-size:16px;font-weight:600;text-decoration:none;border-radius:8px;text-align:center;">${text}</a>
            """.replace("${brand}", BRAND_COLOR);

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

        String titleBlock = """
                <h1 style="margin:0;font-size:22px;font-weight:700;color:${textColor};text-align:center;">激活您的迷星群聚账号</h1>
                """.replace("${textColor}", TEXT_COLOR);

        String bodyBlock = """
                <p style="margin:0;">感谢您注册迷星群聚！请点击下方按钮激活您的账号，开始探索身边的精彩活动与社群。</p>
                """;

        String buttonBlock = BUTTON_STYLE.replace("${link}", link).replace("${text}", "激 活 账 号");

        String hintBlock = """
                <p style="margin:0;font-size:13px;color:${subtextColor};line-height:1.6;">
                  若按钮无法点击，请复制以下链接到浏览器：<br>
                  <a href="${link}" style="color:${brand};word-break:break-all;">${link}</a>
                </p>
                <p style="margin:8px 0 0 0;font-size:12px;color:${subtextColor};">此链接 24 小时内有效，仅可使用一次。<br>若您未注册此账号，请忽略此邮件。</p>
                """.replace("${link}", link)
                .replace("${subtextColor}", SUBTEXT_COLOR)
                .replace("${brand}", BRAND_COLOR);

        String html = EMAIL_WRAPPER
                .replace("${titleBlock}", titleBlock)
                .replace("${bodyBlock}", bodyBlock)
                .replace("${buttonBlock}", buttonBlock)
                .replace("${hintBlock}", hintBlock);

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

        String titleBlock = """
                <h1 style="margin:0;font-size:22px;font-weight:700;color:${textColor};text-align:center;">重置您的迷星群聚密码</h1>
                """.replace("${textColor}", TEXT_COLOR);

        String bodyBlock = """
                <p style="margin:0;">我们收到了您重置密码的请求。请点击下方按钮设置新密码。</p>
                """;

        String buttonBlock = BUTTON_STYLE.replace("${link}", link).replace("${text}", "重 置 密 码");

        String hintBlock = """
                <p style="margin:0;font-size:13px;color:${subtextColor};line-height:1.6;">
                  若按钮无法点击，请复制以下链接到浏览器：<br>
                  <a href="${link}" style="color:${brand};word-break:break-all;">${link}</a>
                </p>
                <p style="margin:8px 0 0 0;font-size:12px;color:${subtextColor};">此链接 24 小时内有效，仅可使用一次。<br>若您未请求此操作，请忽略此邮件，您的密码将保持不变。</p>
                """.replace("${link}", link)
                .replace("${subtextColor}", SUBTEXT_COLOR)
                .replace("${brand}", BRAND_COLOR);

        String html = EMAIL_WRAPPER
                .replace("${titleBlock}", titleBlock)
                .replace("${bodyBlock}", bodyBlock)
                .replace("${buttonBlock}", buttonBlock)
                .replace("${hintBlock}", hintBlock);

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

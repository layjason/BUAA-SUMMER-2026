package io.github.layjason.mayoistar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件服务配置属性。
 *
 * <p>类职责：绑定 mayoistar.mail 前缀的配置项。
 *
 * <p>类不变量：凭据仅从环境变量读取，不得写入代码或配置文件。
 *
 * @param activationLinkBase    激活链接基础 URL
 * @param passwordResetLinkBase 密码重置链接基础 URL
 * @param sender                发件邮箱地址
 */
@ConfigurationProperties(prefix = "mayoistar.mail")
public record MailProperties(String activationLinkBase, String passwordResetLinkBase, String sender) {}

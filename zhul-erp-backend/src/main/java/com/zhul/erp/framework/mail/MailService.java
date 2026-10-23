package com.zhul.erp.framework.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * 系统邮件（SMTP）：服务商在 spring.mail 配置（见 docs/06-部署运维/09-发信邮箱.md）。
 * 没配置 spring.mail.host 时 {@link #configured()} 为 false，调用方决定是报错还是只写日志（zhul.mail.log-only）。
 */
@Slf4j
@Service
public class MailService {

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String host;
    private final String username;
    private final String from;
    private final String fromName;
    private final boolean logOnly;

    public MailService(ObjectProvider<JavaMailSender> senderProvider,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${spring.mail.username:}") String username,
                       @Value("${zhul.mail.from:}") String from,
                       @Value("${zhul.mail.from-name:烛龙ERP}") String fromName,
                       @Value("${zhul.mail.log-only:false}") boolean logOnly) {
        this.senderProvider = senderProvider;
        this.host = host;
        this.username = username;
        this.from = from;
        this.fromName = fromName;
        this.logOnly = logOnly;
    }

    /** 是否配置了发信邮箱 */
    public boolean configured() {
        return StringUtils.hasText(host) && senderProvider.getIfAvailable() != null;
    }

    /** 没配置发信邮箱时是否只写日志（本地开发与测试） */
    public boolean logOnly() {
        return logOnly;
    }

    /**
     * 发一封 HTML 邮件（UTF-8）。发件人取 zhul.mail.from，为空时用登录账号。
     *
     * @throws MailSendException 连接、认证或投递失败
     */
    public void sendHtml(String to, String subject, String html) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (!StringUtils.hasText(host) || sender == null) {
            throw new MailSendException("发信邮箱未配置", null);
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String address = StringUtils.hasText(from) ? from : username;
            helper.setFrom(new InternetAddress(address, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("邮件已发送，to={}，subject={}", mask(to), subject);
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            log.warn("邮件发送失败，to={}，subject={}，原因={}", mask(to), subject, e.getMessage());
            throw new MailSendException("邮件发送失败", e);
        }
    }

    /** 日志里邮箱脱敏：ab***@example.com */
    public static String mask(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    /** 邮件没发出去 */
    public static class MailSendException extends RuntimeException {
        public MailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

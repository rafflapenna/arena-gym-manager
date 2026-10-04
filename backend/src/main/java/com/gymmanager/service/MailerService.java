package com.gymmanager.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Fire-and-forget SMTP delivery, split out of {@link InviteService} so the actual send can run
 * {@code @Async}. Self-invocation from within the same bean bypasses Spring's AOP proxy and
 * would run synchronously, hence a separate bean (see {@code @Async}'s Javadoc).
 *
 * <p>Callers pass plain strings, not entities: the send runs on a different thread outside the
 * caller's persistence context, so a lazy-loaded field (e.g. {@code AppUser.gym}) touched here
 * would risk a {@code LazyInitializationException} — everything needed must already be resolved
 * by the caller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailerService {

    private final JavaMailSender mailSender;

    @Async
    public void sendMail(String fromAddress, String fromDisplayName, String to, String replyTo,
                          String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(new InternetAddress(fromAddress, fromDisplayName, "UTF-8"));
            helper.setTo(to);
            if (StringUtils.hasText(replyTo)) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject(subject);
            helper.setText(body);

            mailSender.send(message);
        } catch (Exception e) {
            // SMTP may be unconfigured or transiently down; callers already have their own
            // fallback (e.g. the invite link returned in the API response) and never wait on
            // this thread, so a failure here is logged only.
            log.warn("Could not send email to {}: {}", to, e.getMessage());
        }
    }
}

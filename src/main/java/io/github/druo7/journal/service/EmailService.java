package io.github.druo7.journal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {
    private final ObjectProvider<JavaMailSender> mailSender;

    public EmailService(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    /** @throws org.springframework.mail.MailException on delivery failure, so callers can decide what to do */
    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Mail is not configured (spring.mail.host); skipping email '{}'", subject);
            return;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject(subject);
        mail.setText(body);
        sender.send(mail);
    }
}

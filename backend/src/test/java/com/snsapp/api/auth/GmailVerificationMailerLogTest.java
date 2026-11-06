package com.snsapp.api.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.snsapp.api.common.ApiException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ログは CloudWatch に送るので、送信に失敗してもメールアドレスを残さない */
class GmailVerificationMailerLogTest {

    @Test
    void aFailureIsLoggedWithoutTheAddress() {
        String address = "someone@example.com";
        JavaMailSenderImpl failing = new JavaMailSenderImpl() {
            @Override
            public void send(SimpleMailMessage message) {
                throw new MailSendException("送れません: " + address);
            }
        };
        GmailVerificationMailer mailer = new GmailVerificationMailer("sender@example.com", failing);
        Logger logger = (Logger) LoggerFactory.getLogger(GmailVerificationMailer.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            assertThrows(ApiException.class, () -> mailer.send(address, "http://localhost/verify-email?token=abc"));
        } finally {
            logger.detachAppender(logs);
        }

        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.get(0);
        assertTrue(event.getFormattedMessage().startsWith("確認メールを送れませんでした"));
        assertFalse(event.getFormattedMessage().contains(address));
        assertNull(event.getThrowableProxy());
    }
}

package com.snsapp.api.auth;

import com.snsapp.api.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 確認メールを Gmail から送る。app.mail.username が空のとき(ローカル)は送らず、リンクをログに出す。
 * パスワードは Google アカウントのものではなく、Gmail のアプリパスワード
 */
@Component
public class GmailVerificationMailer implements VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(GmailVerificationMailer.class);

    private final String username;
    private final JavaMailSenderImpl sender;

    @Autowired
    public GmailVerificationMailer(@Value("${app.mail.username:}") String username,
                                   @Value("${app.mail.password:}") String password) {
        this(username, gmailSender(username, password));
    }

    GmailVerificationMailer(String username, JavaMailSenderImpl sender) {
        this.username = username;
        this.sender = sender;
    }

    private static JavaMailSenderImpl gmailSender(String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(username);
        // Google の画面では4文字ずつ空白で区切って表示される
        sender.setPassword(password.replace(" ", ""));
        sender.setDefaultEncoding("UTF-8");
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        // Gmail の応答が無いときに、登録のリクエストを待たせ続けない
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    @Override
    public void send(String to, String link) {
        deliver(to, link, "RaiseLine メールアドレスの確認", """
                RaiseLine をご利用いただきありがとうございます。
                次のリンクを開いて、メールアドレスの確認を済ませてください。リンクの有効期限は24時間です。

                %s

                心当たりがない場合は、このメールを無視してください。
                """.formatted(link));
    }

    @Override
    public void sendPasswordReset(String to, String link) {
        deliver(to, link, "RaiseLine パスワードの再設定", """
                パスワードの再設定を受け付けました。
                次のリンクを開いて、新しいパスワードを決めてください。リンクの有効期限は1時間で、1回だけ使えます。

                %s

                心当たりがない場合は、このメールを無視してください。パスワードは変わりません。
                """.formatted(link));
    }

    private void deliver(String to, String link, String subject, String text) {
        if (username.isEmpty()) {
            log.info("メールは送らず、リンクをログに出します: {} {} {}", subject, to, link);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("RaiseLine <" + username + ">");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException e) {
            // 例外の文章にはメールアドレスが入ることがあるので、種類だけ出す
            log.error("確認メールを送れませんでした: {}", e.getMostSpecificCause().getClass().getSimpleName());
            throw ApiException.serviceUnavailable("確認メールを送れませんでした。時間をおいてもう一度お試しください");
        }
    }
}

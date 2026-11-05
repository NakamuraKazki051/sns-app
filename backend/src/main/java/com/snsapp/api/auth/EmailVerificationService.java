package com.snsapp.api.auth;

import com.snsapp.api.common.ApiException;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * メールアドレスの確認。登録と、メールアドレスの変更で使う。
 * リンクは1回だけ使えて、新しいリンクを送ると古いものは無効になる。
 * app.email-verification.required が false(テストと負荷試験)なら、確認を求めない
 */
@Service
public class EmailVerificationService {

    public static final Duration LINK_LIFETIME = Duration.ofHours(24);

    private final EmailVerificationRepository verifications;
    private final UserRepository users;
    private final VerificationMailer mailer;
    private final String publicUrl;
    private final boolean required;
    private final SecureRandom random = new SecureRandom();
    private Clock clock = Clock.systemUTC();

    public EmailVerificationService(EmailVerificationRepository verifications, UserRepository users,
                                    VerificationMailer mailer,
                                    @Value("${app.public-url}") String publicUrl,
                                    @Value("${app.email-verification.required}") boolean required) {
        this.verifications = verifications;
        this.users = users;
        this.mailer = mailer;
        this.publicUrl = publicUrl;
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }

    /** 確認リンクを email に送る。確認できるとそのアドレスが user のものになる */
    public void send(User user, String email) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        verifications.deleteAllOfUser(user.getId());
        verifications.save(new EmailVerification(hash(token), user.getId(), email, clock.instant().plus(LINK_LIFETIME)));
        mailer.send(email, publicUrl + "/verify-email?token=" + token);
    }

    public void verify(String token) {
        EmailVerification verification = verifications.findById(hash(token))
                .filter(v -> v.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> ApiException.badRequest("リンクが正しくないか、有効期限が切れています"));
        User user = users.findById(verification.getUserId())
                .orElseThrow(() -> ApiException.badRequest("リンクが正しくないか、有効期限が切れています"));
        String email = verification.getEmail();
        // 変更のときは、リンクを開くまでの間に他の人が同じアドレスを登録しているかもしれない
        if (!email.equals(user.getEmail()) && users.existsByEmail(email)) {
            throw ApiException.conflict("このメールアドレスは既に登録されています: " + email);
        }
        user.verifyEmail(email, clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("このメールアドレスは既に登録されています: " + email);
        }
        verifications.deleteAllOfUser(user.getId());
    }

    /** 確認されないまま期限が切れた登録を消す。残すと、他人のメールアドレスで登録するだけで本人が使えなくなる */
    public void deleteExpiredRegistrations() {
        users.deleteUnverifiedBefore(clock.instant().minus(LINK_LIFETIME));
    }

    /** テストで時間を進めるため */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

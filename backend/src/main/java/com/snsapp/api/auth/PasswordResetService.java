package com.snsapp.api.auth;

import com.snsapp.api.common.ApiException;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * パスワードの再設定(F-18)。メールで送ったリンクを開いて、新しいパスワードを決める。
 * リンクは1時間で切れ、1回だけ使える。新しいリンクを送ると古いものは使えなくなる
 */
@Service
public class PasswordResetService {

    public static final Duration LINK_LIFETIME = Duration.ofHours(1);
    private static final String INVALID = "リンクが正しくないか、有効期限が切れています。もう一度、再設定のメールを送ってください";

    private final PasswordResetRepository resets;
    private final UserRepository users;
    private final VerificationMailer mailer;
    private final PasswordEncoder passwordEncoder;
    private final String publicUrl;
    private Clock clock = Clock.systemUTC();

    public PasswordResetService(PasswordResetRepository resets, UserRepository users, VerificationMailer mailer,
                                PasswordEncoder passwordEncoder, @Value("${app.public-url}") String publicUrl) {
        this.resets = resets;
        this.users = users;
        this.mailer = mailer;
        this.passwordEncoder = passwordEncoder;
        this.publicUrl = publicUrl;
    }

    /**
     * 登録済みで確認の済んだメールアドレスなら、再設定のリンクを送る。それ以外は何もしない
     * (登録の有無が分からないよう、呼んだ側はどちらでも同じ応答を返す)
     */
    public void request(String email) {
        users.findByEmail(email).filter(User::isEmailVerified).ifPresent(user -> {
            String token = LinkTokens.newToken();
            resets.deleteAllOfUser(user.getId());
            resets.save(new PasswordReset(LinkTokens.hash(token), user.getId(), clock.instant().plus(LINK_LIFETIME)));
            mailer.sendPasswordReset(email, publicUrl + "/reset-password?token=" + token);
        });
    }

    /** 新しいパスワードにする。それまでのログインは、すべての端末で使えなくなる */
    @Transactional
    public void reset(String token, String newPassword) {
        PasswordReset reset = resets.findById(LinkTokens.hash(token))
                .filter(r -> r.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> ApiException.badRequest(INVALID));
        User user = users.findById(reset.getUserId()).orElseThrow(() -> ApiException.badRequest(INVALID));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // パスワードを盗まれて再設定した人のために、古いパスワードで入っていた端末を締め出す
        user.revokeAllTokens();
        users.save(user);
        resets.deleteAllOfUser(user.getId());
    }

    /** テストで時間を進めるため */
    void setClock(Clock clock) {
        this.clock = clock;
    }
}

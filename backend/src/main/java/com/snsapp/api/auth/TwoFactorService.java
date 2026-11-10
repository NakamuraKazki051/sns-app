package com.snsapp.api.auth;

import com.snsapp.api.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 2段階認証(F-24)。認証アプリのコード(TOTP)と、使い捨てのリカバリーコード */
@Service
public class TwoFactorService {

    public static final String ISSUER = "RaiseLine";
    public static final int RECOVERY_CODES = 10;
    // 紛らわしい文字(0/O、1/I/L)を抜いた小文字と数字
    private static final String RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RecoveryCodeRepository recoveryCodes;
    private Clock clock = Clock.systemUTC();

    public TwoFactorService(RecoveryCodeRepository recoveryCodes) {
        this.recoveryCodes = recoveryCodes;
    }

    /** テストで時刻を進める */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    /** 設定中の鍵を作る(前の設定中の鍵は捨てる)。有効なときは呼ばない */
    public String startSetup(User user) {
        String secret = Totp.newSecret();
        user.startTwoFactorSetup(secret);
        return secret;
    }

    /** 設定中の鍵でコードが合えば有効にし、リカバリーコードを返す(画面に一度だけ出す)。合わなければ null */
    @Transactional
    public List<String> enable(User user, String code) {
        if (user.getTotpPendingSecret() == null) {
            return null;
        }
        long step = Totp.match(user.getTotpPendingSecret(), normalize(code), now(), 0);
        if (step < 0) {
            return null;
        }
        user.enableTwoFactor(step);
        return newRecoveryCodes(user.getId());
    }

    /**
     * 認証アプリのコードか、まだ使っていないリカバリーコードが合うか。合えば使ったことにする
     * (同じコードはもう使えない)
     */
    @Transactional
    public boolean verify(User user, String code) {
        if (!user.hasTwoFactor() || code == null) {
            return false;
        }
        String normalized = normalize(code);
        long step = Totp.match(user.getTotpSecret(), normalized, now(), user.getTotpLastStep());
        if (step >= 0) {
            user.useTotpStep(step);
            return true;
        }
        String hash = LinkTokens.hash(normalized.toLowerCase(Locale.ROOT));
        for (RecoveryCode recovery : recoveryCodes.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (java.security.MessageDigest.isEqual(recovery.getCodeHash().getBytes(), hash.getBytes())) {
                recovery.use(now());
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void disable(User user) {
        user.disableTwoFactor();
        recoveryCodes.deleteByUser(user.getId());
    }

    public long recoveryCodesLeft(String userId) {
        return recoveryCodes.countByUserIdAndUsedAtIsNull(userId);
    }

    /** 前のリカバリーコードを捨てて、新しく作る */
    @Transactional
    public List<String> newRecoveryCodes(String userId) {
        recoveryCodes.deleteByUser(userId);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String code = randomRecoveryCode();
            codes.add(code);
            recoveryCodes.save(new RecoveryCode(userId, LinkTokens.hash(code.replace("-", ""))));
        }
        return codes;
    }

    /** 「abcd-efgh」の形(ハイフンは読みやすさのためで、無くてもよい) */
    private static String randomRecoveryCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 空白とハイフンを除く(「123 456」「abcd-efgh」と入れても通るように) */
    static String normalize(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "");
    }
}

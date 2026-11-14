package com.snsapp.api.auth;

import com.snsapp.api.user.User;
import com.snsapp.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * リフレッシュトークン(7日)を作り、アクセストークン(JWT、15分)を作り直すときに取り替える(ローテーション)。
 * 使い終わったものがもう一度届いたら、盗まれて使われたとみなし、そのログインごと無効にする(docs/auth.md)
 */
@Component
public class RefreshTokens {

    /**
     * 使い終わったものが、取り替えてからこの時間のうちに届いたときは、盗まれたとはみなさない。
     * 2つのタブが同時に作り直そうとすると、遅れた方は1つ前のトークンを送ってくるため。
     * Cookie はタブ同士で同じなので、遅れた方は何もせず、届いた新しい Cookie でやり直せばよい
     */
    static final Duration REUSE_GRACE = Duration.ofSeconds(30);

    /** 取り替えた結果。新しいトークンと、アクセストークンに入れる中身 */
    public record Rotated(String token, String userId, String sessionId, int version) {
    }

    /** 取り替えられなかった理由 */
    public enum Failure {
        /** 少し前にほかのタブが取り替えた。Cookie はもう新しいので、そのままやり直せばよい */
        JUST_ROTATED,
        /** 無い・期限切れ・ログアウト済み・パスワード変更前のもの・使い回し */
        INVALID
    }

    public record Result(Optional<Rotated> rotated, Failure failure) {
        static Result ok(Rotated rotated) {
            return new Result(Optional.of(rotated), null);
        }

        static Result fail(Failure failure) {
            return new Result(Optional.empty(), failure);
        }
    }

    private final RefreshTokenRepository repository;
    private final RevokedSessionRepository revokedSessions;
    private final UserRepository users;
    private final TokenService tokens;
    private final Duration ttl;

    public RefreshTokens(RefreshTokenRepository repository, RevokedSessionRepository revokedSessions,
                         UserRepository users, TokenService tokens,
                         @Value("${app.auth.refresh-token-ttl}") Duration ttl) {
        this.repository = repository;
        this.revokedSessions = revokedSessions;
        this.users = users;
        this.tokens = tokens;
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }

    /** ログインしたときに、新しいログインのリフレッシュトークンを作る */
    @Transactional
    public String issue(User user, String sessionId) {
        repository.deleteExpired(tokens.now());
        return save(user.getId(), sessionId, user.getTokenVersion());
    }

    /** 届いたリフレッシュトークンを、同じログインの新しいものと取り替える */
    @Transactional
    public Result rotate(String token) {
        Instant now = tokens.now();
        String hash = LinkTokens.hash(token);
        Optional<RefreshToken> found = repository.findById(hash);
        if (found.isEmpty() || !found.get().getExpiresAt().isAfter(now)) {
            return Result.fail(Failure.INVALID);
        }
        RefreshToken current = found.get();
        if (repository.markUsed(hash, now) == 0) {
            // もう使われていた。読んだあとに使われた(同時に届いた)ときは usedAt が null のまま
            Instant usedAt = current.getUsedAt();
            if (usedAt == null || usedAt.plus(REUSE_GRACE).isAfter(now)) {
                return Result.fail(Failure.JUST_ROTATED);
            }
            // 取り替えて時間が経ったものがまた届いた = 盗んだ人か本人のどちらかが古いものを持っている。
            // どちらが本物か分からないので、そのログインごと終える
            revokeSession(current.getSessionId());
            return Result.fail(Failure.INVALID);
        }
        // パスワードを変えた(世代が違う)・アカウントを消した人のものは使えない
        boolean currentVersion = users.findById(current.getUserId())
                .map(user -> user.getTokenVersion() == current.getTokenVersion())
                .orElse(false);
        if (!currentVersion || revokedSessions.existsById(current.getSessionId())) {
            return Result.fail(Failure.INVALID);
        }
        String next = save(current.getUserId(), current.getSessionId(), current.getTokenVersion());
        return Result.ok(new Rotated(next, current.getUserId(), current.getSessionId(), current.getTokenVersion()));
    }

    /** このリフレッシュトークンのログイン ID。無いもの・期限切れは空 */
    public Optional<String> sessionOf(String token) {
        return repository.findById(LinkTokens.hash(token))
                .filter(r -> r.getExpiresAt().isAfter(tokens.now()))
                .map(RefreshToken::getSessionId);
    }

    /**
     * そのログインを終える。リフレッシュトークンをすべて消し、アクセストークンも期限前に断るよう記録する。
     * アクセストークンは取り替えても同じ ID なので、今から15分たてばどれも期限切れになる
     */
    @Transactional
    public void revokeSession(String sessionId) {
        Instant now = tokens.now();
        repository.deleteBySession(sessionId);
        revokedSessions.deleteExpired(now);
        revokedSessions.save(new RevokedSession(sessionId, now.plus(tokens.ttl())));
    }

    private String save(String userId, String sessionId, int version) {
        String token = LinkTokens.newToken();
        repository.save(new RefreshToken(LinkTokens.hash(token), sessionId, userId, version, tokens.now().plus(ttl)));
        return token;
    }
}

package com.snsapp.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** リフレッシュトークン1つ分。DB にはハッシュだけを置く(RefreshTokens、docs/auth.md) */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @Column(length = 64)
    private String tokenHash;

    // どのログインか。アクセストークンの sid と同じ。取り替えても変わらない
    @Column(nullable = false, length = 36)
    private String sessionId;

    @Column(nullable = false, length = 36)
    private String userId;

    // 作ったときの users.token_version。パスワードを変えると合わなくなる
    @Column(nullable = false)
    private int tokenVersion;

    @Column(nullable = false)
    private Instant expiresAt;

    // 新しいものと取り替えた時刻。入っていれば、もう使えない
    private Instant usedAt;

    protected RefreshToken() {
    }

    public RefreshToken(String tokenHash, String sessionId, String userId, int tokenVersion, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.sessionId = sessionId;
        this.userId = userId;
        this.tokenVersion = tokenVersion;
        this.expiresAt = expiresAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}

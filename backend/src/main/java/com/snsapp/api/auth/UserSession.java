package com.snsapp.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** ログイン1回分(JWT の sid)。設定画面の「ログイン中の端末」に出す(V15__user_sessions.sql) */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    static final int USER_AGENT_MAX = 255;

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false)
    private int tokenVersion;

    @Column(length = USER_AGENT_MAX)
    private String userAgent;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastUsedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    protected UserSession() {
    }

    public UserSession(String id, String userId, int tokenVersion, String userAgent, Instant now, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenVersion = tokenVersion;
        this.userAgent = trim(userAgent);
        this.createdAt = now;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
    }

    /** アクセストークンを作り直した(端末がまだ使われている) */
    public void touch(String userAgent, Instant now, Instant expiresAt) {
        if (userAgent != null) {
            this.userAgent = trim(userAgent);
        }
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
    }

    private static String trim(String userAgent) {
        return userAgent == null || userAgent.length() <= USER_AGENT_MAX ? userAgent : userAgent.substring(0, USER_AGENT_MAX);
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

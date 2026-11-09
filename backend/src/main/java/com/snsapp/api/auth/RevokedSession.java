package com.snsapp.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** ログアウトしたログイン1つ分。この ID(JWT の sid)の JWT は、期限前でも受け付けない */
@Entity
@Table(name = "revoked_sessions")
public class RevokedSession {

    @Id
    @Column(length = 36)
    private String sessionId;

    // これより後は、この ID の JWT がどれも期限切れになる。過ぎたら行を消してよい
    @Column(nullable = false)
    private Instant expiresAt;

    protected RevokedSession() {
    }

    public RevokedSession(String sessionId, Instant expiresAt) {
        this.sessionId = sessionId;
        this.expiresAt = expiresAt;
    }
}

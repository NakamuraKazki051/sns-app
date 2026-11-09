package com.snsapp.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 送ったパスワード再設定のリンク1つ分の控え。リンクそのものではなくハッシュを持つ(F-18) */
@Entity
@Table(name = "password_resets")
public class PasswordReset {

    @Id
    @Column(length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false)
    private Instant expiresAt;

    protected PasswordReset() {
    }

    public PasswordReset(String tokenHash, String userId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

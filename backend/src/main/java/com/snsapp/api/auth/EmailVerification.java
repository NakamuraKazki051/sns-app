package com.snsapp.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 送った確認リンク1つ分の控え。リンクそのものではなくハッシュを持つ */
@Entity
@Table(name = "email_verifications")
public class EmailVerification {

    @Id
    @Column(length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Instant expiresAt;

    protected EmailVerification() {
    }

    public EmailVerification(String tokenHash, String userId, String email, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.email = email;
        this.expiresAt = expiresAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

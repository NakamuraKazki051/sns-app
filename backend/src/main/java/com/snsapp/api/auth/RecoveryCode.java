package com.snsapp.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 2段階認証のリカバリーコード1つ(F-24)。スマホをなくしたときに、認証アプリのコードの代わりに1回だけ使える */
@Entity
@Table(name = "recovery_codes")
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 64)
    private String codeHash;

    private Instant usedAt;

    protected RecoveryCode() {
    }

    public RecoveryCode(String userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void use(Instant at) {
        this.usedAt = at;
    }
}

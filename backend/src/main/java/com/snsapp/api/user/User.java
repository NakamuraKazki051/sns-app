package com.snsapp.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    public static final String USERNAME_PATTERN = "[A-Za-z0-9_]{1,15}";
    public static final int DISPLAY_NAME_MAX = 50;
    public static final int BIO_MAX = 160;

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    // MySQL の照合順序は大文字・小文字を区別しないので、Alice と alice は同じユーザー名として一意制約にかかる
    @Column(nullable = false, unique = true, length = 15)
    private String username;

    @Column(nullable = false, length = DISPLAY_NAME_MAX)
    private String displayName;

    // アイコンの更新時刻。アイコンが無ければ null。画像の URL に付けて、変更したらブラウザのキャッシュを使わないようにする
    private Instant iconUpdatedAt;

    // 自己紹介。160字は見た目の1文字で数えるので、絵文字を考えて長めの列にする
    @Column(length = 2000)
    private String bio;

    private Instant createdAt;

    // メールアドレスを確認した時刻。確認が済むまで null で、ログインできない
    private Instant emailVerifiedAt;

    // ログインの JWT の世代。進めると、それまでに渡した JWT がすべて使えなくなる(パスワードの変更など)
    @Column(nullable = false)
    private int tokenVersion;

    // 2段階認証(F-24)。totpSecret が null なら無効。totpPendingSecret は設定中の鍵
    @Column(length = 64)
    private String totpSecret;

    @Column(length = 64)
    private String totpPendingSecret;

    @Column(nullable = false)
    private long totpLastStep;

    protected User() {
    }

    public User(String email, String passwordHash, String username, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.username = username;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** email の持ち主だと確かめられた */
    public void verifyEmail(String email, Instant at) {
        this.email = email;
        this.emailVerifiedAt = at;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    /** それまでに渡したログインの JWT を、すべての端末で使えなくする */
    public void revokeAllTokens() {
        tokenVersion++;
    }

    /** 2段階認証(F-24)が有効か */
    public boolean hasTwoFactor() {
        return totpSecret != null;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public String getTotpPendingSecret() {
        return totpPendingSecret;
    }

    public long getTotpLastStep() {
        return totpLastStep;
    }

    /** 設定を始める。認証アプリでコードを確かめるまでは有効にしない */
    public void startTwoFactorSetup(String secret) {
        this.totpPendingSecret = secret;
    }

    public void enableTwoFactor(long usedStep) {
        this.totpSecret = totpPendingSecret;
        this.totpPendingSecret = null;
        this.totpLastStep = usedStep;
    }

    public void disableTwoFactor() {
        this.totpSecret = null;
        this.totpPendingSecret = null;
        this.totpLastStep = 0;
    }

    /** 使ったコードの時間枠を覚える(同じコードをもう一度使えないように) */
    public void useTotpStep(long step) {
        this.totpLastStep = step;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Instant getIconUpdatedAt() {
        return iconUpdatedAt;
    }

    public void setIconUpdatedAt(Instant iconUpdatedAt) {
        this.iconUpdatedAt = iconUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

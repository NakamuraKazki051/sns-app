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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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

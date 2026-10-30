package com.snsapp.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** アイコン画像。users と分けて、投稿一覧などでユーザーを読むたびに画像まで読まないようにする */
@Entity
@Table(name = "user_icons")
public class UserIcon {

    public static final int MAX_BYTES = 1024 * 1024;

    @Id
    @Column(length = 36)
    private String userId;

    @Column(nullable = false, length = 20)
    private String contentType;

    // MySQL では長さから MEDIUMBLOB になる
    @Column(nullable = false, length = MAX_BYTES)
    private byte[] data;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserIcon() {
    }

    public UserIcon(String userId) {
        this.userId = userId;
    }

    public void replace(String contentType, byte[] data, Instant updatedAt) {
        this.contentType = contentType;
        this.data = data;
        this.updatedAt = updatedAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

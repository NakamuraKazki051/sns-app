package com.snsapp.api.post;

import com.snsapp.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "posts", indexes = @Index(name = "idx_posts_user_id_id", columnList = "user_id, id"))
public class Post {

    public static final int BODY_MAX = 140;

    // ID は連番。新しい投稿ほど大きいので、「もっと見る」は id < before で続きを取れる
    // (UUID だと、目印にした投稿が削除されたときに続きの位置が分からなくなる)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 140字は見た目の1文字で数えるので、絵文字(1字が複数のコードポイント)を考えて長めの列にする
    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    protected Post() {
    }

    public Post(User user, String body) {
        this.user = user;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

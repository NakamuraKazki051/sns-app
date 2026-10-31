package com.snsapp.api.repost;

import com.snsapp.api.post.Post;
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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

// テーブルは db/migration/V2__reposts.sql で作る
@Entity
@Table(name = "reposts",
        uniqueConstraints = @UniqueConstraint(name = "uk_reposts_user_id_post_id", columnNames = {"user_id", "post_id"}),
        indexes = {
                @Index(name = "idx_reposts_user_id_id", columnList = "user_id, id"),
                @Index(name = "idx_reposts_post_id", columnList = "post_id"),
        })
public class Repost {

    // タイムラインの「もっと見る」の目印に使う。新しいリポストほど大きい
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 投稿を消したら、その投稿のリポストも DB が一緒に消す
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Post post;

    @Column(nullable = false)
    private Instant createdAt;

    protected Repost() {
    }

    public Repost(User user, Post post) {
        this.user = user;
        this.post = post;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Post getPost() {
        return post;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.snsapp.api.like;

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

/** いいね。1人1投稿1回だけなのを (user_id, post_id) の一意制約で DB に守らせる(連打しても二重に入らない) */
@Entity
@Table(name = "likes",
        uniqueConstraints = @UniqueConstraint(name = "uk_likes_user_id_post_id", columnNames = {"user_id", "post_id"}),
        indexes = @Index(name = "idx_likes_post_id", columnList = "post_id"))
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 投稿を消したら、その投稿へのいいねも DB が一緒に消す
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Post post;

    @Column(nullable = false)
    private Instant createdAt;

    protected PostLike() {
    }

    public PostLike(User user, Post post) {
        this.user = user;
        this.post = post;
        this.createdAt = Instant.now();
    }
}

package com.snsapp.api.follow;

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

import java.time.Instant;

/** follower が followee をフォローしている。同じ組み合わせは一意制約で1回だけ */
@Entity
@Table(name = "follows",
        uniqueConstraints = @UniqueConstraint(name = "uk_follows_follower_id_followee_id",
                columnNames = {"follower_id", "followee_id"}),
        indexes = @Index(name = "idx_follows_followee_id_id", columnList = "followee_id, id"))
public class Follow {

    // 一覧の「もっと見る」の目印に使う。新しくフォローしたものほど大きい
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "followee_id", nullable = false)
    private User followee;

    @Column(nullable = false)
    private Instant createdAt;

    protected Follow() {
    }

    public Follow(User follower, User followee) {
        this.follower = follower;
        this.followee = followee;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getFollower() {
        return follower;
    }

    public User getFollowee() {
        return followee;
    }
}
